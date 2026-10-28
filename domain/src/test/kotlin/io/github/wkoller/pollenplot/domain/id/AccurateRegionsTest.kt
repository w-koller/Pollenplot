package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccurateRegionsTest {
    @Test
    fun `similarities become shares that add up to the whole`() {
        val shares = AccurateRegions.shares(listOf("Grevillea robusta" to 0.31f, "Grevillea banksii" to 0.29f, "Hakea salicifolia" to 0.20f), logitScale = 100f)
        assertEquals(listOf("Grevillea robusta", "Grevillea banksii", "Hakea salicifolia"), shares.map { it.label })
        assertEquals(BoxBp.FULL.toDouble(), shares.sumOf { it.scoreBp }.toDouble(), 2.0)
        // A difference of 0.02 in similarity at a scale of 100 is a factor of e² ≈ 7.4 between the shares.
        assertEquals(7.39, shares[0].scoreBp.toDouble() / shares[1].scoreBp, 0.05)
        assertTrue(shares[2].scoreBp < 2)
    }

    @Test
    fun `only tiles the quick model was unsure about and no region covers are looked at again`() {
        val whole = TileResult(BoxBp.WHOLE, emptyList(), backgroundBp = 1_000)
        val left = TileResult(BoxBp(0, 0, 6_000, 6_000), emptyList(), backgroundBp = 800)
        val sky = TileResult(BoxBp(4_000, 0, 10_000, 6_000), emptyList(), backgroundBp = 7_000)
        val covered = listOf(BoxBp(0, 0, 6_000, 6_000))
        assertEquals(listOf(BoxBp.WHOLE), AccurateRegions.tilesToCheck(listOf(whole, left, sky), covered))
    }

    @Test
    fun `confident tiles become regions without repeating a plant`() {
        val a = BoxBp(0, 0, 4_000, 4_000)
        val b = BoxBp(3_000, 0, 7_000, 4_000)
        val c = BoxBp(6_000, 6_000, 10_000, 10_000)
        val unsure = BoxBp(0, 6_000, 4_000, 10_000)
        val checked = mapOf(
            a to listOf(LabelScore("Grevillea robusta", 8_000)),
            b to listOf(LabelScore("Grevillea robusta", 6_000)),
            c to listOf(LabelScore("Lomandra longifolia", 5_000)),
            unsure to listOf(LabelScore("Dianella caerulea", 2_000)),
        )
        val regions = AccurateRegions.select(checked, existing = emptyList(), max = 6)
        assertEquals("The second Grevillea tile overlaps the first", listOf(a, c), regions.map { it.first })
        assertEquals(
            "The quick model's region already holds this Grevillea",
            listOf(c),
            AccurateRegions.select(checked, existing = listOf(BoxBp(0, 0, 4_000, 4_000) to "Grevillea robusta"), max = 6).map { it.first },
        )
        assertEquals(
            "A different plant beside an existing region is a region of its own",
            listOf(a, c),
            AccurateRegions.select(checked, existing = listOf(BoxBp(3_000, 0, 7_000, 4_000) to "Hakea salicifolia"), max = 6).map { it.first },
        )
    }

    @Test
    fun `a photo is taken to show a group only when its species clearly lead`() {
        val scale = 100f
        assertEquals("Birds take nearly all the shares", "bird", AccurateRegions.groupOf(listOf("bird" to 0.30f, "bird" to 0.29f, "plant" to 0.25f), scale))
        assertEquals("Two close plants outweigh one bird", "plant", AccurateRegions.groupOf(listOf("bird" to 0.300f, "plant" to 0.299f, "plant" to 0.298f), scale))
        assertNull("Three groups about even", AccurateRegions.groupOf(listOf("bird" to 0.300f, "insect" to 0.299f, "plant" to 0.298f), scale))
        assertNull(AccurateRegions.groupOf(emptyList(), scale))
    }
}
