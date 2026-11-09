package app.timeline.islamic.core.inference

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compile/runtime API-shape test against the pinned LiteRT-LM AAR. It deliberately does not
 * initialize a native engine or claim that a model has loaded; real model compatibility requires
 * a separately installed, verified artifact and device-specific test.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtApiContractTest {
    @Test
    fun generationConfigurationIsCpuOnlyAndContextBounded() {
        val config = EngineConfig(
            modelPath = "/not-opened/test-model.litertlm",
            backend = Backend.CPU(threadCount = LiteRtCpuGenerationRuntime.CPU_THREAD_COUNT),
            maxNumTokens = LiteRtCpuGenerationRuntime.MAX_CONTEXT_TOKENS,
            cacheDir = "/not-opened/cache"
        )
        assertEquals("CPU", config.backend.name)
        assertEquals(4, (config.backend as Backend.CPU).threadCount)
        assertEquals(4_096, config.maxNumTokens)
    }

    @Test
    fun generationConversationHasNoToolsOrAutomaticToolExecution() {
        val config = ConversationConfig(
            tools = emptyList(),
            automaticToolCalling = false,
            maxOutputToken = GenerationRequestPolicy.MAX_OUTPUT_TOKENS
        )
        assertTrue(config.tools.isEmpty())
        assertFalse(config.automaticToolCalling)
        assertEquals(256, config.maxOutputToken)
    }

    @Test
    fun embeddingConfigurationPinsCpuAndBoundedSequenceLength() {
        val config = EmbeddingEngineConfig(
            modelPath = "/not-opened/embedding-model.litertlm",
            backend = Backend.CPU(threadCount = 4),
            cacheDir = "/not-opened/cache",
            maxInputLength = 8_192
        )
        assertEquals("CPU", config.backend.name)
        assertEquals(8_192, config.maxInputLength)
        val options = EmbeddingOptions(normalize = true, outputSize = 768)
        assertEquals(true, options.normalize)
        assertEquals(768, options.outputSize)
    }
}
