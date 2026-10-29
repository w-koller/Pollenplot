package io.github.wkoller.pollenplot.domain.weather

import io.github.wkoller.pollenplot.domain.place.GardenPlace
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ForecastTest {
    @Test
    fun `the request sends only the rounded place`() {
        val url = OpenMeteoForecast.url(GardenPlace.at(-27.4987, 152.9934))
        assertEquals("https://api.open-meteo.com/v1/forecast?latitude=-27.50&longitude=152.99&daily=temperature_2m_max&timezone=auto&forecast_days=3", url)
    }

    @Test
    fun `a weather answer becomes days of ET0 and rain in micrometres`() {
        val answer = """
            {"timezone":"Australia/Brisbane",
             "daily":{"time":["2026-10-27","2026-10-28","2026-10-29"],
              "et0_fao_evapotranspiration":[4.13,4.36,null],"precipitation_sum":[13.40,0.20,0.00],
              "precipitation_probability_max":[98,null,8],"temperature_2m_max":[28.6,28.1,27.1]}}
        """.trimIndent()
        val days = OpenMeteoForecast.parseWeather(answer)
        assertEquals(2, days.size)
        assertEquals(io.github.wkoller.pollenplot.domain.irrigation.WeatherDay(LocalDate.of(2026, 10, 27), 4_130, 13_400, 9_800, 286), days[0])
        assertEquals(null, days[1].rainChanceBp)
        assertEquals(true, OpenMeteoForecast.weatherUrl(GardenPlace.at(-27.4987, 152.9934)).contains("past_days=7&forecast_days=7"))
    }

    @Test
    fun `an answer becomes days with their highs in tenths`() {
        val answer = """
            {"latitude":-27.521969,"longitude":152.98993,"timezone":"Australia/Brisbane",
             "daily_units":{"time":"iso8601","temperature_2m_max":"°C"},
             "daily":{"time":["2026-10-29","2026-10-30","2026-10-31"],"temperature_2m_max":[27.1,39.25,null]}}
        """.trimIndent()
        assertEquals(
            listOf(DayForecast(LocalDate.of(2026, 10, 29), 271), DayForecast(LocalDate.of(2026, 10, 30), 393)),
            OpenMeteoForecast.parse(answer),
        )
        assertEquals(emptyList<DayForecast>(), OpenMeteoForecast.parse("""{"error":true,"reason":"Latitude must be in range"}"""))
    }
}
