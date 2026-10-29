package io.github.wkoller.pollenplot.domain.bees.stingless

import io.github.wkoller.pollenplot.domain.weather.DayForecast
import java.time.LocalDate

/**
 * Which hot days to warn a keeper of stingless bees about. Stingless bees cannot cool their nest the way honey bees do,
 * and Cumberland City Council's guide says that on days above 38 °C a hive can heat to dangerous levels even in a good
 * position, and asks for it to be cooled above 39 °C. Each hot day is told about once, on the morning before it or of
 * it.
 */
object HeatAlerts {
    const val DEFAULT_TENTHS_C = 380

    /** The temperatures a keeper can choose to be warned at, in tenths of a degree. */
    val CHOICES = listOf(350, 380, 400)

    /** Today and tomorrow, if they are forecast to reach [thresholdTenthsC] and have not been told about. */
    fun toTell(forecast: List<DayForecast>, today: LocalDate, thresholdTenthsC: Int, told: Set<LocalDate>): List<DayForecast> =
        forecast.filter { day -> !day.date.isBefore(today) && !day.date.isAfter(today.plusDays(1)) && day.maxTenthsC >= thresholdTenthsC && day.date !in told }
            .sortedBy { it.date }

    /** The hot days to remember as told about: those still to come, and those told about now. Past days drop out. */
    fun remembered(told: Set<LocalDate>, today: LocalDate, tellingNow: List<DayForecast>): Set<LocalDate> =
        told.filterTo(mutableSetOf()) { !it.isBefore(today) } + tellingNow.map { it.date }
}
