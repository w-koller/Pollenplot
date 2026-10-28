package io.github.wkoller.pollenplot.ui.plot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenShapes
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.tone
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import io.github.wkoller.pollenplot.ui.wildlife.SightingRow
import kotlinx.coroutines.launch

/** A zone's own page: what kind of area it is, its size and conditions, its badges and its plants. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneDetailScreen(
    views: GardenViews,
    zoneId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onAddPlant: () -> Unit,
    onOpenSighting: (String) -> Unit = {},
    onAddSighting: () -> Unit = {},
) {
    val view by views.zone(zoneId).collectAsStateWithLifecycle(initialValue = null)
    val loaded = view
    DetailScaffold(
        title = loaded?.zone?.name.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.zone_edit)) }
        },
    ) { padding ->
        if (loaded == null) return@DetailScaffold
        val zone = loaded.zone
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(72.dp).clip(PollenShapes.cookie()).background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(zone.kind.icon), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(36.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(zone.kind.label), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.zone_area, UiFormat.squareMetres(zone.areaCm2)), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.zone_size, UiFormat.metresFromCm(zone.rect.widthCm), UiFormat.metresFromCm(zone.rect.heightCm)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    zone.sun?.let { Badge(it.icon, stringResource(it.label), BadgeTone.PLANT) }
                    zone.soil?.let { Badge(R.drawable.ic_landscape, stringResource(it.label), BadgeTone.PLANT) }
                    if (zone.mulched) Badge(R.drawable.ic_compost, stringResource(R.string.zone_mulched), BadgeTone.PLANT)
                    loaded.badges.forEach { Badge(it.icon, stringResource(it.label), it.tone) }
                }
            }
            if (zone.notes.isNotBlank()) {
                item { SectionHeading(stringResource(R.string.zone_notes)) }
                item { Text(zone.notes, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            item { SectionHeading(stringResource(R.string.zone_plants_heading)) }
            if (loaded.plants.isEmpty()) {
                item { Text(stringResource(R.string.zone_no_plants), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(loaded.plants, key = { it.plant.id }) { plant -> PlantRow(plant, onClick = { onOpenPlant(plant.plant.id) }, showZone = false) }
            item {
                Button(onClick = onAddPlant, modifier = Modifier.padding(16.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.zone_add_plant), modifier = Modifier.padding(start = 8.dp))
                }
            }
            item { SectionHeading(stringResource(R.string.zone_wildlife_heading)) }
            if (loaded.sightings.isEmpty()) {
                item { Text(stringResource(R.string.zone_no_wildlife), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(loaded.sightings.take(ZONE_SIGHTINGS), key = { "sighting-" + it.sighting.id }) { sighting ->
                SightingRow(sighting, onClick = { onOpenSighting(sighting.sighting.id) }, showZone = false)
            }
            item {
                OutlinedButton(onClick = onAddSighting, modifier = Modifier.padding(16.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Text(stringResource(R.string.zone_add_sighting), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** The latest sightings a zone's page lists; the rest are on the Wildlife tab. */
private const val ZONE_SIGHTINGS = 8

/** Edits a zone's name, kind, conditions and notes, or removes it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoneEditorScreen(garden: GardenRepository, zoneId: String, onBack: () -> Unit, onRemoved: () -> Unit) {
    // The zone as stored. The fields below are saved across configuration changes, so edits survive a turn of the phone.
    var original by remember { mutableStateOf<Zone?>(null) }
    var initialised by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ZoneKind.GARDEN_BED) }
    var sun by rememberSaveable { mutableStateOf<Sun?>(null) }
    var soil by rememberSaveable { mutableStateOf<Soil?>(null) }
    var mulched by rememberSaveable { mutableStateOf(false) }
    var area by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var areaError by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(zoneId) {
        val zone = garden.zone(zoneId) ?: return@LaunchedEffect onBack()
        original = zone
        if (!initialised) {
            name = zone.name
            kind = zone.kind
            sun = zone.sun
            soil = zone.soil
            mulched = zone.mulched
            area = zone.measuredAreaCm2?.let(UiFormat::squareMetres).orEmpty()
            notes = zone.notes
            initialised = true
        }
    }
    val zone = original ?: return

    DetailScaffold(title = stringResource(R.string.zone_edit_title, zone.name), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.zone_name_label)) },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.zone_name_needed)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ChipGroup(stringResource(R.string.zone_kind_label), ZoneKind.entries, kind, { stringResource(it.label) }) { kind = it } }
            item { ChipGroup(stringResource(R.string.zone_sun), listOf(null) + Sun.entries, sun, { it?.let { s -> stringResource(s.label) } ?: stringResource(R.string.zone_not_set) }) { sun = it } }
            item { ChipGroup(stringResource(R.string.zone_soil), listOf(null) + Soil.entries, soil, { it?.let { s -> stringResource(s.label) } ?: stringResource(R.string.zone_not_set) }) { soil = it } }
            item {
                ListItem(checked = mulched, onCheckedChange = { mulched = it }, trailingContent = { Switch(checked = mulched, onCheckedChange = null) }) {
                    Text(stringResource(R.string.zone_mulched))
                }
            }
            item {
                OutlinedTextField(
                    value = area,
                    onValueChange = {
                        area = it
                        areaError = false
                    },
                    label = { Text(stringResource(R.string.zone_measured_area)) },
                    supportingText = { Text(stringResource(if (areaError) R.string.zone_area_invalid else R.string.zone_measured_area_help)) },
                    isError = areaError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.zone_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Button(
                    onClick = {
                        val measured = if (area.isBlank()) null else UiFormat.parseSquareMetres(area)
                        nameError = name.isBlank()
                        areaError = area.isNotBlank() && measured == null
                        if (!nameError && !areaError) {
                            scope.launch {
                                garden.updateZone(zone.copy(name = name.trim(), kind = kind, sun = sun, soil = soil, mulched = mulched, measuredAreaCm2 = measured, notes = notes.trim()))
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.zone_save)) }
            }
            item {
                OutlinedButton(onClick = { confirmRemove = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(R.string.zone_remove), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.zone_remove_title, zone.name)) },
            text = { Text(stringResource(R.string.zone_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        garden.deleteZone(zone.id)
                        onRemoved()
                    }
                }) { Text(stringResource(R.string.zone_remove)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A labelled group of filter chips for choosing one of a few options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onChoose: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> FilterChip(selected = option == selected, onClick = { onChoose(option) }, label = { Text(label(option)) }) }
        }
    }
}
