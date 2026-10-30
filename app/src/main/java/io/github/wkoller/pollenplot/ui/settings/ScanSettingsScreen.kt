package io.github.wkoller.pollenplot.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.scan.Album
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.GalleryAccess
import io.github.wkoller.pollenplot.scan.ScanScheduler
import io.github.wkoller.pollenplot.ui.components.ActionNotice
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock

/** How far back the first scan may look, in days. */
private val BACK_DAYS = listOf(0, 7, 30, 90, 365)

/** The permissions that let the app read the gallery on this version of Android. */
internal fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Settings › Photo scanning: whether to look for plants in new photos, which albums, and how far back. */
@Composable
fun ScanSettingsScreen(store: SettingsStore, gallery: Gallery, notifications: Notifications, clock: Clock, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = null)
    val scanning by remember { ScanScheduler.scanning(context) }.collectAsStateWithLifecycle(initialValue = false)
    var access by remember { mutableStateOf(gallery.access()) }
    var notificationsAllowed by remember { mutableStateOf(notifications.allowed) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // The user may change permissions in the phone's settings and come back.
    LifecycleResumeEffect(Unit) {
        access = gallery.access()
        notificationsAllowed = notifications.allowed
        onPauseOrDispose { }
    }
    val albums by produceState(emptyList<Album>(), access) {
        value = if (access == GalleryAccess.NONE) emptyList() else withContext(Dispatchers.IO) { gallery.albums() }
    }

    fun setScanning(on: Boolean) = scope.launch {
        store.update { it.copy(scanLibrary = on, scanSince = it.scanSince ?: clock.millis()) }
        ScanScheduler.update(context, on)
    }

    val askForPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = gallery.access()
        notificationsAllowed = notifications.allowed
        // After two refusals Android stops asking, so the app offers its settings page instead.
        refused = access == GalleryAccess.NONE && activity?.shouldShowRequestPermissionRationale(galleryPermissions().first()) != true
        if (access != GalleryAccess.NONE) setScanning(true)
    }
    val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = notifications.allowed
    }
    fun requestPhotos() {
        val notify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        askForPhotos.launch(galleryPermissions() + notify)
    }

    DetailScaffold(title = stringResource(R.string.scan_title), onBack = onBack) { padding ->
        val current = settings ?: return@DetailScaffold
        val on = current.scanLibrary && access != GalleryAccess.NONE
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    stringResource(R.string.scan_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                ListItem(
                    checked = on,
                    onCheckedChange = { wanted ->
                        when {
                            !wanted -> setScanning(false)
                            access == GalleryAccess.NONE -> requestPhotos()
                            else -> setScanning(true)
                        }
                    },
                    supportingContent = { Text(stringResource(R.string.scan_switch_body)) },
                    trailingContent = { Switch(checked = on, onCheckedChange = null) },
                ) { Text(stringResource(R.string.scan_switch)) }
            }
            if (refused && access == GalleryAccess.NONE) {
                item {
                    ActionNotice(stringResource(R.string.scan_denied), stringResource(R.string.identify_permission_settings)) {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
            }
            if (on && access == GalleryAccess.PARTIAL) {
                item { ActionNotice(stringResource(R.string.scan_partial), stringResource(R.string.scan_partial_allow), onAction = ::requestPhotos) }
            }
            if (on && !notificationsAllowed) {
                item {
                    ActionNotice(stringResource(R.string.scan_notifications_off), stringResource(R.string.scan_notifications_allow)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) != false) {
                            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
            if (on) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(if (scanning) R.string.scan_running else R.string.scan_idle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { ScanScheduler.scanNow(context) }, enabled = !scanning) { Text(stringResource(R.string.scan_now)) }
                    }
                }
                item { SectionHeading(stringResource(R.string.scan_albums)) }
                item {
                    Text(
                        stringResource(R.string.scan_albums_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(albums, key = { it.id }) { album ->
                    val chosen = current.chosenAlbums(albums)
                    val checked = album.id in chosen
                    ListItem(
                        checked = checked,
                        onCheckedChange = { add ->
                            val next = if (add) chosen + album.id else chosen - album.id
                            scope.launch { store.update { it.copy(scanAlbums = next) } }
                        },
                        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        supportingContent = { Text(pluralStringResource(R.plurals.scan_album_count, album.count, album.count)) },
                    ) { Text(album.name) }
                }
                item { SectionHeading(stringResource(R.string.scan_back)) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.scan_back_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ChipGroup(stringResource(R.string.scan_back_choice), BACK_DAYS, current.scanBackDays, { stringResource(backLabel(it)) }) { days ->
                            scope.launch {
                                store.update { it.copy(scanBackDays = days) }
                                ScanScheduler.scanNow(context)
                            }
                        }
                    }
                }
            }
            item { SectionHeading(stringResource(R.string.scan_second_opinion_heading)) }
            item {
                ListItem(
                    checked = current.secondOpinions,
                    onCheckedChange = { wanted -> scope.launch { store.update { it.copy(secondOpinions = wanted) } } },
                    supportingContent = { Text(stringResource(R.string.scan_second_opinion_body)) },
                    trailingContent = { Switch(checked = current.secondOpinions, onCheckedChange = null) },
                ) { Text(stringResource(R.string.scan_second_opinion)) }
            }
        }
    }
}

/** The albums being scanned: the user's choice, or else the camera's own album. */
private fun Settings.chosenAlbums(albums: List<Album>): Set<String> = scanAlbums ?: albums.filter { it.camera }.map { it.id }.toSet()

private fun backLabel(days: Int): Int = when (days) {
    0 -> R.string.scan_back_none
    7 -> R.string.scan_back_week
    30 -> R.string.scan_back_month
    90 -> R.string.scan_back_three_months
    else -> R.string.scan_back_year
}

