package io.github.wkoller.pollenplot.domain.place

import io.github.wkoller.pollenplot.domain.plants.AuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacesTest {
    private fun resource(name: String) = javaClass.getResource("/place/$name")!!.readText()

    @Test
    fun `a garden is rounded to about a kilometre`() {
        val place = GardenPlace.at(-27.50302, 152.97517)
        assertEquals(-275_000_000, place.latE7)
        assertEquals(1_529_800_000, place.lonE7)
        assertEquals("-27.50", place.latText)
        assertEquals("152.98", place.lonText)
        assertEquals("-9.10", Places.text(Places.round(-9.104)))
    }

    @Test
    fun `council and state names are tidied`() {
        assertEquals("Brisbane City", Places.councilName("BRISBANE CITY"))
        assertEquals("Mid-Coast", Places.councilName("MID-COAST"))
        assertEquals(AuState.QLD, Places.state("Queensland"))
        assertEquals(AuState.ACT, Places.state(" Australian Capital Territory"))
        assertNull(Places.state("Ontario"))
        assertTrue(Places.inAustralia(-27.5, 153.0))
        assertTrue(Places.inAustralia(-42.9, 147.3))
        assertFalse(Places.inAustralia(51.5, -0.1))
    }

    @Test
    fun `a suburb search keeps the suburb and leaves out the island and the park`() {
        val found = Places.parseSearch(resource("open_meteo_suburb.json"))
        assertEquals(listOf("Indooroopilly"), found.map { it.name })
        assertEquals("Brisbane", found.single().district)
        assertEquals(AuState.QLD, found.single().state)
        assertEquals(-27.50302, found.single().latitude, 1e-9)
    }

    @Test
    fun `a postcode search finds the suburb with that postcode`() {
        val found = Places.parseSearch(resource("open_meteo_postcode.json")).single()
        assertEquals("Taringa", found.name)
        assertEquals(listOf("4068"), found.postcodes)
        assertEquals(emptyList<PlaceMatch>(), Places.parseSearch("""{"generationtime_ms":0.1}"""))
    }

    @Test
    fun `the council area and state come from the Atlas of Living Australia`() {
        assertEquals(Area("BRISBANE CITY", AuState.QLD), Places.parseArea(resource("ala_intersect.json")))
        assertEquals(Area(null, null), Places.parseArea("[]"))
    }
}
