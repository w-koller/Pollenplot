package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.rhythms.CandidatePlant
import io.github.wkoller.pollenplot.domain.rhythms.Lane
import io.github.wkoller.pollenplot.domain.rhythms.RhythmPlant
import io.github.wkoller.pollenplot.domain.rhythms.RhythmsEngine
import io.github.wkoller.pollenplot.domain.rhythms.RhythmsFeed
import io.github.wkoller.pollenplot.domain.rhythms.Suggestion
import io.github.wkoller.pollenplot.domain.rhythms.TimingShift
import io.github.wkoller.pollenplot.domain.wildlife.FaunaRole
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.Month

/**
 * Pollen Rhythms for the screen: each plant's flowering for the garden's hemisphere and as photos showed it in the
 * last year, the three groups' lanes, and plants that would fill each group's gaps.
 */
data class RhythmsState(
    val today: LocalDate,
    val plants: List<RhythmPlant>,
    val lanes: List<Lane>,
    val suggestions: Map<BeeGroup, List<Suggestion>>,
    val keepsHoneyBees: Boolean = false,
    val keepsStinglessBees: Boolean = false,
    val shifts: List<TimingShift> = emptyList(),
    /** Pollinators seen on the garden's plants in the last month, the latest first. */
    val visits: List<SightingView> = emptyList(),
) {
    val month: Month get() = today.month

    /** The plants flowering this month, expected or seen. */
    val nowFlowering: List<RhythmPlant> get() = plants.filter { month in it.allFlowering }

    /** The plants that start flowering next month. */
    val startingNextMonth: List<RhythmPlant> get() = plants.filter { month.plus(1) in it.flowering && month !in it.allFlowering }

    val richNectar: List<RhythmPlant> get() = RhythmsFeed.richNectar(plants, month)

    val forStingless: List<RhythmPlant> get() = RhythmsFeed.forStingless(plants, month)

    fun title(plantId: String): String? = plants.firstOrNull { it.id == plantId }?.title
}

class RhythmsViews(
    garden: GardenRepository,
    views: GardenViews,
    bees: BeeRepository,
    natives: NativeBeeRepository,
    private val knowledge: KnowledgeRepository,
    settings: SettingsStore,
    private val clock: Clock,
) {
    private val calendar: Flow<RhythmsState> = combine(views.plants, garden.floweringList, views.locality, settings.settings, garden.zoneList) { plants, seen, locality, current, zones ->
        val kb = knowledge.await()
        val today = LocalDate.now(clock)
        val hemisphere = current.gardenPlace?.let { Hemisphere.ofLatitude(it.latE7) } ?: Hemisphere.SOUTHERN
        // Flowers seen in the last year, so that last year's timing does not stay on the calendar for ever.
        val since = today.minusYears(1)
        val seenMonths = seen.filter { (_, day) -> day.isAfter(since) }.groupBy({ it.first }, { it.second.month })
        val growing = plants.filter { it.plant.status == PlantStatus.GROWING }
        val rows = growing.map { view ->
            val care = view.care?.care
            RhythmPlant(
                id = view.plant.id,
                title = view.title,
                speciesName = view.plant.speciesName,
                form = view.species?.form,
                quantity = view.plant.quantity,
                flowering = care?.flowering?.fromSouthern(hemisphere) ?: MonthSet.NONE,
                peak = care?.peakFlowering?.fromSouthern(hemisphere) ?: MonthSet.NONE,
                seenFlowering = MonthSet.of(seenMonths[view.plant.id].orEmpty()),
                forage = care?.forage,
            )
        }
        val lanes = RhythmsEngine.lanes(rows)
        val inGarden = growing.mapNotNull { it.species?.name }.toSet()
        val candidates = kb.allSpecies.map { species ->
            val care = CareResolver.resolve(kb, species).care
            CandidatePlant(species, care.flowering?.fromSouthern(hemisphere) ?: MonthSet.NONE, care.forage, care.sun, weed = species.wons || locality.weed(species).isWeed)
        }
        val zoneSun = zones.map { it.name to it.sun }
        val suggestions = lanes.associate { lane -> lane.group to RhythmsEngine.suggestions(lane.group, lane.gaps, candidates, inGarden, current.gardenState, zoneSun) }
        RhythmsState(today, rows.sortedBy { it.title.lowercase() }, lanes, suggestions, shifts = RhythmsFeed.shifts(seen, today))
    }

    val state: Flow<RhythmsState> = combine(calendar, bees.hives, natives.hives, views.sightings) { state, honey, stingless, sightings ->
        val since = state.today.minusDays(VISITS_DAYS).atStartOfDay(clock.zone).toInstant()
        state.copy(
            keepsHoneyBees = honey.any { it.status == HiveStatus.ACTIVE || it.status == HiveStatus.QUEENLESS },
            keepsStinglessBees = stingless.any { it.status == NativeStatus.ACTIVE },
            visits = sightings.filter { it.sighting.plantId != null && it.sighting.seenAt.isAfter(since) && it.animal?.roles?.contains(FaunaRole.POLLINATOR) == true }
                .sortedByDescending { it.sighting.seenAt }
                .take(VISITS_SHOWN),
        )
    }

    private companion object {
        const val VISITS_DAYS = 30L
        const val VISITS_SHOWN = 5
    }
}
