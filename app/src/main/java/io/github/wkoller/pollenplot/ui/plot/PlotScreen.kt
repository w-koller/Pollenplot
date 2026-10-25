package io.github.wkoller.pollenplot.ui.plot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.nav.AddAction
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.plants.ZoneCard
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import kotlinx.coroutines.launch

/**
 * The home tab: the backyard canvas of zones, or the same garden as a list. Edit layout turns on a floating toolbar for
 * adding, moving, resizing and editing zones.
 */
@Composable
fun PlotScreen(
    garden: GardenRepository,
    views: GardenViews,
    onOpenSettings: () -> Unit,
    onAdd: (AddAction) -> Unit,
    onOpenZone: (String) -> Unit,
    onEditZone: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = null)
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = null)
    var listMode by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var addingZone by rememberSaveable { mutableStateOf(false) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val loadedZones = zones
    val loadedPlants = plants
    val hasZones = !loadedZones.isNullOrEmpty()
    val showCanvas = hasZones && !listMode
    val selectedZone = loadedZones?.firstOrNull { it.zone.id == selectedId }?.zone

    fun finishEditing() {
        editing = false
        selectedId = null
    }
    BackHandler(editing) { finishEditing() }

    TabScaffold(
        title = stringResource(R.string.plot_title),
        subtitle = stringResource(R.string.plot_subtitle),
        onOpenSettings = onOpenSettings,
        compact = showCanvas,
        actions = {
            if (!editing) {
                IconButton(onClick = onSearch) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title))
                }
                if (hasZones) {
                    IconButton(onClick = { listMode = !listMode }) {
                        Icon(
                            painterResource(if (listMode) R.drawable.ic_map else R.drawable.ic_view_list),
                            contentDescription = stringResource(if (listMode) R.string.plot_show_canvas else R.string.plot_show_list),
                        )
                    }
                }
                if (showCanvas) {
                    IconButton(onClick = { editing = true }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.plot_edit_layout))
                    }
                }
            }
        },
        floatingActionButton = {
            if (editing) {
                EditToolbar(
                    hasSelection = selectedZone != null,
                    onAddZone = { addingZone = true },
                    onAdjust = { adjusting = true },
                    onDetails = { selectedZone?.let { onEditZone(it.id) } },
                    onDone = ::finishEditing,
                )
            } else if (loadedZones != null) {
                AddMenu(onAdd)
            }
        },
        floatingActionButtonPosition = if (editing) FabPosition.Center else FabPosition.End,
    ) { padding ->
        when {
            loadedZones == null || loadedPlants == null -> Unit
            !hasZones && loadedPlants.isEmpty() -> EmptyPlot(padding, onAddZone = { addingZone = true }, onAddPlant = { onAdd(AddAction.PLANT) })
            showCanvas -> Box(Modifier.padding(padding).fillMaxSize()) {
                PlotCanvas(
                    zones = loadedZones,
                    editing = editing,
                    selectedId = selectedId,
                    onTapZone = { id -> if (editing) selectedId = id else onOpenZone(id) },
                    onChangeZone = { id, rect -> scope.launch { garden.moveZone(id, rect) } },
                )
                if (editing && selectedZone == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                    ) {
                        Text(stringResource(R.string.plot_edit_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                    }
                }
            }
            else -> PlotList(loadedZones, loadedPlants, padding, onOpenZone, onOpenPlant, onAddZone = { addingZone = true })
        }
    }

    if (addingZone) {
        AddZoneDialog(
            onDismiss = { addingZone = false },
            onAdd = { name, kind ->
                addingZone = false
                scope.launch {
                    val zone = garden.addZone(name, kind)
                    listMode = false
                    editing = true
                    selectedId = zone.id
                }
            },
        )
    }
    if (adjusting && selectedZone != null) {
        AdjustZoneSheet(
            zone = selectedZone,
            onDismiss = { adjusting = false },
            onAdjust = { change -> scope.launch { garden.adjustZone(selectedZone.id, change) } },
        )
    }
}

@Composable
private fun EmptyPlot(padding: PaddingValues, onAddZone: () -> Unit, onAddPlant: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
        item {
            EmptyState(
                icon = R.drawable.ic_yard,
                title = stringResource(R.string.plot_empty_title),
                body = stringResource(R.string.plot_empty_body),
            )
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = onAddZone) { Text(stringResource(R.string.plot_add_zone)) }
                OutlinedButton(onClick = onAddPlant) { Text(stringResource(R.string.add_plant_by_name)) }
            }
        }
    }
}

@Composable
private fun PlotList(
    zones: List<ZoneView>,
    plants: List<PlantView>,
    padding: PaddingValues,
    onOpenZone: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
    onAddZone: () -> Unit,
) {
    val unzoned = plants.filter { it.plant.zoneId == null && it.isGrowing }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (zones.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.plot_zoned_empty_body), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onAddZone) { Text(stringResource(R.string.plot_add_zone)) }
                }
            }
        }
        items(zones, key = { it.zone.id }) { view ->
            ZoneCard(view, onClick = { onOpenZone(view.zone.id) }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (unzoned.isNotEmpty()) {
            item { SectionHeading(stringResource(R.string.plot_unzoned)) }
            items(unzoned, key = { it.plant.id }) { view -> PlantRow(view, onClick = { onOpenPlant(view.plant.id) }) }
        }
    }
}

/** The floating toolbar shown while the layout is being edited. */
@Composable
private fun EditToolbar(hasSelection: Boolean, onAddZone: () -> Unit, onAdjust: () -> Unit, onDetails: () -> Unit, onDone: () -> Unit) {
    HorizontalFloatingToolbar(expanded = true) {
        IconButton(onClick = onAddZone) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.plot_add_zone))
        }
        IconButton(onClick = onAdjust, enabled = hasSelection) {
            Icon(painterResource(R.drawable.ic_open_with), contentDescription = stringResource(R.string.plot_adjust))
        }
        IconButton(onClick = onDetails, enabled = hasSelection) {
            Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.plot_details))
        }
        TextButton(onClick = onDone) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Text(stringResource(R.string.plot_done))
            }
        }
    }
}

/** The add menu: a floating action button that opens into the ways of adding to the garden. */
@Composable
private fun AddMenu(onAdd: (AddAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    val openLabel = stringResource(R.string.add_menu_open)
    val closeLabel = stringResource(R.string.add_menu_close)
    val expandedState = stringResource(R.string.add_menu_expanded)
    val collapsedState = stringResource(R.string.add_menu_collapsed)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    stateDescription = if (expanded) expandedState else collapsedState
                    contentDescription = if (expanded) closeLabel else openLabel
                },
                checked = expanded,
                onCheckedChange = { expanded = !expanded },
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) R.drawable.ic_close else R.drawable.ic_add } }
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        AddAction.entries.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onAdd(action)
                },
                icon = { Icon(painterResource(action.icon), contentDescription = null) },
                text = { Text(stringResource(action.label)) },
            )
        }
    }
}
