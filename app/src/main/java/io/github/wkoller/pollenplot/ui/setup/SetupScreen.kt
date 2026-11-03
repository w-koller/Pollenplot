package io.github.wkoller.pollenplot.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ai.GemmaModel
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.GalleryAccess
import io.github.wkoller.pollenplot.scan.ScanScheduler
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.ui.components.PollenProgressBar
import io.github.wkoller.pollenplot.ui.components.PollenShapes
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.settings.GardenLocationContent
import io.github.wkoller.pollenplot.ui.settings.GardenLocationModel
import io.github.wkoller.pollenplot.ui.settings.ModelsContent
import io.github.wkoller.pollenplot.ui.settings.galleryPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

/** The steps of the setup, in order. */
private enum class SetupStep { WELCOME, LOCATION, MODELS, ZONES, PERMISSIONS }

/** The zones that can be ticked: every kind but "other", which has no name to start from. */
private val SETUP_KINDS = ZoneKind.entries.filter { it != ZoneKind.OTHER }

/** Keeps the ticked zones and their names, as kind and name in turn, so they survive the phone being turned. */
private val TickedSaver = listSaver<Map<ZoneKind, String>, String>(
    save = { map -> map.flatMap { (kind, name) -> listOf(kind.name, name) } },
    restore = { list -> list.chunked(2).associate { (kind, name) -> ZoneKind.valueOf(kind) to name } },
)

/**
 * The setup shown when Pollenplot is first opened, and from Settings › Set up again: a welcome, the garden's location,
 * the models to download, the first zones, then notifications and photo scanning. Every step can be skipped, and
 * finishing or skipping the setup marks it done.
 */
@Composable
fun SetupScreen(
    location: GardenLocationModel,
    settings: SettingsStore,
    meaning: MeaningSearch,
    accurate: AccuratePack,
    gemma: GemmaModel,
    garden: GardenRepository,
    gallery: Gallery,
    notifications: Notifications,
    clock: Clock,
    onDone: () -> Unit,
) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = SetupStep.entries[index]
    val scope = rememberCoroutineScope()
    val finish: () -> Unit = {
        scope.launch {
            settings.update { it.copy(setupDone = true) }
            // DataStore may finish on its own thread, and navigation must happen on the main one.
            withContext(Dispatchers.Main) { onDone() }
        }
    }
    val next: () -> Unit = { if (index == SetupStep.entries.lastIndex) finish() else index += 1 }
    val previous: () -> Unit = { index -= 1 }
    BackHandler(index > 0) { previous() }

    when (step) {
        SetupStep.WELCOME -> SetupScaffold(
            index = index,
            title = stringResource(R.string.setup_welcome_title),
            onPrevious = null,
            skipLabel = stringResource(R.string.setup_welcome_skip),
            onSkip = finish,
            nextLabel = stringResource(R.string.setup_welcome_start),
            onNext = next,
        ) { padding -> Welcome(padding) }
        SetupStep.LOCATION -> SetupScaffold(index, stringResource(R.string.setup_location_title), previous, onSkip = next, onNext = next) { padding ->
            GardenLocationContent(location, settings, padding)
        }
        SetupStep.MODELS -> SetupScaffold(index, stringResource(R.string.setup_models_title), previous, onSkip = next, onNext = next) { padding ->
            ModelsContent(meaning, accurate, gemma, padding, setup = true)
        }
        SetupStep.ZONES -> ZonesStep(index, garden, previous, next)
        SetupStep.PERMISSIONS -> SetupScaffold(
            index,
            stringResource(R.string.setup_permissions_title),
            previous,
            onSkip = finish,
            nextLabel = stringResource(R.string.setup_finish),
            onNext = finish,
        ) { padding -> Permissions(padding, settings, gallery, notifications, clock) }
    }
}

/**
 * One step: its title, how far through the setup it is, as words and a bar, and buttons to skip it or go on. The
 * previous step is a button in the top bar and the back gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScaffold(
    index: Int,
    title: String,
    onPrevious: (() -> Unit)?,
    skipLabel: String = stringResource(R.string.setup_skip),
    onSkip: () -> Unit,
    nextLabel: String = stringResource(R.string.setup_next),
    onNext: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val total = SetupStep.entries.size
    Scaffold(
        topBar = {
            // The step and its bar sit on the bar's colour, so the list does not show through them as it scrolls.
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(bottom = 12.dp)) {
                    TopAppBar(
                        title = { Text(title, modifier = Modifier.semantics { heading() }) },
                        navigationIcon = {
                            if (onPrevious != null) {
                                IconButton(onClick = onPrevious) {
                                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.setup_back))
                                }
                            }
                        },
                    )
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.setup_step, index + 1, total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        PollenProgressBar((index + 1).toFloat() / total, Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                // With large text the buttons wrap onto two lines rather than squeezing their words.
                FlowRow(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onSkip) { Text(skipLabel) }
                    Button(onClick = onNext) { Text(nextLabel) }
                }
            }
        },
        content = content,
    )
}

@Composable
private fun Welcome(padding: PaddingValues) {
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(96.dp).clip(PollenShapes.flower()).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_local_florist_filled), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp))
        }
        Text(stringResource(R.string.setup_welcome_body), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.setup_welcome_steps), style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The kinds of zone to tick, each with a name that starts as the kind's. Going on adds the ticked zones, which
 * Pollenplot sets out on the Plot's grid.
 */
