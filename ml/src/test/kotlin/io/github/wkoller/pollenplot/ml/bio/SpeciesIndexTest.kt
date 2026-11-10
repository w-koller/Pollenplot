package io.github.wkoller.pollenplot.ml.bio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpeciesIndexTest {
    @Test
    fun `half floats read as the same numbers`() {
        assertEquals(1f, SpeciesIndex.halfToFloat(0x3C00), 0f)
        assertEquals(-2f, SpeciesIndex.halfToFloat(0xC000), 0f)
        assertEquals(0.5f, SpeciesIndex.halfToFloat(0x3800), 0f)
        assertEquals(65504f, SpeciesIndex.halfToFloat(0x7BFF), 0f)
        assertEquals("Smallest subnormal", 5.9604645e-8f, SpeciesIndex.halfToFloat(0x0001), 1e-12f)
        assertTrue(SpeciesIndex.halfToFloat(0x7E00).isNaN())
    }

    @Test
    fun `the nearest species come first`() {
        // Three species in two dimensions: east, north and north-east.
        val tsv = listOf("name\tcommon\taliases", "Grevillea robusta\tSilky oak\t", "Melaleuca citrina\tCrimson bottlebrush\tCallistemon citrinus", "Lantana camara\t\t")
        val halves = listOf(1f, 0f, 0f, 1f, 0.7071f, 0.7071f)
        val bytes = ByteBuffer.allocate(halves.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        halves.forEach { bytes.putShort(toHalf(it)) }
        val index = SpeciesIndex.read(tsv, bytes.array(), dimensions = 2)

        val matches = index.nearest(floatArrayOf(0.9f, 0.436f), count = 2)
        assertEquals(listOf("Lantana camara", "Grevillea robusta"), matches.map { it.species.name })
        assertEquals(listOf("Callistemon citrinus"), index.species[1].aliases)
        assertEquals("Silky oak", index.species[0].common)
        assertEquals(null, index.species[2].common)
    }

    @Test
    fun `flower vectors weigh a photo between flowering and leafy`() {
        val halves = listOf(1f, 0f, 0f, 1f)
        val bytes = ByteBuffer.allocate(halves.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        halves.forEach { bytes.putShort(toHalf(it)) }
        val flowers = FlowerVectors.read(bytes.array(), dimensions = 2, yesBp = 7_000, noBp = 3_000)
        assertEquals(7_000, flowers.yesBp)
        assertEquals("Halfway between is an even belief", 5_000, flowers.flowerBp(floatArrayOf(0.7071f, 0.7071f), logitScale = 100f))
        assertTrue(flowers.flowerBp(floatArrayOf(0.8f, 0.6f), logitScale = 100f) > 9_900)
        assertTrue(flowers.flowerBp(floatArrayOf(0.6f, 0.8f), logitScale = 100f) < 100)
    }

    @Test
    fun `groups keep plants and animals apart`() {
        val tsv = listOf("name\tcommon\taliases\tgroup", "Grevillea robusta\tSilky oak\t\tplant", "Manorina melanocephala\tNoisy miner\t\tbird")
        val halves = listOf(1f, 0f, 0.8f, 0.6f)
        val bytes = ByteBuffer.allocate(halves.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        halves.forEach { bytes.putShort(toHalf(it)) }
        val index = SpeciesIndex.read(tsv, bytes.array(), dimensions = 2)
        assertTrue(index.hasGroups)
        assertEquals("bird", index.species[1].group)
        assertEquals(listOf("Grevillea robusta"), index.nearest(floatArrayOf(1f, 0f), count = 1).map { it.species.name })
        assertEquals(listOf("Manorina melanocephala"), index.nearest(floatArrayOf(1f, 0f), count = 1) { it.group == "bird" }.map { it.species.name })
        val old = SpeciesIndex.read(listOf("name\tcommon\taliases", "Grevillea robusta\tSilky oak\t"), bytes.array().copyOf(4), dimensions = 2)
        assertTrue("Packs from before animals joined have no groups", !old.hasGroups)
    }

    @Test
    fun `a prototype close to the photo lifts its species`() {
        // The text puts the photo nearer the grevillea; the bottlebrush's own photos look just like it.
        val tsv = listOf("name\tcommon\taliases\tgroup", "Grevillea robusta\t\t\tplant", "Melaleuca citrina\t\t\tplant", "Lantana camara\t\t\tplant")
        fun bytes(halves: List<Float>) = ByteBuffer.allocate(halves.size * 2).order(ByteOrder.LITTLE_ENDIAN).also { b -> halves.forEach { b.putShort(toHalf(it)) } }.array()
        val text = bytes(listOf(0.8f, 0.6f, 0.6f, 0.8f, 0f, 1f))
        val prototypes = bytes(listOf(0f, 0f, 1f, 0f, 0f, 1f))
        val photo = floatArrayOf(1f, 0f)

        val plain = SpeciesIndex.read(tsv, text, dimensions = 2)
        assertEquals(listOf("Grevillea robusta", "Melaleuca citrina", "Lantana camara"), plain.nearest(photo, count = 3).map { it.species.name })

        val index = SpeciesIndex.read(tsv, text, dimensions = 2, prototypesF16 = prototypes, alpha = 0.5f, beta = 4f)
        val matches = index.nearest(photo, count = 3)
        assertEquals(listOf("Melaleuca citrina", "Grevillea robusta", "Lantana camara"), matches.map { it.species.name })
        assertEquals("Its text similarity and all of alpha, as the photo is its prototype", 0.6f + 0.5f, matches[0].similarity, 1e-3f)
        assertEquals("A species without a prototype gains nothing", 0.8f, matches[1].similarity, 1e-3f)
        assertEquals("A prototype far from the photo adds next to nothing", 0.5f * kotlin.math.exp(-4f), matches[2].similarity, 1e-4f)
    }

    @Test
    fun `pixels are scaled and normalised plane by plane`() {
        val white = IntArray(BioCapEncoder.SIZE * BioCapEncoder.SIZE) { 0xFFFFFFFF.toInt() }
        val pixels = BioCapEncoder.pixels(white)
        val plane = BioCapEncoder.SIZE * BioCapEncoder.SIZE
        assertEquals((1f - 0.48145466f) / 0.26862954f, pixels[0], 1e-5f)
        assertEquals((1f - 0.4578275f) / 0.26130258f, pixels[plane], 1e-5f)
        assertEquals((1f - 0.40821073f) / 0.27577711f, pixels[2 * plane + plane - 1], 1e-5f)
    }

    /** Half precision for the small, normal numbers in these tests. */
    private fun toHalf(value: Float): Short {
        if (value == 0f) return 0
        val bits = java.lang.Float.floatToIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        val exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        val fraction = (bits ushr 13) and 0x3FF
        return (sign or (exponent shl 10) or fraction).toShort()
    }
}
