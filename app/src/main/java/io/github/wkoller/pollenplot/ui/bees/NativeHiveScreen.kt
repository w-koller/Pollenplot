package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLog
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import kotlinx.coroutines.launch

/**
 * One stingless bee hive: its bees, box and place, its latest weight and count of bees coming home, buttons to
 * record what was seen or done, and every record, the latest first, each with its own delete button.
 */
@Composable
fun NativeHiveScreen(views: BeeViews, natives: NativeBeeRepository, hiveId: String, onBack: () -> Unit, onEdit: () -> Unit, onRecord: (NativeLogKind) -> Unit) {
    val view by remember(hiveId) { views.nativeHive(hiveId) }.collectAsStateWithLifecycle(initialValue = null)
    val logs by natives.logs.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val loaded = view

    DetailScaffold(
        title = loaded?.hive?.name.orEmpty(),
        onBack = onBack,
        actions = { IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.native_edit_hive)) } },
    ) { padding ->
        if (loaded == null) return@DetailScaffold
        val hive = loaded.hive
        val strength = loaded.strength
        val own = logs.filter { it.hiveId == hiveId }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item(key = "summary") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(listOfNotNull(stringResource(hive.species.label), stringResource(hive.box.label), loaded.zoneName).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                    hive.facing?.let { Text(stringResource(R.string.native_facing) + " · " + stringResource(it.label), style = MaterialTheme.typography.bodyLarge) }
                    Text(stringResource(hive.status.label), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        strength.weightGrams?.let { stringResource(R.string.native_weighed, kilograms(it), UiFormat.longDate(strength.weighedOn!!)) } ?: stringResource(R.string.native_not_weighed),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        strength.beesPerMinute?.let { pluralStringResource(R.plurals.native_counted, it, it, UiFormat.longDate(strength.countedOn!!)) } ?: stringResource(R.string.native_count_none),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item(key = "record") {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRecord(NativeLogKind.ACTIVITY) }) { Text(stringResource(R.string.native_record_activity)) }
                    Button(onClick = { onRecord(NativeLogKind.WEIGHT) }) { Text(stringResource(R.string.native_record_weight)) }
                    OutlinedButton(onClick = { onRecord(NativeLogKind.HONEY) }) { Text(stringResource(R.string.native_record_other)) }
                }
            }
            item(key = "records") { SectionHeading(stringResource(R.string.native_records)) }
            if (own.isEmpty()) {
                item(key = "none") {
                    Text(stringResource(R.string.honey_nothing_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
            items(own, key = { it.id }) { log -> LogRow(log, onDelete = { pendingDelete = log.id }) }
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.honey_delete_record_title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { natives.deleteLog(id) }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A record with what it was, the day, what was measured and any notes. */
@Composable
private fun LogRow(log: NativeLog, onDelete: () -> Unit) {
    val measure = when (log.kind) {
        NativeLogKind.ACTIVITY -> log.beesPerMinute?.let { pluralStringResource(R.plurals.native_per_minute, it, it) }
        NativeLogKind.WEIGHT -> log.weightGrams?.let { stringResource(R.string.native_weight_line, kilograms(it)) }
        NativeLogKind.HONEY -> log.honeyMl?.let { stringResource(R.string.native_honey_line, it) }
        NativeLogKind.PEST -> log.pest?.let { stringResource(it.label) }
        NativeLogKind.SWARM -> log.swarm?.let { stringResource(it.label) }
        else -> null
    }
    ListItem(
        supportingContent = { Text(listOfNotNull(UiFormat.longDate(log.date), measure, log.notes.ifBlank { null }).joinToString(" · ")) },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_record)) }
        },
    ) { Text(stringResource(log.kind.label)) }
}
