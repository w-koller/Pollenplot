package io.github.wkoller.pollenplot.ui.bees

import io.github.wkoller.pollenplot.ui.components.GroupedItem
import io.github.wkoller.pollenplot.ui.components.MenuAction
import io.github.wkoller.pollenplot.ui.components.RecordHero
import io.github.wkoller.pollenplot.ui.components.RecordMenu
import io.github.wkoller.pollenplot.ui.components.heroGone
import io.github.wkoller.pollenplot.ui.components.listPadding
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalResources
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import kotlinx.coroutines.flow.map
import io.github.wkoller.pollenplot.ui.components.MissingRecord
import io.github.wkoller.pollenplot.ui.components.Found
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLog
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import kotlinx.coroutines.launch

/**
 * One stingless bee hive: a hero with its bees, box and place, its latest weight and count of bees coming home, and
 * every record as one group, the latest first, each with its own delete button. The record menu adds what was seen or
 * done.
 */
@Composable
fun NativeHiveScreen(views: BeeViews, natives: NativeBeeRepository, hiveId: String, onBack: () -> Unit, onEdit: () -> Unit, onRecord: (NativeLogKind) -> Unit) {
    val found by remember(hiveId) { views.nativeHive(hiveId).map { Found(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val view = found?.value
    val logs by natives.logs.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val loaded = view
    val list = rememberLazyListState()
    val resources = LocalResources.current

    DetailScaffold(
        title = loaded?.hive?.name.orEmpty(),
        onBack = onBack,
        titleShown = heroGone(list),
        actions = { IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.native_edit_hive)) } },
        floatingActionButton = if (loaded == null) null else {
            {
                RecordMenu(
                    listOf(
                        MenuAction(resources.getString(R.string.native_record_activity), R.drawable.ic_timer) { onRecord(NativeLogKind.ACTIVITY) },
                        MenuAction(resources.getString(R.string.native_record_weight), R.drawable.ic_scale) { onRecord(NativeLogKind.WEIGHT) },
                        MenuAction(resources.getString(R.string.native_record_other), R.drawable.ic_edit_note) { onRecord(NativeLogKind.HONEY) },
                    ),
                )
            }
        },
    ) { padding ->
        if (loaded == null) {
            if (found != null) MissingRecord(padding)
            return@DetailScaffold
        }
        val hive = loaded.hive
        val strength = loaded.strength
        val own = logs.filter { it.hiveId == hiveId }
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = listPadding(padding)) {
            item(key = "summary") {
                RecordHero(
                    title = hive.name,
                    details = listOfNotNull(
                        listOfNotNull(stringResource(hive.species.label), stringResource(hive.box.label), loaded.zoneName).joinToString(" · "),
                        hive.facing?.let { stringResource(R.string.native_facing) + " · " + stringResource(it.label) },
                        stringResource(hive.status.label),
                        strength.weightGrams?.let { stringResource(R.string.native_weighed, kilograms(it), UiFormat.longDate(strength.weighedOn!!)) } ?: stringResource(R.string.native_not_weighed),
                        strength.beesPerMinute?.let { pluralStringResource(R.plurals.native_counted, it, it, UiFormat.longDate(strength.countedOn!!)) } ?: stringResource(R.string.native_count_none),
                    ),
                ) { BeeHeroPicture(R.drawable.ic_native_bee) }
            }
            item(key = "records") { SectionHeading(stringResource(R.string.native_records)) }
            if (own.isEmpty()) {
                item(key = "none") { NothingRecorded() }
            }
            itemsIndexed(own, key = { _, log -> log.id }) { index, log -> LogRow(log, index, own.size, onDelete = { pendingDelete = log.id }) }
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

/** A record with what it was, the day, what was measured and any notes, the [index]th of [count] in its group. */
@Composable
private fun LogRow(log: NativeLog, index: Int, count: Int, onDelete: () -> Unit) {
    val measure = when (log.kind) {
        NativeLogKind.ACTIVITY -> log.beesPerMinute?.let { pluralStringResource(R.plurals.native_per_minute, it, it) }
        NativeLogKind.WEIGHT -> log.weightGrams?.let { stringResource(R.string.native_weight_line, kilograms(it)) }
        NativeLogKind.HONEY -> log.honeyMl?.let { stringResource(R.string.native_honey_line, it) }
        NativeLogKind.PEST -> log.pest?.let { stringResource(it.label) }
        NativeLogKind.SWARM -> log.swarm?.let { stringResource(it.label) }
        else -> null
    }
    GroupedItem(
        index = index,
        count = count,
        supporting = { Text(listOfNotNull(UiFormat.longDate(log.date), measure, log.notes.ifBlank { null }).joinToString(" · ")) },
        trailing = {
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_record)) }
        },
    ) { Text(stringResource(log.kind.label)) }
}
