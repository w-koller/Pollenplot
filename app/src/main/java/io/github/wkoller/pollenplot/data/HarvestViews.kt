package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.domain.garden.Harvest
import io.github.wkoller.pollenplot.domain.garden.Harvests
import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PruneSchedule
import io.github.wkoller.pollenplot.domain.garden.PruneTasks
import io.github.wkoller.pollenplot.domain.garden.PruningGuide
import io.github.wkoller.pollenplot.domain.garden.SeasonHarvest
import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A plant's harvests as its page shows them: whether it is offered one, each harvest, and the totals by season. */
data class HarvestState(val offered: Boolean, val harvests: List<Harvest>, val seasons: List<SeasonHarvest>, val months: MonthSet)

/**
 * A plant's pruning as its page shows it: the sourced schedule, the months to prune in for the garden's hemisphere
 * (the user's own when they corrected them), whether a prune is due today, and when it was last pruned.
 */
data class PruneState(
    val schedule: PruneSchedule?,
    val months: MonthSet?,
    val own: Boolean,
    val due: Boolean,
    val lastPruned: LocalDate?,
    /** The plant flowers in every month, so no month comes after its flowering. */
    val floweringAllYear: Boolean = false,
)

/** A plant due a prune, for the Today card and the morning notification. */
data class PruneDue(val plantId: String, val title: String, val since: LocalDate) {
    /** Names this prune for one season, so a notification tells about it once. */
    val key: String get() = "$plantId:$since"
}

/** Harvests and pruning, joined with the garden. */
class HarvestViews(
    harvests: HarvestRepository,
    views: GardenViews,
    pots: PotRepository,
    private val guide: () -> PruningGuide,
    private val clock: Clock,
) {
    private val harvestStates: Flow<Map<String, HarvestState>> = combine(views.plants, views.zones, harvests.harvests, views.locality) { plants, zones, all, locality ->
        val kinds = zones.associate { it.zone.id to it.zone.kind }
        val byPlant = all.groupBy { it.plantId }
        plants.associate { view ->
            val own = byPlant[view.plant.id].orEmpty()
            view.plant.id to HarvestState(
                offered = Harvests.offered(view.care?.care?.edible, view.plant.zoneId?.let(kinds::get)) || own.isNotEmpty(),
                harvests = own,
                seasons = Harvests.bySeason(own, locality.hemisphere),
                months = Harvests.months(own),
            )
        }
    }

    fun harvest(plantId: String): Flow<HarvestState?> = harvestStates.map { it[plantId] }

    private val pruneStates: Flow<Map<String, PruneState>> = combine(views.plants, pots.events, views.locality) { plants, events, locality ->
        val today = LocalDate.now(clock)
        val byPlant = events.groupBy { it.plantId }
        val pruning = guide()
        plants.associate { view ->
            val schedule = view.species?.let { pruning.forPlant(it.name, it.genus, it.family) }
            val care = view.care?.care
            val months = PruneTasks.months(schedule, care?.pruningMonths, care?.flowering, locality.hemisphere)
            val own = byPlant[view.plant.id].orEmpty()
            view.plant.id to PruneState(
                schedule = schedule,
                months = months,
                own = care?.pruningMonths != null,
                due = view.isGrowing && PruneTasks.due(months, own, today),
                lastPruned = own.lastPruned(),
                floweringAllYear = care?.flowering == MonthSet.ALL,
            )
        }
    }

    fun prune(plantId: String): Flow<PruneState?> = pruneStates.map { it[plantId] }

    /** The plants due a prune today. */
    val due: Flow<List<PruneDue>> = combine(pruneStates, views.plants) { states, plants ->
        val today = LocalDate.now(clock)
        plants.mapNotNull { view ->
            val state = states[view.plant.id]?.takeIf { it.due } ?: return@mapNotNull null
            PruneDue(view.plant.id, view.title, PruneTasks.runStart(state.months!!, today)!!)
        }
    }

    private fun List<PlantEvent>.lastPruned(): LocalDate? = filter { it.kind.pruning }.maxOfOrNull { it.date }
}
