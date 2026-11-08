package app.timeline.islamic.core.knowledge

import java.io.File

/** Languages are ISO 639-1 values used by Corpus Pack v1. */
enum class CorpusLanguage(val code: String) {
    ARABIC("ar"), ENGLISH("en");

    companion object {
        fun fromCode(value: String): CorpusLanguage? = entries.firstOrNull { it.code == value }
    }
}

enum class CorpusSourceType(val code: String) {
    QURAN("QURAN"), HADITH("HADITH"), TAFSIR("TAFSIR"), FIQH("FIQH"), REFERENCE("REFERENCE");

    companion object {
        fun fromCode(value: String): CorpusSourceType? = entries.firstOrNull { it.code == value }
    }
}

enum class LicenseState(val code: String) { CLEARED("CLEARED"), RESTRICTED("RESTRICTED"), UNKNOWN("UNKNOWN"), EXPIRED("EXPIRED") }
enum class ScholarlyReviewState(val code: String) { APPROVED("APPROVED"), PENDING("PENDING"), REJECTED("REJECTED") }

enum class MadhhabScope(val code: String) {
    ALL("ALL"), COMPARATIVE("COMPARATIVE"), HANAFI("HANAFI"), MALIKI("MALIKI"), SHAFII("SHAFII"), HANBALI("HANBALI");

    companion object {
        fun fromCode(value: String): MadhhabScope? = entries.firstOrNull { it.code == value }
    }
}

data class CorpusSourceMetadata(
    val sourceId: String,
    val sourceType: CorpusSourceType,
    val title: String,
    val author: String?,
    val publisher: String?,
    val editionId: String,
    val edition: String,
    val rightsHolder: String,
    val licenseId: String,
    val licenseUrl: String?,
    val licenseState: LicenseState,
    val licenseVerifiedAtEpochMillis: Long?,
    val licenseExpiresAtEpochMillis: Long?,
    val reviewState: ScholarlyReviewState,
    val reviewerId: String?,
    val reviewedAtEpochMillis: Long?,
    val provenanceSha256: String
)

/**
 * Evidence is source text, never an LLM paraphrase. originalText is immutable source transcription;
 * normalizedText is only an index field and must not be displayed as a quotation.
 */
data class EvidencePassage(
    val packId: String,
    val packVersion: String,
    val passageId: String,
    val stableEvidenceKey: String,
    val source: CorpusSourceMetadata,
    val locator: String,
    val language: CorpusLanguage,
    val originalText: String,
    val normalizedText: String,
    val contentSha256: String,
    val madhhabScope: MadhhabScope,
    val sourceGrade: String? = null
)

data class RetrievalRequest(
    val queryText: String,
    val languages: Set<CorpusLanguage>,
    val selectedMadhhab: MadhhabScope? = null,
    val maxResults: Int = 12,
    val maxCandidatesPerPack: Int = 100
) {
    init {
        require(queryText.isNotBlank()) { "queryText must not be blank" }
        require(queryText.length <= 4096) { "queryText exceeds the 4096-character safety limit" }
        require(languages.isNotEmpty()) { "At least one language is required" }
        require(maxResults in 1..100) { "maxResults must be between 1 and 100" }
        require(maxCandidatesPerPack in maxResults..500) { "maxCandidatesPerPack must be between maxResults and 500" }
        require(selectedMadhhab == null || selectedMadhhab in setOf(
            MadhhabScope.HANAFI, MadhhabScope.MALIKI, MadhhabScope.SHAFII, MadhhabScope.HANBALI
        )) { "Selected madhhab must be one of the four Sunni schools" }
    }
}

data class NormalizedQuery(val text: String, val tokens: List<String>, val ftsExpression: String)

data class RetrievalFilter(val languages: Set<CorpusLanguage>, val selectedMadhhab: MadhhabScope?, val asOfEpochMillis: Long)

data class CorpusPackManifest(
    val schemaVersion: Int,
    val packId: String,
    val packVersion: String,
    val createdAtEpochMillis: Long,
    val keyId: String,
    val licenseProfile: String,
    val sourceCount: Int,
    val passageCount: Int,
    val indexFile: String,
    val indexSha256: String,
    val languages: Set<CorpusLanguage>
)

