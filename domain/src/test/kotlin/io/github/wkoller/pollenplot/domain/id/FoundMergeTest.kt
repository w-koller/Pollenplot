package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Test

class FoundMergeTest {
    private data class Find(val box: BoxBp, val candidates: List<Candidate>, val flowering: Boolean = false)

    private fun find(box: BoxBp, vararg suggestions: Pair<String, Int>, flowering: Boolean = false) = Find(
        box,
        suggestions.map { (name, score) -> Candidate(name, name, score, setOf(IdSource.BIOCAP)) },
        flowering,
    )

    private fun merge(found: List<Find>) = FoundMerge.merge(
        found,
        box = { it.box },
        candidates = { it.candidates },
        fold = { kept, other -> kept.copy(flowering = kept.flowering || other.flowering) },
    )

    @Test
    fun `one bush seen in five tiles is one find`() {
        // The case from a phone: a single blueberry bush gave four cards of itself and one of a gum it hid.
        val found = listOf(
            find(BoxBp(0, 0, 4_000, 4_000), "Vaccinium angustifolium" to 2_800, "Phyllanthus myrtifolius" to 900),
            find(BoxBp(3_000, 3_000, 7_000, 7_000), "Vaccinium angustifolium" to 4_500, "Berberis thunbergii" to 800),
            find(BoxBp(6_000, 0, 10_000, 4_000), "Vaccinium angustifolium" to 4_200, "Vaccinium myrtillus" to 1_100),
            find(BoxBp(3_000, 6_000, 7_000, 10_000), "Eucalyptus parvula" to 2_400, "Eucalyptus deglupta" to 700),
            find(BoxBp(6_000, 6_000, 10_000, 10_000), "Vaccinium angustifolium" to 4_000, "Umbellularia californica" to 900),
            find(BoxBp(0, 4_000, 10_000, 10_000), "Vaccinium angustifolium" to 3_900),
        )
        val merged = merge(found)
        assertEquals(listOf("Vaccinium angustifolium"), merged.map { it.candidates.first().label })
        assertEquals("The strongest tile's box is kept", BoxBp(3_000, 3_000, 7_000, 7_000), merged.single().box)
    }

    @Test
    fun `two plants side by side stay two`() {
        val found = listOf(
            find(BoxBp(0, 0, 4_000, 10_000), "Lavandula angustifolia" to 7_000),
            find(BoxBp(6_000, 0, 10_000, 10_000), "Salvia rosmarinus" to 6_500),
        )
        assertEquals(2, merge(found).size)
    }

    @Test
    fun `a sure find inside another is a plant of its own`() {
        val found = listOf(
            find(BoxBp.WHOLE, "Grevillea robusta" to 7_000),
            find(BoxBp(0, 0, 4_000, 4_000), "Lomandra longifolia" to 6_000),
        )
        assertEquals(2, merge(found).size)
    }

    @Test
    fun `an unsure find inside another, or one it already suggests, is folded into it`() {
        val big = find(BoxBp.WHOLE, "Grevillea robusta" to 7_000, "Hakea salicifolia" to 1_000)
        assertEquals(1, merge(listOf(big, find(BoxBp(0, 0, 4_000, 4_000), "Lomandra longifolia" to 2_000))).size)
        assertEquals(1, merge(listOf(big, find(BoxBp(0, 0, 4_000, 4_000), "Hakea salicifolia" to 5_000))).size)
    }

    @Test
    fun `flowers seen on any part of a plant are kept`() {
        val found = listOf(
            find(BoxBp(0, 0, 4_000, 4_000), "Callistemon citrinus" to 8_000),
            find(BoxBp(6_000, 6_000, 10_000, 10_000), "Callistemon citrinus" to 5_000, flowering = true),
        )
        assertEquals(true, merge(found).single().flowering)
    }

    @Test
    fun `a photo gives at most four finds`() {
        val found = (0 until 6).map { i -> find(BoxBp(i * 1_500, 0, i * 1_500 + 1_000, 1_000), "Species $i" to 8_000 - i * 100) }
        assertEquals(FoundMerge.MAX_FOUND, merge(found).size)
    }
}
