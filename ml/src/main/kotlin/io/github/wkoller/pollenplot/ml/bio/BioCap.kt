package io.github.wkoller.pollenplot.ml.bio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * BioCAP's image encoder, which turns a photo of an organism into a unit vector that lies close to the text vectors
 * of its species. The photo must already be [SIZE] x [SIZE]: the phone scales its shorter side to [SIZE] and takes
 * the middle, as the pack's manifest says. Calls take turns, as the session is not safe to share between threads.
 */
class BioCapEncoder(modelPath: String, threads: Int = 4) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = OrtSession.SessionOptions().use { options ->
        options.setIntraOpNumThreads(threads)
        environment.createSession(modelPath, options)
    }

    /** The vector for one photo, given as [SIZE] x [SIZE] ARGB pixels row by row, as Android's bitmaps give them. */
    @Synchronized
    fun encode(argb: IntArray): FloatArray {
        val shape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(pixels(argb)), shape).use { input ->
            session.run(mapOf("pixels" to input)).use { result ->
                @Suppress("UNCHECKED_CAST")
                return (result[0].value as Array<FloatArray>)[0]
            }
        }
    }

    override fun close() = session.close()

    companion object {
        const val SIZE = 224
        private val MEAN = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        private val STD = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        /** ARGB pixels as the model's input: red, green and blue planes, each scaled to 0..1 and normalised. */
        fun pixels(argb: IntArray): FloatArray {
            require(argb.size == SIZE * SIZE) { "BioCAP takes $SIZE x $SIZE pixels" }
            val plane = SIZE * SIZE
            val out = FloatArray(3 * plane)
            for (i in 0 until plane) {
                val p = argb[i]
                out[i] = ((p shr 16 and 0xFF) / 255f - MEAN[0]) / STD[0]
                out[plane + i] = ((p shr 8 and 0xFF) / 255f - MEAN[1]) / STD[1]
                out[2 * plane + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
            }
            return out
        }
    }
}

/** One species the pack can name, with the knowledge base's or iNaturalist's common name and other names it goes by. */
/**
 * One species the pack can name, with the knowledge base's or iNaturalist's common name, other names it goes by, and
 * its group, such as "plant" or "bird". Packs made before animals joined the index have no groups.
 */
data class IndexedSpecies(val name: String, val common: String?, val aliases: List<String>, val group: String? = null)

/** A species and how close a photo's vector is to it, as cosine similarity. */
data class SpeciesMatch(val index: Int, val species: IndexedSpecies, val similarity: Float)

/**
 * The species the accurate pack can name, each with BioCAP's text vector. The vectors stay as 16-bit floats, half the
 * memory of 32-bit ones, and are read through a table of all 65,536 values.
 */
class SpeciesIndex(val species: List<IndexedSpecies>, private val vectors: ShortArray, val dimensions: Int) {
    init {
        require(vectors.size == species.size * dimensions) { "Expected ${species.size} vectors of $dimensions numbers" }
    }

    /** Whether the index knows each species' group, so it can tell a plant from an animal. */
    val hasGroups: Boolean = species.any { it.group != null }

    /** The [count] species closest to [vector] among those [accept] lets through, the closest first. */
    fun nearest(vector: FloatArray, count: Int = 5, accept: (IndexedSpecies) -> Boolean = { true }): List<SpeciesMatch> {
        require(vector.size == dimensions)
        val best = java.util.PriorityQueue<Pair<Int, Float>>(count + 1, compareBy { it.second })
        for (s in species.indices) {
            if (!accept(species[s])) continue
            val base = s * dimensions
            var dot = 0f
            for (d in 0 until dimensions) dot += vector[d] * HALF[vectors[base + d].toInt() and 0xFFFF]
            if (best.size < count) {
                best.add(s to dot)
            } else if (dot > best.peek().second) {
                best.poll()
                best.add(s to dot)
            }
        }
        return best.sortedByDescending { it.second }.map { (s, similarity) -> SpeciesMatch(s, species[s], similarity) }
    }

    companion object {
        /** Every 16-bit float as a 32-bit one. */
        private val HALF = FloatArray(1 shl 16) { halfToFloat(it) }

        /** IEEE 754 half precision to single precision, for the bits in the low 16 of [bits]. */
        fun halfToFloat(bits: Int): Float {
            val sign = (bits ushr 15) and 1
            val exponent = (bits ushr 10) and 0x1F
            val fraction = bits and 0x3FF
            val value = when (exponent) {
                0 -> fraction / 1024f * 6.1035156e-5f
                0x1F -> if (fraction == 0) Float.POSITIVE_INFINITY else Float.NaN
                else -> (1 + fraction / 1024f) * Math.scalb(1f, exponent - 15)
            }
            return if (sign == 1) -value else value
        }

        /** The index from the pack's species.tsv rows and species.f16 bytes. */
        fun read(tsv: List<String>, f16: ByteArray, dimensions: Int): SpeciesIndex {
            val rows = tsv.drop(1).filter { it.isNotBlank() }.map { line ->
                val cells = line.split('\t')
                IndexedSpecies(
                    cells[0],
                    cells.getOrNull(1)?.ifBlank { null },
                    cells.getOrNull(2)?.split('|')?.filter { it.isNotBlank() }.orEmpty(),
                    cells.getOrNull(3)?.ifBlank { null },
                )
            }
            val buffer = java.nio.ByteBuffer.wrap(f16).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val vectors = ShortArray(buffer.remaining()).also { buffer.get(it) }
            return SpeciesIndex(rows, vectors, dimensions)
        }
    }
}
