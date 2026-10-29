package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.NativeHiveView
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.bees.stingless.HeatAlerts
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.domain.wildlife.SeenBee
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.ActionNotice
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.NotificationAccess
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.math.BigDecimal

/**
 * The native bee section of the Bees tab: the stingless bee hives, the native bees seen in the yard this year, the
 * guide's pages, and the warnings before hot days.
 */
internal fun LazyListScope.nativeSection(
    hives: List<NativeHiveView>,
    seen: List<SeenBee>,
    settings: Settings,
    onSettings: ((Settings) -> Settings) -> Unit,
    notifications: NotificationAccess,
    guide: List<Guide>,
    onAddHive: () -> Unit,
    onOpenHive: (String) -> Unit,
    onOpenAnimal: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
) {
    if (hives.isEmpty()) {
        item(key = "native-empty") {
            Column {
                EmptyState(icon = R.drawable.ic_emoji_nature, title = stringResource(R.string.bees_native_empty_title), body = stringResource(R.string.bees_native_empty_body))
                Button(onClick = onAddHive, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.honey_add_hive)) }
            }
        }
    } else {
        item(key = "native-hives") { SectionHeading(stringResource(R.string.native_hives)) }
        items(hives, key = { "native-" + it.hive.id }) { view -> NativeHiveRow(view, onClick = { onOpenHive(view.hive.id) }) }
        item(key = "native-add") {
            OutlinedButton(onClick = onAddHive, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Text(stringResource(R.string.honey_add_hive)) }
        }
    }

    item(key = "native-seen") { SectionHeading(stringResource(R.string.native_seen)) }
    if (seen.isEmpty()) {
        item(key = "native-seen-none") {
            Text(stringResource(R.string.native_seen_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    items(seen, key = { "seen-" + it.key }) { bee ->
        ListItem(
            onClick = { onOpenAnimal(bee.key) },
            leadingContent = { Icon(painterResource(R.drawable.ic_emoji_nature), contentDescription = null) },
            supportingContent = {
                Text(pluralStringResource(R.plurals.native_seen_count, bee.sightings, bee.sightings) + "\n" + stringResource(R.string.native_seen_last, UiFormat.longDate(bee.lastSeen)))
            },
        ) { Text(bee.animal.displayName) }
    }

    val pages = guide.filter { it.appliesTo(settings.gardenState) }
    if (pages.isNotEmpty()) {
        item(key = "native-guide") { SectionHeading(stringResource(R.string.guide)) }
        items(pages, key = { "guide-" + it.id }) { page ->
            ListItem(
                onClick = { onOpenGuide(page.id) },
                leadingContent = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                supportingContent = { Text(page.summary) },
            ) { Text(page.title) }
        }
    }

    item(key = "native-heat") {
        Column {
            SectionHeading(stringResource(R.string.native_heat))
            ListItem(
                checked = settings.heatAlerts,
                onCheckedChange = { on ->
                    onSettings { it.copy(heatAlerts = on) }
                    if (on && !notifications.allowed) notifications.ask()
                },
                supportingContent = { Text(stringResource(R.string.native_heat_body)) },
                trailingContent = { Switch(checked = settings.heatAlerts, onCheckedChange = null) },
            ) { Text(stringResource(R.string.native_heat_switch)) }
            if (settings.heatAlerts && settings.gardenPlace == null) {
                Text(stringResource(R.string.native_heat_no_place), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            if (settings.heatAlerts && !notifications.allowed) {
                ActionNotice(stringResource(R.string.native_heat_off), stringResource(R.string.scan_notifications_allow), notifications.ask)
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ChipGroup(stringResource(R.string.native_heat_at), HeatAlerts.CHOICES, settings.heatAlertTenthsC, { stringResource(R.string.native_heat_choice, degrees(it)) }) { tenths ->
                    onSettings { it.copy(heatAlertTenthsC = tenths) }
                }
            }
        }
    }
}

@Composable
private fun NativeHiveRow(view: NativeHiveView, onClick: () -> Unit) {
    val hive = view.hive
    val strength = view.strength
    val lines = listOfNotNull(
        listOfNotNull(stringResource(hive.species.label), stringResource(hive.box.label), view.zoneName).joinToString(" · "),
        if (hive.status != NativeStatus.ACTIVE) stringResource(hive.status.label) else null,
        strength.weightGrams?.let { stringResource(R.string.native_weighed, kilograms(it), UiFormat.longDate(strength.weighedOn!!)) },
        strength.beesPerMinute?.let { pluralStringResource(R.plurals.native_counted, it, it, UiFormat.longDate(strength.countedOn!!)) },
    )
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(painterResource(R.drawable.ic_emoji_nature), contentDescription = null) },
        supportingContent = { Text(lines.joinToString("\n"), maxLines = 4) },
    ) { Text(hive.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Grams as kilograms, such as 7100 as "7.1". */
internal fun kilograms(grams: Int): String = BigDecimal(grams).movePointLeft(3).stripTrailingZeros().toPlainString()

/** Tenths of a degree as degrees, such as 380 as "38". */
internal fun degrees(tenths: Int): String = BigDecimal(tenths).movePointLeft(1).stripTrailingZeros().toPlainString()
