package app.timeline.islamic.core.retrieval

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusPackManifest
import app.timeline.islamic.core.knowledge.CorpusTextNormalizer
import app.timeline.islamic.core.knowledge.EvidenceIdentity
import app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord
import app.timeline.islamic.core.knowledge.RetrievalFilter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BundledSqliteCorpusIndexTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun validatesPackAndReturnsOriginalArabicEvidenceThroughFts5() = runBlocking {
        val file = newIndexFile("valid")
        try {
            buildIndex(file)
            val validator = BundledSqliteCorpusIndex()
            val report = validator.validate(file, manifest(), asOfEpochMillis = 200)
            assertEquals(1, report.sourceCount)
            assertEquals(1, report.passageCount)
            assertEquals(setOf(CorpusLanguage.ARABIC), report.languages)

            val pack = InstalledCorpusPackRecord(
                packId = "fixture-pack", version = "1.0.0", manifestSha256 = "d".repeat(64),
                installedAtEpochMillis = 150, licenseProfile = "fixture-cleared"
            )
            val query = CorpusTextNormalizer.normalize("الله").ftsExpression
            val result = validator.search(
                pack, file, query,
                RetrievalFilter(setOf(CorpusLanguage.ARABIC), selectedMadhhab = null, asOfEpochMillis = 200),
                limit = 5
            )
            assertEquals(1, result.size)
            assertEquals(ORIGINAL_TEXT, result.single().originalText)
            assertEquals("2:255", result.single().locator)
        } finally {
            deleteIndexFiles(file)
        }
    }

    @Test fun rejectsAnFtsIndexWhosePostingsDoNotMatchItsContentTable() {
        val file = newIndexFile("broken-fts")
        try {
            buildIndex(file, corruptFts = true)
            assertThrows(Exception::class.java) {
                BundledSqliteCorpusIndex().validate(file, manifest(), asOfEpochMillis = 200)
            }
        } finally {
            deleteIndexFiles(file)
        }
    }

    @Test fun rejectsNormalizedTextThatDoesNotMatchItsOriginalPassage() {
        val file = newIndexFile("bad-normalization")
        try {
            buildIndex(file, normalizedTextOverride = "unrelated injected search terms")
            assertThrows(IllegalArgumentException::class.java) {
                BundledSqliteCorpusIndex().validate(file, manifest(), asOfEpochMillis = 200)
            }
        } finally {
            deleteIndexFiles(file)
        }
    }

    @Test fun searchExcludesFutureDatedReviewBeforeApplyingCandidateLimit() = runBlocking {
        val file = newIndexFile("future-review")
        try {
            buildIndex(file, reviewedAtEpochMillis = 250L)
            val pack = InstalledCorpusPackRecord(
                packId = "fixture-pack", version = "1.0.0", manifestSha256 = "d".repeat(64),
                installedAtEpochMillis = 150, licenseProfile = "fixture-cleared"
            )
            val result = BundledSqliteCorpusIndex().search(
                pack, file, CorpusTextNormalizer.normalize("الله").ftsExpression,
                RetrievalFilter(setOf(CorpusLanguage.ARABIC), selectedMadhhab = null, asOfEpochMillis = 200),
                limit = 5
            )
            assertEquals(0, result.size)
        } finally {
            deleteIndexFiles(file)
        }
    }

    @Test fun rejectsPassageWhoseOriginalTextHashDoesNotMatch() {
        val file = newIndexFile("bad-hash")
        try {
            buildIndex(file, contentHashOverride = "e".repeat(64))
            assertThrows(IllegalArgumentException::class.java) {
                BundledSqliteCorpusIndex().validate(file, manifest(), asOfEpochMillis = 200)
            }
        } finally {
            deleteIndexFiles(file)
        }
    }

    private fun buildIndex(
        file: File,
        contentHashOverride: String? = null,
        normalizedTextOverride: String? = null,
        corruptFts: Boolean = false,
        licenseVerifiedAtEpochMillis: Long = 100L,
        reviewedAtEpochMillis: Long = 101L
    ) {
        BundledSQLiteDriver().open(file.absolutePath).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("PRAGMA journal_mode = DELETE")
            db.execSQL("PRAGMA application_id = 1413563971")
            db.execSQL("PRAGMA user_version = 1")
            db.execSQL("CREATE TABLE pack_metadata (`key` TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL) WITHOUT ROWID")
            db.execSQL("""CREATE TABLE source (
                source_id TEXT PRIMARY KEY NOT NULL, source_type TEXT NOT NULL, title TEXT NOT NULL,
                author TEXT, publisher TEXT, edition_id TEXT NOT NULL, edition TEXT NOT NULL,
                rights_holder TEXT NOT NULL, license_id TEXT NOT NULL, license_url TEXT,
                license_status TEXT NOT NULL, license_verified_at_epoch_ms INTEGER,
                license_expires_at_epoch_ms INTEGER, review_status TEXT NOT NULL, reviewer_id TEXT,
                reviewed_at_epoch_ms INTEGER, provenance_sha256 TEXT NOT NULL
            )""".trimIndent())
            db.execSQL("""CREATE TABLE passage (
                rowid INTEGER PRIMARY KEY, passage_id TEXT NOT NULL UNIQUE,
                source_id TEXT NOT NULL REFERENCES source(source_id) ON DELETE RESTRICT,
                locator TEXT NOT NULL, language TEXT NOT NULL, original_text TEXT NOT NULL,
                normalized_text TEXT NOT NULL, normalizer_version TEXT NOT NULL,
                madhhab_scope TEXT NOT NULL, source_grade TEXT, stable_evidence_key TEXT NOT NULL,
                content_sha256 TEXT NOT NULL
            )""".trimIndent())
            db.execSQL("""CREATE VIRTUAL TABLE passage_fts USING fts5(
                passage_id UNINDEXED, normalized_text, content='passage', content_rowid='rowid',
                tokenize='unicode61 remove_diacritics 2'
            )""".trimIndent())
            db.execSQL("""CREATE TRIGGER passage_ai AFTER INSERT ON passage BEGIN
                INSERT INTO passage_fts(rowid, passage_id, normalized_text)
                VALUES (new.rowid, new.passage_id, new.normalized_text);
            END""".trimIndent())
            listOf(
                "schema_version" to "1", "pack_id" to "fixture-pack", "pack_version" to "1.0.0",
                "license_profile" to "fixture-cleared", "normalizer_version" to "timeline-normalizer-v1",
                "builder_version" to "android-test"
            ).forEach { (key, value) ->
                db.prepare("INSERT INTO pack_metadata(`key`, value) VALUES (?, ?)").use { insert ->
                    insert.bindText(1, key); insert.bindText(2, value); insert.step()
                }
            }
            db.prepare("""INSERT INTO source(
                source_id, source_type, title, author, publisher, edition_id, edition, rights_holder,
                license_id, license_url, license_status, license_verified_at_epoch_ms,
                license_expires_at_epoch_ms, review_status, reviewer_id, reviewed_at_epoch_ms, provenance_sha256
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent()).use { insert ->
                val values: List<Any?> = listOf(
                    "quran-source", "QURAN", "Qur'an", null, "Test publisher", "edition-1", "Edition 1",
                    "Test rights holder", "license-1", null, "CLEARED", licenseVerifiedAtEpochMillis, null,
                    "APPROVED", "reviewer-1", reviewedAtEpochMillis, "a".repeat(64)
                )
                values.forEachIndexed { index, value ->
                    when (value) {
                        null -> insert.bindNull(index + 1)
                        is Long -> insert.bindLong(index + 1, value)
                        else -> insert.bindText(index + 1, value.toString())
                    }
                }
                insert.step()
            }
            db.prepare("""INSERT INTO passage(
                passage_id, source_id, locator, language, original_text, normalized_text,
                normalizer_version, madhhab_scope, source_grade, stable_evidence_key, content_sha256
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""".trimIndent()).use { insert ->
                val normalized = CorpusTextNormalizer.normalize(ORIGINAL_TEXT).text
                val values = listOf(
                    "ayah-2-255", "quran-source", "2:255", "ar", ORIGINAL_TEXT,
                    normalizedTextOverride ?: normalized,
                    "timeline-normalizer-v1", "ALL", "quran", EvidenceIdentity.stableKey("quran-source", "edition-1", "2:255", CorpusLanguage.ARABIC),
                    contentHashOverride ?: EvidenceIdentity.contentSha256(ORIGINAL_TEXT)
                )
                values.forEachIndexed { index, value -> insert.bindText(index + 1, value) }
                insert.step()
            }
            if (corruptFts) db.execSQL("DELETE FROM passage_fts")
        }
    }

    private fun manifest() = CorpusPackManifest(
        schemaVersion = 1, packId = "fixture-pack", packVersion = "1.0.0", createdAtEpochMillis = 150,
        keyId = "test-key", licenseProfile = "fixture-cleared", sourceCount = 1, passageCount = 1,
        indexFile = "corpus.sqlite", indexSha256 = "b".repeat(64), languages = setOf(CorpusLanguage.ARABIC)
    )

    private fun newIndexFile(prefix: String): File = File(context.cacheDir, "$prefix-${System.nanoTime()}.sqlite")

    private fun deleteIndexFiles(file: File) {
        file.delete()
        File(file.parentFile, file.name + "-wal").delete()
        File(file.parentFile, file.name + "-shm").delete()
        File(file.parentFile, file.name + "-journal").delete()
    }


    companion object {
        private const val ORIGINAL_TEXT = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ"
    }
}
