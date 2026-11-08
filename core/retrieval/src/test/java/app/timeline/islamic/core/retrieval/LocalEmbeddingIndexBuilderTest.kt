package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.DenseEmbeddingIndexStore
import app.timeline.islamic.core.knowledge.DenseIndexGeneration
import app.timeline.islamic.core.knowledge.EmbeddingPassageInput
import app.timeline.islamic.core.knowledge.EmbeddingPassageSource
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.LocalEmbeddingModelDescriptor
import app.timeline.islamic.core.knowledge.LocalTextEmbeddingModel
import app.timeline.islamic.core.knowledge.QuantizedEmbeddingVector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalEmbeddingIndexBuilderTest {
    private val pack = InstalledCorpusPackRecord("fixture-pack", "1.0.0", "a".repeat(64), 1L, "fixture")
    private val descriptor = LocalEmbeddingModelDescriptor("test-model", "1.0", "b".repeat(64), 16)

    @Test fun completedBuildActivatesOnlyAfterAllBatchesAreWritten() = runBlocking {
        val fixtureStore = FixtureStore()
        val file = File.createTempFile("corpus-test", ".sqlite")
        try {
            val source = FixtureSource(listOf(
                EmbeddingPassageInput(1, "1".repeat(64), "Arabic passage"),
                EmbeddingPassageInput(2, "2".repeat(64), "English passage")
            ))
            val model = fixtureModel { text -> FloatArray(16) { if (text.startsWith("Arabic")) 0.75f else 0.25f } }
            val result = LocalEmbeddingIndexBuilder(source, fixtureStore, batchSize = 1).build(pack, file, model)
            assertEquals(2L, result.vectorCount)
            assertTrue(fixtureStore.activated)
            assertEquals(2, fixtureStore.staged.size)
            assertEquals(0, fixtureStore.abortCount)
        } finally { file.delete() }
    }

    @Test fun modelFailureAbortsStagingAndNeverActivates() = runBlocking {
        val fixtureStore = FixtureStore()
        val file = File.createTempFile("corpus-test", ".sqlite")
        try {
            val source = FixtureSource(listOf(EmbeddingPassageInput(1, "1".repeat(64), "bad output")))
            val model = object : LocalTextEmbeddingModel {
                override val descriptor = this@LocalEmbeddingIndexBuilderTest.descriptor
                override suspend fun embedBatch(texts: List<String>) = listOf(FloatArray(8))
            }
            runCatching { LocalEmbeddingIndexBuilder(source, fixtureStore).build(pack, file, model) }
            assertEquals(1, fixtureStore.abortCount)
            assertTrue(!fixtureStore.activated)
        } finally { file.delete() }
    }

    @Test fun builderCannotExceedThePerGenerationRoomBudget() {
        val tooLarge = 129L * 1024L * 1024L
        val failure = runCatching { LocalEmbeddingIndexBuilder(FixtureSource(emptyList()), FixtureStore(), maximumEncodedBytes = tooLarge) }
        assertTrue(failure.isFailure)
    }

    @Test fun oversizedPassageBatchIsRejectedAndAborted() = runBlocking {
        val fixtureStore = FixtureStore()
        val file = File.createTempFile("corpus-test", ".sqlite")
        try {
            val source = EmbeddingPassageSource { _, _, after, limit ->
                if (after == 0L) (1L..(limit + 1)).map { EmbeddingPassageInput(it, it.toString().padStart(64, '0'), "text") } else emptyList()
            }
            val model = fixtureModel { FloatArray(16) { 1f } }
            runCatching { LocalEmbeddingIndexBuilder(source, fixtureStore).build(pack, file, model) }
            assertEquals(1, fixtureStore.abortCount)
            assertTrue(!fixtureStore.activated)
        } finally { file.delete() }
    }

    private fun fixtureModel(factory: (String) -> FloatArray) = object : LocalTextEmbeddingModel {
        override val descriptor = this@LocalEmbeddingIndexBuilderTest.descriptor
        override suspend fun embedBatch(texts: List<String>) = texts.map(factory)
    }

    private class FixtureSource(private val rows: List<EmbeddingPassageInput>) : EmbeddingPassageSource {
        override suspend fun readBatch(pack: InstalledCorpusPackRecord, indexFile: File, afterRowId: Long, limit: Int) =
            rows.filter { it.rowId > afterRowId }.take(limit)
    }

    private class FixtureStore : DenseEmbeddingIndexStore {
        val staged = mutableMapOf<String, QuantizedEmbeddingVector>()
        var activated = false
        var abortCount = 0
        override suspend fun beginGeneration(pack: InstalledCorpusPackRecord, model: LocalEmbeddingModelDescriptor, maximumTotalEncodedBytes: Long) = "generation"
        override suspend fun appendVectors(generationId: String, vectors: Map<String, QuantizedEmbeddingVector>): Long {
            staged.putAll(vectors); return staged.values.sumOf { it.dimension.toLong() }
        }
        override suspend fun activateGeneration(generationId: String) { activated = true }
        override suspend fun abortGeneration(generationId: String) { abortCount++; staged.clear() }
        override suspend fun removePackVersion(packId: String, packVersion: String) = 0
        override suspend fun recoverAbandonedGenerations() = 0
        override suspend fun activeGeneration(packId: String, packVersion: String, modelId: String, modelVersion: String): DenseIndexGeneration? = null
        override suspend fun vectorsFor(generationId: String, stableEvidenceKeys: List<String>) = emptyMap<String, QuantizedEmbeddingVector>()
    }
}
