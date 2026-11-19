package io.github.wkoller.pollenplot.ui.settings

import io.github.wkoller.pollenplot.ui.components.SwitchRow
import io.github.wkoller.pollenplot.ui.components.LinkRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import io.github.wkoller.pollenplot.domain.garden.ManyZones
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import io.github.wkoller.pollenplot.ui.components.ActionNotice
import io.github.wkoller.pollenplot.ui.components.Segment
import io.github.wkoller.pollenplot.ui.components.SegmentSurface
import io.github.wkoller.pollenplot.ui.components.SegmentedGroup
import io.github.wkoller.pollenplot.ui.components.listPadding
import io.github.wkoller.pollenplot.ui.components.rememberNotificationAccess
import io.github.wkoller.pollenplot.notify.Notifications
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.plants.label
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.ui.components.ConnectedChoice
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
fun SettingsScreen(
    store: SettingsStore,
    onBack: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenScanning: () -> Unit,
    onOpenModels: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onSetUpAgain: () -> Unit = {},
    onOpenBees: () -> Unit = {},
    onOpenWater: () -> Unit = {},
    notifications: Notifications? = null,
) {
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { change -> scope.launch { store.update(change) } }
    val access = notifications?.let { rememberNotificationAccess(it) }
    // Turning a reminder on asks for notifications, as the Bees and Water tabs do.
    val remind: ((Settings) -> Settings, Boolean) -> Unit = { change, on ->
        update(change)
        if (on && access != null && !access.allowed) access.ask()
    }
    val anyReminder = settings.potReminders || settings.feedReminders || settings.problemReminders ||
        settings.pruneReminders || settings.conditionerBeforeHeat
    DetailScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item { SectionHeading(stringResource(R.string.settings_garden)) }
            item {
                SegmentedGroup {
                    row { segment ->
                        val place = settings.gardenPlace
                        LinkRow(
                            segment,
                            R.drawable.ic_location_on,
                            stringResource(R.string.settings_location),
                            when {
                                place != null -> listOfNotNull(placeTitle(place), settings.gardenState?.let { stringResource(it.label) }).joinToString(" · ")
                                settings.gardenState != null -> stringResource(R.string.settings_location_state_only, stringResource(settings.gardenState.label))
                                else -> stringResource(R.string.settings_location_not_set)
                            },
                            onOpenLocation,
                        )
                    }
                    row { segment ->
                        SwitchRow(segment, settings.petsAtHome, R.drawable.ic_pets, stringResource(R.string.settings_pets), stringResource(R.string.settings_pets_body)) { on ->
                            update { it.copy(petsAtHome = on) }
                        }
                    }
                    row { segment ->
                        SwitchRow(
                            segment,
                            settings.libraryPhotos == true,
                            R.drawable.ic_photo_library,
                            stringResource(R.string.settings_library_photos),
                            stringResource(R.string.settings_library_photos_body),
                        ) { choice -> update { it.copy(libraryPhotos = choice) } }
                    }
                    row { segment ->
                        SegmentSurface(segment) { ManyZonesSetting(settings.manyZones) { choice -> update { it.copy(manyZones = choice) } } }
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.settings_photos)) }
            item {
                SegmentedGroup {
                    row { segment ->
                        LinkRow(
                            segment,
                            R.drawable.ic_photo_library,
                            stringResource(R.string.settings_scanning),
                            stringResource(if (settings.scanLibrary) R.string.settings_scanning_on else R.string.settings_scanning_off),
                            onOpenScanning,
                        )
                    }
                    row { segment ->
                        LinkRow(segment, R.drawable.ic_download, stringResource(R.string.models_title), stringResource(R.string.settings_models_body), onOpenModels)
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.settings_reminders)) }
            item {
                Text(
                    stringResource(R.string.settings_reminders_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
            if (anyReminder && access != null && !access.allowed) {
                item { ActionNotice(stringResource(R.string.settings_reminders_off), stringResource(R.string.scan_notifications_allow), access.ask) }
            }
            item {
                SegmentedGroup {
                    row { segment ->
                        SwitchRow(segment, settings.potReminders, R.drawable.ic_potted_plant, stringResource(R.string.settings_pot_reminders), stringResource(R.string.settings_pot_reminders_body)) { on ->
                            remind({ it.copy(potReminders = on) }, on)
                        }
                    }
                    row { segment ->
                        SwitchRow(segment, settings.feedReminders, R.drawable.ic_compost, stringResource(R.string.settings_feed_reminders), stringResource(R.string.settings_feed_reminders_body)) { on ->
                            remind({ it.copy(feedReminders = on) }, on)
                        }
                    }
                    row { segment ->
                        SwitchRow(segment, settings.problemReminders, R.drawable.ic_bug_report, stringResource(R.string.settings_problem_reminders), stringResource(R.string.settings_problem_reminders_body)) { on ->
                            remind({ it.copy(problemReminders = on) }, on)
                        }
                    }
                    row { segment ->
                        SwitchRow(segment, settings.pruneReminders, R.drawable.ic_content_cut, stringResource(R.string.settings_prune_reminders), stringResource(R.string.settings_prune_reminders_body)) { on ->
                            remind({ it.copy(pruneReminders = on) }, on)
                        }
                    }
                    row { segment ->
                        SwitchRow(segment, settings.conditionerBeforeHeat, R.drawable.ic_sunny, stringResource(R.string.settings_conditioner), stringResource(R.string.settings_conditioner_body)) { on ->
                            remind({ it.copy(conditionerBeforeHeat = on) }, on)
                        }
                    }
                    row { segment ->
                        LinkRow(segment, R.drawable.ic_hive, stringResource(R.string.settings_bee_reminders), stringResource(R.string.settings_bee_reminders_body), onOpenBees)
                    }
                    row { segment ->
                        LinkRow(segment, R.drawable.ic_water_drop, stringResource(R.string.settings_water_reminders), stringResource(R.string.settings_water_reminders_body), onOpenWater)
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.settings_background)) }
            item { SegmentedGroup { row { segment -> BatteryRow(segment) } } }
            item { SectionHeading(stringResource(R.string.settings_records)) }
            item {
                SegmentedGroup {
                    row { segment ->
                        LinkRow(segment, R.drawable.ic_download, stringResource(R.string.export_title), stringResource(R.string.export_body), onOpenExport)
                    }
                    row { segment ->
                        LinkRow(
                            segment,
                            R.drawable.ic_settings_backup_restore,
                            stringResource(R.string.backup_title),
                            settings.lastBackupAt?.let { stringResource(R.string.settings_backup_last, UiFormat.longDate(Instant.ofEpochMilli(it))) }
                                ?: stringResource(R.string.settings_backup_never),
                            onOpenBackup,
                        )
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.settings_appearance)) }
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    ConnectedChoice(
                        options = ThemeChoice.entries,
                        selected = settings.theme,
                        label = { stringResource(it.label) },
                        onChoose = { choice -> update { it.copy(theme = choice) } },
                    )
                }
            }
            item {
                SegmentedGroup {
                    row { segment ->
                        SwitchRow(
                            segment,
                            settings.wallpaperColours,
                            R.drawable.ic_palette,
                            stringResource(R.string.settings_wallpaper_colours),
                            stringResource(R.string.settings_wallpaper_colours_body),
                        ) { on -> update { it.copy(wallpaperColours = on) } }
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.settings_app)) }
            item {
                SegmentedGroup {
                    row { segment ->
                        LinkRow(segment, R.drawable.ic_restart_alt, stringResource(R.string.settings_setup_again), stringResource(R.string.settings_setup_again_body), onSetUpAgain)
                    }
                    row { segment -> LinkRow(segment, R.drawable.ic_info, stringResource(R.string.settings_about), null, onOpenAbout) }
                }
            }
        }
    }
}

