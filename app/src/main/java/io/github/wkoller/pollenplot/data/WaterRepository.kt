package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.WateringEventEntity
import io.github.wkoller.pollenplot.data.db.WateringLineEntity
import io.github.wkoller.pollenplot.data.db.WeatherDayEntity
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import io.github.wkoller.pollenplot.domain.irrigation.WeatherDay
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.weather.OpenMeteoForecast
import io.github.wkoller.pollenplot.web.Web
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/** A watering someone recorded: for a line, or for a zone watered by hand. */
data class Watering(val id: String, val lineId: String?, val zoneId: String?, val date: LocalDate, val notes: String = "")

/**
 * Watering lines, the waterings recorded, and the garden's weather from Open-Meteo, kept for when there is no
 * connection. Only the garden's place, rounded to about 1 km, is sent for the weather.
 */
class WaterRepository(private val db: PollenplotDatabase, private val web: Web, private val clock: Clock) {
    private val dao = db.waterDao()

    private fun now() = clock.millis()

    val lines: Flow<List<WateringLine>> = dao.observeLines().map { list ->
        list.map { WateringLine(it.id, it.name, WaterMethod.valueOf(it.method), it.zoneIds.split(',').filter(String::isNotBlank).toSet(), it.flowMlPerHour, it.notes, it.poolingMinutes) }
    }

    val waterings: Flow<List<Watering>> = dao.observeEvents().map { list ->
        list.map { Watering(it.id, it.lineId, it.zoneId, LocalDate.ofEpochDay(it.dateEpochDay), it.notes) }
    }

    val weather: Flow<List<WeatherDay>> = dao.observeWeather().map { list ->
        list.map { WeatherDay(LocalDate.ofEpochDay(it.dateEpochDay), it.et0Um, it.rainUm, it.rainChanceBp, it.maxTenthsC) }
    }

    /** The newest weather fetched, in epoch milliseconds, or null before the first. */
    val weatherFetchedAt: Flow<Long?> = dao.observeWeather().map { list -> list.maxOfOrNull { it.fetchedAt } }

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun saveLine(line: WateringLine) {
        val createdAt = dao.line(line.id)?.createdAt ?: now()
        dao.upsertLine(
            WateringLineEntity(
                line.id, line.name.trim(), line.method.name, line.zoneIds.sorted().joinToString(","), line.flowMlPerHour, line.notes.trim(), createdAt, now(),
                line.poolingMinutes?.takeIf { line.method.spreads && it > 0 },
            ),
        )
    }

    suspend fun deleteLine(id: String) = db.withTransaction {
        dao.deleteEventsFor(id)
        dao.deleteLine(id)
    }

    suspend fun recordWatering(watering: Watering) =
        dao.upsertEvent(WateringEventEntity(watering.id, watering.lineId, watering.zoneId, watering.date.toEpochDay(), watering.notes.trim(), now()))

    suspend fun deleteWatering(id: String) = dao.deleteEvent(id)

    /**
     * Fetches the last week and the next for [place], keeps them, and forgets weather older than a fortnight. Returns
     * whether it worked; without a connection the weather already kept is used.
     */
    suspend fun refreshWeather(place: GardenPlace): Boolean {
        val days = try {
            OpenMeteoForecast.parseWeather(web.get(OpenMeteoForecast.weatherUrl(place)))
        } catch (e: IOException) {
            return false
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (days.isEmpty()) return false
        val fetched = now()
        dao.upsertWeather(days.map { WeatherDayEntity(it.date.toEpochDay(), it.et0Um, it.rainUm, it.rainChanceBp, it.maxTenthsC, fetched) })
        dao.deleteWeatherBefore(LocalDate.now(clock).minusDays(KEPT_DAYS).toEpochDay())
        return true
    }

    private companion object {
        const val KEPT_DAYS = 14L
    }
}
