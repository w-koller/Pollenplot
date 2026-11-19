package io.github.wkoller.pollenplot.ui.compost

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.CompostBinView
import io.github.wkoller.pollenplot.data.CompostRepository
import io.github.wkoller.pollenplot.data.CompostViews
import io.github.wkoller.pollenplot.domain.compost.CompostEvent
import io.github.wkoller.pollenplot.domain.compost.CompostEventKind
import io.github.wkoller.pollenplot.domain.compost.CompostPhase
import io.github.wkoller.pollenplot.domain.compost.CompostStages
import io.github.wkoller.pollenplot.domain.compost.CompostStep
import io.github.wkoller.pollenplot.domain.compost.CompostTask
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.AddButton
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import io.github.wkoller.pollenplot.ui.components.CompostAnimation
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.LocalNotices
import io.github.wkoller.pollenplot.ui.components.MissingRecord
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import io.github.wkoller.pollenplot.ui.components.SegmentedGroup
import io.github.wkoller.pollenplot.ui.components.listPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every compost bin, each with what it is doing and its next job, and a button to add one. */
@Composable
fun CompostScreen(views: CompostViews, repository: CompostRepository, onBack: () -> Unit, onOpenBin: (String) -> Unit) {
    val bins by views.bins.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val newName = stringResource(R.string.compost_new_name, (bins?.size ?: 0) + 1)
    DetailScaffold(
        title = stringResource(R.string.compost_title),
        onBack = onBack,
        floatingActionButton = {
            AddButton(stringResource(R.string.compost_add)) {
                scope.launch {
                    val id = repository.addBin(newName).id
                    // The database may answer on another thread, and navigation belongs on the main one.
                    withContext(Dispatchers.Main) { onOpenBin(id) }
                }
            }
        },
    ) { padding ->
        val list = bins ?: return@DetailScaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding, bottom = 96.dp)) {
            item {
                Text(
                    stringResource(R.string.compost_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (list.isEmpty()) {
                item { EmptyState(R.drawable.ic_compost, stringResource(R.string.compost_empty_title), stringResource(R.string.compost_empty_body)) }
            } else {
                item {
                    SegmentedGroup(Modifier.padding(top = 8.dp)) {
                        list.forEach { view ->
                            row { segment ->
                                ListItem(
                                    onClick = { onOpenBin(view.bin.id) },
                                    leadingContent = { Icon(painterResource(R.drawable.ic_compost), contentDescription = null) },
                                    supportingContent = { Text(binSummary(view)) },
                                    shapes = segment.shapes,
                                    colors = segment.colors,
                                ) { Text(view.bin.name) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A bin's phase and its job due, or its next one, in one line. */
@Composable
private fun binSummary(view: CompostBinView): String {
    val phase = stringResource(phaseLabel(view.phase))
    val job = view.due.firstOrNull()?.let { stringResource(if (it.kind == CompostTask.Kind.TURN) R.string.compost_turn_due else R.string.compost_check_due) }
        ?: view.next.firstOrNull()?.let { nextLine(it) }
    return listOfNotNull(phase, job).joinToString(". ")
}

@Composable
private fun nextLine(task: CompostTask): String = stringResource(
    if (task.kind == CompostTask.Kind.TURN) R.string.compost_next_turn else R.string.compost_next_check,
    UiFormat.longDate(task.due),
)

/**
 * One bin: a picture of what it is doing, the three steps with the one it is on, what to do in that step, the buttons
 * that record it, the next jobs, this fill's history and the bin's own settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompostBinScreen(views: CompostViews, repository: CompostRepository, binId: String, onBack: () -> Unit) {
    val flow = remember(binId) { views.bin(binId) }
    var loaded by remember { mutableStateOf(false) }
    val found by flow.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(found) { if (found != null) loaded = true }
    val scope = rememberCoroutineScope()
    val notices = LocalNotices.current
    val today = views.today()
    var deleting by rememberSaveable { mutableStateOf(false) }
    val view = found
    DetailScaffold(title = view?.bin?.name ?: stringResource(R.string.compost_bin_title), onBack = onBack) { padding ->
        if (view == null) {
            if (loaded) MissingRecord(padding)
            return@DetailScaffold
        }
        val batch = view.batch
        val phase = view.phase
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val label = stringResource(phaseLabel(phase))
                    CompostAnimation(phase ?: CompostPhase.EMPTIED, stringResource(R.string.compost_phase_description, label))
                    Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Steps(phase?.step ?: CompostStep.EMPTY, phase == null || phase == CompostPhase.EMPTIED)
                }
            }
            item {
                Text(
                    stringResource(phaseBody(phase)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                // Each line records what was just done, with an undo in the message that follows.
                fun record(kind: CompostEventKind, what: String) {
                    val open = batch ?: return
                    scope.launch {
                        val event = repository.addEvent(open.id, kind, today)
                        notices?.show(what) { repository.deleteEvent(event.id) }
                    }
                }
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val turned = stringResource(R.string.compost_recorded, stringResource(R.string.compost_event_turned).lowercase())
                    val watered = stringResource(R.string.compost_recorded, stringResource(R.string.compost_event_watered).lowercase())
                    when {
                        batch == null -> Button(onClick = { scope.launch { repository.startBatch(binId, today) } }) { Text(stringResource(R.string.compost_start)) }
                        phase == CompostPhase.FILLING -> {
                            Button(onClick = { scope.launch { repository.filled(batch.id, today) } }) { Text(stringResource(R.string.compost_filled)) }
                        }
                        else -> {
                            if (phase == CompostPhase.READY) {
                                Button(onClick = { scope.launch { repository.emptied(batch.id, today) } }) { Text(stringResource(R.string.compost_emptied)) }
                                OutlinedButton(onClick = { record(CompostEventKind.TURNED, turned) }) { Text(stringResource(R.string.compost_turned)) }
                            } else {
                                Button(onClick = { record(CompostEventKind.TURNED, turned) }) { Text(stringResource(R.string.compost_turned)) }
                                OutlinedButton(onClick = { record(CompostEventKind.WATERED, watered) }) { Text(stringResource(R.string.compost_watered)) }
                                OutlinedButton(onClick = { scope.launch { repository.emptied(batch.id, today) } }) { Text(stringResource(R.string.compost_emptied)) }
                            }
                            if (view.events.none { it.kind == CompostEventKind.TURNED } && batch.filledOn == today) {
                                TextButton(onClick = { scope.launch { repository.filled(batch.id, null) } }) { Text(stringResource(R.string.compost_not_filled)) }
                            }
                        }
                    }
                }
            }
            if (batch != null && batch.filledOn != null) {
                item { Reading(onSave = { tenths -> scope.launch { repository.addEvent(batch.id, CompostEventKind.TEMPERATURE, today, tenthsC = tenths) } }) }
            }
            if (view.due.isNotEmpty() || view.next.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        view.due.forEach { task ->
                            Text(
                                stringResource(if (task.kind == CompostTask.Kind.TURN) R.string.compost_turn_due else R.string.compost_check_due),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                        view.next.forEach { Text(nextLine(it), style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            if (batch != null) {
                item { SectionHeading(stringResource(R.string.compost_history)) }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.compost_started_on, UiFormat.longDate(batch.startedOn)), style = MaterialTheme.typography.bodyMedium)
                        batch.filledOn?.let { Text(stringResource(R.string.compost_filled_on, UiFormat.longDate(it)), style = MaterialTheme.typography.bodyMedium) }
                        if (view.events.isEmpty()) Text(stringResource(R.string.compost_history_none), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                view.events.forEach { event ->
                    item(key = event.id) {
                        val what = eventText(event)
                        ListItem(
                            trailingContent = {
                                val removed = stringResource(R.string.compost_event_removed, what)
                                IconButton(onClick = {
                                    scope.launch {
                                        repository.deleteEvent(event.id)
                                        notices?.show(removed) { repository.restoreEvent(event) }
                                    }
                                }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.compost_event_remove, what)) }
                            },
                        ) { Text(stringResource(R.string.compost_event_line, what, UiFormat.longDate(event.date))) }
                    }
                }
            }
            if (view.emptied > 0) {
                item {
                    Text(
                        pluralStringResource(R.plurals.compost_emptied_count, view.emptied, view.emptied),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            item { SectionHeading(stringResource(R.string.compost_bin_settings)) }
            item {
                var name by rememberSaveable(view.bin.id) { mutableStateOf(view.bin.name) }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.compost_name)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            enabled = name.isNotBlank() && name.trim() != view.bin.name,
                            onClick = { scope.launch { repository.updateBin(view.bin.copy(name = name)) } },
                        ) { Text(stringResource(R.string.compost_name_save)) }
                    }
                    ChipGroup(
                        stringResource(R.string.compost_turn_every),
                        CompostStages.TURN_CHOICES,
                        view.bin.turnEveryDays,
                        { stringResource(R.string.compost_turn_days, it) },
                    ) { days -> scope.launch { repository.updateBin(view.bin.copy(turnEveryDays = days)) } }
                    Text(stringResource(R.string.compost_turn_source), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.compost_phase_estimate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.compost_sources), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { deleting = true }) {
                        Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.compost_delete), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (deleting) {
            val deleted = stringResource(R.string.compost_deleted, view.bin.name)
            AlertDialog(
                onDismissRequest = { deleting = false },
                title = { Text(stringResource(R.string.compost_delete_title, view.bin.name)) },
                text = { Text(stringResource(R.string.compost_delete_body)) },
                confirmButton = {
                    TextButton(onClick = {
                        deleting = false
                        scope.launch {
                            repository.deleteBin(view.bin.id)
                            notices?.show(deleted)
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    }) { Text(stringResource(R.string.compost_delete_confirm)) }
                },
                dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

/** The three steps in a row, each with an icon and words for done, now or to come, so the step does not rest on colour. */
@Composable
private fun Steps(current: CompostStep, finished: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        CompostStep.entries.forEach { step ->
            val name = stringResource(
                when (step) {
                    CompostStep.FILL -> R.string.compost_step_fill
                    CompostStep.WAIT -> R.string.compost_step_wait
                    CompostStep.EMPTY -> R.string.compost_step_empty
                },
            )
            val done = step.ordinal < current.ordinal || (finished && step == CompostStep.EMPTY)
            val now = step == current && !done
            val spoken = stringResource(if (done) R.string.compost_step_done else if (now) R.string.compost_step_now else R.string.compost_step_later, name)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { contentDescription = spoken }) {
                Icon(
                    painterResource(if (done) R.drawable.ic_check_circle else if (now) R.drawable.ic_compost else R.drawable.ic_history),
                    contentDescription = null,
                    tint = if (now) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(name, style = if (now) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A thermometer reading in the middle of the heap, in whole or tenth degrees. */
@Composable
private fun Reading(onSave: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val tenths = text.replace(',', '.').toBigDecimalOrNull()?.movePointRight(1)?.toInt()?.takeIf { it in 0..900 }
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.compost_reading)) },
            singleLine = true,
            isError = text.isNotBlank() && tenths == null,
            supportingText = if (text.isNotBlank() && tenths == null) {
                { Text(stringResource(R.string.compost_reading_bad)) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = tenths != null, onClick = {
            tenths?.let(onSave)
            text = ""
        }) { Text(stringResource(R.string.compost_reading_save)) }
    }
}

@Composable
private fun eventText(event: CompostEvent): String = when (event.kind) {
    CompostEventKind.TURNED -> stringResource(R.string.compost_event_turned)
    CompostEventKind.WATERED -> stringResource(R.string.compost_event_watered)
    CompostEventKind.TEMPERATURE -> stringResource(R.string.compost_event_temperature, java.math.BigDecimal(event.tenthsC ?: 0).movePointLeft(1).stripTrailingZeros().toPlainString())
}

internal fun phaseLabel(phase: CompostPhase?): Int = when (phase) {
    null -> R.string.compost_phase_idle
    CompostPhase.FILLING -> R.string.compost_phase_filling
    CompostPhase.WARMING -> R.string.compost_phase_warming
    CompostPhase.HOT -> R.string.compost_phase_hot
    CompostPhase.COOLING -> R.string.compost_phase_cooling
    CompostPhase.CURING -> R.string.compost_phase_curing
    CompostPhase.READY -> R.string.compost_phase_ready
    CompostPhase.EMPTIED -> R.string.compost_phase_emptied
}

private fun phaseBody(phase: CompostPhase?): Int = when (phase) {
    null -> R.string.compost_phase_idle_body
    CompostPhase.FILLING -> R.string.compost_phase_filling_body
    CompostPhase.WARMING -> R.string.compost_phase_warming_body
    CompostPhase.HOT -> R.string.compost_phase_hot_body
    CompostPhase.COOLING -> R.string.compost_phase_cooling_body
    CompostPhase.CURING -> R.string.compost_phase_curing_body
    CompostPhase.READY -> R.string.compost_phase_ready_body
    CompostPhase.EMPTIED -> R.string.compost_phase_emptied_body
}
