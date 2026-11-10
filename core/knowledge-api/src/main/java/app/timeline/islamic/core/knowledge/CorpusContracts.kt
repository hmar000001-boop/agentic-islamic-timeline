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

/** A locally installed, cryptographically identified embedding model; no remote provider is allowed. */
data class LocalEmbeddingModelDescriptor(
    val modelId: String,
    val modelVersion: String,
    val artifactSha256: String,
    val dimension: Int,
    val maximumInputCharacters: Int = 16_384,
    /** Model-specific asymmetric retrieval instructions. These are part of the index identity. */
    val documentPrefix: String = "",
    val queryPrefix: String = "",
    /** Versioned normalization/quantization contract; changing it requires a fresh generation. */
    val normalizationPolicy: String = "l2-normalized-int8-symmetric-v1"
) {
    val embeddingProfileSha256: String = profileFingerprint(
        modelId, modelVersion, artifactSha256, dimension, maximumInputCharacters,
        documentPrefix, queryPrefix, normalizationPolicy
    )

    init {
        require(modelId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")))
        require(modelVersion.isNotBlank() && modelVersion.length <= 64)
        require(artifactSha256.matches(Regex("[a-f0-9]{64}")))
        require(dimension in 8..4096)
        require(maximumInputCharacters in 256..65_536)
        require(documentPrefix.length <= 512 && queryPrefix.length <= 512)
        require(normalizationPolicy.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,95}")))
    }

    companion object {
        private fun profileFingerprint(
            modelId: String,
            modelVersion: String,
            artifactSha256: String,
            dimension: Int,
            maximumInputCharacters: Int,
            documentPrefix: String,
            queryPrefix: String,
            normalizationPolicy: String
        ): String {
            // Length-prefix every value to avoid delimiter ambiguity in a canonical profile.
            val values = listOf(
                "embedding-profile-v1", modelId, modelVersion, artifactSha256,
                dimension.toString(), maximumInputCharacters.toString(), documentPrefix,
                queryPrefix, normalizationPolicy
            )
            val canonical = values.joinToString("") { "${it.length}:$it;" }
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}

data class EmbeddingPassageInput(
    val rowId: Long,
    val stableEvidenceKey: String,
    val originalText: String
) {
    init {
        require(rowId > 0)
        require(stableEvidenceKey.matches(Regex("[a-f0-9]{64}")))
        require(originalText.isNotBlank() && originalText.length <= 100_000)
    }
}

/** Implemented only by a verified on-device model adapter. Implementations must never call a network. */
interface LocalTextEmbeddingModel {
    val descriptor: LocalEmbeddingModelDescriptor
    suspend fun embedBatch(texts: List<String>): List<FloatArray>

    /** Query embeddings may use an asymmetric task prefix; generic models may share embedBatch. */
    suspend fun embedQuery(text: String): FloatArray = embedBatch(listOf(text)).single()
}

/** Streams immutable source passages from a validated signed Corpus Pack in row-id order. */
fun interface EmbeddingPassageSource {
    suspend fun readBatch(
        pack: InstalledCorpusPackRecord,
        indexFile: File,
        afterRowId: Long,
        limit: Int,
        maximumInputCharacters: Int
    ): List<EmbeddingPassageInput>
}

class QuantizedEmbeddingVector(dimension: Int, values: ByteArray) {
    val dimension: Int = dimension
    private val encoded = values.copyOf()
    val values: ByteArray get() = encoded.copyOf()

    init {
        require(dimension in 8..4096)
        require(encoded.size == dimension)
    }

    override fun equals(other: Any?): Boolean = other is QuantizedEmbeddingVector &&
        dimension == other.dimension && encoded.contentEquals(other.encoded)
    override fun hashCode(): Int = 31 * dimension + encoded.contentHashCode()
}

data class DenseIndexGeneration(
    val generationId: String,
    val packId: String,
    val packVersion: String,
    val modelId: String,
    val modelVersion: String,
    val modelSha256: String,
    val dimension: Int,
    val vectorCount: Long,
    val encodedBytes: Long,
    val state: String,
    /** Exact signed pack-manifest identity used to build this derived index. */
    val corpusManifestSha256: String = "",
    /** Includes model artifact, dimensions, task prefixes and vector normalization policy. */
    val embeddingProfileSha256: String = ""
)

data class DenseIndexVectorRecord(
    val stableEvidenceKey: String,
    val vector: QuantizedEmbeddingVector
)

/** Storage owns the staging/active generation transaction; callers cannot partially publish an index. */
interface DenseEmbeddingIndexStore {
    suspend fun beginGeneration(
        pack: InstalledCorpusPackRecord,
        model: LocalEmbeddingModelDescriptor,
        maximumTotalEncodedBytes: Long
    ): String
    suspend fun appendVectors(generationId: String, vectors: Map<String, QuantizedEmbeddingVector>): Long
    suspend fun activateGeneration(generationId: String)
    suspend fun abortGeneration(generationId: String)
    suspend fun removePackVersion(packId: String, packVersion: String): Int
    suspend fun recoverAbandonedGenerations(): Int
    suspend fun activeGeneration(packId: String, packVersion: String, modelId: String, modelVersion: String): DenseIndexGeneration?
    suspend fun vectorsFor(generationId: String, stableEvidenceKeys: List<String>): Map<String, QuantizedEmbeddingVector>

    /** Exact identity match: a generation is usable only for this signed pack and embedding profile. */
    suspend fun activeGeneration(
        pack: InstalledCorpusPackRecord,
        model: LocalEmbeddingModelDescriptor
    ): DenseIndexGeneration? = activeGeneration(pack.packId, pack.version, model.modelId, model.modelVersion)
        ?.takeIf {
            it.corpusManifestSha256 == pack.manifestSha256 &&
                it.modelSha256 == model.artifactSha256 &&
                it.dimension == model.dimension &&
                it.embeddingProfileSha256 == model.embeddingProfileSha256
        }

    /** Stable-key keyset pagination; implementations must return strictly increasing keys. */
    suspend fun vectorsPage(
        generationId: String,
        afterStableEvidenceKey: String,
        limit: Int
    ): List<DenseIndexVectorRecord>
}
