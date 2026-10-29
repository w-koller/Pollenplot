package io.github.wkoller.pollenplot.ui.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.ZoneWatering
import io.github.wkoller.pollenplot.domain.irrigation.PlannedWatering
import io.github.wkoller.pollenplot.domain.irrigation.WateringWindow
import io.github.wkoller.pollenplot.ui.UiFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The day a watering falls on, as part of a sentence: "today", "tomorrow", "on Thursday" or, further off, the date. */
@Composable
internal fun dayPhrase(date: LocalDate, today: LocalDate): String {
    val locale = locale()
    return when (date) {
        today -> stringResource(R.string.water_day_today)
        today.plusDays(1) -> stringResource(R.string.water_day_tomorrow)
        else -> stringResource(
            R.string.water_day_on,
            if (date.isBefore(today.plusDays(WEEK))) date.dayOfWeek.getDisplayName(TextStyle.FULL, locale) else UiFormat.longDate(date, locale),
        )
    }
}

/** A planned watering as a sentence, such as "Water tomorrow at 5:00 am, for 12 minutes." */
@Composable
fun nextWateringSentence(next: PlannedWatering, today: LocalDate, window: WateringWindow, byHand: Boolean): String {
    val day = dayPhrase(next.date, today)
    val at = UiFormat.timeOfDay(window.fromMinute, locale())
    val minutes = next.minutes
    return when {
        next.rainLikely -> stringResource(R.string.water_next_rain, day)
        !byHand && minutes != null -> pluralStringResource(R.plurals.water_next_minutes, minutes, day, at, minutes)
        else -> stringResource(R.string.water_next_litres, day, at, litres(next.applyMl))
    }
}

/** A planned watering in a few words for a tile on the Plot, such as "Tomorrow, 5:00 am". */
@Composable
fun nextWateringShort(next: PlannedWatering, today: LocalDate, window: WateringWindow): String {
    val locale = locale()
    val at = UiFormat.timeOfDay(window.fromMinute, locale)
    return when (next.date) {
        today -> stringResource(R.string.water_tile_today, at)
        today.plusDays(1) -> stringResource(R.string.water_tile_tomorrow, at)
        else -> stringResource(R.string.water_tile_day, next.date.dayOfWeek.getDisplayName(TextStyle.FULL, locale), at)
    }
}

/** Whether a zone's water comes by hand: it has no line, or its line is a hose or a can. */
val ZoneWatering.byHand: Boolean get() = line?.method?.byHand ?: true

/** The icon for a planned watering: a drop, or a cloud when good rain may do it instead. */
fun PlannedWatering.icon(): Int = if (rainLikely) R.drawable.ic_cloud else R.drawable.ic_water_drop

/** A zone's next watering as a sentence beside its icon. */
@Composable
fun NextWateringLine(next: PlannedWatering, today: LocalDate, window: WateringWindow, byHand: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(next.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(nextWateringSentence(next, today, window, byHand), style = MaterialTheme.typography.bodyMedium)
    }
}

/** The language the app is shown in, read so that a change of language redraws the words. */
@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

private const val WEEK = 7L
