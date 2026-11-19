package io.github.wkoller.pollenplot.domain.calendar

import io.github.wkoller.pollenplot.domain.feeding.Feed
import io.github.wkoller.pollenplot.domain.feeding.FeedPlanner
import io.github.wkoller.pollenplot.domain.feeding.FeedPlant
import io.github.wkoller.pollenplot.domain.feeding.FeedSchedule
import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PlantEventKind
import io.github.wkoller.pollenplot.domain.garden.PruneTasks
import io.github.wkoller.pollenplot.domain.hotels.HotelCare
import io.github.wkoller.pollenplot.domain.hotels.HotelState
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.weather.OpenMeteoForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

class CalendarRulesTest {
    /** A Wednesday. */
    private val wednesday = LocalDate.of(2026, 10, 28)

    @Test
    fun eachDayRemindsOnTheDayAJobFallsDue() {
        val schedule = ReminderSchedule()
        assertTrue(schedule.isReminderDay(wednesday))
        assertEquals(wednesday, schedule.remindOn(wednesday))
    }

    @Test
    fun weekendsGatherTheWeeksJobsForSaturday() {
        val schedule = ReminderSchedule(ReminderMode.WEEKENDS)
        assertFalse(schedule.isReminderDay(wednesday))
        assertEquals(LocalDate.of(2026, 10, 31), schedule.remindOn(wednesday))
        assertEquals("Sunday is a weekend day too", LocalDate.of(2026, 11, 1), schedule.remindOn(LocalDate.of(2026, 11, 1)))
    }

    @Test
    fun chosenDaysRemindOnTheNextOneAndNoneChosenIsEveryDay() {
        val schedule = ReminderSchedule(ReminderMode.CHOSEN_DAYS, setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        assertEquals(LocalDate.of(2026, 10, 30), schedule.remindOn(wednesday))
        assertTrue(ReminderSchedule(ReminderMode.CHOSEN_DAYS).isReminderDay(wednesday))
    }

    @Test
    fun aListCoversItsWeekOrItsMonth() {
        val weekly = ReminderSchedule(ReminderMode.WEEKLY_LIST)
        assertTrue(weekly.isList)
        assertTrue(weekly.isReminderDay(LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 10, 26)..LocalDate.of(2026, 11, 1), weekly.listPeriod(wednesday))
        val monthly = ReminderSchedule(ReminderMode.MONTHLY_LIST)
        assertTrue(monthly.isReminderDay(LocalDate.of(2026, 11, 1)))
        assertEquals(LocalDate.of(2026, 10, 1)..LocalDate.of(2026, 10, 31), monthly.listPeriod(wednesday))
    }

    @Test
    fun weatherCodesBecomeKinds() {
        assertEquals(WeatherKind.CLEAR, WeatherKind.of(0))
        assertEquals(WeatherKind.PARTLY_CLOUDY, WeatherKind.of(2))
        assertEquals(WeatherKind.RAIN, WeatherKind.of(63))
        assertEquals(WeatherKind.STORM, WeatherKind.of(95))
        assertNull(WeatherKind.of(null))
        assertNull(WeatherKind.of(42))
    }

    @Test
    fun theWeatherAnswerGivesLowsAndCodesWhenAsked() {
        val json = """{"daily":{"time":["2026-10-28"],"et0_fao_evapotranspiration":[4.2],"precipitation_sum":[0.0],
            "precipitation_probability_max":[10],"temperature_2m_max":[29.4],"temperature_2m_min":[17.1],"weather_code":[2]}}"""
        val day = OpenMeteoForecast.parseWeather(json).single()
        assertEquals(171, day.minTenthsC)
        assertEquals(2, day.code)
        val older = """{"daily":{"time":["2026-10-28"],"et0_fao_evapotranspiration":[4.2],"temperature_2m_max":[29.4]}}"""
        assertNull("An answer without them leaves them out", OpenMeteoForecast.parseWeather(older).single().code)
    }

    @Test
    fun feedsAreForecastEveryScheduleDaysWithinFeedingMonths() {
        val citrus = FeedSchedule("Citrus", MonthSet.of(listOf(Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER, Month.FEBRUARY)), 42, "Guide", "https://example.org")
        val plants = listOf(FeedPlant("Lemon", "zone", citrus))
        val fed = listOf(Feed("f", "dynamic", LocalDate.of(2026, 10, 1), setOf("zone")))
        val days = FeedPlanner.upcoming(plants, fed, emptyMap(), wednesday, LocalDate.of(2027, 3, 31), Hemisphere.SOUTHERN).map { it.due }
        // Due 12 November, then 24 December falls outside the months, so on to 1 February and six weeks on, 15 March, is out too.
        assertEquals(listOf(LocalDate.of(2026, 11, 12), LocalDate.of(2027, 2, 1)), days)
    }

    @Test
    fun aPruneFallsDueAtTheStartOfEachRunOfPruningMonths() {
        val winter = MonthSet.of(listOf(Month.JUNE, Month.JULY))
        val days = PruneTasks.upcoming(winter, emptyList(), wednesday, LocalDate.of(2027, 12, 31))
        assertEquals(listOf(LocalDate.of(2027, 6, 1)), days)
        // In a run, with no prune since it began, it is due now.
        val inJune = LocalDate.of(2027, 6, 10)
        assertEquals(inJune, PruneTasks.upcoming(winter, emptyList(), inJune, inJune.plusMonths(1)).first())
        val pruned = listOf(PlantEvent("p", "plant", PlantEventKind.PRUNED, LocalDate.of(2027, 6, 2)))
        assertTrue(PruneTasks.upcoming(winter, pruned, inJune, inJune.plusMonths(1)).isEmpty())
    }

    @Test
    fun aHotelIsWorthCleaningOnTheFirstDayOfSpring() {
        val cleaned = HotelState(latest = null, lastCleaned = LocalDate.of(2026, 9, 15))
        assertNull("Cleaned this spring", HotelCare.nextClean(cleaned, wednesday, LocalDate.of(2027, 3, 1), Hemisphere.SOUTHERN))
        assertEquals(LocalDate.of(2027, 9, 1), HotelCare.nextClean(cleaned, wednesday, LocalDate.of(2027, 12, 1), Hemisphere.SOUTHERN))
        assertEquals("Never cleaned, it is due now in spring", wednesday, HotelCare.nextClean(HotelState(null, null), wednesday, wednesday, Hemisphere.SOUTHERN))
    }
}
