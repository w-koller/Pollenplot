package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.HiveView
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.bees.honey.BeekeeperRegistration
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTask
import io.github.wkoller.pollenplot.domain.bees.honey.StateMiteChecks
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.ActionNotice
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.NotificationAccess
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.time.LocalDate

/**
 * The honey bee section of the Bees tab: the checks due across the hives, soonest first, the hives, the guide's pages,
 * the reminders and how often inspections come, whether Varroa is established where the hives are, and the state's
 * registration page.
 */
internal fun LazyListScope.honeySection(
    hives: List<HiveView>,
    today: LocalDate,
    settings: Settings,
    onSettings: ((Settings) -> Settings) -> Unit,
    notifications: NotificationAccess,
    guide: List<Guide>,
    onAddHive: () -> Unit,
    onOpenHive: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val state = settings.gardenState
    if (hives.isEmpty()) {
        item(key = "honey-empty") {
            Column {
                EmptyState(icon = R.drawable.ic_hive, title = stringResource(R.string.bees_honey_empty_title), body = stringResource(R.string.bees_honey_empty_body))
                Button(onClick = onAddHive, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.honey_add_hive)) }
            }
        }
    } else {
        val due = hives.flatMap { view -> view.tasks.map { view to it } }.sortedBy { it.second.due }.take(DUE_SHOWN)
        item(key = "honey-due") { SectionHeading(stringResource(R.string.honey_due)) }
        if (due.isEmpty()) {
            item(key = "honey-due-none") {
                Text(stringResource(R.string.honey_due_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        items(due, key = { (view, task) -> "due-" + view.hive.id + task.kind }) { (view, task) ->
            TaskRow(task, today, prefix = view.hive.name, onClick = { onOpenHive(view.hive.id) })
        }
        item(key = "honey-hives") { SectionHeading(stringResource(R.string.honey_hives)) }
        items(hives, key = { "hive-" + it.hive.id }) { view -> HiveRow(view, onClick = { onOpenHive(view.hive.id) }) }
        item(key = "honey-add") {
            OutlinedButton(onClick = onAddHive, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.honey_add_hive)) }
        }
    }
    if (guide.isNotEmpty()) {
        item(key = "honey-guide") { SectionHeading(stringResource(R.string.guide)) }
        items(guide, key = { "guide-" + it.id }) { page ->
            ListItem(
                onClick = { onOpenGuide(page.id) },
                leadingContent = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                supportingContent = { Text(page.summary) },
            ) { Text(page.title) }
        }
    }
    item(key = "honey-reminders") {
        Column {
            SectionHeading(stringResource(R.string.honey_reminders))
            ListItem(
                checked = settings.hiveReminders,
                onCheckedChange = { on ->
                    onSettings { it.copy(hiveReminders = on) }
                    if (on && !notifications.allowed) notifications.ask()
                },
                supportingContent = { Text(stringResource(R.string.honey_reminders_body)) },
                trailingContent = { Switch(checked = settings.hiveReminders, onCheckedChange = null) },
            ) { Text(stringResource(R.string.honey_reminders_switch)) }
            if (settings.hiveReminders && !notifications.allowed) {
                ActionNotice(stringResource(R.string.honey_reminders_off), stringResource(R.string.scan_notifications_allow), notifications.ask)
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ChipGroup(stringResource(R.string.honey_inspect_every), Settings.INSPECT_EVERY_CHOICES, settings.inspectEveryDays, { stringResource(inspectEveryLabel(it)) }) { days ->
                    onSettings { it.copy(inspectEveryDays = days) }
                }
            }
        }
    }
    item(key = "honey-varroa") {
        val monthly = StateMiteChecks.monthly(settings.monthlyMiteChecks, state)
        val rule = StateMiteChecks.of(state)
        Column {
            SectionHeading(stringResource(R.string.honey_varroa))
            ListItem(
                checked = monthly,
                onCheckedChange = { on -> onSettings { it.copy(monthlyMiteChecks = on) } },
                supportingContent = { Text(stringResource(R.string.honey_varroa_body)) },
                trailingContent = { Switch(checked = monthly, onCheckedChange = null) },
            ) { Text(stringResource(R.string.honey_varroa_switch)) }
            if (rule != null) {
                Text(stringResource(R.string.honey_mites_qld), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                TextButton(onClick = { onOpenLink(rule.page) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(stringResource(R.string.honey_mites_qld_open))
                }
            }
            TextButton(onClick = { onOpenLink(BeekeeperRegistration.CODE_OF_PRACTICE) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                Text(stringResource(R.string.honey_code_open))
            }
        }
    }
    item(key = "honey-registration") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeading(stringResource(R.string.honey_registration))
            if (state == null) {
                Text(stringResource(R.string.honey_registration_no_state), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                Text(stringResource(R.string.honey_registration_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                TextButton(onClick = { onOpenLink(BeekeeperRegistration.page(state)) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(stringResource(R.string.honey_registration_open))
                }
            }
        }
    }
}

/** A check a hive is due for, with the day; one that is overdue says so in words and with an icon. */
@Composable
internal fun TaskRow(task: HiveTask, today: LocalDate, prefix: String?, onClick: (() -> Unit)?) {
    val overdue = task.overdue(today)
    val name = stringResource(task.kind.label)
    val headline = if (prefix != null) stringResource(R.string.honey_task_line, prefix, name) else name
    val date = UiFormat.longDate(task.due)
    val supporting: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (overdue) Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            Text(
                stringResource(if (overdue) R.string.honey_overdue else R.string.honey_due_by, date),
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val leading: @Composable () -> Unit = { Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null) }
    if (onClick != null) {
        ListItem(onClick = onClick, leadingContent = leading, supportingContent = supporting) { Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    } else {
        ListItem(leadingContent = leading, supportingContent = supporting) { Text(headline, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun HiveRow(view: HiveView, onClick: () -> Unit) {
    val hive = view.hive
    val details = listOfNotNull(
        listOfNotNull(stringResource(hive.type.label), view.zoneName).joinToString(" · "),
        hive.queenColour?.let { stringResource(R.string.honey_queen, hive.queenYear!!, stringResource(it.label)) },
        if (hive.status != HiveStatus.ACTIVE) stringResource(hive.status.label) else null,
        view.lastInspection?.let { stringResource(R.string.honey_last_inspected, UiFormat.longDate(it)) } ?: stringResource(R.string.honey_never_inspected),
    )
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(painterResource(R.drawable.ic_hive), contentDescription = null) },
        supportingContent = { Text(details.joinToString("\n"), maxLines = 4) },
    ) { Text(hive.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** The checks due shown in the section; each hive's page shows all of its own. */
private const val DUE_SHOWN = 6

/** How a choice of days between inspections is written. */
private fun inspectEveryLabel(days: Int): Int = when (days) {
    7 -> R.string.honey_every_week
    10 -> R.string.honey_every_10_days
    else -> R.string.honey_every_fortnight
}
