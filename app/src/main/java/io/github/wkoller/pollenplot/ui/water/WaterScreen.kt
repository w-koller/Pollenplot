package io.github.wkoller.pollenplot.ui.water

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.data.WaterViews
import io.github.wkoller.pollenplot.data.Watering
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.irrigation.PlanReason
import io.github.wkoller.pollenplot.domain.irrigation.PlannedWatering
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WaterPlan
import io.github.wkoller.pollenplot.domain.irrigation.WaterPlant
import io.github.wkoller.pollenplot.domain.irrigation.WateringWindow
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.ActionNotice
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.components.rememberNotificationAccess
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Watering: the weather the plans are worked out from, each line's plan with a button to record watering and any
 * plants of very different thirst sharing it, the zones watered by hand with the water for each plant, and how the
 * plan is worked out.
 */
@Composable
fun WaterScreen(
    views: WaterViews,
    water: WaterRepository,
    settings: SettingsStore,
    notifications: Notifications,
    onOpenSettings: () -> Unit,
    onAddLine: () -> Unit,
    onEditLine: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
) {
    val state by views.state.collectAsStateWithLifecycle(initialValue = null)
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val access = rememberNotificationAccess(notifications)
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch { settings.current().gardenPlace?.let { water.refreshWeather(it) } }
    // The weather is brought up to date when the tab opens, unless it was fetched in the last three hours.
    LaunchedEffect(Unit) {
        val fetchedAt = water.weatherFetchedAt.first()
        if (fetchedAt == null || Instant.ofEpochMilli(fetchedAt).isBefore(Instant.now().minus(REFRESH_HOURS, ChronoUnit.HOURS))) refresh()
    }

    TabScaffold(title = stringResource(R.string.water_title), subtitle = stringResource(R.string.water_subtitle), onOpenSettings = onOpenSettings) { padding ->
        val loaded = state ?: return@TabScaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (!loaded.hasPlants) {
                item(key = "empty") {
                    Column {
                        EmptyState(icon = R.drawable.ic_water_drop, title = stringResource(R.string.water_empty_title), body = stringResource(R.string.water_empty_body))
                        if (loaded.lines.isEmpty()) Button(onClick = onAddLine, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.water_add_line)) }
                    }
                }
            }
            item(key = "weather") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        !loaded.hasPlace -> Text(stringResource(R.string.water_no_place), style = MaterialTheme.typography.bodyLarge)
                        loaded.et0TodayUm == null -> Text(stringResource(R.string.water_no_weather), style = MaterialTheme.typography.bodyLarge)
                        else -> {
                            Text(stringResource(R.string.water_et0, millimetres(loaded.et0TodayUm)), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(R.string.water_rain_week, millimetres(loaded.rainLastWeekUm)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    loaded.weatherFetchedAt?.let {
                        Text(stringResource(R.string.water_weather_updated, UiFormat.time(Instant.ofEpochMilli(it))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.water_weather_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (loaded.hasPlace) TextButton(onClick = { refresh() }) { Text(stringResource(R.string.water_update)) }
                }
            }
            if (loaded.lines.isNotEmpty()) {
                item(key = "lines") { SectionHeading(stringResource(R.string.water_lines)) }
                items(loaded.lines, key = { "line-" + it.line.id }) { view ->
                    PlanCard(
                        title = view.line.name,
                        subtitle = listOf(stringResource(view.line.method.label), view.zoneNames.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
                        plan = view.plan,
                        week = view.week,
                        window = loaded.window,
                        plants = view.plants,
                        byHand = view.line.method.byHand,
                        lastWatered = view.lastWatered,
                        today = loaded.today,
                        notes = buildList {
                            for (note in view.pairing) add(pluralStringResource(R.plurals.water_pairing, note.timesNeeded, note.plant.title, note.thirstiest.title, note.timesNeeded))
                        },
                        onEdit = { onEditLine(view.line.id) },
                        onWatered = { scope.launch { water.recordWatering(Watering(water.newId(), view.line.id, null, loaded.today)) } },
                    )
                }
            }
            if (loaded.lines.isNotEmpty() || loaded.hasPlants) {
                item(key = "add") {
                    OutlinedButton(onClick = onAddLine, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.water_add_line)) }
                }
            }
            if (loaded.byHand.isNotEmpty()) {
                item(key = "hand") { SectionHeading(stringResource(R.string.water_by_hand)) }
                items(loaded.byHand, key = { "hand-" + it.zone.id }) { view ->
                    PlanCard(
                        title = view.zone.name,
                        subtitle = null,
                        plan = view.plan,
                        week = view.week,
                        window = loaded.window,
                        plants = view.plants,
                        byHand = true,
                        lastWatered = view.lastWatered,
                        today = loaded.today,
                        notes = emptyList(),
                        onEdit = null,
                        onWatered = { scope.launch { water.recordWatering(Watering(water.newId(), null, view.zone.id, loaded.today)) } },
                    )
                }
            }
            current?.let { chosen ->
                item(key = "reminders") {
                    Column {
                        ListItem(
                            checked = chosen.waterReminders,
                            onCheckedChange = { on ->
                                scope.launch { settings.update { it.copy(waterReminders = on) } }
                                if (on && !access.allowed) access.ask()
                            },
                            supportingContent = { Text(stringResource(R.string.water_reminders_body)) },
                            trailingContent = { Switch(checked = chosen.waterReminders, onCheckedChange = null) },
                        ) { Text(stringResource(R.string.water_reminders)) }
                        if (chosen.waterReminders && !access.allowed) {
                            ActionNotice(stringResource(R.string.water_reminders_off), stringResource(R.string.scan_notifications_allow), access.ask)
                        }
                    }
                }
                item(key = "window") {
                    WindowSetting(chosen.wateringWindow) { window -> scope.launch { settings.update { it.copy(wateringWindow = window) } } }
                }
            }
            item(key = "how") {
                ListItem(
                    onClick = { onOpenGuide(HOW_IT_WORKS) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                ) { Text(stringResource(R.string.water_how)) }
            }
        }
    }
}

/** A line's or a zone's plan, in words, with the water for each plant when it is watered by hand. */
@Composable
private fun PlanCard(
    title: String,
    subtitle: String?,
    plan: WaterPlan,
    week: List<PlannedWatering>,
    window: WateringWindow,
    plants: List<WaterPlant>,
    byHand: Boolean,
    lastWatered: LocalDate?,
    today: LocalDate,
    notes: List<String>,
    onEdit: (() -> Unit)?,
    onWatered: () -> Unit,
) {
    val words = listWords()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                onEdit?.let { IconButton(onClick = it) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.water_edit_line)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val due = plan.reason == PlanReason.DUE
                Icon(painterResource(if (due) R.drawable.ic_water_drop else R.drawable.ic_calendar_month), contentDescription = null, tint = if (due) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(planText(plan, byHand, window), style = MaterialTheme.typography.bodyLarge)
            }
            if (plan.reason == PlanReason.DUE || plan.reason == PlanReason.NOT_YET || plan.reason == PlanReason.WAITING_FOR_RAIN) {
                Text(stringResource(R.string.water_used, plan.usedTodayBp / 100), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (byHand && plan.reason == PlanReason.DUE) {
                for (plant in plants) {
                    val millilitres = plan.perPlantMl[plant.id] ?: continue
                    Text(stringResource(R.string.water_per_plant, plant.title, litres(millilitres)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (plan.uncounted.isNotEmpty()) {
                Text(stringResource(R.string.water_uncounted, UiFormat.joinAnd(plan.uncounted, words)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            for (note in notes) Text(note, style = MaterialTheme.typography.bodyMedium)
            // Today's watering is in the sentence above, so the list starts tomorrow.
            val ahead = week.filter { it.date.isAfter(today) }
            if (ahead.isNotEmpty()) {
                Text(stringResource(R.string.water_coming_up), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                for (watering in ahead) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(watering.icon()), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(aheadText(watering, byHand, window), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                lastWatered?.let { stringResource(R.string.water_last, UiFormat.longDate(it)) } ?: stringResource(R.string.water_never),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (lastWatered != today && plan.reason != PlanReason.NOTHING_TO_COUNT) {
                OutlinedButton(onClick = onWatered) { Text(stringResource(R.string.water_watered)) }
            }
        }
    }
}

@Composable
private fun planText(plan: WaterPlan, byHand: Boolean, window: WateringWindow): String = when (plan.reason) {
    PlanReason.DUE -> {
        val minutes = plan.minutes
        val from = UiFormat.timeOfDay(window.fromMinute)
        val to = UiFormat.timeOfDay(window.toMinute)
        if (!byHand && minutes != null) {
            pluralStringResource(R.plurals.water_due_minutes, minutes, from, to, minutes, litres(plan.applyMl))
        } else {
            stringResource(R.string.water_due, from, to, litres(plan.applyMl))
        }
    }
    PlanReason.WAITING_FOR_RAIN -> stringResource(R.string.water_wait, millimetres(plan.rainTomorrowUm))
    PlanReason.NOT_YET -> plan.dueOn?.let { stringResource(R.string.water_next, UiFormat.longDate(it)) } ?: stringResource(R.string.water_not_this_week)
    PlanReason.NOTHING_TO_COUNT -> stringResource(R.string.water_nothing)
    PlanReason.NO_WEATHER -> stringResource(R.string.water_no_weather_plan)
}

/** A watering in the days ahead, such as "Thursday 29 October at 5:00 am, for 12 minutes". */
@Composable
private fun aheadText(watering: PlannedWatering, byHand: Boolean, window: WateringWindow): String {
    val day = UiFormat.weekdayDate(watering.date)
    val at = UiFormat.timeOfDay(window.fromMinute)
    val minutes = watering.minutes
    return when {
        watering.rainLikely -> stringResource(R.string.water_ahead_rain, day)
        !byHand && minutes != null -> pluralStringResource(R.plurals.water_ahead_minutes, minutes, day, at, minutes)
        else -> stringResource(R.string.water_ahead_litres, day, at, litres(watering.applyMl))
    }
}

/** The hours watering is planned for, each end changed with a clock. */
@Composable
private fun WindowSetting(window: WateringWindow, onChange: (WateringWindow) -> Unit) {
    // Which end is being changed: the start, the end, or neither.
    var picking by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val from = UiFormat.timeOfDay(window.fromMinute)
    val to = UiFormat.timeOfDay(window.toMinute)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.water_window), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.water_window_body, from, to), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.water_window_from, from)) }
            OutlinedButton(onClick = { picking = false }) { Text(stringResource(R.string.water_window_to, to)) }
        }
    }
    picking?.let { start ->
        TimeDialog(
            title = stringResource(if (start) R.string.water_window_pick_from else R.string.water_window_pick_to),
            minute = (if (start) window.fromMinute else window.toMinute) % WateringWindow.MINUTES_IN_DAY,
            onDismiss = { picking = null },
            onPick = { minute ->
                picking = null
                onChange(if (start) window.startingAt(minute) else window.endingAt(minute))
            },
        )
    }
}

/** A clock for choosing a time of day, in the phone's 12 or 24 hour style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, minute: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.water_window_set)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@get:StringRes
internal val WaterMethod.label: Int
    get() = when (this) {
        WaterMethod.DRIP_LINE -> R.string.water_method_drip_line
        WaterMethod.DRIPPERS -> R.string.water_method_drippers
        WaterMethod.MICRO_SPRAYS -> R.string.water_method_sprays
        WaterMethod.SPRINKLER -> R.string.water_method_sprinkler
        WaterMethod.HOSE -> R.string.water_method_hose
        WaterMethod.WATERING_CAN -> R.string.water_method_can
    }

/** µm as mm with one decimal place, such as 5140 as "5.1". */
internal fun millimetres(um: Int): String = BigDecimal(um).movePointLeft(3).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** mL as whole litres, such as 141370 as "141". */
internal fun litres(ml: Long): String = BigDecimal(ml).movePointLeft(3).setScale(0, RoundingMode.HALF_UP).toPlainString()

private const val REFRESH_HOURS = 3L

/** The guide page that explains the plan. */
private const val HOW_IT_WORKS = "water/how-it-works"
