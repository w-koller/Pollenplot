package io.github.wkoller.pollenplot.domain.garden

import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.domain.season.Seasons
import java.time.LocalDate

/**
 * What was picked from a plant on a day: its weight in grams, how many, a rating from 1 to 5 and the user's notes. Any
 * of them may be left out. The notes are the user's own and never go to the assistant.
 */
data class Harvest(
    val id: String,
    val plantId: String,
    val date: LocalDate,
    val weightG: Int? = null,
    val count: Int? = null,
    val rating: Int? = null,
    val notes: String = "",
) {
    init {
        require(weightG == null || weightG > 0) { "A weight is more than nothing" }
        require(count == null || count > 0) { "A count is at least one" }
        require(rating == null || rating in 1..5) { "A rating is from 1 to 5" }
    }
}

/**
 * One season's harvests from a plant: the season, the day it began, whether it runs into the next year (a Southern
 * summer runs from December), the total weight and count, and how many harvests there were.
 */
data class SeasonHarvest(val season: Season, val start: LocalDate, val crossesYear: Boolean, val weightG: Long, val count: Int, val harvests: Int)

object Harvests {
    /** Whether a plant is offered a harvest: the knowledge base says something on it is edible, or it grows in a veg patch or orchard. */
    fun offered(edible: String?, zoneKind: ZoneKind?): Boolean = edible != null || zoneKind == ZoneKind.VEG_PATCH || zoneKind == ZoneKind.ORCHARD

    /** The season [date] falls in, and the first day of that season. */
    fun seasonOf(date: LocalDate, hemisphere: Hemisphere): Pair<Season, LocalDate> {
        val season = Seasons.of(date.month, hemisphere)
        val first = Seasons.months(season, hemisphere).first()
        val year = if (date.monthValue >= first.value) date.year else date.year - 1
        return season to LocalDate.of(year, first, 1)
    }

    /** The harvests added up by season, the latest season first. */
    fun bySeason(harvests: List<Harvest>, hemisphere: Hemisphere): List<SeasonHarvest> =
        harvests.groupBy { seasonOf(it.date, hemisphere) }
            .map { (key, list) ->
                val (season, start) = key
                SeasonHarvest(
                    season = season,
                    start = start,
                    crossesYear = start.plusMonths(2).year != start.year,
                    weightG = list.sumOf { (it.weightG ?: 0).toLong() },
                    count = list.sumOf { it.count ?: 0 },
                    harvests = list.size,
                )
            }
            .sortedByDescending { it.start }

    /** The months anything was picked in, in the garden's own calendar. */
    fun months(harvests: List<Harvest>): MonthSet = MonthSet.of(harvests.map { it.date.month })
}
