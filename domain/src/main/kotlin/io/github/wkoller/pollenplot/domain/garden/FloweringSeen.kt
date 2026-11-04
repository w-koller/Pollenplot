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
}
