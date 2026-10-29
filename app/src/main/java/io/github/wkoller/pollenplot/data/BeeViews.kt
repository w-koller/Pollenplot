package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.honey.Apiary
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTask
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTasks
import io.github.wkoller.pollenplot.domain.bees.honey.HoneyHive
import io.github.wkoller.pollenplot.domain.bees.honey.StateMiteChecks
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A hive with the name of its zone, its apiary, when it was last inspected and what it is due for, soonest first. */
data class HiveView(val hive: HoneyHive, val zoneName: String?, val apiary: Apiary?, val lastInspection: LocalDate?, val tasks: List<HiveTask>)

/** The honey bee records joined up for the screens, with each hive's reminders worked out for today. */
class BeeViews(bees: BeeRepository, garden: GardenRepository, settings: SettingsStore, private val clock: Clock) {
    private val records = combine(bees.hives, bees.inspections, bees.checks) { hives, inspections, checks -> Triple(hives, inspections, checks) }

    val hives: Flow<List<HiveView>> = combine(records, garden.zoneList, bees.apiaries, settings.settings) { (hives, inspections, checks), zones, apiaries, current ->
        val today = LocalDate.now(clock)
        val hemisphere = current.gardenPlace?.let { Hemisphere.ofLatitude(it.latE7) } ?: Hemisphere.SOUTHERN
        val zoneNames = zones.associate { it.id to it.name }
        val monthly = StateMiteChecks.monthly(current.monthlyMiteChecks, current.gardenState)
        hives.map { hive ->
            HiveView(
                hive = hive,
                zoneName = hive.zoneId?.let(zoneNames::get),
                apiary = apiaries.firstOrNull { it.id == hive.apiaryId },
                lastInspection = inspections.filter { it.hiveId == hive.id }.maxOfOrNull { it.date },
                tasks = HiveTasks.of(hive, inspections, checks, today, hemisphere, monthly, current.inspectEveryDays.toLong()),
            )
        }
    }

    fun hive(id: String): Flow<HiveView?> = hives.map { list -> list.firstOrNull { it.hive.id == id } }

    fun today(): LocalDate = LocalDate.now(clock)
}
