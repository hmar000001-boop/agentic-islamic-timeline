package app.timeline.islamic.core.persistence

import androidx.room.withTransaction
import app.timeline.islamic.core.knowledge.DenseEmbeddingIndexStore
import app.timeline.islamic.core.knowledge.DenseIndexGeneration
import app.timeline.islamic.core.knowledge.DenseIndexVectorRecord
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LocalEmbeddingModelDescriptor
import app.timeline.islamic.core.knowledge.QuantizedEmbeddingVector
import java.util.UUID

/** All derived vectors are persisted inside the SQLCipher-encrypted application Room database. */
class RoomDenseEmbeddingIndexStore(private val database: AppDatabase) : DenseEmbeddingIndexStore {
    private val dao get() = database.denseIndexDao()

    override suspend fun beginGeneration(
        pack: InstalledCorpusPackRecord,
        model: LocalEmbeddingModelDescriptor,
        maximumTotalEncodedBytes: Long
    ): String {
        require(pack.status == InstalledCorpusPackRecord.STATUS_INSTALLED)
        require(maximumTotalEncodedBytes in MIN_GENERATION_BYTES..MAX_GENERATION_BYTES)
        val id = UUID.randomUUID().toString()
        dao.insertGeneration(
            DenseIndexGenerationEntity(
                generationId = id,
                packId = pack.packId,
                packVersion = pack.version,
                modelId = model.modelId,
                modelVersion = model.modelVersion,
                modelSha256 = model.artifactSha256,
                dimension = model.dimension,
                vectorCount = 0,
                encodedBytes = 0,
                maximumEncodedBytes = maximumTotalEncodedBytes,
                state = STATE_STAGING,
                createdAtEpochMillis = System.currentTimeMillis(),
                corpusManifestSha256 = pack.manifestSha256,
                embeddingProfileSha256 = model.embeddingProfileSha256
            )
        )
        return id
    }

    override suspend fun appendVectors(generationId: String, vectors: Map<String, QuantizedEmbeddingVector>): Long = database.withTransaction {
        require(vectors.isNotEmpty() && vectors.size <= 64)
        val generation = dao.generation(generationId) ?: error("Dense index generation does not exist")
        check(generation.state == STATE_STAGING) { "Only a staging generation accepts vectors" }
        val batchBytes = vectors.values.sumOf { it.dimension.toLong() }
        require(generation.encodedBytes + batchBytes <= generation.maximumEncodedBytes) {
            "Dense index generation storage budget exceeded"
        }
        require(dao.totalEncodedBytes() + batchBytes <= MAX_TOTAL_STORE_BYTES) {
            "Total dense index storage budget exceeded"
        }
        val entities = vectors.map { (key, vector) ->
            require(key.matches(Regex("[a-f0-9]{64}")))
            require(vector.dimension == generation.dimension)
            DenseIndexVectorEntity(generationId, key, vector.values)
        }
        dao.insertVectors(entities)
        check(dao.addGenerationBytes(generationId, vectors.size.toLong(), batchBytes) == 1) {
            "Dense index generation changed during append"
        }
        generation.encodedBytes + batchBytes
    }

    override suspend fun activateGeneration(generationId: String): Unit {
        database.withTransaction {
            val incoming = dao.generation(generationId) ?: error("Dense index generation does not exist")
            check(incoming.state == STATE_STAGING && incoming.vectorCount > 0) {
                "Only non-empty staging generations can become active"
            }
            dao.retireActiveGeneration(incoming.packId, incoming.packVersion, incoming.modelId, incoming.modelVersion)
            check(dao.activateStagingGeneration(generationId) == 1) { "Dense index activation lost its compare-and-set" }
            // Deleting the retired generation cascades to its vectors, in the same encrypted DB transaction.
            dao.deleteRetiredGenerations(incoming.packId, incoming.packVersion, incoming.modelId, incoming.modelVersion)
            Unit
        }
    }

    override suspend fun abortGeneration(generationId: String) {
        dao.deleteStagingGeneration(generationId)
    }

    override suspend fun removePackVersion(packId: String, packVersion: String): Int = database.withTransaction {
        val count = dao.countPackGenerations(packId, packVersion)
        dao.deletePackGenerations(packId, packVersion)
        count
    }

    override suspend fun recoverAbandonedGenerations(): Int = database.withTransaction {
        val count = dao.countStagingGenerations()
        dao.deleteAllStagingGenerations()
        count
    }

    override suspend fun activeGeneration(
        packId: String,
        packVersion: String,
        modelId: String,
        modelVersion: String
    ): DenseIndexGeneration? = dao.activeGeneration(packId, packVersion, modelId, modelVersion)?.toContract()

    override suspend fun activeGeneration(
        pack: InstalledCorpusPackRecord,
        model: LocalEmbeddingModelDescriptor
    ): DenseIndexGeneration? = dao.activeGeneration(
        pack.packId, pack.version, model.modelId, model.modelVersion
    )?.toContract()?.takeIf {
        it.corpusManifestSha256 == pack.manifestSha256 &&
            it.modelSha256 == model.artifactSha256 &&
            it.dimension == model.dimension &&
            it.embeddingProfileSha256 == model.embeddingProfileSha256 &&
            it.state == STATE_ACTIVE
    }

    override suspend fun vectorsFor(generationId: String, stableEvidenceKeys: List<String>): Map<String, QuantizedEmbeddingVector> {
        require(stableEvidenceKeys.size <= 500)
        if (stableEvidenceKeys.isEmpty()) return emptyMap()
        val generation = dao.generation(generationId) ?: return emptyMap()
        if (generation.state != STATE_ACTIVE) return emptyMap()
        return buildMap {
            stableEvidenceKeys.distinct().chunked(400).forEach { batch ->
                dao.vectors(generationId, batch).forEach { row ->
                    put(row.stableEvidenceKey, QuantizedEmbeddingVector(generation.dimension, row.vector))
                }
            }
        }
    }

    override suspend fun vectorsPage(
        generationId: String,
        afterStableEvidenceKey: String,
        limit: Int
    ): List<DenseIndexVectorRecord> {
        require(afterStableEvidenceKey.isEmpty() || afterStableEvidenceKey.matches(Regex("[a-f0-9]{64}")))
        require(limit in 1..MAX_PAGE_SIZE)
        val generation = dao.generation(generationId) ?: return emptyList()
        if (generation.state != STATE_ACTIVE) return emptyList()
        return dao.vectorsPage(generationId, afterStableEvidenceKey, limit).map { row ->
            require(row.stableEvidenceKey.matches(Regex("[a-f0-9]{64}"))) { "Dense index contains an invalid evidence key" }
            require(row.vector.size == generation.dimension) { "Dense index contains a vector with the wrong dimension" }
            DenseIndexVectorRecord(row.stableEvidenceKey, QuantizedEmbeddingVector(generation.dimension, row.vector))
        }
    }

    private fun DenseIndexGenerationEntity.toContract() = DenseIndexGeneration(
        generationId, packId, packVersion, modelId, modelVersion, modelSha256,
        dimension, vectorCount, encodedBytes, state, corpusManifestSha256, embeddingProfileSha256
    )

    private companion object {
        const val STATE_STAGING = "STAGING"
        const val STATE_ACTIVE = "ACTIVE"
        const val MIN_GENERATION_BYTES = 1_048_576L
        const val MAX_GENERATION_BYTES = 128L * 1024L * 1024L
        const val MAX_TOTAL_STORE_BYTES = 256L * 1024L * 1024L
        const val MAX_PAGE_SIZE = 2048
    }
}
