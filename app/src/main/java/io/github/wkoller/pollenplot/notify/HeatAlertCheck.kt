package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.stingless.HeatAlerts
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.weather.OpenMeteoForecast
import io.github.wkoller.pollenplot.web.Web
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Clock
import java.time.LocalDate

/**
 * Each morning's look at the forecast for the garden, while there are stingless bee hives: a warning before a day
 * forecast to reach the temperature the keeper chose. Only the garden's place, rounded to about 1 km, is sent.
 */
class HeatAlertCheck(
    private val natives: NativeBeeRepository,
    private val settings: SettingsStore,
    private val web: Web,
    private val notifications: Notifications,
    private val clock: Clock,
) {
    suspend fun run() {
        val current = settings.current()
        if (!current.heatAlerts) return
        val place = current.gardenPlace ?: return
        if (natives.hives.first().none { it.status == NativeStatus.ACTIVE }) return
        val forecast = try {
            OpenMeteoForecast.parse(web.get(OpenMeteoForecast.url(place)))
        } catch (e: IOException) {
            // Without a connection there is no forecast; tomorrow morning tries again.
            return
        } catch (e: IllegalArgumentException) {
            return
        }
        val today = LocalDate.now(clock)
        val told = current.heatToldDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val hot = HeatAlerts.toTell(forecast, today, current.heatAlertTenthsC, told)
        val shown = notifications.showHeat(hot, today)
        val remembered = HeatAlerts.remembered(told, today, if (shown) hot else emptyList()).map { it.toString() }.toSet()
        if (remembered != current.heatToldDays) settings.update { it.copy(heatToldDays = remembered) }
    }
}
