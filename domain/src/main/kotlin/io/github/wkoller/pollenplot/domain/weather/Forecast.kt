package io.github.wkoller.pollenplot.domain.weather

import io.github.wkoller.pollenplot.domain.irrigation.WeatherDay
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** A day's forecast top temperature, in tenths of a degree Celsius. */
data class DayForecast(val date: LocalDate, val maxTenthsC: Int)

/**
 * Open-Meteo's daily forecast for the garden. The garden's place is already rounded to about 1 km, and that is all
 * the request sends. The days come in the garden's own time zone.
 */
object OpenMeteoForecast {
    const val FORECAST = "https://api.open-meteo.com/v1/forecast"

    fun url(place: GardenPlace, days: Int = 3): String =
        "$FORECAST?latitude=${place.latText}&longitude=${place.lonText}&daily=temperature_2m_max&timezone=auto&forecast_days=$days"

    /** The last week and the next for the watering plan: ET₀, rain, the chance of rain and the top temperature. */
    fun weatherUrl(place: GardenPlace): String =
        "$FORECAST?latitude=${place.latText}&longitude=${place.lonText}" +
            "&daily=et0_fao_evapotranspiration,precipitation_sum,precipitation_probability_max,temperature_2m_max&timezone=auto&past_days=7&forecast_days=7"

    /** The days in a weather answer, leaving out any day without ET₀; rain not given counts as none. */
    fun parseWeather(json: String): List<WeatherDay> {
        val daily = Json.parseToJsonElement(json).jsonObject["daily"]?.jsonObject ?: return emptyList()
        val days = daily["time"]?.jsonArray ?: return emptyList()
        fun column(name: String) = daily[name]?.jsonArray
        val et0 = column("et0_fao_evapotranspiration") ?: return emptyList()
        val rain = column("precipitation_sum")
        val chance = column("precipitation_probability_max")
        val highs = column("temperature_2m_max")
        return days.mapIndexedNotNull { index, day ->
            val evaporation = et0.getOrNull(index)?.takeIf { it !is JsonNull } ?: return@mapIndexedNotNull null
            WeatherDay(
                date = LocalDate.parse(day.jsonPrimitive.content),
                et0Um = scaled(evaporation.jsonPrimitive.content, 3),
                rainUm = rain?.getOrNull(index)?.takeIf { it !is JsonNull }?.let { scaled(it.jsonPrimitive.content, 3) } ?: 0,
                rainChanceBp = chance?.getOrNull(index)?.takeIf { it !is JsonNull }?.let { scaled(it.jsonPrimitive.content, 2) },
                maxTenthsC = highs?.getOrNull(index)?.takeIf { it !is JsonNull }?.let { scaled(it.jsonPrimitive.content, 1) },
            )
        }
    }

    /** A decimal from an answer as a whole number of smaller units, such as mm as µm with [places] 3. */
    private fun scaled(text: String, places: Int): Int = BigDecimal(text).movePointRight(places).setScale(0, RoundingMode.HALF_UP).toInt()

    /** The days in an answer, leaving out any day without a temperature. */
    fun parse(json: String): List<DayForecast> {
        val daily = Json.parseToJsonElement(json).jsonObject["daily"]?.jsonObject ?: return emptyList()
        val days = daily["time"]?.jsonArray ?: return emptyList()
        val highs = daily["temperature_2m_max"]?.jsonArray ?: return emptyList()
        return days.zip(highs).mapNotNull { (day, high) ->
            if (high is JsonNull) return@mapNotNull null
            val tenths = BigDecimal(high.jsonPrimitive.content).movePointRight(1).setScale(0, RoundingMode.HALF_UP).toInt()
            DayForecast(LocalDate.parse(day.jsonPrimitive.content), tenths)
        }
    }
}
