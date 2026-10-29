package io.github.wkoller.pollenplot.domain.rhythms

import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

/** A plant that flowered earlier or later this year than last, by [days]: below zero is earlier. */
data class TimingShift(val plantId: String, val days: Int)

/** What the Rhythms tab's feed picks out from the garden this month. */
object RhythmsFeed {
    /** The shortest shift in flowering worth telling. */
    const val SHIFT_DAYS = 7

    /** The plants flowering in [month] that are a rich source of nectar for honey bees. */
    fun richNectar(plants: List<RhythmPlant>, month: Month): List<RhythmPlant> =
        plants.filter { month in it.allFlowering && (it.forage?.honeyNectar ?: 0) >= 3 }

    /** The plants flowering in [month] that stingless bees forage on well. */
    fun forStingless(plants: List<RhythmPlant>, month: Month): List<RhythmPlant> =
        plants.filter { month in it.allFlowering && RhythmsEngine.score(it.forage, BeeGroup.STINGLESS) >= 2 }

    /**
     * Plants first seen flowering this year at least [SHIFT_DAYS] days earlier or later in the year than they were first
     * seen flowering last year. Only the year's first photo of each plant flowering counts, and only up to [today].
     */
    fun shifts(seen: List<Pair<String, LocalDate>>, today: LocalDate): List<TimingShift> {
        val thisYear = today.year
        return seen.filter { !it.second.isAfter(today) }.groupBy { it.first }.mapNotNull { (plantId, days) ->
            val first = days.map { it.second }.groupBy { it.year }.mapValues { (_, inYear) -> inYear.min() }
            val now = first[thisYear] ?: return@mapNotNull null
            val before = first[thisYear - 1] ?: return@mapNotNull null
            val shift = ChronoUnit.DAYS.between(before.withYear(thisYear), now).toInt()
            if (kotlin.math.abs(shift) >= SHIFT_DAYS) TimingShift(plantId, shift) else null
        }.sortedByDescending { kotlin.math.abs(it.days) }
    }
}
