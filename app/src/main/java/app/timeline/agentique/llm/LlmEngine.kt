package app.timeline.agentique.llm

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object Prompts {
    const val MUSHAF =
        "Transcribe the Arabic Quran text visible in this photo of a mushaf page. " +
            "Output only the Arabic text. No translation, no explanation, no additions."
    const val LABEL =
        "Transcribe the ingredient list printed on this product label exactly as written. " +
            "Output only the text."
}

interface LlmEngine {
    /** Reads text from an image. Only transcription: callers verify the result with deterministic code. */
    suspend fun extractText(image: Bitmap, instruction: String): Result<String>
}

object ModelFiles {
    fun file(ctx: Context): File = File(ctx.filesDir, "models/gemma.task")
    fun installed(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 0 }
}

/**
 * On-device inference through MediaPipe LLM Inference. The model is loaded per call and released
 * right after, which is slower but keeps RAM free on mid-range phones.
 * This is the one file most likely to need tweaks when the MediaPipe GenAI API or model version changes.
 */
class MediaPipeLlmEngine(private val ctx: Context) : LlmEngine {
    private val mutex = Mutex()

    override suspend fun extractText(image: Bitmap, instruction: String): Result<String> =
        mutex.withLock {
            withContext(Dispatchers.Default) {
                runCatching {
                    check(ModelFiles.installed(ctx)) { "model_missing" }
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(ModelFiles.file(ctx).absolutePath)
                        .setMaxTokens(1024)
                        .setMaxNumImages(1)
                        .build()
                    val llm = LlmInference.createFromOptions(ctx, options)
                    try {
                        val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                            .setTopK(1)
                            .setTemperature(0.1f)
                            .setGraphOptions(GraphOptions.builder().setEnableVisionModality(true).build())
                            .build()
                        LlmInferenceSession.createFromOptions(llm, sessionOptions).use { session ->
                            session.addQueryChunk(instruction)
                            session.addImage(BitmapImageBuilder(image).build())
                            session.generateResponse()
                        }
                    } finally {
                        llm.close()
                    }
                }
            }
        }
}
