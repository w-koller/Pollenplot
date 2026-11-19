package io.github.wkoller.pollenplot.ui.calendar

import android.content.res.Resources
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.CalendarItem
import io.github.wkoller.pollenplot.data.CalendarKind
import io.github.wkoller.pollenplot.data.CalendarViews
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.domain.calendar.WeatherKind
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.ui.ListWords
import io.github.wkoller.pollenplot.ui.UiFormat
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The words for calendar jobs and the weather, shared by the calendar's screens and the to-do list's notification, so
 * a job reads the same wherever it appears.
 */
object CalendarWords {
    fun line(res: Resources, item: CalendarItem): String = when (item.kind) {
        CalendarKind.WATER -> item.extra?.let { res.getString(R.string.calendar_water_minutes, item.subject, it) } ?: res.getString(R.string.calendar_water, item.subject)
        CalendarKind.HIVE -> res.getString(
            when (CalendarViews.hiveKind(item)) {
                HiveTaskKind.BROOD_CHECK -> R.string.calendar_hive_brood
                HiveTaskKind.MITE_CHECK -> R.string.calendar_hive_mites
                else -> R.string.calendar_hive_inspect
            },
            item.subject,
        )
        CalendarKind.POT -> res.getString(if (CalendarViews.potKind(item) == PotTaskKind.REPOT) R.string.calendar_pot_repot else R.string.calendar_pot_flush, item.subject)
        CalendarKind.FEED -> res.getString(R.string.calendar_feed, item.subject, UiFormat.joinAnd(item.names, words(res)))
        CalendarKind.PROBLEM -> item.extra?.let { res.getString(R.string.calendar_problem, item.subject, it) } ?: res.getString(R.string.calendar_problem_no_plant, item.subject)
        CalendarKind.PRUNE -> res.getString(R.string.calendar_prune, item.subject)
        CalendarKind.HOTEL -> res.getString(R.string.calendar_hotel, item.subject)
        CalendarKind.COMPOST -> res.getString(if (CalendarViews.isTurn(item)) R.string.calendar_compost_turn else R.string.calendar_compost_check, item.subject)
    }

    fun kindLabel(kind: CalendarKind): Int = when (kind) {
        CalendarKind.WATER -> R.string.calendar_kind_water
        CalendarKind.HIVE -> R.string.calendar_kind_hive
        CalendarKind.POT -> R.string.calendar_kind_pot
        CalendarKind.FEED -> R.string.calendar_kind_feed
        CalendarKind.PROBLEM -> R.string.calendar_kind_problem
        CalendarKind.PRUNE -> R.string.calendar_kind_prune
        CalendarKind.HOTEL -> R.string.calendar_kind_hotel
        CalendarKind.COMPOST -> R.string.calendar_kind_compost
    }

    fun kindIcon(kind: CalendarKind): Int = when (kind) {
        CalendarKind.WATER -> R.drawable.ic_water_drop
        CalendarKind.HIVE -> R.drawable.ic_hive
        CalendarKind.POT -> R.drawable.ic_potted_plant
        CalendarKind.FEED -> R.drawable.ic_eco
        CalendarKind.PROBLEM -> R.drawable.ic_bug_report
        CalendarKind.PRUNE -> R.drawable.ic_content_cut
        CalendarKind.HOTEL -> R.drawable.ic_cottage
        CalendarKind.COMPOST -> R.drawable.ic_compost
    }

    fun weatherLabel(kind: WeatherKind?): Int = when (kind) {
        WeatherKind.CLEAR -> R.string.weather_clear
        WeatherKind.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherKind.CLOUDY -> R.string.weather_cloudy
        WeatherKind.FOG -> R.string.weather_fog
        WeatherKind.DRIZZLE -> R.string.weather_drizzle
        WeatherKind.RAIN -> R.string.weather_rain
        WeatherKind.STORM -> R.string.weather_storm
        null -> R.string.weather_unknown
    }

    fun weatherIcon(kind: WeatherKind?): Int = when (kind) {
        WeatherKind.CLEAR -> R.drawable.ic_sunny
        WeatherKind.PARTLY_CLOUDY -> R.drawable.ic_partly_cloudy_day
        WeatherKind.CLOUDY -> R.drawable.ic_cloud
        WeatherKind.FOG -> R.drawable.ic_foggy
        WeatherKind.DRIZZLE, WeatherKind.RAIN -> R.drawable.ic_rainy
        WeatherKind.STORM -> R.drawable.ic_thunderstorm
        null -> R.drawable.ic_cloud
    }

    private fun words(res: Resources) = ListWords(res.getString(R.string.list_and), res.getString(R.string.list_range), res.getString(R.string.list_all_year))

    /** Tenths of a degree as whole degrees, such as 294 as "29". */
    fun degrees(tenths: Int): String = BigDecimal(tenths).movePointLeft(1).setScale(0, RoundingMode.HALF_UP).toPlainString()
}
