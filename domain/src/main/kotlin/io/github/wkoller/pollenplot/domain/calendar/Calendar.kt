package io.github.wkoller.pollenplot.domain.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * When the garden's reminders come. [EACH_DAY] tells of each job on the morning it falls due, as before. [WEEKENDS]
 * and [CHOSEN_DAYS] gather the jobs that can wait until the next Saturday or Sunday, or the next of the user's
 * [ReminderSchedule.days]. [WEEKLY_LIST] and [MONTHLY_LIST] send one to-do list on Monday morning, or on the first of
 * the month, of the jobs in the week or month ahead.
 */
enum class ReminderMode { EACH_DAY, WEEKENDS, CHOSEN_DAYS, WEEKLY_LIST, MONTHLY_LIST }

/**
 * The reminder schedule. Watering, hot days for stingless bee hives and pots before a hot day cannot wait for another
 * day, so they are told on the morning they fall due whatever the schedule; everything else follows it.
 */
data class ReminderSchedule(val mode: ReminderMode = ReminderMode.EACH_DAY, val days: Set<DayOfWeek> = emptySet()) {
    val isList: Boolean get() = mode == ReminderMode.WEEKLY_LIST || mode == ReminderMode.MONTHLY_LIST

    /** Whether reminders that can wait are told on [day]. Chosen days with none chosen are every day. */
    fun isReminderDay(day: LocalDate): Boolean = when (mode) {
        ReminderMode.EACH_DAY -> true
        ReminderMode.WEEKENDS -> day.dayOfWeek in WEEKEND
        ReminderMode.CHOSEN_DAYS -> days.isEmpty() || day.dayOfWeek in days
        ReminderMode.WEEKLY_LIST -> day.dayOfWeek == DayOfWeek.MONDAY
        ReminderMode.MONTHLY_LIST -> day.dayOfMonth == 1
    }

    /** The first reminder day on or after [due]: the morning a job due then is told about. */
    fun remindOn(due: LocalDate): LocalDate {
        var day = due
        repeat(31) {
            if (isReminderDay(day)) return day
            day = day.plusDays(1)
        }
        return due
    }

    /** The days a to-do list sent on [day] covers: its week, Monday to Sunday, or its month. */
    fun listPeriod(day: LocalDate): ClosedRange<LocalDate> = when (mode) {
        ReminderMode.MONTHLY_LIST -> day.withDayOfMonth(1)..day.with(TemporalAdjusters.lastDayOfMonth())
        else -> day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it..it.plusDays(6) }
    }

    companion object {
        val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** The weather a day's WMO code describes, in the few kinds the calendar draws. */
enum class WeatherKind {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    STORM,
    ;

    companion object {
        /** The kind of the WMO weather interpretation code that Open-Meteo gives, or null for none or one not known. */
        fun of(code: Int?): WeatherKind? = when (code) {
            null -> null
            0, 1 -> CLEAR
            2 -> PARTLY_CLOUDY
            3 -> CLOUDY
            45, 48 -> FOG
            51, 53, 55, 56, 57 -> DRIZZLE
            61, 63, 65, 66, 67, 80, 81, 82, 71, 73, 75, 77, 85, 86 -> RAIN
            95, 96, 99 -> STORM
            else -> null
        }
    }
}
