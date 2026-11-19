package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.MonthSet
import java.time.LocalDate
import java.time.Month

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

    /**
     * Whether a plant is likely in flower in [month]: [expected], its flowering months in the garden's own months,
     * holds it, or [seen], the months it was seen in flower in the last year, does.
     */
    fun inFlowerIn(expected: MonthSet?, seen: MonthSet, month: Month): Boolean = expected?.contains(month) == true || month in seen

    /** Whether anything says when a plant flowers: its flowering months, or a day it was seen in flower. */
    fun known(expected: MonthSet?, seen: Iterable<LocalDate>): Boolean = (expected != null && !expected.isEmpty) || seen.any()
}

/**
 * How much of a zone is in flower, for the Plot's flowering layers: the share of the zone's plants that are in flower
 * among those whose flowering is known. Plants that nothing says the flowering of are left out, so they do not make a
 * zone look bare. The cut-offs at a third and two thirds are Pollenplot's choice.
 */
enum class BloomGrade {
    NONE,
    FEW,
    SOME,
    MOST,
    ;

    companion object {
        /** The grade of [inFlower] plants out of [known], or null when no plant's flowering is known. */
        fun of(inFlower: Int, known: Int): BloomGrade? = when {
            known <= 0 -> null
            inFlower <= 0 -> NONE
            inFlower * 3 < known -> FEW
            inFlower * 3 < known * 2 -> SOME
            else -> MOST
        }
    }
}
