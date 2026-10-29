package io.github.wkoller.pollenplot.domain.bees.stingless

import io.github.wkoller.pollenplot.domain.weather.DayForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StinglessBeesTest {
    private val today = LocalDate.of(2026, 12, 10)
    private val forecast = listOf(
        DayForecast(today, 345),
        DayForecast(today.plusDays(1), 392),
        DayForecast(today.plusDays(2), 410),
    )

    @Test
    fun `a hot day tomorrow is told about once`() {
        val first = HeatAlerts.toTell(forecast, today, HeatAlerts.DEFAULT_TENTHS_C, emptySet())
        assertEquals("Today is cooler and the day after tomorrow is too far off", listOf(today.plusDays(1)), first.map { it.date })
        val told = HeatAlerts.remembered(emptySet(), today, first)
        assertTrue("Not again the next morning", HeatAlerts.toTell(forecast, today.plusDays(1), HeatAlerts.DEFAULT_TENTHS_C, told).none { it.date == today.plusDays(1) })
        assertEquals("The day after is new", listOf(today.plusDays(2)), HeatAlerts.toTell(forecast, today.plusDays(1), HeatAlerts.DEFAULT_TENTHS_C, told).map { it.date })
        assertTrue("Past days are forgotten", HeatAlerts.remembered(told, today.plusDays(3), emptyList()).isEmpty())
    }

    @Test
    fun `a keeper can choose to be warned sooner`() {
        assertEquals(listOf(today, today.plusDays(1)), HeatAlerts.toTell(forecast, today, 340, emptySet()).map { it.date })
    }

    @Test
    fun `a hive's strength is its latest weight and count`() {
        val logs = listOf(
            NativeLog("a", "h1", LocalDate.of(2026, 9, 1), NativeLogKind.WEIGHT, weightGrams = 6_200),
            NativeLog("b", "h1", LocalDate.of(2026, 11, 1), NativeLogKind.WEIGHT, weightGrams = 7_100),
            NativeLog("c", "h1", LocalDate.of(2026, 10, 5), NativeLogKind.ACTIVITY, beesPerMinute = 42),
            NativeLog("d", "h2", LocalDate.of(2026, 11, 20), NativeLogKind.WEIGHT, weightGrams = 9_000),
        )
        assertEquals(NativeStrength(7_100, LocalDate.of(2026, 11, 1), 42, LocalDate.of(2026, 10, 5)), NativeStrength.of("h1", logs))
    }
}
