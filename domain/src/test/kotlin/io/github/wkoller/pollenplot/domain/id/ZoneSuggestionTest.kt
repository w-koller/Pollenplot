package io.github.wkoller.pollenplot.domain.id

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ZoneSuggestionTest {
    private val zones = setOf("front", "citrus", "hive")

    @Test
    fun `the zone of the other plants in the photo comes first`() {
        assertEquals("citrus", ZoneSuggestion.suggest(listOf("citrus", null, "citrus", "front"), lastUsed = "hive", zones))
    }

    @Test
    fun `a tie goes to the zone confirmed first`() {
        assertEquals("front", ZoneSuggestion.suggest(listOf("front", "citrus"), lastUsed = null, zones))
    }

    @Test
    fun `with nothing confirmed in the photo the last zone used is offered`() {
        assertEquals("hive", ZoneSuggestion.suggest(emptyList(), lastUsed = "hive", zones))
        assertEquals("Plants added without a zone say nothing about the rest", "hive", ZoneSuggestion.suggest(listOf(null), "hive", zones))
    }

    @Test
    fun `zones that were removed are never offered`() {
        assertEquals("front", ZoneSuggestion.suggest(listOf("gone", "gone", "front"), lastUsed = "gone", zones))
        assertNull(ZoneSuggestion.suggest(listOf("gone"), lastUsed = "gone", zones))
    }
}
