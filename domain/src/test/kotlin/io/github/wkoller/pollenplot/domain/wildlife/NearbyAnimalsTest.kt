package io.github.wkoller.pollenplot.domain.wildlife

import org.junit.Assert.assertEquals
import org.junit.Test

class NearbyAnimalsTest {
    // The shape of the Atlas's answer, trimmed from the reptiles recorded near Taringa in October 2026.
    private val page = """
        {"totalRecords": 6326, "facetResults": [{"fieldName": "common_name_and_lsid", "fieldResult": [
          {"label": "Water Dragon|Intellagama lesueurii|https://biodiversity.org.au/afd/taxa/a|Water Dragon|Animalia|Agamidae", "count": 2427, "fq": "common_name_and_lsid:\"Water Dragon|Intellagama lesueurii\""},
          {"label": "Water Dragon|Intellagama lesueurii lesueurii|https://biodiversity.org.au/afd/taxa/b|Water Dragon|Animalia|Agamidae", "count": 2208},
          {"label": "Carpet Python|Morelia spilota|https://biodiversity.org.au/afd/taxa/c|Carpet Python|Animalia|Pythonidae", "count": 727},
          {"label": "|Lampropholis (Lampropholis) delicata|https://biodiversity.org.au/afd/taxa/d||Animalia|Scincidae", "count": 300},
          {"label": "Skinks|SCINCIDAE|https://biodiversity.org.au/afd/taxa/e|Skinks|Animalia|Scincidae", "count": 900},
          {"label": "", "count": 12, "fq": "-common_name_and_lsid:*"}
        ]}]}
    """.trimIndent()

    @Test
    fun `species keep their common names, and subspecies count towards them`() {
        val reptiles = NearbyAnimals.parse(page, LifeGroup.REPTILE)
        assertEquals(
            listOf(
                RecordedAnimal("Intellagama lesueurii", "Water dragon", LifeGroup.REPTILE, 4635),
                RecordedAnimal("Morelia spilota", "Carpet python", LifeGroup.REPTILE, 727),
                RecordedAnimal("Lampropholis delicata", null, LifeGroup.REPTILE, 300),
            ),
            reptiles,
        )
        val nearby = NearbyAnimals(reptiles, radiusKm = 10, sinceYear = 2016)
        assertEquals("A name in any case and spacing", 727, nearby.records("morelia  Spilota"))
        assertEquals(0, nearby.records("Pseudonaja textilis"))
        assertEquals(listOf(LifeGroup.REPTILE), nearby.groups)
    }

    @Test
    fun `names are written as the app writes them`() {
        assertEquals("Australian white ibis", NearbyAnimals.sentenceCase("Australian White Ibis"))
        assertEquals("Torresian crow", NearbyAnimals.sentenceCase("Torresian Crow"))
        assertEquals("Lewin's honeyeater", NearbyAnimals.sentenceCase("Lewin's Honeyeater"))
        assertEquals("Humped golden orb-weaving spider", NearbyAnimals.sentenceCase("Humped Golden Orb-weaving Spider"))
        assertEquals("Common Australian crow", NearbyAnimals.sentenceCase("Common Australian Crow"))
    }

    @Test
    fun `a page without the facet has no animals`() {
        assertEquals(emptyList<RecordedAnimal>(), NearbyAnimals.parse("""{"totalRecords": 0, "facetResults": []}""", LifeGroup.FISH))
    }
}
