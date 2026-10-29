package io.github.wkoller.pollenplot.ui.settings

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.backup.BackupContents
import io.github.wkoller.pollenplot.backup.BackupRead
import io.github.wkoller.pollenplot.backup.BackupService
import io.github.wkoller.pollenplot.backup.RestoreException
import io.github.wkoller.pollenplot.backup.StagedBackup
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenProgressBar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * Saves backups and restores them. The passwords are kept here rather than in saved state, so they are never written
 * to the phone's storage.
 */
class BackupModel(private val service: BackupService, private val resolver: ContentResolver) : ViewModel() {
    sealed interface Save {
        data object Idle : Save

        data class Saving(val progress: Float) : Save

        data object Saved : Save

        data object Failed : Save
    }

    sealed interface Restore {
        data object Idle : Restore

        data object Reading : Restore

        /** The backup is encrypted, and needs its password, or another try at it. */
        data class Locked(val uri: Uri, val wrongPassword: Boolean) : Restore

        data class Found(val contents: BackupContents) : Restore

        data object Restoring : Restore

        data object Restored : Restore

        /** The file cannot be restored, for the reason given; nothing on the phone has changed. */
        data class Refused(val reason: BackupRead) : Restore

        data class Failed(val recordsReplaced: Boolean) : Restore
    }

    var password by mutableStateOf("")
    var repeat by mutableStateOf("")
    var unlockPassword by mutableStateOf("")

    private val _save = MutableStateFlow<Save>(Save.Idle)
    val save: StateFlow<Save> = _save.asStateFlow()
    private val _restore = MutableStateFlow<Restore>(Restore.Idle)
    val restore: StateFlow<Restore> = _restore.asStateFlow()
    private var staged: StagedBackup? = null

    val sealed: Boolean get() = password.isNotEmpty()
    val passwordsMatch: Boolean get() = password == repeat

    fun fileName(): String = service.fileName(sealed)

    fun saveTo(uri: Uri) {
        val secret = password.ifEmpty { null }
        viewModelScope.launch {
            _save.value = Save.Saving(0f)
            _save.value = try {
                withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("The file could not be opened")
                    out.use { service.write(it, secret) { done -> _save.value = Save.Saving(done) } }
                }
                password = ""
                repeat = ""
                Save.Saved
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A file cut short would look like a backup, so it is removed.
                runCatching { DocumentsContract.deleteDocument(resolver, uri) }
                Save.Failed
            }
        }
    }

    fun choose(uri: Uri) {
        unlockPassword = ""
        read(uri, null)
    }

    fun unlock() {
        val locked = _restore.value as? Restore.Locked ?: return
        read(locked.uri, unlockPassword)
    }

    private fun read(uri: Uri, secret: String?) {
        putAway()
        viewModelScope.launch {
            _restore.value = Restore.Reading
            val result = try {
                withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { service.read(it, secret) } } ?: BackupRead.Unreadable
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BackupRead.Unreadable
            }
            _restore.value = when (result) {
                is BackupRead.Ready -> {
                    staged = result.backup
                    Restore.Found(result.backup.contents)
                }
                BackupRead.NeedsPassword -> Restore.Locked(uri, wrongPassword = false)
                BackupRead.WrongPassword -> Restore.Locked(uri, wrongPassword = true)
                else -> Restore.Refused(result)
            }
        }
    }

    fun restore() {
        val backup = staged ?: return
        viewModelScope.launch {
            _restore.value = Restore.Restoring
            _restore.value = try {
                service.restore(backup)
                staged = null
                unlockPassword = ""
                Restore.Restored
            } catch (e: CancellationException) {
                throw e
            } catch (e: RestoreException) {
                Restore.Failed(e.recordsReplaced)
            } catch (_: Exception) {
                Restore.Failed(recordsReplaced = false)
            }
        }
    }

    fun cancel() {
        putAway()
        unlockPassword = ""
        _restore.value = Restore.Idle
    }

    private fun putAway() {
        staged?.let(service::discard)
        staged = null
    }

    override fun onCleared() = putAway()
}

/**
 * Backup and restore. A backup is saved through the system's file picker, with a password if one is typed. Restoring
 * reads and checks the chosen file first, shows what it holds, and replaces the phone's records only once confirmed.
 */
