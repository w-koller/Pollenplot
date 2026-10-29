package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLog
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.NativePest
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeSwarm
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A new stingless bee hive, or a hive's details to change when [hiveId] is given: its name, bees, box, where it came
 * from, its zone and which way its entrance faces. A hive can be deleted from here.
 */
@Composable
fun NativeHiveEditorScreen(natives: NativeBeeRepository, views: GardenViews, hiveId: String?, today: LocalDate, onBack: () -> Unit, onDeleted: () -> Unit, onCreated: (String) -> Unit) {
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var started by rememberSaveable { mutableStateOf(hiveId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var species by rememberSaveable { mutableStateOf(StinglessSpecies.TETRAGONULA_CARBONARIA) }
    var box by rememberSaveable { mutableStateOf(NativeBox.OATH) }
    var origin by rememberSaveable { mutableStateOf(NativeOrigin.BOUGHT) }
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var facing by rememberSaveable { mutableStateOf<Facing?>(null) }
    var status by rememberSaveable { mutableStateOf(NativeStatus.ACTIVE) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hiveId) {
        if (started || hiveId == null) return@LaunchedEffect
        val hive = natives.hives.first().firstOrNull { it.id == hiveId } ?: return@LaunchedEffect
        name = hive.name
        species = hive.species
        box = hive.box
        origin = hive.origin
        zoneId = hive.zoneId
        facing = hive.facing
        status = hive.status
        notes = hive.notes
        started = true
    }

    DetailScaffold(
        title = stringResource(if (hiveId == null) R.string.native_new_hive else R.string.native_hive),
        onBack = onBack,
        actions = {
            if (hiveId != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_hive)) }
            }
        },
    ) { padding ->
        if (!started) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.honey_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ChipGroup(stringResource(R.string.native_species), StinglessSpecies.entries, species, { stringResource(it.label) }) { species = it }
            ChipGroup(stringResource(R.string.native_box), NativeBox.entries, box, { stringResource(it.label) }) { box = it }
            ChipGroup(stringResource(R.string.native_origin), NativeOrigin.entries, origin, { stringResource(it.label) }) { origin = it }
            ZoneChooser(zones, zoneId) { zoneId = it }
            ChipGroup(
                stringResource(R.string.native_facing),
                listOf<Facing?>(null) + Facing.entries,
                facing,
                { it?.let { direction -> stringResource(direction.label) } ?: stringResource(R.string.native_facing_unknown) },
            ) { facing = it }
            if (hiveId != null) ChipGroup(stringResource(R.string.native_status), NativeStatus.entries, status, { stringResource(it.label) }) { status = it }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        if (hiveId == null) {
                            val hive = natives.addHive(name, species, box, origin, zoneId, facing, today)
                            if (notes.isNotBlank()) natives.updateHive(hive.copy(notes = notes))
                            withContext(Dispatchers.Main) { onCreated(hive.id) }
                        } else {
                            val hive = natives.hives.first().firstOrNull { it.id == hiveId } ?: return@launch
                            natives.updateHive(hive.copy(name = name, species = species, box = box, origin = origin, zoneId = zoneId, facing = facing, status = status, notes = notes))
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    }
                },
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }

    if (confirmDelete && hiveId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.native_delete_title)) },
            text = { Text(stringResource(R.string.native_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        natives.deleteHive(hiveId)
                        withContext(Dispatchers.Main) { onDeleted() }
                    }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A record for a stingless bee hive: what it is, the day, and the measure or kind that goes with it. */
@Composable
fun NativeLogScreen(initialKind: NativeLogKind, hiveId: String, natives: NativeBeeRepository, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var honey by rememberSaveable { mutableStateOf("") }
    var pest by rememberSaveable { mutableStateOf(NativePest.SYRPHID_FLY) }
    var swarm by rememberSaveable { mutableStateOf(NativeSwarm.FIGHTING) }
    var notes by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(day)
    val grams = weight.toGrams()
    val valid = when (kind) {
        NativeLogKind.ACTIVITY -> count.toIntOrNull() != null
        NativeLogKind.WEIGHT -> grams != null
        NativeLogKind.HONEY -> honey.toIntOrNull()?.let { it > 0 } == true
        else -> true
    }

    DetailScaffold(title = stringResource(R.string.native_log), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChipGroup(stringResource(R.string.native_log_kind), NativeLogKind.entries, kind, { stringResource(it.label) }) { kind = it }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.honey_date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(UiFormat.longDate(date), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.honey_change_date)) }
                }
            }
            when (kind) {
                NativeLogKind.ACTIVITY -> NumberField(stringResource(R.string.native_bees_per_minute), count, KeyboardType.Number) { text -> count = text.filter(Char::isDigit).take(4) }
                NativeLogKind.WEIGHT -> NumberField(stringResource(R.string.native_weight_kg), weight, KeyboardType.Decimal) { weight = it.take(8) }
                NativeLogKind.HONEY -> NumberField(stringResource(R.string.native_honey_ml), honey, KeyboardType.Number) { text -> honey = text.filter(Char::isDigit).take(5) }
                NativeLogKind.PEST -> ChipGroup(stringResource(R.string.native_pest_kind), NativePest.entries, pest, { stringResource(it.label) }) { pest = it }
                NativeLogKind.SWARM -> ChipGroup(stringResource(R.string.native_swarm_kind), NativeSwarm.entries, swarm, { stringResource(it.label) }) { swarm = it }
                else -> Unit
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = valid,
                onClick = {
                    scope.launch {
                        natives.saveLog(
                            NativeLog(
                                id = natives.newRecordId(),
                                hiveId = hiveId,
                                date = date,
                                kind = kind,
                                beesPerMinute = count.toIntOrNull().takeIf { kind == NativeLogKind.ACTIVITY },
                                weightGrams = grams.takeIf { kind == NativeLogKind.WEIGHT },
                                honeyMl = honey.toIntOrNull().takeIf { kind == NativeLogKind.HONEY },
                                pest = pest.takeIf { kind == NativeLogKind.PEST },
                                swarm = swarm.takeIf { kind == NativeLogKind.SWARM },
                                notes = notes,
                            ),
                        )
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }

    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, keyboard: KeyboardType, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Kilograms typed with a point or a comma as whole grams, or null if it is not a positive number. */
private fun String.toGrams(): Int? {
    val value = trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    return value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toInt()
}
