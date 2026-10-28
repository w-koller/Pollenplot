package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plants.QuantityStepper
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** The choice in the apiary chips that starts a new apiary, alongside none and the apiaries there are. */
private const val NEW_APIARY = "new"

/**
 * A new honey bee hive, or a hive's details to change, when [hiveId] is given: its name, kind, zone, apiary, queen,
 * status and boxes. A new apiary can be started here with its registration number. A hive can be deleted from here.
 */
@Composable
fun HiveEditorScreen(bees: BeeRepository, views: GardenViews, hiveId: String?, today: LocalDate, onBack: () -> Unit, onDeleted: () -> Unit, onCreated: (String) -> Unit) {
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val apiaries by bees.apiaries.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var started by rememberSaveable { mutableStateOf(hiveId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(HiveType.LANGSTROTH_8) }
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var apiaryChoice by rememberSaveable { mutableStateOf<String?>(null) }
    var apiaryName by rememberSaveable { mutableStateOf("") }
    var registration by rememberSaveable { mutableStateOf("") }
    var queenYear by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(HiveStatus.ACTIVE) }
    var broodBoxes by rememberSaveable { mutableIntStateOf(1) }
    var supers by rememberSaveable { mutableIntStateOf(0) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hiveId) {
        if (started || hiveId == null) return@LaunchedEffect
        val hive = bees.hives.first().firstOrNull { it.id == hiveId } ?: return@LaunchedEffect
        name = hive.name
        type = hive.type
        zoneId = hive.zoneId
        apiaryChoice = hive.apiaryId
        queenYear = hive.queenYear?.toString().orEmpty()
        status = hive.status
        broodBoxes = hive.broodBoxes
        supers = hive.supers
        notes = hive.notes
        started = true
    }

    DetailScaffold(
        title = stringResource(if (hiveId == null) R.string.honey_new_hive else R.string.honey_hive),
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
            ChipGroup(stringResource(R.string.honey_type), HiveType.entries, type, { stringResource(it.label) }) { type = it }
            ZoneChooser(zones, zoneId) { zoneId = it }
            val apiaryNames = apiaries.associate { it.id to it.name }
            ChipGroup(
                stringResource(R.string.honey_apiary),
                listOf<String?>(null) + apiaries.map { it.id } + NEW_APIARY,
                apiaryChoice,
                { id ->
                    when (id) {
                        null -> stringResource(R.string.honey_apiary_none)
                        NEW_APIARY -> stringResource(R.string.honey_apiary_new)
                        else -> apiaryNames[id].orEmpty()
                    }
                },
            ) { apiaryChoice = it }
            if (apiaryChoice == NEW_APIARY) {
                OutlinedTextField(value = apiaryName, onValueChange = { apiaryName = it }, label = { Text(stringResource(R.string.honey_apiary_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = registration, onValueChange = { registration = it }, label = { Text(stringResource(R.string.honey_apiary_registration)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(
                value = queenYear,
                onValueChange = { text -> queenYear = text.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.honey_queen_year)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            if (hiveId != null) ChipGroup(stringResource(R.string.honey_status), HiveStatus.entries, status, { stringResource(it.label) }) { status = it }
            QuantityStepper(broodBoxes, title = stringResource(R.string.honey_brood_boxes), countText = { it.toString() }) { broodBoxes = it }
            QuantityStepper(supers, title = stringResource(R.string.honey_supers), min = 0, countText = { it.toString() }) { supers = it }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            val year = queenYear.toIntOrNull()?.takeIf { it in 1990..today.year }
            Button(
                enabled = name.isNotBlank() && (queenYear.isEmpty() || year != null) && (apiaryChoice != NEW_APIARY || apiaryName.isNotBlank()),
                onClick = {
                    scope.launch {
                        val apiaryId = if (apiaryChoice == NEW_APIARY) bees.addApiary(apiaryName, registration).id else apiaryChoice
                        if (hiveId == null) {
                            val hive = bees.addHive(name, type, zoneId, apiaryId, year, today)
                            bees.updateHive(hive.copy(broodBoxes = broodBoxes, supers = supers, notes = notes.trim()))
                            withContext(Dispatchers.Main) { onCreated(hive.id) }
                        } else {
                            val hive = bees.hives.first().firstOrNull { it.id == hiveId } ?: return@launch
                            bees.updateHive(hive.copy(name = name, type = type, zoneId = zoneId, apiaryId = apiaryId, queenYear = year, status = status, broodBoxes = broodBoxes, supers = supers, notes = notes.trim()))
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
            title = { Text(stringResource(R.string.honey_delete_title)) },
            text = { Text(stringResource(R.string.honey_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        bees.deleteHive(hiveId)
                        withContext(Dispatchers.Main) { onDeleted() }
                    }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
