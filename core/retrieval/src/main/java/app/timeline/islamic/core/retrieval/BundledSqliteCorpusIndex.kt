package app.timeline.islamic.core.retrieval

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.timeline.islamic.core.knowledge.CorpusIndexValidationReport
import app.timeline.islamic.core.knowledge.CorpusIndexValidator
import app.timeline.islamic.core.knowledge.CorpusLanguage
import app.timeline.islamic.core.knowledge.CorpusPackManifest
import app.timeline.islamic.core.knowledge.CorpusSourceMetadata
import app.timeline.islamic.core.knowledge.CorpusSourceType
import app.timeline.islamic.core.knowledge.CorpusTextNormalizer
import app.timeline.islamic.core.knowledge.EvidenceIdentity
import app.timeline.islamic.core.knowledge.EvidencePassage
import app.timeline.islamic.core.knowledge.LicenseState
import app.timeline.islamic.core.knowledge.MadhhabScope
import app.timeline.islamic.core.knowledge.RetrievalFilter
import app.timeline.islamic.core.knowledge.ScholarlyReviewState
import java.io.File

/**
 * Uses AndroidX's bundled SQLite implementation, not android.database.sqlite, because corpus packs
 * require FTS5 on every supported Android release. A fresh connection is scoped to a single operation.
 */
