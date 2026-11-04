package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PlantPot
import io.github.wkoller.pollenplot.domain.garden.PotCare
import io.github.wkoller.pollenplot.domain.garden.PotTask
import io.github.wkoller.pollenplot.domain.irrigation.PotsBeforeHeat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A plant's pot as its page shows it: the pot, whether its zone is one of pots, its events and its pot jobs. */
data class PotState(val pot: PlantPot?, val inPotZone: Boolean, val events: List<PlantEvent>, val tasks: List<PotTask>) {
    val potted: Boolean get() = PotCare.isPotted(pot, inPotZone)
}

/** A pot job that has fallen due, for the Today card and the morning notification. */
data class PotDue(val plantId: String, val title: String, val task: PotTask) {
    /** Names this job for one due date, so a notification tells about it once. */
    val key: String get() = "$plantId:${task.kind}:${task.due}"
}

/** Every plant's pot and events joined with the garden, and the pot jobs due. */
class PotViews(pots: PotRepository, views: GardenViews, private val clock: Clock) {
    private val states: Flow<Map<String, PotState>> = combine(views.plants, views.zones, pots.pots, pots.events) { plants, zones, potsByPlant, events ->
        val potZones = zones.filter { PotsBeforeHeat.isPots(it.zone.kind, it.zone.soil) }.map { it.zone.id }.toSet()
        val byPlant = events.groupBy { it.plantId }
        plants.associate { view ->
            val pot = potsByPlant[view.plant.id]
            val inPotZone = view.plant.zoneId in potZones
            val own = byPlant[view.plant.id].orEmpty()
            val tasks = if (PotCare.isPotted(pot, inPotZone)) PotCare.tasks(own, view.plant.plantedOn) else emptyList()
            view.plant.id to PotState(pot, inPotZone, own, tasks)
        }
    }

    fun plant(plantId: String): Flow<PotState?> = states.map { it[plantId] }

    /** The pot jobs due today or before, for plants still growing. */
    val due: Flow<List<PotDue>> = combine(states, views.plants) { states, plants ->
        val today = LocalDate.now(clock)
        plants.filter { it.isGrowing }.flatMap { view ->
            states[view.plant.id]?.tasks.orEmpty().filter { it.isDue(today) }.map { PotDue(view.plant.id, view.title, it) }
        }
    }

    /** The zones that hold a potted plant, whatever the zone's kind, for watering before a hot day. */
    val pottedZones: Flow<Set<String>> = combine(views.plants, pots.pots) { plants, potsByPlant ->
        plants.filter { it.isGrowing && it.plant.id in potsByPlant }.mapNotNull { it.plant.zoneId }.toSet()
    }
}
