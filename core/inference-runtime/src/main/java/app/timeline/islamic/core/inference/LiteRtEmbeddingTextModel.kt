package app.timeline.islamic.core.inference

import app.timeline.islamic.core.knowledge.LocalEmbeddingModelDescriptor
import app.timeline.islamic.core.knowledge.LocalTextEmbeddingModel
import app.timeline.islamic.core.models.ModelCatalog
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sqrt

/**
 * EmbeddingGemma 2 text-only adapter for the existing Phase 4 LocalTextEmbeddingModel contract.
 * Indexing and querying use the model's distinct documented instruction prefixes. The provider
 * is opened only around a bounded operation and closed afterwards; no second multi-GB engine is
 * held alongside a generation engine by the app lifecycle.
 */
class LiteRtEmbeddingTextModel private constructor(
    private val engine: EmbeddingEngine
) : LocalTextEmbeddingModel {
    private val operationMutex = Mutex()
    @Volatile private var closed = false

    override val descriptor = descriptorForCatalog()

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> = operationMutex.withLock {
        check(!closed) { "Embedding model is closed" }
        require(texts.isNotEmpty() && texts.size <= MAX_BATCH_SIZE) { "Embedding batch must contain 1..$MAX_BATCH_SIZE passages" }
        val prefixed = texts.map(::documentInput)
        val responses = engine.computeEmbeddingBatchAsync(
            contentsBatch = prefixed.map { listOf(InputData.Text(it)) },
            options = EmbeddingOptions(normalize = true, outputSize = EMBEDDING_DIMENSION)
        )
        require(responses.size == texts.size) { "LiteRT-LM returned a different number of vectors" }
        responses.map { response -> validateVector(response.embedding) }
    }

    /** Query-side helper using the asymmetric query task prefix required by EmbeddingGemma retrieval. */
    suspend fun embedSearchQuery(text: String): FloatArray = operationMutex.withLock {
        check(!closed) { "Embedding model is closed" }
        val normalized = normalizeInput(text)
        val response = engine.computeEmbeddingAsync(
            contents = listOf(InputData.Text("$QUERY_PREFIX$normalized")),
            options = EmbeddingOptions(normalize = true, outputSize = EMBEDDING_DIMENSION)
        )
        validateVector(response.embedding)
    }

    override suspend fun embedQuery(text: String): FloatArray = embedSearchQuery(text)

    suspend fun close() = operationMutex.withLock {
        if (!closed) {
            // Mark closed only after native close succeeds so a failed close can be retried.
            withContext(Dispatchers.IO + NonCancellable) { engine.close() }
            closed = true
        }
    }

    private fun validateVector(vector: FloatArray): FloatArray = EmbeddingVectorPolicy.validate(vector)

    companion object {
        const val EMBEDDING_MODEL_ID = "embeddinggemma-2-text-270m"
        const val EMBEDDING_DIMENSION = 768
        const val MAX_INPUT_TOKENS = 8_192
        // LiteRT-LM's EmbeddingGemma 2 guidance defines asymmetric prefixes for retrieval.
        // Do not substitute SentenceTransformers' PromptName formatting here.
        const val DOCUMENT_PREFIX = "task: search result | text: "
        const val QUERY_PREFIX = "task: search query | text: "
        /** Raw passage/query allowance after reserving space for the longest task prefix. */
        val MAX_INPUT_CHARACTERS = MAX_INPUT_TOKENS - maxOf(DOCUMENT_PREFIX.length, QUERY_PREFIX.length)
        const val MAX_BATCH_SIZE = 24

        /** Descriptor used by both the live engine and preflight/index-state inspection. */
        fun descriptorForCatalog(): LocalEmbeddingModelDescriptor {
            val spec = ModelCatalog.require(EMBEDDING_MODEL_ID)
            return LocalEmbeddingModelDescriptor(
                modelId = EMBEDDING_MODEL_ID,
                modelVersion = spec.revision,
                artifactSha256 = spec.sha256,
                dimension = EMBEDDING_DIMENSION,
                maximumInputCharacters = MAX_INPUT_CHARACTERS,
                documentPrefix = DOCUMENT_PREFIX,
                queryPrefix = QUERY_PREFIX,
                normalizationPolicy = "embeddinggemma2-l2-normalized-int8-symmetric-v1"
            )
        }

        fun documentInput(text: String): String = "$DOCUMENT_PREFIX${normalizeInput(text)}"

        fun queryInput(text: String): String = "$QUERY_PREFIX${normalizeInput(text)}"

        private fun normalizeInput(text: String): String {
            val trimmed = text.trim()
            require(trimmed.isNotEmpty()) { "Embedding input must not be blank" }
            require(trimmed.length <= MAX_INPUT_CHARACTERS) {
                "Embedding input exceeds $MAX_INPUT_CHARACTERS characters"
            }
            return trimmed
        }

        suspend fun open(modelFile: File, cacheDirectory: File): LiteRtEmbeddingTextModel {
            require(modelFile.isFile && modelFile.length() == ModelCatalog.require(EMBEDDING_MODEL_ID).expectedBytes) {
                "Verified EmbeddingGemma artifact is unavailable"
            }
            val callerContext = currentCoroutineContext()
            val engine = withContext(Dispatchers.IO + NonCancellable) {
                val created = EmbeddingEngine(
                    EmbeddingEngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU(),
                        cacheDir = File(cacheDirectory, "embedding-$EMBEDDING_MODEL_ID").apply { mkdirs() }.absolutePath,
                        maxInputLength = MAX_INPUT_TOKENS
                    )
                )
                try {
                    created.initialize()
                    created
                } catch (failure: Throwable) {
                    // Close only when the SDK reports a live native handle.
                    if (created.isInitialized()) runCatching { created.close() }
                    throw failure
                }
            }
            try {
                callerContext.ensureActive()
                return LiteRtEmbeddingTextModel(engine)
            } catch (cancelled: CancellationException) {
                withContext(Dispatchers.IO + NonCancellable) {
                    if (engine.isInitialized()) runCatching { engine.close() }
                }
                throw cancelled
            }
        }

    }
}

/** Strict validation boundary shared by indexing/query paths and unit tests. */
internal object EmbeddingVectorPolicy {
    const val REQUIRED_DIMENSION = 768

    fun validate(vector: FloatArray): FloatArray {
        require(vector.size == REQUIRED_DIMENSION) {
            "EmbeddingGemma returned ${vector.size} dimensions; $REQUIRED_DIMENSION are required by the active index contract"
        }
        require(vector.all { it.isFinite() }) { "Embedding contains NaN or infinite values" }
        val norm = sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
        require(norm.isFinite() && norm in 0.90..1.10) {
            "EmbeddingGemma normalized vector has an invalid L2 norm"
        }
        return vector
    }
}
