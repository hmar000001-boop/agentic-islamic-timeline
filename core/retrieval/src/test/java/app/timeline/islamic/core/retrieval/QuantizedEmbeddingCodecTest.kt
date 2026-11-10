package app.timeline.islamic.core.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuantizedEmbeddingCodecTest {
    @Test fun normalizedVectorsHaveNearUnitSelfSimilarity() {
        val vector = FloatArray(128) { index -> if (index % 3 == 0) 0.7f else -0.2f }
        val encoded = QuantizedEmbeddingCodec.encode(vector)
        assertTrue(QuantizedEmbeddingCodec.cosine(encoded, vector) > 0.999)
    }

    @Test fun orthogonalVectorsRemainNearZeroSimilarity() {
        val stored = QuantizedEmbeddingCodec.encode(FloatArray(16) { if (it == 0) 1f else 0f })
        val query = FloatArray(16) { if (it == 1) 1f else 0f }
        assertEquals(0.0, QuantizedEmbeddingCodec.cosine(stored, query), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroMagnitudeVectorIsRejected() {
        QuantizedEmbeddingCodec.encode(FloatArray(16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteVectorIsRejected() {
        QuantizedEmbeddingCodec.encode(FloatArray(16) { if (it == 2) Float.NaN else 0.5f })
    }

    @Test(expected = IllegalArgumentException::class)
    fun dimensionMismatchIsRejected() {
        val encoded = QuantizedEmbeddingCodec.encode(FloatArray(16) { 0.5f })
        QuantizedEmbeddingCodec.cosine(encoded, FloatArray(32) { 0.25f })
    }
}