data class InstalledCorpusPackRecord(
    val packId: String,
    val version: String,
    val manifestSha256: String,
    val installedAtEpochMillis: Long,
    val licenseProfile: String,
    val status: String = STATUS_INSTALLED
) {
    val stableId: String get() = "$packId@$version"
    val relativeIndexPath: String get() = "$packId/$version/corpus.sqlite"

    companion object {
        const val STATUS_INSTALLED = "INSTALLED"
        const val STATUS_QUARANTINED = "QUARANTINED"
        const val STATUS_SUPERSEDED = "SUPERSEDED"
        const val STATUS_REMOVAL_PENDING = "REMOVAL_PENDING"
    }
}

/** Registry operations are deliberately compare-and-set shaped to make lifecycle transitions race-safe. */
interface CorpusPackRegistry {
    suspend fun isInstalled(packId: String, version: String): Boolean
    suspend fun register(record: InstalledCorpusPackRecord)
    suspend fun listInstalled(): List<InstalledCorpusPackRecord>

    /** Includes quarantined, superseded and removal-pending rows for recovery and audit. */
    suspend fun listAll(): List<InstalledCorpusPackRecord> = listInstalled()

    /** Atomically switches the one active version to a new version; false means state changed. */
    suspend fun activateReplacement(expectedCurrentVersion: String, replacement: InstalledCorpusPackRecord): Boolean = false

    /** Atomic status transition guarded by the expected current state. */
    suspend fun transitionStatus(stableId: String, expectedStatus: String, newStatus: String): Boolean = false

    /** Deletes only the registry row; callers must first complete the filesystem lifecycle. */
    suspend fun removeRecord(stableId: String): Boolean = false
}

/** Exact signed publisher-keyring bytes stored in the encrypted application database. */
class PublisherKeyringEnvelope(
    val version: Long,
    payload: ByteArray,
    signature: ByteArray,
    val installedAtEpochMillis: Long
) {
    private val payloadBytes = payload.copyOf()
    private val signatureBytes = signature.copyOf()
    val payload: ByteArray get() = payloadBytes.copyOf()
    val signature: ByteArray get() = signatureBytes.copyOf()

    init {
        require(version > 0)
        require(payloadBytes.isNotEmpty() && payloadBytes.size <= 256 * 1024)
        require(signatureBytes.size == 64)
        require(installedAtEpochMillis > 0)
    }
}

interface PublisherKeyringStore {
    suspend fun read(): PublisherKeyringEnvelope?
    /** Atomically writes only when stored version equals expectedCurrentVersion and incoming is newer. */
    suspend fun writeIfCurrentVersion(expectedCurrentVersion: Long, envelope: PublisherKeyringEnvelope): Boolean
}

data class CorpusIndexValidationReport(
    val schemaVersion: Int,
    val sourceCount: Int,
    val passageCount: Int,
    val languages: Set<CorpusLanguage>
)

/** Implemented by the bundled SQLite layer; called before a pack is made visible. */
interface CorpusIndexValidator {
    fun validate(indexFile: File, manifest: CorpusPackManifest, asOfEpochMillis: Long): CorpusIndexValidationReport
}

/** Hard eligibility is intentionally independent of retrieval rank or model confidence. */
object EvidenceEligibility {
    fun isEligible(source: CorpusSourceMetadata, nowEpochMillis: Long): Boolean =
        source.licenseState == LicenseState.CLEARED &&
            source.licenseId.isNotBlank() &&
            source.rightsHolder.isNotBlank() &&
            source.licenseVerifiedAtEpochMillis != null && source.licenseVerifiedAtEpochMillis in 1..nowEpochMillis &&
            (source.licenseExpiresAtEpochMillis == null || nowEpochMillis < source.licenseExpiresAtEpochMillis) &&
            source.reviewState == ScholarlyReviewState.APPROVED &&
            !source.reviewerId.isNullOrBlank() && source.reviewedAtEpochMillis != null && source.reviewedAtEpochMillis in 1..nowEpochMillis &&
            source.provenanceSha256.matches(Regex("[a-f0-9]{64}"))

    fun matchesMadhhab(scope: MadhhabScope, selected: MadhhabScope?): Boolean =
        selected == null || scope == MadhhabScope.ALL || scope == MadhhabScope.COMPARATIVE || scope == selected
}
