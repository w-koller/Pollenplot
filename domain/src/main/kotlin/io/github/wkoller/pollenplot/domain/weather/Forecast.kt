package io.github.wkoller.pollenplot.domain.weather

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
