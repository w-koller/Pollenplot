package io.github.wkoller.pollenplot.ui.calendar

import io.github.wkoller.pollenplot.ui.theme.Spacing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import io.github.wkoller.pollenplot.ui.components.ChoiceChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.CalendarDay
import io.github.wkoller.pollenplot.data.CalendarItem
import io.github.wkoller.pollenplot.data.CalendarViews
import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.calendar.ReminderMode
import io.github.wkoller.pollenplot.domain.calendar.WeatherKind
import io.github.wkoller.pollenplot.domain.irrigation.WeatherDay
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.ConnectedChoice
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import io.github.wkoller.pollenplot.ui.components.SegmentedGroup
import io.github.wkoller.pollenplot.ui.components.listPadding
import io.github.wkoller.pollenplot.ui.components.readable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private enum class CalendarView { MONTH, TODO }

/**
 * Every reminder in one place: a month of days, each with its jobs counted and, in the week the forecast covers, its
 * weather; or a to-do list of the week's or the month's jobs. A press on a day opens it.
 */
@Composable
fun CalendarScreen(
    views: CalendarViews,
    water: WaterRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onOpenItem: (CalendarItem) -> Unit,
) {
    val today = views.today()
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    // A to-do list is what someone who chose list reminders is after, so the calendar opens on it for them.
    var view by rememberSaveable { mutableStateOf<CalendarView?>(null) }
    LaunchedEffect(current?.reminderMode) {
        val mode = current?.reminderMode ?: return@LaunchedEffect
        if (view == null) view = if (mode == ReminderMode.WEEKLY_LIST || mode == ReminderMode.MONTHLY_LIST) CalendarView.TODO else CalendarView.MONTH
    }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var todoMonth by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(current?.reminderMode) { if (current?.reminderMode == ReminderMode.MONTHLY_LIST) todoMonth = true }
    val scope = rememberCoroutineScope()
    // The weather is brought up to date when the calendar opens, unless it was fetched in the last three hours.
    LaunchedEffect(Unit) {
        val place = settings.current().gardenPlace ?: return@LaunchedEffect
        val fetchedAt = water.weatherFetchedAt.first()
        if (fetchedAt == null || Instant.ofEpochMilli(fetchedAt).isBefore(Instant.now().minus(3, ChronoUnit.HOURS))) {
            scope.launch { runCatching { water.refreshWeather(place) } }
        }
    }
    DetailScaffold(title = stringResource(R.string.calendar_title), onBack = onBack) { padding ->
        val shown = view ?: return@DetailScaffold
        val shownMonth = YearMonth.parse(month)
        val from = if (shown == CalendarView.MONTH) shownMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else today
        val until = when {
            shown == CalendarView.MONTH -> shownMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            todoMonth -> today.with(TemporalAdjusters.lastDayOfMonth())
            else -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        }
        val daysFlow = remember(from, until) { views.days(from, until) }
        val days by daysFlow.collectAsStateWithLifecycle(initialValue = null)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item {
                ConnectedChoice(
                    options = CalendarView.entries,
                    selected = shown,
                    label = { stringResource(if (it == CalendarView.MONTH) R.string.calendar_view_month else R.string.calendar_view_todo) },
                    onChoose = { view = it },
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
            val loaded = days ?: return@LazyColumn
            if (shown == CalendarView.MONTH) {
                item {
                    MonthHeader(shownMonth, onPrevious = { month = shownMonth.minusMonths(1).toString() }, onNext = { month = shownMonth.plusMonths(1).toString() })
                }
                item { MonthGrid(shownMonth, loaded, today, onOpenDay) }
                // Without a location there is no weather to show, and the month says how to get it.
                if (current?.gardenPlace == null) {
                    item {
                        Text(
                            stringResource(R.string.calendar_no_place),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(Spacing.l),
                        )
                    }
                }
            } else {
                item {
                    Row(Modifier.padding(horizontal = Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ChoiceChip(selected = !todoMonth, onClick = { todoMonth = false }, label = { Text(stringResource(R.string.calendar_todo_week)) })
                        ChoiceChip(selected = todoMonth, onClick = { todoMonth = true }, label = { Text(stringResource(R.string.calendar_todo_month)) })
                    }
                }
                val withJobs = loaded.filter { it.items.isNotEmpty() }
                if (withJobs.isEmpty()) {
                    item { Text(stringResource(R.string.calendar_todo_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.l)) }
                }
                withJobs.forEach { day ->
                    item(key = "day-${day.date}") { SectionHeading(dayTitle(day.date, today)) }
                    item(key = "jobs-${day.date}") { Jobs(day.items, onOpenItem) }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.calendar_previous)) }
        Text(
            month.month.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]) + " " + month.year,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        IconButton(onClick = onNext) { Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = stringResource(R.string.calendar_next)) }
    }
}

