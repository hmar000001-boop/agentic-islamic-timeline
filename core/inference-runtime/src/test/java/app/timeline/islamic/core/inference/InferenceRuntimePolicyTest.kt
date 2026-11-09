package app.timeline.islamic.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class InferenceRuntimePolicyTest {
    @Test
    fun promptIsTrimmedBeforeInference() {
        assertEquals("hello", GenerationRequestPolicy.validatePrompt("  hello \n"))
    }

    @Test
    fun blankPromptIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationRequestPolicy.validatePrompt(" \n \t")
        }
    }

    @Test
    fun oversizedPromptIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationRequestPolicy.validatePrompt("x".repeat(GenerationRequestPolicy.MAX_PROMPT_CHARACTERS + 1))
        }
    }

    @Test
    fun outputTokenBudgetIsBoundedAtBothEnds() {
        assertEquals(GenerationRequestPolicy.MIN_OUTPUT_TOKENS, GenerationRequestPolicy.boundedOutputTokens(Int.MIN_VALUE))
        assertEquals(128, GenerationRequestPolicy.boundedOutputTokens(128))
        assertEquals(GenerationRequestPolicy.MAX_OUTPUT_TOKENS, GenerationRequestPolicy.boundedOutputTokens(Int.MAX_VALUE))
    }

    @Test
    fun documentAndQueryEmbeddingPrefixesAreAsymmetric() {
        assertEquals(
            "task: search result | text: passage",
            LiteRtEmbeddingTextModel.documentInput("  passage  ")
        )
        assertEquals(
            "task: search query | text: question",
            LiteRtEmbeddingTextModel.queryInput(" question ")
        )
    }

    @Test
    fun embeddingInputMustNotBeBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtEmbeddingTextModel.documentInput(" \n ")
        }
    }

    @Test
    fun embeddingInputLengthIsBounded() {
        assertEquals(
            LiteRtEmbeddingTextModel.MAX_INPUT_CHARACTERS,
            LiteRtEmbeddingTextModel.queryInput("x".repeat(LiteRtEmbeddingTextModel.MAX_INPUT_CHARACTERS))
                .length - LiteRtEmbeddingTextModel.QUERY_PREFIX.length
        )
        assertThrows(IllegalArgumentException::class.java) {
            LiteRtEmbeddingTextModel.queryInput("x".repeat(LiteRtEmbeddingTextModel.MAX_INPUT_CHARACTERS + 1))
        }
    }

    @Test
    fun embeddingVectorMustMatchTheDenseIndexDimension() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingVectorPolicy.validate(FloatArray(767) { 0.0f })
        }
    }

    @Test
    fun embeddingVectorRejectsNonFiniteValues() {
        val vector = FloatArray(768)
        vector[0] = Float.NaN
        assertThrows(IllegalArgumentException::class.java) { EmbeddingVectorPolicy.validate(vector) }
    }

    @Test
    fun embeddingVectorRejectsZeroOrUnnormalizedNorm() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddingVectorPolicy.validate(FloatArray(768))
        }
    }

    @Test
    fun validNormalizedEmbeddingIsReturnedWithoutMutation() {
        val values = FloatArray(768).also { it[0] = 1.0f }
        assertSame(values, EmbeddingVectorPolicy.validate(values))
    }
}
