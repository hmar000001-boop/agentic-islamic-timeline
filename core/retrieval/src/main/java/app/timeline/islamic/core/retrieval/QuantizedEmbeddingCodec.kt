package app.timeline.islamic.core.retrieval

import app.timeline.islamic.core.knowledge.QuantizedEmbeddingVector
import kotlin.math.sqrt

/** Symmetric signed-int8 quantization for L2-normalized embeddings. The quantized vectors contain no text. */
object QuantizedEmbeddingCodec {
    fun encode(values: FloatArray): QuantizedEmbeddingVector {
        require(values.size in 8..4096) { "Embedding dimension is outside the supported range" }
        var normSquared = 0.0
        values.forEach { value ->
            require(value.isFinite()) { "Embedding contains a non-finite value" }
            normSquared += value.toDouble() * value.toDouble()
        }
        require(normSquared > 1e-18) { "Embedding vector has zero magnitude" }
        val norm = sqrt(normSquared)
        val encoded = ByteArray(values.size) { index ->
            (values[index].toDouble() / norm * 127.0).toInt().coerceIn(-127, 127).toByte()
        }
        return QuantizedEmbeddingVector(values.size, encoded)
    }

    /** Normalize once per search, not once per stored row in an exhaustive scan. */
    fun normalizeQuery(values: FloatArray): FloatArray {
        require(values.size in 8..4096) { "Query dimension is outside the supported range" }
        var normSquared = 0.0
        values.forEach { value ->
            require(value.isFinite()) { "Query embedding contains a non-finite value" }
            normSquared += value.toDouble() * value.toDouble()
        }
        require(normSquared > 1e-18) { "Query embedding has zero magnitude" }
        val norm = sqrt(normSquared)
        return FloatArray(values.size) { (values[it].toDouble() / norm).toFloat() }
    }

    /** Cosine score of a stored normalized int8 vector against a pre-normalized query vector. */
    internal fun cosineWithNormalizedQuery(stored: QuantizedEmbeddingVector, normalizedQuery: FloatArray): Double {
        require(stored.dimension == normalizedQuery.size) { "Query and index dimensions differ" }
        val values = stored.values
        var dot = 0.0
        var storedNormSquared = 0.0
        for (index in values.indices) {
            val value = values[index].toInt().toDouble()
            val queryValue = normalizedQuery[index].toDouble()
            require(queryValue.isFinite()) { "Query embedding contains a non-finite value" }
            dot += value * queryValue
            storedNormSquared += value * value
        }
        if (storedNormSquared == 0.0) return 0.0
        return (dot / sqrt(storedNormSquared)).coerceIn(-1.0, 1.0)
    }

    /** Convenience form for tests and callers that do not scan multiple rows. */
    fun cosine(stored: QuantizedEmbeddingVector, query: FloatArray): Double =
        cosineWithNormalizedQuery(stored, normalizeQuery(query))
}