/** Seven columns from Monday, each day with its number, its weather when known and how many jobs it has. */
@Composable
private fun MonthGrid(month: YearMonth, days: List<CalendarDay>, today: LocalDate, onOpenDay: (LocalDate) -> Unit) {
    Column(Modifier.readable().padding(horizontal = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.clearAndSetSemantics { }) {
            DayOfWeek.entries.forEach { weekday ->
                Text(
                    weekday.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { day -> DayCell(day, inMonth = YearMonth.from(day.date) == month, today = today, onOpen = { onOpenDay(day.date) }, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, inMonth: Boolean, today: LocalDate, onOpen: () -> Unit, modifier: Modifier) {
    val isToday = day.date == today
    val weather = day.weather
    val kind = WeatherKind.of(weather?.code)
    val resources = LocalResources.current
    val jobs = day.items.size
    val title = dayTitle(day.date, today)
    val spoken = buildList {
        add(if (jobs > 0) resources.getString(R.string.calendar_day_jobs, title, resources.getQuantityString(R.plurals.calendar_jobs, jobs, jobs)) else resources.getString(R.string.calendar_day_none, title))
        weatherWords(weather)?.let { add(it) }
    }.joinToString(". ")
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (jobs > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
            },
    ) {
        Column(Modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                day.date.dayOfMonth.toString(),
                style = if (isToday) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
                color = if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            if (weather != null && (kind != null || weather.maxTenthsC != null)) {
                // Without a code the day's high still shows, with no picture standing in for weather not known.
                if (kind != null) Icon(painterResource(CalendarWords.weatherIcon(kind)), contentDescription = null, modifier = Modifier.size(16.dp))
                weather.maxTenthsC?.let { Text(stringResource(R.string.calendar_temp_short, CalendarWords.degrees(it)), style = MaterialTheme.typography.labelSmall) }
            }
            if (jobs > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Icon(painterResource(R.drawable.ic_notifications), contentDescription = null, modifier = Modifier.size(12.dp))
                    Text(jobs.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** A day's jobs, grouped by kind on the same tiles as the rest of the app, each opening what it is for. */
@Composable
private fun Jobs(items: List<CalendarItem>, onOpenItem: (CalendarItem) -> Unit) {
    val resources = LocalResources.current
    SegmentedGroup {
        items.forEach { item ->
            row { segment ->
                ListItem(
                    onClick = { onOpenItem(item) },
                    leadingContent = { Icon(painterResource(CalendarWords.kindIcon(item.kind)), contentDescription = null) },
                    supportingContent = { Text(stringResource(CalendarWords.kindLabel(item.kind))) },
                    shapes = segment.shapes,
                    colors = segment.colors,
                ) { Text(CalendarWords.line(resources, item)) }
            }
        }
    }
}

/** One day: its weather, then its jobs by kind. */
@Composable
fun CalendarDayScreen(views: CalendarViews, date: LocalDate, onBack: () -> Unit, onOpenItem: (CalendarItem) -> Unit) {
    val today = views.today()
    val flow = remember(date) { views.days(date, date) }
    val days by flow.collectAsStateWithLifecycle(initialValue = null)
    DetailScaffold(title = dayTitle(date, today), onBack = onBack) { padding ->
        val day = days?.firstOrNull() ?: return@DetailScaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            day.weather?.let { weather ->
                item { SectionHeading(stringResource(R.string.calendar_day_weather_heading)) }
                item {
                    Row(Modifier.padding(horizontal = Spacing.l), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        WeatherKind.of(weather.code)?.let { Icon(painterResource(CalendarWords.weatherIcon(it)), contentDescription = null) }
                        Column {
                            weatherWords(weather)?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                            weather.rainChanceBp?.let { Text(stringResource(R.string.calendar_day_rain, it / 100), style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
            if (day.items.isEmpty()) {
                item { Text(stringResource(R.string.calendar_day_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(Spacing.l)) }
            }
            day.items.groupBy { it.kind }.forEach { (kind, items) ->
                item(key = kind.name) { SectionHeading(stringResource(CalendarWords.kindLabel(kind))) }
                item(key = kind.name + "-jobs") { Jobs(items, onOpenItem) }
            }
        }
    }
}

/** "Today" or the day's full date, such as "Saturday 31 October 2026". */
@Composable
private fun dayTitle(date: LocalDate, today: LocalDate): String =
    if (date == today) stringResource(R.string.calendar_today) else date.dayOfWeek.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0]) + " " + UiFormat.longDate(date)

/** The weather in words, such as "Partly cloudy, 17 to 29 °C", or null when nothing is known. */
@Composable
private fun weatherWords(weather: WeatherDay?): String? {
    if (weather == null) return null
    val kind = stringResource(CalendarWords.weatherLabel(WeatherKind.of(weather.code)))
    val high = weather.maxTenthsC ?: return kind.takeIf { weather.code != null }
    val low = weather.minTenthsC ?: return "$kind, ${CalendarWords.degrees(high)} °C"
    return stringResource(R.string.calendar_day_weather, kind, CalendarWords.degrees(low), CalendarWords.degrees(high))
}
