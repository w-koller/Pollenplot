package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import kotlinx.coroutines.launch
import java.math.BigDecimal

/** The kinds of record a hive's page can add, each with its own form. */
enum class HoneyRecordKind(val title: Int, val action: Int) {
    INSPECTION(R.string.honey_inspection, R.string.honey_record_inspection),
    MITE_CHECK(R.string.honey_check, R.string.honey_record_mites),
    TREATMENT(R.string.honey_treatment, R.string.honey_record_treatment),
    FEEDING(R.string.honey_feeding, R.string.honey_record_feeding),
    HARVEST(R.string.honey_harvest, R.string.honey_record_harvest),
    EVENT(R.string.honey_event, R.string.honey_record_event),
}

/**
 * One honey bee hive: what it is and where, what it is due for, buttons to record what was done, and everything
 * recorded for it, the latest first. Each record can be deleted with its own button.
 */
@Composable
fun HiveScreen(views: BeeViews, bees: BeeRepository, hiveId: String, onBack: () -> Unit, onEdit: () -> Unit, onRecord: (HoneyRecordKind) -> Unit) {
    val view by remember(hiveId) { views.hive(hiveId) }.collectAsStateWithLifecycle(initialValue = null)
    val inspections by bees.inspections.collectAsStateWithLifecycle(initialValue = emptyList())
    val checks by bees.checks.collectAsStateWithLifecycle(initialValue = emptyList())
    val treatments by bees.treatments.collectAsStateWithLifecycle(initialValue = emptyList())
    val feedings by bees.feedings.collectAsStateWithLifecycle(initialValue = emptyList())
    val harvests by bees.harvests.collectAsStateWithLifecycle(initialValue = emptyList())
    val events by bees.events.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    // A record is deleted only once the user confirms; this is the deletion waiting for that.
    var pendingDelete by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val confirm: (suspend () -> Unit) -> Unit = { pendingDelete = it }
    val loaded = view
    val today = views.today()

    DetailScaffold(
        title = loaded?.hive?.name.orEmpty(),
        onBack = onBack,
        actions = { IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.honey_edit_hive)) } },
    ) { padding ->
        if (loaded == null) return@DetailScaffold
        val hive = loaded.hive
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item(key = "summary") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(listOfNotNull(stringResource(hive.type.label), loaded.zoneName).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                    hive.queenColour?.let { Text(stringResource(R.string.honey_queen, hive.queenYear!!, stringResource(it.label)), style = MaterialTheme.typography.bodyLarge) }
                    Text(stringResource(hive.status.label), style = MaterialTheme.typography.bodyLarge)
                    loaded.apiary?.let { apiary ->
                        Text(listOfNotNull(apiary.name, apiary.registration).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (loaded.tasks.isNotEmpty()) {
                item(key = "due") { SectionHeading(stringResource(R.string.honey_due)) }
                items(loaded.tasks, key = { "task-" + it.kind }) { task -> TaskRow(task, today, prefix = null, onClick = null) }
            }
            item(key = "record") {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRecord(HoneyRecordKind.INSPECTION) }) { Text(stringResource(R.string.honey_record_inspection)) }
                    Button(onClick = { onRecord(HoneyRecordKind.MITE_CHECK) }) { Text(stringResource(R.string.honey_record_mites)) }
                    listOf(HoneyRecordKind.TREATMENT, HoneyRecordKind.FEEDING, HoneyRecordKind.HARVEST, HoneyRecordKind.EVENT).forEach { kind ->
                        OutlinedButton(onClick = { onRecord(kind) }) { Text(stringResource(kind.action)) }
                    }
                }
            }
            history(
                key = "inspections",
                title = R.string.honey_inspections,
                records = inspections.filter { it.hiveId == hiveId },
                id = { it.id },
                onDelete = { id -> confirm { bees.deleteInspection(id) } },
            ) { inspection ->
                val seen = when {
                    inspection.queenSeen -> stringResource(R.string.honey_inspection_queen)
                    inspection.eggsSeen -> stringResource(R.string.honey_inspection_eggs)
                    else -> stringResource(R.string.honey_inspection_no_queen)
                }
                val signs = mutableListOf<String>()
                for (sign in inspection.signs.sortedBy { it.ordinal }) signs += stringResource(sign.label)
                UiFormat.longDate(inspection.date) to listOfNotNull(seen, stringResource(inspection.brood.label).takeIf { inspection.brood != null }, signs.joinToString(", ").ifBlank { null }, inspection.actions.ifBlank { null }).joinToString(" · ")
            }
            history("checks", R.string.honey_checks, checks.filter { it.hiveId == hiveId }, { it.id }, { id -> confirm { bees.deleteCheck(id) } }) { check ->
                // Exactly one is "1 mite"; any other amount, 1.3 or 0 included, is "mites".
                val result = check.mitesPer100BeesTenths?.let { pluralStringResource(R.plurals.honey_mites_per_100, if (it == 10) 1 else 2, tenths(it)) }
                    ?: check.mitesPerDayTenths?.let { pluralStringResource(R.plurals.honey_mites_per_day, if (it == 10) 1 else 2, tenths(it)) }
                    ?: pluralStringResource(R.plurals.honey_mites_found, check.mites, check.mites)
                UiFormat.longDate(check.date) to "${stringResource(check.method.label)} · $result"
            }
            history(
                key = "treatments",
                title = R.string.honey_treatments,
                records = treatments.filter { it.hiveId == hiveId },
                id = { it.id },
                onDelete = { id -> confirm { bees.deleteTreatment(id) } },
                extra = { treatment ->
                    if (treatment.ended == null) {
                        TextButton(onClick = { scope.launch { bees.saveTreatment(treatment.copy(ended = maxOf(today, treatment.started))) } }) {
                            Text(stringResource(R.string.honey_treatment_end_now))
                        }
                    }
                },
            ) { treatment ->
                val ended = treatment.ended?.let { stringResource(R.string.honey_treatment_ended, UiFormat.longDate(it)) } ?: stringResource(R.string.honey_treatment_ongoing)
                UiFormat.longDate(treatment.started) to listOf(treatment.product, treatment.activeIngredient, ended).filter { it.isNotBlank() }.joinToString(" · ")
            }
            history("feedings", R.string.honey_feedings, feedings.filter { it.hiveId == hiveId }, { it.id }, { id -> confirm { bees.deleteFeeding(id) } }) { feeding ->
                UiFormat.longDate(feeding.date) to stringResource(feeding.kind.label)
            }
            history("harvests", R.string.honey_harvests, harvests.filter { it.hiveId == hiveId }, { it.id }, { id -> confirm { bees.deleteHarvest(id) } }) { harvest ->
                val kilograms = BigDecimal(harvest.honeyGrams).movePointLeft(3).stripTrailingZeros().toPlainString()
                UiFormat.longDate(harvest.date) to if (harvest.frames > 0) {
                    pluralStringResource(R.plurals.honey_harvest_line, harvest.frames, kilograms, harvest.frames)
                } else {
                    stringResource(R.string.honey_harvest_kg_only, kilograms)
                }
            }
            history("events", R.string.honey_events, events.filter { it.hiveId == hiveId }, { it.id }, { id -> confirm { bees.deleteEvent(id) } }) { event ->
                UiFormat.longDate(event.date) to listOf(stringResource(event.kind.label), event.notes).filter { it.isNotBlank() }.joinToString(" · ")
            }
        }
    }

    pendingDelete?.let { delete ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.honey_delete_record_title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { delete() }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * One kind of record for the hive, the latest first, each with the day and what was noted, and a delete button.
 * [extra] adds anything a record of this kind can have under its details, such as a button.
 */
private fun <T> LazyListScope.history(
    key: String,
    title: Int,
    records: List<T>,
    id: (T) -> String,
    onDelete: (String) -> Unit,
    extra: @Composable (T) -> Unit = {},
    line: @Composable (T) -> Pair<String, String>,
) {
    item(key = "heading-$key") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    if (records.isEmpty()) {
        item(key = "none-$key") {
            Text(stringResource(R.string.honey_nothing_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
    items(records, key = { "$key-" + id(it) }) { record ->
        val (headline, details) = line(record)
        ListItem(
            supportingContent = {
                Column {
                    if (details.isNotBlank()) Text(details)
                    extra(record)
                }
            },
            trailingContent = {
                IconButton(onClick = { onDelete(id(record)) }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_record))
                }
            },
        ) { Text(headline) }
    }
}

/** A count in tenths, such as 13 as "1.3". */
internal fun tenths(value: Int): String = BigDecimal(value).movePointLeft(1).stripTrailingZeros().toPlainString()
