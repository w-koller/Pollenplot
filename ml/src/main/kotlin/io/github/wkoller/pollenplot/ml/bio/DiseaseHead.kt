package io.github.wkoller.pollenplot.ml.bio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * The plant problems pack's classifier: multinomial logistic regression on BioCAP's image vector, one class for each
 * problem key the pack has enough photos of (some of them problems the app's catalogue does not hold, which the app
 * never offers) and one for a healthy plant. It adds no encoder of its own, so it runs on the vector
 * the accurate pack's encoder makes. The logits are W·v + b, divided by the [temperature] the pack's evaluation tuned
 * on its tuning observers, then turned into shares. `disease_head.head_shares` in tools/models works it out the same
 * way, and writes the pack's reference.json with it.
 */
class DiseaseHead(
    val classes: List<String>,
    private val weights: FloatArray,
    private val bias: FloatArray,
    val dimensions: Int,
    val temperature: Float,
) {
    init {
        require(classes.isNotEmpty()) { "A head needs classes" }
        require(weights.size == classes.size * dimensions) { "Expected ${classes.size} rows of $dimensions weights" }
        require(bias.size == classes.size) { "Expected a bias for each class" }
        require(temperature > 0f) { "The temperature must be above zero" }
    }

    /** Each class's share of the belief for [vector], in the order of [classes], summing to 1. */
    fun shares(vector: FloatArray): FloatArray {
        require(vector.size == dimensions) { "Expected a vector of $dimensions numbers" }
        val logits = FloatArray(classes.size) { c ->
            var sum = bias[c]
            val base = c * dimensions
            for (d in 0 until dimensions) sum += weights[base + d] * vector[d]
            sum / temperature
        }
        val top = logits.max()
        val weighted = FloatArray(logits.size) { exp(logits[it] - top) }
        val total = weighted.sum()
        return FloatArray(weighted.size) { weighted[it] / total }
    }

    /** [shares] by class, in basis points. */
    fun sharesBp(vector: FloatArray): Map<String, Int> {
        val shares = shares(vector)
        return classes.indices.associate { classes[it] to (shares[it] * 10_000).roundToInt() }
    }

    companion object {
        /** The head from the pack's class list, weights.f16 (one row per class), bias.f16 and the manifest's temperature. */
        fun read(classes: List<String>, weightsF16: ByteArray, biasF16: ByteArray, dimensions: Int, temperature: Float): DiseaseHead =
            DiseaseHead(classes, halves(weightsF16), halves(biasF16), dimensions, temperature)

        /** Little-endian 16-bit floats as 32-bit ones. */
        fun halves(bytes: ByteArray): FloatArray {
            require(bytes.size % 2 == 0) { "Half floats take two bytes each" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            return FloatArray(buffer.remaining()) { SpeciesIndex.halfToFloat(buffer.get(it).toInt() and 0xFFFF) }
        }
    }
}
