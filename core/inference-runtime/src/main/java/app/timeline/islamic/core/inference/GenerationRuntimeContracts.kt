package app.timeline.islamic.core.inference

/** Observable lifecycle only; no prompt or generated content is persisted by this contract. */
enum class GenerationRuntimeState {
    UNLOADED,
    LOADING,
    READY,
    GENERATING,
    FAILED
}

data class GenerationRuntimeSnapshot(
    val state: GenerationRuntimeState = GenerationRuntimeState.UNLOADED,
    val modelId: String? = null,
    /** Stable failure category only. Never includes prompt text, model path or native log output. */
    val failureCode: String? = null,
    /** Ephemeral last output for the explicit runtime smoke test; not persisted. */
    val lastOutput: String? = null
)

object GenerationRequestPolicy {
    const val MAX_PROMPT_CHARACTERS = 16_384
    const val MIN_OUTPUT_TOKENS = 16
    const val MAX_OUTPUT_TOKENS = 256

    fun validatePrompt(prompt: String): String {
        val normalized = prompt.trim()
        require(normalized.isNotEmpty()) { "Prompt must not be blank" }
        require(normalized.length <= MAX_PROMPT_CHARACTERS) { "Prompt exceeds the local inference limit" }
        return normalized
    }

    fun boundedOutputTokens(requested: Int): Int = requested.coerceIn(MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS)
}
