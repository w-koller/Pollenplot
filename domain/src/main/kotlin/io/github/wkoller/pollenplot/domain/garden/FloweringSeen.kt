package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.MonthSet
import java.time.LocalDate

/** The months a plant was seen in flower, from the days it was recorded flowering. */
object FloweringSeen {
    /**
     * The months of the days in the year before [today], so that last year's timing does not stay on the calendar for
     * ever. The months are the garden's own, so they need no turning for the hemisphere.
     */
    fun lastYear(days: Iterable<LocalDate>, today: LocalDate): MonthSet {
        val since = today.minusYears(1)
        return MonthSet.of(days.filter { it.isAfter(since) && !it.isAfter(today) }.map { it.month })
    }

    /**
     * Whether a plant is likely in flower now: [expected], its flowering months in the garden's own months, holds this
     * month, or it was seen in flower within the last [RECENT_DAYS] days. A plant with neither is not counted.
     */
    fun inFlowerNow(expected: MonthSet?, seen: Iterable<LocalDate>, today: LocalDate): Boolean =
        expected?.contains(today.month) == true || seen.any { !it.isAfter(today) && it.isAfter(today.minusDays(RECENT_DAYS)) }

    /** How long a day seen in flower counts as flowering now, Pollenplot's choice of about a month. */
    const val RECENT_DAYS = 30L
}
