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

    /** Cosine score of a stored normalized int8 vector against a query vector. */
    fun cosine(stored: QuantizedEmbeddingVector, query: FloatArray): Double {
        require(stored.dimension == query.size) { "Query and index dimensions differ" }
        var queryNormSquared = 0.0
        query.forEach {
            require(it.isFinite()) { "Query embedding contains a non-finite value" }
            queryNormSquared += it.toDouble() * it.toDouble()
        }
        require(queryNormSquared > 1e-18) { "Query embedding has zero magnitude" }
        val queryNorm = sqrt(queryNormSquared)
        val values = stored.values
        var dot = 0.0
        var storedNormSquared = 0.0
        for (index in values.indices) {
            val value = values[index].toInt().toDouble()
            dot += value * query[index].toDouble() / queryNorm
            storedNormSquared += value * value
        }
        if (storedNormSquared == 0.0) return 0.0
        return (dot / sqrt(storedNormSquared)).coerceIn(-1.0, 1.0)
    }
}