class BundledSqliteCorpusIndex(
    private val driver: BundledSQLiteDriver = BundledSQLiteDriver()
) : CorpusIndexValidator, CorpusPackIndexSearcher {

    override fun validate(indexFile: File, manifest: CorpusPackManifest, asOfEpochMillis: Long): CorpusIndexValidationReport {
        require(indexFile.isFile && indexFile.length() > 0L) { "Corpus SQLite file is absent or empty" }
        return open(indexFile).use { connection ->
            // FTS5's external-content integrity check is an INSERT command. Run it before
            // query_only is enabled; rank=1 requests comparison against the content table.
            connection.execSQL("INSERT INTO passage_fts(passage_fts, rank) VALUES ('integrity-check', 1)")
            connection.execSQL("PRAGMA query_only = ON")
            val journalMode = scalarText(connection, "PRAGMA journal_mode")
            require(journalMode.equals("delete", ignoreCase = true)) { "Corpus index must use a self-contained DELETE journal mode" }
            val integrity = connection.prepare("PRAGMA integrity_check").use { statement ->
                require(statement.step()) { "SQLite integrity_check returned no result" }
                statement.getText(0)
            }
            require(integrity == "ok") { "SQLite integrity_check failed: $integrity" }
            val foreignKeyErrors = connection.prepare("PRAGMA foreign_key_check").use { statement ->
                var count = 0
                while (statement.step()) count++
                count
            }
            require(foreignKeyErrors == 0) { "Corpus index has foreign-key violations" }
            val userVersion = scalarLong(connection, "PRAGMA user_version")
            require(userVersion == 1L && manifest.schemaVersion == userVersion.toInt()) { "Unsupported corpus index schema" }
            require(tableExists(connection, "passage_fts", "table")) { "FTS5 passage_fts table is missing" }
            // Preparing a MATCH statement proves that the installed SQLite binary actually has FTS5.
            connection.prepare("SELECT passage_id FROM passage_fts WHERE passage_fts MATCH ? LIMIT 0").use { /* compile only */ }
            require(tableExists(connection, "source", "table") && tableExists(connection, "passage", "table") && tableExists(connection, "pack_metadata", "table")) {
                "Corpus index is missing required tables"
            }
            val appId = scalarLong(connection, "PRAGMA application_id")
            require(appId == 1413563971L) { "Corpus SQLite application_id is invalid" }
            val internalSchema = metadata(connection, "schema_version")
            require(metadata(connection, "pack_id") == manifest.packId) { "pack_id metadata does not match manifest" }
            require(metadata(connection, "pack_version") == manifest.packVersion) { "pack_version metadata does not match manifest" }
            require(metadata(connection, "license_profile") == manifest.licenseProfile) { "license_profile metadata does not match manifest" }
            require(internalSchema == "1") { "Corpus pack metadata schema_version is missing or invalid" }
            val normalizerVersion = metadata(connection, "normalizer_version")
            require(normalizerVersion == CorpusTextNormalizer.VERSION) { "Unsupported or missing corpus normalizer version" }
            require(metadata(connection, "builder_version").isNotBlank()) { "Corpus builder version is missing" }
            val invalidRows = scalarLong(connection, """
                SELECT
                  (SELECT COUNT(*) FROM source WHERE
                    source_id IS NULL OR trim(source_id) = '' OR length(source_id) > 128 OR
                    source_type IS NULL OR source_type NOT IN ('QURAN','HADITH','TAFSIR','FIQH','REFERENCE') OR
                    title IS NULL OR trim(title) = '' OR length(title) > 512 OR
                    (author IS NOT NULL AND length(author) > 512) OR
                    (publisher IS NOT NULL AND length(publisher) > 256) OR
                    (license_url IS NOT NULL AND length(license_url) > 2048) OR
                    edition_id IS NULL OR trim(edition_id) = '' OR length(edition_id) > 128 OR
                    edition IS NULL OR trim(edition) = '' OR length(edition) > 512 OR
                    rights_holder IS NULL OR trim(rights_holder) = '' OR length(rights_holder) > 256 OR
                    license_id IS NULL OR trim(license_id) = '' OR length(license_id) > 128 OR
                    license_status IS NULL OR license_status NOT IN ('CLEARED','RESTRICTED','UNKNOWN','EXPIRED') OR
                    review_status IS NULL OR review_status NOT IN ('APPROVED','PENDING','REJECTED') OR
                    provenance_sha256 IS NULL OR length(provenance_sha256) <> 64 OR provenance_sha256 GLOB '*[^a-f0-9]*') +
                  (SELECT COUNT(*) FROM passage WHERE
                    passage_id IS NULL OR trim(passage_id) = '' OR length(passage_id) > 160 OR
                    source_id IS NULL OR locator IS NULL OR trim(locator) = '' OR length(locator) > 512 OR
                    language IS NULL OR language NOT IN ('ar','en') OR madhhab_scope IS NULL OR
                    madhhab_scope NOT IN ('ALL','COMPARATIVE','HANAFI','MALIKI','SHAFII','HANBALI') OR
                    original_text IS NULL OR length(original_text) NOT BETWEEN 1 AND 100000 OR
                    (source_grade IS NOT NULL AND length(source_grade) > 128) OR
                    normalized_text IS NULL OR length(normalized_text) NOT BETWEEN 1 AND 100000 OR
                    normalizer_version IS NULL OR normalizer_version <> '${CorpusTextNormalizer.VERSION}' OR
                    stable_evidence_key IS NULL OR length(stable_evidence_key) <> 64 OR stable_evidence_key GLOB '*[^a-f0-9]*' OR
                    content_sha256 IS NULL OR length(content_sha256) <> 64 OR content_sha256 GLOB '*[^a-f0-9]*')
            """.trimIndent())
            val orphanPassages = scalarLong(
                connection,
                "SELECT COUNT(*) FROM passage p LEFT JOIN source s ON s.source_id = p.source_id WHERE s.source_id IS NULL"
            )
            require(invalidRows == 0L) { "Corpus contains null, malformed, oversized or unsupported source/passage fields" }
            require(orphanPassages == 0L) { "Corpus contains passages without source/edition metadata" }
            val ineligibleSources = connection.prepare(
                "SELECT COUNT(*) FROM source WHERE license_status <> 'CLEARED' OR trim(license_id) = '' OR trim(rights_holder) = '' OR license_verified_at_epoch_ms IS NULL OR license_verified_at_epoch_ms <= 0 OR license_verified_at_epoch_ms > ? OR (license_expires_at_epoch_ms IS NOT NULL AND license_expires_at_epoch_ms <= ?) OR review_status <> 'APPROVED' OR reviewer_id IS NULL OR trim(reviewer_id) = '' OR reviewed_at_epoch_ms IS NULL OR reviewed_at_epoch_ms <= 0 OR reviewed_at_epoch_ms > ? OR length(provenance_sha256) <> 64 OR provenance_sha256 GLOB '*[^a-f0-9]*'"
            ).use { statement ->
                statement.bindLong(1, asOfEpochMillis)
                statement.bindLong(2, asOfEpochMillis)
                statement.bindLong(3, asOfEpochMillis)
                if (!statement.step()) throw IllegalArgumentException("Source eligibility query returned no result")
                statement.getLong(0)
            }
            require(ineligibleSources == 0L) { "Pack contains a source with uncleared redistribution rights or missing scholarly approval" }
            val sourceCount = scalarLong(connection, "SELECT COUNT(*) FROM source").toInt()
            val passageCount = scalarLong(connection, "SELECT COUNT(*) FROM passage").toInt()
            require(sourceCount == manifest.sourceCount) { "Source count does not match manifest" }
            require(passageCount == manifest.passageCount) { "Passage count does not match manifest" }
            val languages = mutableSetOf<CorpusLanguage>()
            connection.prepare("SELECT DISTINCT language FROM passage ORDER BY language").use { statement ->
                while (statement.step()) {
                    val language = CorpusLanguage.fromCode(statement.getText(0))
                        ?: throw IllegalArgumentException("Corpus uses an unsupported language")
                    languages += language
                }
            }
            require(languages == manifest.languages) { "Passage languages do not match manifest" }
            // Validate every source-text hash, canonical search form and citation identity before
            // installation. Retrieval independently rechecks returned passages as defense in depth.
            connection.prepare(
                "SELECT p.source_id, s.edition_id, p.locator, p.language, p.stable_evidence_key, p.original_text, p.content_sha256, p.normalized_text FROM passage p JOIN source s ON s.source_id = p.source_id ORDER BY p.rowid"
            ).use { statement ->
                while (statement.step()) {
                    val language = CorpusLanguage.fromCode(statement.getText(3))
                        ?: throw IllegalArgumentException("Unsupported passage language")
                    val stableKey = EvidenceIdentity.stableKey(
                        statement.getText(0), statement.getText(1), statement.getText(2), language
                    )
                    require(stableKey == statement.getText(4)) { "Passage stable evidence identity is invalid" }
                    val originalText = statement.getText(5)
                    require(EvidenceIdentity.contentSha256(originalText) == statement.getText(6)) {
                        "Passage original_text SHA-256 is invalid"
                    }
                    val expectedNormalizedText = CorpusTextNormalizer.normalize(originalText).text
                    require(expectedNormalizedText == statement.getText(7)) {
                        "Passage normalized_text does not match original_text and the declared normalizer"
                    }
                }
            }
            CorpusIndexValidationReport(userVersion.toInt(), sourceCount, passageCount, languages)
        }
    }

    override suspend fun search(
        pack: app.timeline.islamic.core.knowledge.InstalledCorpusPackRecord,
        indexFile: File,
        query: String,
        filter: RetrievalFilter,
        limit: Int
    ): List<EvidencePassage> {
        require(limit in 1..500)
        if (query.isBlank()) return emptyList()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            require(indexFile.isFile) { "Corpus index no longer exists" }
            open(indexFile).use { connection ->
                connection.execSQL("PRAGMA query_only = ON")
                val languageSql = filter.languages.joinToString(",") { "?" }
                require(filter.languages.isNotEmpty())
                val madhhabClause = if (filter.selectedMadhhab == null) "" else " AND p.madhhab_scope IN ('ALL','COMPARATIVE', ?)"
                val sql = """
                    SELECT p.passage_id, p.stable_evidence_key, p.source_id, p.locator, p.language,
                           p.original_text, p.normalized_text, p.content_sha256, p.madhhab_scope, p.source_grade,
                           s.source_type, s.title, s.author, s.publisher, s.edition_id, s.edition,
                           s.rights_holder, s.license_id, s.license_url, s.license_status,
                           s.license_verified_at_epoch_ms, s.license_expires_at_epoch_ms,
                           s.review_status, s.reviewer_id, s.reviewed_at_epoch_ms, s.provenance_sha256
                    FROM passage_fts
                    JOIN passage p ON p.rowid = passage_fts.rowid
                    JOIN source s ON s.source_id = p.source_id
                    WHERE passage_fts MATCH ?
                      AND s.license_status = 'CLEARED'
                      AND trim(s.license_id) <> ''
                      AND trim(s.rights_holder) <> ''
                      AND s.license_verified_at_epoch_ms IS NOT NULL
                      AND s.license_verified_at_epoch_ms > 0
                      AND s.license_verified_at_epoch_ms <= ?
                      AND (s.license_expires_at_epoch_ms IS NULL OR s.license_expires_at_epoch_ms > ?)
                      AND s.review_status = 'APPROVED'
                      AND s.reviewer_id IS NOT NULL AND trim(s.reviewer_id) <> ''
                      AND s.reviewed_at_epoch_ms IS NOT NULL
                      AND s.reviewed_at_epoch_ms > 0
                      AND s.reviewed_at_epoch_ms <= ?
                      AND p.language IN ($languageSql)
                      $madhhabClause
                    ORDER BY bm25(passage_fts) ASC, p.stable_evidence_key ASC, p.passage_id ASC
                    LIMIT ?
                """.trimIndent()
                val rows = mutableListOf<EvidencePassage>()
                connection.prepare(sql).use { statement ->
                    var bind = 1
                    statement.bindText(bind++, query)
                    statement.bindLong(bind++, filter.asOfEpochMillis) // license verified no later than the query time
                    statement.bindLong(bind++, filter.asOfEpochMillis) // not expired at the query time
                    statement.bindLong(bind++, filter.asOfEpochMillis) // scholarly review no later than the query time
                    filter.languages.map { it.code }.sorted().forEach { statement.bindText(bind++, it) }
                    filter.selectedMadhhab?.let { statement.bindText(bind++, it.code) }
                    statement.bindLong(bind, limit.toLong())
                    while (statement.step()) {
                        val sourceType = CorpusSourceType.fromCode(statement.getText(10)) ?: continue
                        val licenseState = LicenseState.entries.firstOrNull { it.code == statement.getText(19) } ?: continue
                        val reviewState = ScholarlyReviewState.entries.firstOrNull { it.code == statement.getText(22) } ?: continue
                        val language = CorpusLanguage.fromCode(statement.getText(4)) ?: continue
                        val madhhab = MadhhabScope.fromCode(statement.getText(8)) ?: continue
                        val source = CorpusSourceMetadata(
                            sourceId = statement.getText(2), sourceType = sourceType, title = statement.getText(11),
                            author = nullableText(statement, 12), publisher = nullableText(statement, 13),
                            editionId = statement.getText(14), edition = statement.getText(15),
                            rightsHolder = statement.getText(16), licenseId = statement.getText(17),
                            licenseUrl = nullableText(statement, 18), licenseState = licenseState,
                            licenseVerifiedAtEpochMillis = nullableLong(statement, 20),
                            licenseExpiresAtEpochMillis = nullableLong(statement, 21),
                            reviewState = reviewState, reviewerId = nullableText(statement, 23),
                            reviewedAtEpochMillis = nullableLong(statement, 24), provenanceSha256 = statement.getText(25)
                        )
                        rows += EvidencePassage(
                            packId = pack.packId, packVersion = pack.version, passageId = statement.getText(0),
                            stableEvidenceKey = statement.getText(1), source = source, locator = statement.getText(3),
                            language = language, originalText = statement.getText(5), normalizedText = statement.getText(6),
                            contentSha256 = statement.getText(7), madhhabScope = madhhab,
                            sourceGrade = nullableText(statement, 9)
                        )
                    }
                }
                rows
            }
        }
    }

    private fun open(file: File): SQLiteConnection = driver.open(file.absolutePath)

    private fun scalarLong(connection: SQLiteConnection, sql: String): Long = connection.prepare(sql).use { statement ->
        if (!statement.step()) throw IllegalArgumentException("SQLite scalar query returned no value")
        statement.getLong(0)
    }

    private fun scalarText(connection: SQLiteConnection, sql: String): String = connection.prepare(sql).use { statement ->
        if (!statement.step()) throw IllegalArgumentException("SQLite scalar query returned no value")
        statement.getText(0)
    }

    private fun metadata(connection: SQLiteConnection, key: String): String = connection.prepare(
        "SELECT value FROM pack_metadata WHERE key = ? LIMIT 1"
    ).use { statement ->
        statement.bindText(1, key)
        if (!statement.step()) "" else statement.getText(0)
    }

    private fun tableExists(connection: SQLiteConnection, name: String, type: String): Boolean = connection.prepare(
        "SELECT 1 FROM sqlite_master WHERE type = ? AND name = ? LIMIT 1"
    ).use { statement ->
        statement.bindText(1, type)
        statement.bindText(2, name)
        statement.step()
    }

    private fun nullableText(statement: SQLiteStatement, index: Int): String? = if (statement.isNull(index)) null else statement.getText(index)
    private fun nullableLong(statement: SQLiteStatement, index: Int): Long? = if (statement.isNull(index)) null else statement.getLong(index)
}
