package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.irrigation.Pairing
import io.github.wkoller.pollenplot.domain.irrigation.PairingNote
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WaterPlan
import io.github.wkoller.pollenplot.domain.irrigation.WaterPlanner
import io.github.wkoller.pollenplot.domain.irrigation.WaterPlant
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate

/** A line with the names of its zones, its plan, the plants pairing flags, and when it was last watered. */
data class LinePlanView(val line: WateringLine, val zoneNames: List<String>, val plants: List<WaterPlant>, val plan: WaterPlan, val pairing: List<PairingNote>, val lastWatered: LocalDate?)

/** A zone with plants and no line, watered by hand, with its plan. */
data class HandPlanView(val zone: Zone, val plants: List<WaterPlant>, val plan: WaterPlan, val lastWatered: LocalDate?)

data class WaterState(
    val today: LocalDate,
    val hasPlace: Boolean,
    val lines: List<LinePlanView>,
    val byHand: List<HandPlanView>,
    /** Today's reference evapotranspiration, in µm, when the weather is known. */
    val et0TodayUm: Int?,
    val rainLastWeekUm: Int,
    val weatherFetchedAt: Long?,
) {
    val hasPlants: Boolean get() = lines.any { it.plants.isNotEmpty() } || byHand.isNotEmpty()
}

/** The Water tab's plans: each line's and each hand-watered zone's, worked out from the weather kept. */
class WaterViews(water: WaterRepository, views: GardenViews, garden: GardenRepository, settings: SettingsStore, private val clock: Clock) {
    private val records = combine(water.lines, water.waterings, water.weather, water.weatherFetchedAt) { lines, waterings, weather, fetchedAt ->
        Records(lines, waterings, weather, fetchedAt)
    }

    val state: Flow<WaterState> = combine(records, views.plants, garden.zoneList, settings.settings) { records, plants, zones, current ->
        val today = LocalDate.now(clock)
        val byZone = zones.associateBy { it.id }
        val growing = plants.filter { it.plant.status == PlantStatus.GROWING && it.plant.zoneId != null }
        fun waterPlants(zoneIds: Set<String>) = growing.filter { it.plant.zoneId in zoneIds }.map { view ->
            val care = view.care?.care
            WaterPlant(
                id = view.plant.id,
                title = view.title,
                zoneId = view.plant.zoneId,
                plantFactorBp = care?.water?.plantFactorBp,
                widthMm = care?.width?.let { (it.minMm + it.maxMm) / 2 },
                form = view.species?.form,
                quantity = view.plant.quantity,
                soil = view.plant.zoneId?.let(byZone::get)?.soil,
            )
        }
        val lines = records.lines.map { line ->
            val linePlants = waterPlants(line.zoneIds)
            val last = records.waterings.filter { it.lineId == line.id }.maxOfOrNull { it.date }
            LinePlanView(
                line = line,
                zoneNames = line.zoneIds.mapNotNull { byZone[it]?.name }.sorted(),
                plants = linePlants,
                plan = WaterPlanner.plan(line.method, line.flowMlPerHour, linePlants, records.weather, today, last),
                pairing = if (line.method.byHand) emptyList() else Pairing.check(linePlants),
                lastWatered = last,
            )
        }
        val onLines = records.lines.flatMap { it.zoneIds }.toSet()
        val byHand = zones.filter { it.id !in onLines }.mapNotNull { zone ->
            val zonePlants = waterPlants(setOf(zone.id)).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val last = records.waterings.filter { it.zoneId == zone.id }.maxOfOrNull { it.date }
            HandPlanView(zone, zonePlants, WaterPlanner.plan(WaterMethod.HOSE, null, zonePlants, records.weather, today, last), last)
        }
        WaterState(
            today = today,
            hasPlace = current.gardenPlace != null,
            lines = lines,
            byHand = byHand,
            et0TodayUm = records.weather.firstOrNull { it.date == today }?.et0Um,
            rainLastWeekUm = records.weather.filter { it.date.isBefore(today) && !it.date.isBefore(today.minusDays(7)) }.sumOf { WaterPlanner.effectiveRainUm(it) },
            weatherFetchedAt = records.fetchedAt,
        )
    }

    private data class Records(val lines: List<WateringLine>, val waterings: List<Watering>, val weather: List<io.github.wkoller.pollenplot.domain.irrigation.WeatherDay>, val fetchedAt: Long?)
}
