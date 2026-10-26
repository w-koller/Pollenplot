package io.github.wkoller.pollenplot.domain.weeds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeedFinderTest {
    private fun resource(name: String) = javaClass.getResource("/weeds/$name")!!.readText()

    private val brisbane = BrisbaneWeeds.parse(resource("brisbane_sample.json"))

    @Test
    fun `the options are the values on the list, most common first`() {
        val options = WeedFinder.options(brisbane)
        assertEquals("Herb", options.types.first())
        assertEquals(setOf("Herb", "Tree", "Succulent", "Shrub", "Vine"), options.types.toSet())
        assertEquals("Yellow", options.flowerColours.first())
        assertEquals(listOf("Simple", "Cladode", "Compound", "Fern-like"), options.leafForms)
        assertTrue("Opposite" in options.leafArrangements && "Whorled" in options.leafArrangements)
    }

    @Test
    fun `every chosen feature has to match`() {
        val yellowShrubs = WeedFinder.find(brisbane, WeedFeatures(type = "Shrub", flowerColour = "Yellow"))
        assertEquals(listOf("Lantana", "Bitou bush", "Lance-leaved rattlepod"), yellowShrubs.map { it.common })
        assertEquals(brisbane.size, WeedFinder.find(brisbane, WeedFeatures()).size)
        assertEquals(listOf("Ground asparagus fern"), WeedFinder.find(brisbane, WeedFeatures(leafForm = "Fern-like", leafArrangement = "Whorled")).map { it.common })
    }

    @Test
    fun `restricted weeds come before council pests, and the identifier's suggestions before both`() {
        val herbs = WeedFinder.find(brisbane, WeedFeatures(type = "Herb"))
        assertEquals("Bitter weed", herbs.first().common)
        val suggested = WeedFinder.find(brisbane, WeedFeatures(type = "Herb"), suggested = listOf("Dyschoriste depressa", "Euphorbia maculata"))
        assertEquals(listOf("Dyschoriste", "Spotted spurge"), suggested.take(2).map { it.common })
        assertTrue(WeedFinder.isSuggested(suggested.first(), listOf("Dyschoriste depressa")))
        assertFalse(WeedFinder.isSuggested(suggested.last(), listOf("Dyschoriste depressa")))
    }

    @Test
    fun `photos keep their credit and only open licences are shown`() {
        val photos = AlaPhotos.parse(resource("ala_photos.json"))
        assertEquals(8, photos.size)
        val first = photos.first()
        assertEquals("CC-BY 3.0 (Au)", first.licence)
        assertEquals("NatureMapr", first.source)
        assertEquals("Hejor1", first.creator)
        assertTrue(first.thumbnail.startsWith("https://images.ala.org.au/"))
        assertTrue(first.large.contains("proxyImageThumbnailLarge"))
        assertTrue(photos.none { "no-licence" in it.thumbnail })
        assertEquals(emptyList<RecordPhoto>(), AlaPhotos.parse("""{"totalRecords":0}"""))
    }

    @Test
    fun `the council's botanical name is kept as written`() {
        assertEquals("Lantana camara", brisbane.first { it.common == "Lantana" }.botanical)
    }
}
