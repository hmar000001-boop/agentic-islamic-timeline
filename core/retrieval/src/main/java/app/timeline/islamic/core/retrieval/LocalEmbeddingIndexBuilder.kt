package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.DenseEmbeddingIndexStore
import app.timeline.islamic.core.knowledge.EmbeddingPassageSource
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LocalTextEmbeddingModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds a staged, local-only vector generation. No passage text is persisted in the dense index.
 * Activation is atomic; cancellation or model errors leave the previous active generation intact.
 */
class LocalEmbeddingIndexBuilder(
    private val source: EmbeddingPassageSource,
    private val store: DenseEmbeddingIndexStore,
    private val maximumEncodedBytes: Long = DEFAULT_MAXIMUM_ENCODED_BYTES,
    private val batchSize: Int = DEFAULT_BATCH_SIZE
) {
    data class Result(val generationId: String, val vectorCount: Long, val encodedBytes: Long, val modelId: String)

    init {
        require(maximumEncodedBytes in 1_048_576L..MAXIMUM_ENCODED_BYTES_LIMIT)
        require(batchSize in 1..64)
    }

    suspend fun build(
        pack: InstalledCorpusPackRecord,
        indexFile: File,
        model: LocalTextEmbeddingModel
    ): Result {
        require(pack.status == InstalledCorpusPackRecord.STATUS_INSTALLED)
        require(indexFile.isFile && indexFile.length() > 0L) {
            "Corpus index file is missing or empty"
        }
        val descriptor = model.descriptor
        val generationId = store.beginGeneration(pack, descriptor, maximumEncodedBytes)
        var vectors = 0L
        var encodedBytes = 0L
        var afterRowId = 0L
        var previousKey: String? = null
        try {
            while (true) {
                val batch = source.readBatch(pack, indexFile, afterRowId, batchSize)
                if (batch.isEmpty()) break
                require(batch.size <= batchSize) { "Passage source exceeded requested batch size" }
                require(batch.zipWithNext().all { (a, b) -> b.rowId > a.rowId }) { "Passage source rows are not strictly ordered" }
                require(batch.first().rowId > afterRowId) { "Passage source cursor did not advance" }
                if (previousKey != null) require(previousKey != batch.first().stableEvidenceKey) { "Duplicate cursor boundary evidence key" }
                batch.forEach { entry ->
                    require(entry.originalText.length <= descriptor.maximumInputCharacters) {
                        "Passage exceeds this model's declared input character limit"
                    }
                }
                val embeddings = model.embedBatch(batch.map { it.originalText })
                require(embeddings.size == batch.size) { "Embedding adapter returned a different batch size" }
                val encoded = LinkedHashMap<String, app.timeline.islamic.core.knowledge.QuantizedEmbeddingVector>(batch.size)
                batch.forEachIndexed { index, passage ->
                    require(embeddings[index].size == descriptor.dimension) { "Embedding model returned the wrong dimension" }
                    val vector = QuantizedEmbeddingCodec.encode(embeddings[index])
                    require(encoded.put(passage.stableEvidenceKey, vector) == null) { "Duplicate stable evidence key in batch" }
                    encodedBytes += vector.dimension.toLong()
                }
                require(encodedBytes <= maximumEncodedBytes) { "Dense index exceeds the configured on-device storage budget" }
                store.appendVectors(generationId, encoded)
                vectors += batch.size
                afterRowId = batch.last().rowId
                previousKey = batch.last().stableEvidenceKey
            }
            require(vectors > 0L) { "Corpus pack contains no passages to embed" }
            store.activateGeneration(generationId)
            return Result(generationId, vectors, encodedBytes, descriptor.modelId)
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) { store.abortGeneration(generationId) }
            throw cancelled
        } catch (failure: Exception) {
            withContext(NonCancellable + Dispatchers.IO) { store.abortGeneration(generationId) }
            throw failure
        }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 24
        const val DEFAULT_MAXIMUM_ENCODED_BYTES = 128L * 1024L * 1024L
        const val MAXIMUM_ENCODED_BYTES_LIMIT = 128L * 1024L * 1024L
    }
}
