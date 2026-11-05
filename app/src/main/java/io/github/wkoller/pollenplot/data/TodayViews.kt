package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.domain.irrigation.PlanReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.LocalDate

/** Something the garden needs today, for the Today card on the Plot. */
sealed interface TodayItem {
    /** A line or zone due for watering, with its minutes or its litres in all. */
    data class Watering(val name: String, val minutes: Int?, val litres: Long) : TodayItem

    /** A honey bee hive check due by today, overdue when it was due before. */
    data class HiveCheck(val hive: String, val kind: HiveTaskKind, val overdue: Boolean) : TodayItem

    /** A day forecast to reach the keeper's warning temperature, while there are stingless bee hives. */
    data class HotDay(val today: Boolean, val maxTenthsC: Int) : TodayItem

    /** A bee hotel worth cleaning out this spring. */
    data class CleanHotel(val hotel: String) : TodayItem

    /** Pot zones to water deeply today, because tomorrow is forecast to reach [maxTenthsC]. */
    data class PotsBeforeHeat(val zones: List<String>, val maxTenthsC: Int) : TodayItem

    /** A potted plant due a flush, or a look at repotting. */
    data class PotCare(val plantId: String, val plant: String, val kind: PotTaskKind) : TodayItem

    /** A zone due a feed, for the plants in it that want one. */
    data class Feed(val zoneId: String, val zone: String, val plants: List<String>) : TodayItem

    /** A treated problem due a second look. */
    data class ProblemFollowUp(val problemId: String, val problem: String, val plant: String?) : TodayItem

    /** A plant in its pruning months with no prune recorded yet. */
    data class Prune(val plantId: String, val plant: String) : TodayItem

    /** The user's own reminder: a hot day is coming and no conditioner was given lately. */
    data class Conditioner(val today: Boolean, val maxTenthsC: Int) : TodayItem
}

/** What the Today card shows: the day's jobs from every part of the garden, the finds to review and the weeds. */
data class TodayState(val items: List<TodayItem>, val reviews: Int, val weeds: Int) {
    val isEmpty: Boolean get() = items.isEmpty() && reviews == 0 && weeds == 0
}

class TodayViews(
    water: WaterRepository,
    waterViews: WaterViews,
    beeViews: BeeViews,
    garden: GardenRepository,
    views: GardenViews,
    settings: SettingsStore,
    private val clock: Clock,
    /** Garden care due today: pots, feeds and the conditioner reminder. */
    care: Flow<List<TodayItem>> = flowOf(emptyList()),
) {
    private val jobs: Flow<List<TodayItem>> = combine(waterViews.state, beeViews.hives, beeViews.hotels, beeViews.nativeHives, water.weather) { water, hives, hotels, natives, weather ->
        val today = LocalDate.now(clock)
        buildList {
            water.lines.filter { it.plan.reason == PlanReason.DUE }.forEach { add(TodayItem.Watering(it.line.name, it.plan.minutes.takeIf { _ -> !it.line.method.byHand }, it.plan.applyMl / 1000)) }
            // A pot zone due before a hot day gets the deep watering below instead of its usual one.
            val pots = water.potsBeforeHeat?.zones?.map { it.id }.orEmpty().toSet()
            water.byHand.filter { it.plan.reason == PlanReason.DUE && it.zone.id !in pots }.forEach { add(TodayItem.Watering(it.zone.name, null, it.plan.applyMl / 1000)) }
            water.potsBeforeHeat?.let { warning -> add(TodayItem.PotsBeforeHeat(warning.zones.map { it.name }, warning.maxTenthsC)) }
            hives.forEach { view ->
                view.tasks.filter { !it.due.isAfter(today) }.forEach { add(TodayItem.HiveCheck(view.hive.name, it.kind, it.overdue(today))) }
            }
            if (natives.any { it.hive.status == NativeStatus.ACTIVE }) {
                weather.filter { it.date == today || it.date == today.plusDays(1) }.forEach { day ->
                    day.maxTenthsC?.let { add(TodayItem.HotDay(day.date == today, it)) }
                }
            }
            hotels.filter { it.cleanThisSpring }.forEach { add(TodayItem.CleanHotel(it.hotel.name)) }
        }
    }

    val state: Flow<TodayState> = combine(jobs, garden.pendingReviewCount, views.plants, settings.settings, care) { items, reviews, plants, current, careDue ->
        // A hot day counts only at the temperature the keeper chose to be warned at, and only while warnings are on.
        val shown = items.filter { item -> item !is TodayItem.HotDay || (current.heatAlerts && item.maxTenthsC >= current.heatAlertTenthsC) } +
            careDue
        TodayState(shown, reviews, plants.count { it.isGrowing && it.weed.isWeed })
    }
}
