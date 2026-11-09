package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.DenseEmbeddingIndexStore
import app.timeline.islamic.core.knowledge.DenseIndexGeneration
import app.timeline.islamic.core.knowledge.DenseIndexVectorRecord
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

    @Test fun completedBuildActivatesOnlyAfterAllBatchesAreWritten(): Unit = runBlocking {
        val fixtureStore = FixtureStore()
        val file = createFixtureIndexFile()
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

    @Test fun modelFailureAbortsStagingAndNeverActivates(): Unit = runBlocking {
        val fixtureStore = FixtureStore()
        val file = createFixtureIndexFile()
        try {
            val source = FixtureSource(listOf(EmbeddingPassageInput(1, "1".repeat(64), "bad output")))
            val model = object : LocalTextEmbeddingModel {
                override val descriptor = this@LocalEmbeddingIndexBuilderTest.descriptor
                override suspend fun embedBatch(texts: List<String>) = listOf(FloatArray(8))
            }
            val failure = runCatching { LocalEmbeddingIndexBuilder(source, fixtureStore).build(pack, file, model) }
            assertTrue("Wrong-dimension model output must fail the build", failure.isFailure)
            assertEquals("Embedding model returned the wrong dimension", failure.exceptionOrNull()?.message)
            assertEquals(1, fixtureStore.beginCount)
            assertEquals(1, fixtureStore.abortCount)
            assertTrue(!fixtureStore.activated)
        } finally { file.delete() }
    }

    @Test fun emptyIndexFileIsRejectedBeforeCreatingAStagingGeneration(): Unit = runBlocking {
        val fixtureStore = FixtureStore()
        val emptyFile = File.createTempFile("empty-corpus-test", ".sqlite")
        try {
            val failure = runCatching {
                LocalEmbeddingIndexBuilder(FixtureSource(emptyList()), fixtureStore).build(
                    pack, emptyFile, fixtureModel { FloatArray(16) { 0.5f } }
                )
            }
            assertTrue("An empty corpus database must be rejected", failure.isFailure)
            assertEquals("Corpus index file is missing or empty", failure.exceptionOrNull()?.message)
            assertEquals(0, fixtureStore.beginCount)
            assertEquals(0, fixtureStore.abortCount)
            assertTrue(!fixtureStore.activated)
        } finally { emptyFile.delete() }
    }

    @Test fun builderCannotExceedThePerGenerationRoomBudget() {
        val tooLarge = 129L * 1024L * 1024L
        val failure = runCatching { LocalEmbeddingIndexBuilder(FixtureSource(emptyList()), FixtureStore(), maximumEncodedBytes = tooLarge) }
        assertTrue(failure.isFailure)
    }

    @Test fun oversizedPassageBatchIsRejectedAndAborted(): Unit = runBlocking {
        val fixtureStore = FixtureStore()
        val file = createFixtureIndexFile()
        try {
            val source = EmbeddingPassageSource { _, _, after, limit, _ ->
                if (after == 0L) (1L..(limit + 1)).map { EmbeddingPassageInput(it, it.toString().padStart(64, '0'), "text") } else emptyList()
            }
            val model = fixtureModel { FloatArray(16) { 1f } }
            val failure = runCatching { LocalEmbeddingIndexBuilder(source, fixtureStore).build(pack, file, model) }
            assertTrue("A source that exceeds the requested batch must fail", failure.isFailure)
            assertEquals("Passage source exceeded requested batch size", failure.exceptionOrNull()?.message)
            assertEquals(1, fixtureStore.beginCount)
            assertEquals(1, fixtureStore.abortCount)
            assertTrue(!fixtureStore.activated)
        } finally { file.delete() }
    }

    /** FixtureSource is deliberately in-memory; the production source opens/validates SQLite. */
    private fun createFixtureIndexFile(): File = File.createTempFile("corpus-test", ".sqlite").apply {
        writeText("fixture database placeholder consumed only by FixtureSource")
    }

    private fun fixtureModel(factory: (String) -> FloatArray) = object : LocalTextEmbeddingModel {
        override val descriptor = this@LocalEmbeddingIndexBuilderTest.descriptor
        override suspend fun embedBatch(texts: List<String>) = texts.map(factory)
    }

    private class FixtureSource(private val rows: List<EmbeddingPassageInput>) : EmbeddingPassageSource {
        override suspend fun readBatch(pack: InstalledCorpusPackRecord, indexFile: File, afterRowId: Long, limit: Int, maximumInputCharacters: Int) =
            rows.filter { it.rowId > afterRowId }.take(limit)
    }

    private class FixtureStore : DenseEmbeddingIndexStore {
        val staged = mutableMapOf<String, QuantizedEmbeddingVector>()
        var activated = false
        var abortCount = 0
        var beginCount = 0
        override suspend fun beginGeneration(pack: InstalledCorpusPackRecord, model: LocalEmbeddingModelDescriptor, maximumTotalEncodedBytes: Long): String {
            beginCount++
            return "generation"
        }
        override suspend fun appendVectors(generationId: String, vectors: Map<String, QuantizedEmbeddingVector>): Long {
            staged.putAll(vectors); return staged.values.sumOf { it.dimension.toLong() }
        }
        override suspend fun activateGeneration(generationId: String) { activated = true }
        override suspend fun abortGeneration(generationId: String) { abortCount++; staged.clear() }
        override suspend fun removePackVersion(packId: String, packVersion: String) = 0
        override suspend fun recoverAbandonedGenerations() = 0
        override suspend fun activeGeneration(packId: String, packVersion: String, modelId: String, modelVersion: String): DenseIndexGeneration? = null
        override suspend fun vectorsFor(generationId: String, stableEvidenceKeys: List<String>) = emptyMap<String, QuantizedEmbeddingVector>()
        override suspend fun vectorsPage(generationId: String, afterStableEvidenceKey: String, limit: Int) = emptyList<DenseIndexVectorRecord>()
    }
}