/**
 * Whether the phone holds Pollenplot back to save battery, which can delay its morning reminders and scans, and a way
 * to the phone's own battery settings. The app does not ask to be exempted itself.
 */
@Composable
private fun BatteryRow(segment: Segment) {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    fun unrestricted() = power?.isIgnoringBatteryOptimizations(context.packageName) == true
    var unrestricted by remember { mutableStateOf(unrestricted()) }
    // The user may change it in the phone's settings and come back.
    LifecycleResumeEffect(Unit) {
        unrestricted = unrestricted()
        onPauseOrDispose { }
    }
    ListItem(
        onClick = {
            val battery = Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val app = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(battery) }.onFailure { runCatching { context.startActivity(app) } }
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_battery_saver), contentDescription = null) },
        supportingContent = { Text(stringResource(if (unrestricted) R.string.settings_battery_unrestricted else R.string.settings_battery_optimised)) },
        shapes = segment.shapes,
        colors = segment.colors,
    ) { Text(stringResource(R.string.settings_battery)) }
}


private val ThemeChoice.label: Int
    get() = when (this) {
        ThemeChoice.SYSTEM -> R.string.theme_system
        ThemeChoice.LIGHT -> R.string.theme_light
        ThemeChoice.DARK -> R.string.theme_dark
    }

/** Chooses the garden's state or territory, or none. */
@Composable
internal fun StateDialog(current: AuState?, onDismiss: () -> Unit, onChoose: (AuState?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_state)) },
        text = {
            LazyColumn(Modifier.selectableGroup()) {
                items(listOf<AuState?>(null) + AuState.entries) { state ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = state == current, onClick = { onChoose(state) }, role = Role.RadioButton)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state == current, onClick = null)
                        Text(stringResource(state.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** What adding a plant to several zones makes, as a setting: asked in setup and changed in Settings. */
@Composable
fun ManyZonesSetting(choice: ManyZones, modifier: Modifier = Modifier, onChoose: (ManyZones) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroup(
            title = stringResource(R.string.settings_many_zones),
            options = listOf(ManyZones.ASK, ManyZones.ONE_PLANT, ManyZones.PLANT_EACH),
            selected = choice,
            label = {
                stringResource(
                    when (it) {
                        ManyZones.ASK -> R.string.many_zones_ask
                        ManyZones.ONE_PLANT -> R.string.many_zones_one
                        ManyZones.PLANT_EACH -> R.string.many_zones_each
                    },
                )
            },
            onChoose = onChoose,
        )
        Text(
            stringResource(
                when (choice) {
                    ManyZones.ASK -> R.string.many_zones_ask_body
                    ManyZones.ONE_PLANT -> R.string.many_zones_one_body
                    ManyZones.PLANT_EACH -> R.string.many_zones_each_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
