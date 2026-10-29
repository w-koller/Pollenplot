package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.plants.Soil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PotsBeforeHeatTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun weather(tomorrowTenthsC: Int?) = listOf(
        WeatherDay(today, 5_000, 0, null, 400),
        WeatherDay(today.plusDays(1), 6_000, 0, null, tomorrowTenthsC),
    )

    @Test
    fun `a zone of pots or of potting mix holds pots`() {
        assertTrue(PotsBeforeHeat.isPots(ZoneKind.POTS, null))
        assertTrue(PotsBeforeHeat.isPots(ZoneKind.GARDEN_BED, Soil.POTTING_MIX))
        assertFalse(PotsBeforeHeat.isPots(ZoneKind.VEG_PATCH, Soil.LOAM))
    }

    @Test
    fun `tomorrow is hot from 35 degrees`() {
        assertEquals(350, PotsBeforeHeat.hotTomorrow(weather(350), today))
        assertEquals(394, PotsBeforeHeat.hotTomorrow(weather(394), today))
        assertNull(PotsBeforeHeat.hotTomorrow(weather(349), today))
        assertNull("A forecast without a top temperature is not hot", PotsBeforeHeat.hotTomorrow(weather(null), today))
        assertNull("Only tomorrow counts; today is too late for the morning before", PotsBeforeHeat.hotTomorrow(listOf(WeatherDay(today, 5_000, 0, null, 420)), today))
    }
}
