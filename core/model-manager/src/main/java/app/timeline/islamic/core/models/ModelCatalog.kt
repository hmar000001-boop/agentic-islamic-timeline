package app.timeline.islamic.core.models

/** A deliberately closed catalog. SAF imports cannot supply arbitrary ids, revisions or paths. */
data class ModelArtifactSpec(
    val id: String,
    val displayName: String,
    val description: String,
    val descriptionArabic: String,
    val task: ModelTask,
    val repository: String,
    val revision: String,
    val artifactFileName: String,
    val expectedBytes: Long,
    val sha256: String,
    val licenseId: String,
    val licenseUrl: String,
    /** Null means no publisher-backed minimum is asserted by this application. */
    val minimumDeviceRamBytes: Long?
) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}")))
        require(repository.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}/[A-Za-z0-9][A-Za-z0-9._-]{0,95}")))
        require(revision.matches(Regex("[a-f0-9]{40}")))
        require(artifactFileName.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.litertlm")))
        require(expectedBytes > 0)
        require(sha256.matches(Regex("[a-f0-9]{64}")))
        require(licenseId.isNotBlank() && licenseUrl.startsWith("https://"))
        require(minimumDeviceRamBytes == null || minimumDeviceRamBytes > 0)
    }
}

enum class ModelTask { GENERATION, EMBEDDING }

enum class ModelLifecycleStatus(val persisted: String) {
    INSTALLING("INSTALLING"),
    INSTALLED("INSTALLED"),
    QUARANTINED("QUARANTINED"),
    REMOVAL_PENDING("REMOVAL_PENDING");

    companion object {
        fun parse(value: String): ModelLifecycleStatus? = entries.firstOrNull { it.persisted == value }
    }
}

object ModelCatalog {
    const val APACHE_2_0 = "Apache-2.0"

    private val entries = listOf(
        ModelArtifactSpec(
            id = "gemma-4-e2b-it",
            displayName = "Gemma 4 E2B IT",
            description = "Instruction-tuned multimodal model for on-device generation.",
            descriptionArabic = "نموذج متعدد الوسائط مضبوط لاتباع التعليمات وتوليد المحتوى على الجهاز.",
            task = ModelTask.GENERATION,
            repository = "litert-community/gemma-4-E2B-it-litert-lm",
            revision = "6e5c4f1e395deb959c494953478fa5cec4b8008f",
            artifactFileName = "gemma-4-E2B-it.litertlm",
            expectedBytes = 2_588_147_712L,
            sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
            licenseId = APACHE_2_0,
            licenseUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/6e5c4f1e395deb959c494953478fa5cec4b8008f/README.md",
            minimumDeviceRamBytes = 8L * 1024 * 1024 * 1024
        ),
        ModelArtifactSpec(
            id = "gemma-4-e4b-it",
            displayName = "Gemma 4 E4B IT",
            description = "Larger instruction-tuned generation model with substantially higher resource needs.",
            descriptionArabic = "نموذج أكبر لتوليد المحتوى واتباع التعليمات، ويتطلب موارد أعلى بكثير.",
            task = ModelTask.GENERATION,
            repository = "litert-community/gemma-4-E4B-it-litert-lm",
            revision = "28299f30ee4d43294517a4ac93abd6163412f07f",
            artifactFileName = "gemma-4-E4B-it.litertlm",
            expectedBytes = 3_659_530_240L,
            sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
            licenseId = APACHE_2_0,
            licenseUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/blob/28299f30ee4d43294517a4ac93abd6163412f07f/README.md",
            minimumDeviceRamBytes = 12L * 1024 * 1024 * 1024
        ),
        ModelArtifactSpec(
            id = "embeddinggemma-2-text-270m",
            displayName = "EmbeddingGemma 2 Text 270M",
            description = "Text embedding candidate; output dimensions and task prefixes still require runtime verification.",
            descriptionArabic = "نموذج مرشح لتضمين النصوص؛ لا تزال أبعاد المتجهات وبادئات المهام بحاجة إلى تحقق عبر المحرك.",
            task = ModelTask.EMBEDDING,
            repository = "litert-community/embeddinggemma-2-text-270m-litert-lm",
            revision = "9be6e8b90982095dc05c2bd162e4b954ee4dbac7",
            artifactFileName = "embeddinggemma-2-text-270m.litertlm",
            expectedBytes = 164_626_432L,
            sha256 = "2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb",
            licenseId = APACHE_2_0,
            licenseUrl = "https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/blob/9be6e8b90982095dc05c2bd162e4b954ee4dbac7/README.md",
            minimumDeviceRamBytes = null
        )
    )

    private val byId = entries.associateBy(ModelArtifactSpec::id)

    fun all(): List<ModelArtifactSpec> = entries.toList()

    fun find(id: String): ModelArtifactSpec? = byId[id]

    fun require(id: String): ModelArtifactSpec = byId[id] ?: error("Model is not present in the built-in catalog")
}