@Composable
fun BackupScreen(model: BackupModel, lastBackupAt: Long?, zone: ZoneId, onBack: () -> Unit) {
    val save by model.save.collectAsStateWithLifecycle()
    val restore by model.restore.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(model::saveTo) }
    val saveSealed = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(model::saveTo) }
    // A sealed backup is not a zip, and file managers do not agree on what to call it, so every file can be chosen.
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::choose) }

    DetailScaffold(title = stringResource(R.string.backup_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(stringResource(R.string.backup_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            item { SectionHeading(stringResource(R.string.backup_save_heading)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val saving = save is BackupModel.Save.Saving
                    PasswordField(
                        value = model.password,
                        onValueChange = { model.password = it },
                        label = stringResource(R.string.backup_password),
                        enabled = !saving,
                        supporting = stringResource(R.string.backup_password_body),
                    )
                    if (model.sealed) {
                        PasswordField(
                            value = model.repeat,
                            onValueChange = { model.repeat = it },
                            label = stringResource(R.string.backup_password_repeat),
                            enabled = !saving,
                            error = stringResource(R.string.backup_password_mismatch).takeIf { model.repeat.isNotEmpty() && !model.passwordsMatch },
                        )
                    }
                    Button(
                        enabled = !saving && model.passwordsMatch,
                        onClick = { (if (model.sealed) saveSealed else saveZip).launch(model.fileName()) },
                    ) { Text(stringResource(R.string.backup_save)) }
                    when (val state = save) {
                        is BackupModel.Save.Saving -> Working(stringResource(R.string.backup_saving), state.progress)
                        BackupModel.Save.Saved -> StatusLine(ok = true, stringResource(R.string.backup_saved))
                        BackupModel.Save.Failed -> StatusLine(ok = false, stringResource(R.string.backup_save_failed))
                        BackupModel.Save.Idle -> Unit
                    }
                    Text(
                        lastBackupAt?.let { stringResource(R.string.settings_backup_last, UiFormat.longDate(Instant.ofEpochMilli(it), zone = zone)) }
                            ?: stringResource(R.string.settings_backup_never),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
            item { SectionHeading(stringResource(R.string.backup_restore_heading)) }
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.backup_restore_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    when (val state = restore) {
                        BackupModel.Restore.Reading -> Working(stringResource(R.string.backup_reading), null)
                        BackupModel.Restore.Restoring -> Working(stringResource(R.string.backup_restoring), null)
                        is BackupModel.Restore.Locked -> Unlock(model, state.wrongPassword)
                        is BackupModel.Restore.Found -> Found(state.contents, zone, onRestore = { confirming = true }, onCancel = model::cancel)
                        else -> {
                            restoreOutcome(state)?.let { (ok, text) -> StatusLine(ok, text) }
                            OutlinedButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_choose)) }
                        }
                    }
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = { Text(stringResource(R.string.backup_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    model.restore()
                }) { Text(stringResource(R.string.backup_replace)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** What became of the last restore, as a word for its status and a sentence, or null before one is tried. */
@Composable
private fun restoreOutcome(state: BackupModel.Restore): Pair<Boolean, String>? = when (state) {
    BackupModel.Restore.Restored -> true to stringResource(R.string.backup_restored)
    is BackupModel.Restore.Failed ->
        false to stringResource(if (state.recordsReplaced) R.string.backup_photos_failed else R.string.backup_restore_failed)
    is BackupModel.Restore.Refused -> false to stringResource(
        when (state.reason) {
            BackupRead.NewerVersion -> R.string.backup_newer
            BackupRead.Damaged -> R.string.backup_damaged
            BackupRead.NoSpace -> R.string.backup_no_space
            BackupRead.Unreadable -> R.string.backup_unreadable
            else -> R.string.backup_not_a_backup
        },
    )
    else -> null
}

@Composable
private fun Unlock(model: BackupModel, wrongPassword: Boolean) {
    if (wrongPassword) {
        StatusLine(ok = false, stringResource(R.string.backup_wrong_password))
    } else {
        Text(stringResource(R.string.backup_needs_password), style = MaterialTheme.typography.bodyLarge)
    }
    PasswordField(
        value = model.unlockPassword,
        onValueChange = { model.unlockPassword = it },
        label = stringResource(R.string.backup_unlock_password),
        onDone = { if (model.unlockPassword.isNotEmpty()) model.unlock() },
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(enabled = model.unlockPassword.isNotEmpty(), onClick = model::unlock) { Text(stringResource(R.string.backup_open)) }
        TextButton(onClick = model::cancel) { Text(stringResource(R.string.cancel)) }
    }
}

@Composable
private fun Found(contents: BackupContents, zone: ZoneId, onRestore: () -> Unit, onCancel: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.backup_found, UiFormat.longDate(contents.createdAt, zone = zone)), style = MaterialTheme.typography.titleMedium)
            Text(holds(contents), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRestore) { Text(stringResource(R.string.backup_restore)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

/** What a backup holds, as one sentence such as "It holds 3 zones, 42 plants and 120 photos." */
@Composable
private fun holds(contents: BackupContents): String {
    val parts = listOf(
        R.plurals.backup_zones to contents.zones,
        R.plurals.backup_plants to contents.plants,
        R.plurals.backup_photos to contents.photos,
        R.plurals.backup_sightings to contents.sightings,
        R.plurals.backup_hives to contents.hives,
        R.plurals.backup_hotels to contents.hotels,
        R.plurals.backup_lines to contents.wateringLines,
    ).filter { it.second > 0 }.map { (id, count) -> pluralStringResource(id, count, count) }
    if (parts.isEmpty()) return stringResource(R.string.backup_holds_nothing)
    val list = if (parts.size == 1) parts.single() else stringResource(R.string.backup_list_and, parts.dropLast(1).joinToString(", "), parts.last())
    return stringResource(R.string.backup_holds, list)
}

@Composable
private fun Working(text: String, progress: Float?) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
        PollenProgressBar(progress, Modifier.fillMaxWidth())
    }
}

/** A password, hidden unless the button beside it shows it. */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean = true,
    supporting: String? = null,
    error: String? = null,
    onDone: (() -> Unit)? = null,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        isError = error != null,
        visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (onDone != null) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    painterResource(if (shown) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (shown) R.string.backup_hide_password else R.string.backup_show_password),
                )
            }
        },
        supportingText = when {
            error != null -> {
                {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(error)
                    }
                }
            }
            supporting != null -> {
                { Text(supporting) }
            }
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A status in words with an icon beside it, so it never rests on colour alone. */
@Composable
internal fun StatusLine(ok: Boolean, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Icon(painterResource(if (ok) R.drawable.ic_check else R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
