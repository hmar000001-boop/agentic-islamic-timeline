package app.timeline.islamic.core.inference

import app.timeline.islamic.core.models.ModelCatalog
import app.timeline.islamic.core.models.ModelManager
import app.timeline.islamic.core.models.ModelTask
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * CPU-only LiteRT-LM generation owner.
 *
 * Engine creation/initialization, Conversation creation/cancellation/close and Engine close are
 * confined to one dedicated thread. Each request receives a fresh conversation: history is not
 * retained. Tools are never registered and automatic tool calling is disabled, so model output
 * cannot cross the app's ToolExecutionGateway / human-approval boundary.
 */
class LiteRtCpuGenerationRuntime(
    private val modelManager: ModelManager,
    private val cacheDirectory: File
) {
    private val lifecycleMutex = Mutex()
    private val ownerExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "timeline-litertlm-cpu-owner").apply { isDaemon = true }
    }
    private val ownerDispatcher = ownerExecutor.asCoroutineDispatcher()
    private val mutableSnapshot = MutableStateFlow(GenerationRuntimeSnapshot())
    val snapshot: StateFlow<GenerationRuntimeSnapshot> = mutableSnapshot.asStateFlow()

    /** At most one generation engine is active in this process. */
    private var engine: Engine? = null
    private var activeModelId: String? = null

    suspend fun load(modelId: String) = lifecycleMutex.withLock {
        val spec = ModelCatalog.require(modelId)
        require(spec.task == ModelTask.GENERATION) { "Selected model is not a generation model" }
        if (engine != null && activeModelId == modelId) {
            mutableSnapshot.value = GenerationRuntimeSnapshot(
                state = GenerationRuntimeState.READY,
                modelId = modelId,
                lastOutput = mutableSnapshot.value.lastOutput
            )
            return@withLock
        }

        mutableSnapshot.value = GenerationRuntimeSnapshot(GenerationRuntimeState.LOADING, modelId)
        unloadLocked()
        var initializedCandidate: Engine? = null
        try {
            // resolveInstalledFile performs a second exact-size/SHA-256 verification immediately
            // before native runtime access. No catalog path supplied by callers is accepted.
            val verifiedFile = modelManager.resolveInstalledFile(modelId)
            check(verifiedFile.isFile && verifiedFile.length() == spec.expectedBytes)
            val initialized = withContext(ownerDispatcher + NonCancellable) {
                check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) { "Model cache directory is unavailable" }
                val engineCache = File(cacheDirectory, "generation-$modelId")
                check(engineCache.mkdirs() || engineCache.isDirectory) { "Engine cache directory is unavailable" }
                Engine(
                    EngineConfig(
                        modelPath = verifiedFile.absolutePath,
                        backend = Backend.CPU(threadCount = CPU_THREAD_COUNT),
                        // Bound the KV cache on mobile. Conversation output is separately capped.
                        maxNumTokens = MAX_CONTEXT_TOKENS,
                        cacheDir = engineCache.absolutePath
                    )
                ).also {
                    // Record the handle before initialization. If the caller is cancelled exactly
                    // as native initialization completes, the catch path can still close it.
                    initializedCandidate = it
                    it.initialize()
                }
            }
            currentCoroutineContext().ensureActive()
            engine = initialized
            activeModelId = modelId
            mutableSnapshot.value = GenerationRuntimeSnapshot(GenerationRuntimeState.READY, modelId)
        } catch (cancelled: CancellationException) {
            initializedCandidate?.let { candidate ->
                withContext(ownerDispatcher + NonCancellable) {
                    if (candidate.isInitialized()) runCatching { candidate.close() }
                }
            }
            mutableSnapshot.value = GenerationRuntimeSnapshot(GenerationRuntimeState.UNLOADED)
            throw cancelled
        } catch (failure: Exception) {
            // Failed initialization may leave a live native handle only if the SDK reports the
            // engine initialized. Close that case on the owner thread; never close an uninitialized
            // handle. Keep all native access confined to this dispatcher.
            initializedCandidate?.let { candidate ->
                withContext(ownerDispatcher + NonCancellable) {
                    if (candidate.isInitialized()) runCatching { candidate.close() }
                }
            }
            engine = null
            activeModelId = null
            mutableSnapshot.value = GenerationRuntimeSnapshot(
                state = GenerationRuntimeState.FAILED,
                modelId = modelId,
                failureCode = stableFailureCode(failure)
            )
            throw IllegalStateException("Local generation runtime could not initialize (${stableFailureCode(failure)})", failure)
        }
    }

    suspend fun runSmokeTest(): String = generate(
        prompt = "Reply with exactly the word READY.",
        maximumOutputTokens = 32
    )

    suspend fun generate(prompt: String, maximumOutputTokens: Int = 128): String = lifecycleMutex.withLock {
        val safePrompt = GenerationRequestPolicy.validatePrompt(prompt)
        val outputLimit = GenerationRequestPolicy.boundedOutputTokens(maximumOutputTokens)
        val selectedEngine = checkNotNull(engine) { "No local generation model is loaded" }
        val selectedModelId = checkNotNull(activeModelId)
        mutableSnapshot.value = mutableSnapshot.value.copy(
            state = GenerationRuntimeState.GENERATING,
            failureCode = null
        )

        try {
            val output = withContext(ownerDispatcher) {
                val conversation = selectedEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "You are a local assistant component. Do not claim you consulted a source " +
                                "unless source text is included in the request. Do not invent citations. " +
                                "You have no tools and must never request or perform external actions. " +
                                "When evidence is insufficient, state that limitation plainly."
                        ),
                        tools = emptyList(),
                        automaticToolCalling = false,
                        maxOutputToken = outputLimit
                    )
                )
                val response = StringBuilder()
                var conversationClosed = false
                try {
                    conversation.sendMessageAsync(safePrompt, maxOutputToken = outputLimit).collect { message ->
                        check(message.toolCalls.isEmpty()) {
                            "Model attempted a tool call; local generation is text-only and tool-free"
                        }
                        val chunk = message.toString()
                        if (chunk.isNotEmpty()) response.append(chunk)
                        check(response.length <= MAX_RESPONSE_CHARACTERS) {
                            "Local model response exceeded the output safety limit"
                        }
                    }
                    response.toString().trim().also {
                        check(it.isNotEmpty()) { "Local model returned an empty response" }
                    }
                } catch (failure: Throwable) {
                    withContext(NonCancellable) {
                        // Stop native work before releasing the conversation handle. A cancelled
                        // conversation is never reused; every request has its own short lifetime.
                        runCatching { conversation.cancelProcess() }
                        runCatching { conversation.close() }
                        conversationClosed = true
                    }
                    throw failure
                } finally {
                    if (!conversationClosed) {
                        withContext(NonCancellable) { conversation.close() }
                    }
                }
            }
            mutableSnapshot.value = GenerationRuntimeSnapshot(
                state = GenerationRuntimeState.READY,
                modelId = selectedModelId,
                lastOutput = output.take(MAX_EPHEMERAL_OUTPUT_CHARACTERS)
            )
            output
        } catch (cancelled: CancellationException) {
            mutableSnapshot.value = GenerationRuntimeSnapshot(
                state = GenerationRuntimeState.READY,
                modelId = selectedModelId
            )
            throw cancelled
        } catch (failure: Exception) {
            // Native inference failures invalidate the active engine. Releasing it avoids reusing
            // an uncertain native state after a failed decode.
            runCatching { unloadEngineOnly() }
            mutableSnapshot.value = GenerationRuntimeSnapshot(
                state = GenerationRuntimeState.FAILED,
                modelId = selectedModelId,
                failureCode = stableFailureCode(failure)
            )
            throw IllegalStateException("Local generation failed (${stableFailureCode(failure)})", failure)
        }
    }

    suspend fun unload() = lifecycleMutex.withLock { unloadLocked() }

    private suspend fun unloadLocked() {
        unloadEngineOnly()
        mutableSnapshot.value = GenerationRuntimeSnapshot(GenerationRuntimeState.UNLOADED)
    }

    private suspend fun unloadEngineOnly() {
        val oldEngine = engine ?: return
        // Retain the reference until close succeeds. If native close fails, callers can retry
        // instead of losing the only handle to a possibly-live engine.
        withContext(ownerDispatcher) {
            if (oldEngine.isInitialized()) oldEngine.close()
        }
        engine = null
        activeModelId = null
    }

    /** Close on application-owned shutdown/test cleanup. The dispatcher is not used afterwards. */
    suspend fun shutdown() = lifecycleMutex.withLock {
        unloadEngineOnly()
        mutableSnapshot.value = GenerationRuntimeSnapshot(GenerationRuntimeState.UNLOADED)
        ownerDispatcher.close()
        ownerExecutor.shutdown()
    }

    private fun stableFailureCode(failure: Throwable): String =
        failure::class.simpleName?.uppercase()?.take(48) ?: "LITERTLM_FAILURE"

    companion object {
        const val MAX_RESPONSE_CHARACTERS = 12_000
        const val MAX_EPHEMERAL_OUTPUT_CHARACTERS = 4_000
        const val MAX_CONTEXT_TOKENS = 4_096
        const val CPU_THREAD_COUNT = 4
        const val RUNTIME_VERSION = "0.19.1"
    }
}
