package io.github.wkoller.pollenplot.ui.plot

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.TodayViews
import io.github.wkoller.pollenplot.data.TodayState
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.ui.components.AddMenu
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.nav.AddAction
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.plants.ZoneCard
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf

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
    onReview: () -> Unit,
    onWeeds: () -> Unit,
    today: TodayViews? = null,
    onOpenWater: () -> Unit = {},
    onOpenBees: () -> Unit = {},
    onOpenGuide: (String) -> Unit = {},
) {
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = null)
    val todayState by remember(today) { today?.state ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
    val waiting by garden.pendingReviewCount.collectAsStateWithLifecycle(initialValue = 0)
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
    val weeds = loadedPlants.orEmpty().count { it.isGrowing && it.weed.isWeed }

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
                // Shown only while plants found in photos are waiting, with how many.
                if (waiting > 0) {
                    val description = pluralStringResource(R.plurals.review_waiting, waiting, waiting)
                    IconButton(onClick = onReview, modifier = Modifier.semantics { contentDescription = description }) {
                        // The number is in the button's description, so the badge is not read out twice.
                        BadgedBox(badge = { Badge { Text(waiting.toString()) } }, modifier = Modifier.clearAndSetSemantics { }) {
                            Icon(painterResource(R.drawable.ic_rate_review), contentDescription = null)
                        }
                    }
                }
                // Shown only while the garden has weeds in it, with how many.
                if (weeds > 0) {
                    val description = pluralStringResource(R.plurals.plot_weeds_found, weeds, weeds)
                    IconButton(onClick = onWeeds, modifier = Modifier.semantics { contentDescription = description }) {
                        BadgedBox(badge = { Badge { Text(weeds.toString()) } }, modifier = Modifier.clearAndSetSemantics { }) {
                            Icon(painterResource(R.drawable.ic_grass), contentDescription = null)
                        }
                    }
                }
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
                AddMenu(AddAction.PLOT, onAdd)
            }
        },
        floatingActionButtonPosition = if (editing) FabPosition.Center else FabPosition.End,
    ) { padding ->
        when {
            loadedZones == null || loadedPlants == null -> Unit
            !hasZones && loadedPlants.isEmpty() -> EmptyPlot(padding, onAddZone = { addingZone = true }, onAddPlant = { onAdd(AddAction.PLANT) }, onWeeds = onWeeds)
            showCanvas -> BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                // With large text the card could fill the screen and push the canvas away, so it takes part of the
                // height at most and scrolls inside.
                val cardLimit = maxHeight * TODAY_SHARE
                Column(Modifier.fillMaxSize()) {
                    if (!editing) {
                        todayState?.let {
                            TodayOnPlot(it, onOpenWater, onOpenBees, onOpenGuide, onReview, onWeeds, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), cardLimit)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
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
                }
            }
            else -> PlotList(
                loadedZones, loadedPlants, padding, onOpenZone, onOpenPlant, onAddZone = { addingZone = true }, onWeeds = onWeeds,
                header = { todayState?.let { TodayOnPlot(it, onOpenWater, onOpenBees, onOpenGuide, onReview, onWeeds, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) } },
            )
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
private fun EmptyPlot(padding: PaddingValues, onAddZone: () -> Unit, onAddPlant: () -> Unit, onWeeds: () -> Unit) {
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
                TextButton(onClick = onWeeds) { Text(stringResource(R.string.plot_weeds_near)) }
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
    onWeeds: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    val unzoned = plants.filter { it.plant.zoneId == null && it.isGrowing }
    val weeds = plants.count { it.isGrowing && it.weed.isWeed }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "today") { header() }
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
        item {
            ListItem(
                onClick = onWeeds,
                leadingContent = { Icon(painterResource(R.drawable.ic_grass), contentDescription = null) },
                supportingContent = {
                    Text(if (weeds > 0) pluralStringResource(R.plurals.plot_weeds_found, weeds, weeds) else stringResource(R.string.plot_weeds_body))
                },
            ) { Text(stringResource(R.string.weeds_title)) }
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

/** The Today card as the Plot shows it, with a hot day opening the guide's page on cooling a hive. */
@Composable
private fun TodayOnPlot(
    state: TodayState,
    onWater: () -> Unit,
    onBees: () -> Unit,
    onGuide: (String) -> Unit,
    onReview: () -> Unit,
    onWeeds: () -> Unit,
    modifier: Modifier,
    maxHeight: Dp = Dp.Unspecified,
) {
    TodayCard(state, modifier, maxHeight, onWater = onWater, onBees = onBees, onHeat = { onGuide(HEAT_GUIDE) }, onReview = onReview, onWeeds = onWeeds)
}

/** The most of the Plot's height the Today card takes above the canvas. */
private const val TODAY_SHARE = 0.4f

/** The guide page on caring for a stingless bee hive, which says how to cool one. */
private const val HEAT_GUIDE = "native/hive-care"
