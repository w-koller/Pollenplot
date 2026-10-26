package io.github.wkoller.pollenplot.ml.text

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import kotlin.math.sqrt

/**
 * Turns text into vectors that are close together when the texts mean similar things, using bge-small-en-v1.5 on the
 * phone. Each vector has [DIMENSIONS] numbers and unit length, so their dot product is their cosine similarity. The
 * model is not safe to run from two threads at once, so calls take turns.
 */
class TextEmbedder(modelPath: String, private val tokenizer: WordPieceTokenizer, threads: Int = 2) : Embedder {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(threads)
        environment.createSession(modelPath, options)
    }

    override fun embedPassage(text: String): FloatArray = embed(text)

    /** The model was trained to find passages for queries that carry this instruction. */
    override fun embedQuery(text: String): FloatArray = embed(QUERY_INSTRUCTION + text)

    @Synchronized
    private fun embed(text: String): FloatArray {
        val ids = tokenizer.encode(text)
        val shape = longArrayOf(1, ids.size.toLong())
        fun tensor(values: LongArray) = OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape)
        tensor(LongArray(ids.size) { ids[it].toLong() }).use { inputIds ->
            tensor(LongArray(ids.size) { 1L }).use { mask ->
                tensor(LongArray(ids.size)).use { types ->
                    session.run(mapOf("input_ids" to inputIds, "attention_mask" to mask, "token_type_ids" to types)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        val hidden = result[0].value as Array<Array<FloatArray>>
                        // bge takes the first token's state as the sentence's meaning.
                        return normalised(hidden[0][0])
                    }
                }
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val DIMENSIONS = 384
        const val QUERY_INSTRUCTION = "Represent this sentence for searching relevant passages: "

        private fun normalised(vector: FloatArray): FloatArray {
            val length = sqrt(vector.fold(0.0) { sum, x -> sum + x * x }).toFloat()
            return if (length == 0f) vector else FloatArray(vector.size) { vector[it] / length }
        }
    }
}
