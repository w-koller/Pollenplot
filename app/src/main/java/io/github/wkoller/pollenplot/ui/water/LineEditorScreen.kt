package io.github.wkoller.pollenplot.ui.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A new watering line, or a line's details to change when [lineId] is given: its name, how it waters, the zones it
 * waters and its flow. A line can be deleted from here.
 */
@Composable
fun LineEditorScreen(water: WaterRepository, views: GardenViews, lineId: String?, onBack: () -> Unit) {
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var started by rememberSaveable { mutableStateOf(lineId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(WaterMethod.DRIPPERS) }
    var zoneIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var flow by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lineId) {
        if (started || lineId == null) return@LaunchedEffect
        val line = water.lines.first().firstOrNull { it.id == lineId } ?: return@LaunchedEffect
        name = line.name
        method = line.method
        zoneIds = line.zoneIds
        flow = line.flowMlPerHour?.let { (it / 1000).toString() }.orEmpty()
        notes = line.notes
        started = true
    }

    DetailScaffold(
        title = stringResource(if (lineId == null) R.string.water_new_line else R.string.water_line),
        onBack = onBack,
        actions = {
            if (lineId != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.water_delete_title)) }
            }
        },
    ) { padding ->
        if (!started) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.honey_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ChipGroup(stringResource(R.string.water_method), WaterMethod.entries, method, { stringResource(it.label) }) { method = it }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.water_zones), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    zones.forEach { view ->
                        val id = view.zone.id
                        FilterChip(selected = id in zoneIds, onClick = { zoneIds = if (id in zoneIds) zoneIds - id else zoneIds + id }, label = { Text(view.zone.name) })
                    }
                }
            }
            if (!method.byHand) {
                OutlinedTextField(
                    value = flow,
                    onValueChange = { text -> flow = text.filter(Char::isDigit).take(5) },
                    label = { Text(stringResource(R.string.water_flow)) },
                    supportingText = { Text(stringResource(R.string.water_flow_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = name.isNotBlank() && zoneIds.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val flowMl = if (method.byHand) null else flow.toIntOrNull()?.times(1000)
                        water.saveLine(WateringLine(lineId ?: water.newId(), name, method, zoneIds, flowMl, notes))
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }

    if (confirmDelete && lineId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.water_delete_title)) },
            text = { Text(stringResource(R.string.water_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        water.deleteLine(lineId)
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
