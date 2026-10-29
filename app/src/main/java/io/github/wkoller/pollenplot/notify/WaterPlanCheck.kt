package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.data.WaterViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.irrigation.PlanReason
import kotlinx.coroutines.flow.first

/**
 * A watering due today, for the notification: the line or zone, and the minutes to run it or the litres to give, or
 * pots to water deeply [beforeHeat] tomorrow.
 */
data class DueWatering(val name: String, val minutes: Int?, val litres: Long, val beforeHeat: Boolean = false)

/**
 * Each morning's look at the watering plan: the weather is brought up to date, and one notification tells what is
 * due today, once a day.
 */
class WaterPlanCheck(private val water: WaterRepository, private val views: WaterViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        if (!current.waterReminders) return
        val place = current.gardenPlace ?: return
        water.refreshWeather(place)
        val state = views.state.first()
        if (current.waterToldOn == state.today.toString()) return
        // A pot zone due before a hot day is told about once, for its deep watering.
        val pots = state.potsBeforeHeat?.zones.orEmpty()
        val due = state.lines.filter { it.plan.reason == PlanReason.DUE }.map { DueWatering(it.line.name, it.plan.minutes.takeIf { _ -> !it.line.method.byHand }, it.plan.applyMl / 1000) } +
            state.byHand.filter { it.plan.reason == PlanReason.DUE && it.zone !in pots }.map { DueWatering(it.zone.name, null, it.plan.applyMl / 1000) } +
            pots.map { DueWatering(it.name, null, 0, beforeHeat = true) }
        if (due.isEmpty()) return
        if (notifications.showWatering(due, state.window)) settings.update { it.copy(waterToldOn = state.today.toString()) }
    }
}