@Composable
private fun ZonesStep(index: Int, garden: GardenRepository, onPrevious: () -> Unit, onNext: () -> Unit) {
    val existing by garden.zoneList.collectAsStateWithLifecycle(initialValue = emptyList())
    var ticked by rememberSaveable(stateSaver = TickedSaver) { mutableStateOf(emptyMap<ZoneKind, String>()) }
    val scope = rememberCoroutineScope()
    val labels = SETUP_KINDS.associateWith { stringResource(it.label) }
    val add = {
        scope.launch {
            SETUP_KINDS.filter { it in ticked }.forEach { kind ->
                garden.addZone(ticked.getValue(kind).trim().ifBlank { labels.getValue(kind) }, kind)
            }
            withContext(Dispatchers.Main) {
                ticked = emptyMap()
                onNext()
            }
        }
        Unit
    }
    SetupScaffold(
        index,
        stringResource(R.string.setup_zones_title),
        onPrevious,
        onSkip = onNext,
        nextLabel = if (ticked.isEmpty()) stringResource(R.string.setup_next) else pluralStringResource(R.plurals.setup_zones_add, ticked.size, ticked.size),
        onNext = add,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(stringResource(R.string.setup_zones_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
            if (existing.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.setup_zones_have, existing.joinToString(", ") { it.name }),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    )
                }
            }
            items(SETUP_KINDS, key = { it.name }) { kind ->
                val name = ticked[kind]
                Column {
                    ListItem(
                        checked = name != null,
                        onCheckedChange = { on -> ticked = if (on) ticked + (kind to labels.getValue(kind)) else ticked - kind },
                        leadingContent = { Icon(painterResource(kind.icon), contentDescription = null) },
                        trailingContent = { Checkbox(checked = name != null, onCheckedChange = null) },
                    ) { Text(labels.getValue(kind)) }
                    if (name != null) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { ticked = ticked + (kind to it) },
                            label = { Text(stringResource(R.string.setup_zones_name, labels.getValue(kind).lowercase())) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Notifications and photo scanning, each explained before the phone asks, and each shown with its state in words. */
@Composable
private fun Permissions(padding: PaddingValues, settings: SettingsStore, gallery: Gallery, notifications: Notifications, clock: Clock) {
    val context = LocalContext.current
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var notificationsAllowed by remember { mutableStateOf(notifications.allowed) }
    var access by remember { mutableStateOf(gallery.access()) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The user may change permissions in the phone's settings and come back.
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notifications.allowed
        access = gallery.access()
        onPauseOrDispose { }
    }
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = notifications.allowed
    }
    val askForPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = gallery.access()
        refused = access == GalleryAccess.NONE
        if (access != GalleryAccess.NONE) {
            scope.launch {
                settings.update { it.copy(scanLibrary = true, scanSince = it.scanSince ?: clock.millis()) }
                ScanScheduler.update(context, true)
            }
        }
    }
    val scanning = current?.scanLibrary == true && access != GalleryAccess.NONE

    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            Text(stringResource(R.string.setup_permissions_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
        }
        permission(
            icon = R.drawable.ic_notifications,
            title = R.string.setup_notifications,
            body = R.string.setup_notifications_body,
            on = notificationsAllowed,
            onText = R.string.setup_notifications_on,
            action = R.string.setup_notifications_allow,
            note = null,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        permission(
            icon = R.drawable.ic_photo_library,
            title = R.string.setup_scanning,
            body = R.string.setup_scanning_body,
            on = scanning,
            onText = R.string.setup_scanning_on,
            action = R.string.setup_scanning_allow,
            note = R.string.setup_scanning_denied.takeIf { refused },
        ) {
            if (access == GalleryAccess.NONE) {
                askForPhotos.launch(galleryPermissions())
            } else {
                scope.launch {
                    settings.update { it.copy(scanLibrary = true, scanSince = it.scanSince ?: clock.millis()) }
                    ScanScheduler.update(context, true)
                }
            }
        }
    }
}

private fun LazyListScope.permission(icon: Int, title: Int, body: Int, on: Boolean, onText: Int, action: Int, note: Int?, onAllow: () -> Unit) {
    item {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (on) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(onText), style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                OutlinedButton(onClick = onAllow) { Text(stringResource(action)) }
            }
            note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
}
