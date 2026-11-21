package io.github.wkoller.pollenplot.ui.plot

import io.github.wkoller.pollenplot.ui.components.checkedRowColors
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import io.github.wkoller.pollenplot.ui.components.SegmentedGroup
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.PlotLayer
import io.github.wkoller.pollenplot.data.prefs.NorthSide
import io.github.wkoller.pollenplot.domain.garden.Corner
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import io.github.wkoller.pollenplot.data.prefs.PlotBackground
import io.github.wkoller.pollenplot.domain.garden.StructureShape
import io.github.wkoller.pollenplot.domain.garden.StructureKind
import io.github.wkoller.pollenplot.domain.garden.Structure
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import io.github.wkoller.pollenplot.ui.components.ChoiceChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label

/** Asks for a new zone's name and kind. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddZoneDialog(onDismiss: () -> Unit, onAdd: (String, ZoneKind) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ZoneKind.GARDEN_BED) }
    var showError by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.zone_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.zone_name_label)) },
                    placeholder = { Text(stringResource(R.string.zone_name_example)) },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text(stringResource(R.string.zone_name_needed)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.zone_kind_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    ZoneKind.entries.forEach { k ->
                        ChoiceChip(
                            selected = kind == k,
                            onClick = { kind = k },
                            label = { Text(stringResource(k.label)) },
                            leadingIcon = { Icon(painterResource(k.icon), contentDescription = null, modifier = Modifier.size(IconSize.small)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isBlank()) showError = true else onAdd(name, kind) }) {
                Text(stringResource(R.string.zone_add_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Moves and resizes the chosen zone or structure with buttons, one 50 cm step at a time, for anyone who cannot drag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustZoneSheet(name: String, rect: ZoneRect, onDismiss: () -> Unit, onAdjust: ((ZoneRect) -> ZoneRect) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Text(stringResource(R.string.adjust_title, name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.adjust_move), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                ArrowButton(R.drawable.ic_arrow_back, R.string.move_left) { onAdjust { PlotLayout.nudged(it, -1, 0) } }
                ArrowButton(R.drawable.ic_arrow_upward, R.string.move_up) { onAdjust { PlotLayout.nudged(it, 0, -1) } }
                ArrowButton(R.drawable.ic_arrow_downward, R.string.move_down) { onAdjust { PlotLayout.nudged(it, 0, 1) } }
                ArrowButton(R.drawable.ic_arrow_forward, R.string.move_right) { onAdjust { PlotLayout.nudged(it, 1, 0) } }
            }
            Stepper(
                label = stringResource(R.string.adjust_width, UiFormat.metresFromCm(rect.widthCm)),
                less = stringResource(R.string.narrower),
                more = stringResource(R.string.wider),
                onLess = { onAdjust { PlotLayout.resizedBySteps(it, -1, 0) } },
                onMore = { onAdjust { PlotLayout.resizedBySteps(it, 1, 0) } },
            )
            Stepper(
                label = stringResource(R.string.adjust_height, UiFormat.metresFromCm(rect.heightCm)),
                less = stringResource(R.string.shorter),
                more = stringResource(R.string.longer),
                onLess = { onAdjust { PlotLayout.resizedBySteps(it, 0, -1) } },
                onMore = { onAdjust { PlotLayout.resizedBySteps(it, 0, 1) } },
            )
            // The right and bottom edges move above; these move the left and top edges, for a zone that should grow the other way.
            Stepper(
                label = stringResource(R.string.adjust_left_edge),
                less = stringResource(R.string.narrower),
                more = stringResource(R.string.wider),
                onLess = { onAdjust { PlotLayout.resizedFrom(it, Corner.TOP_LEFT, PlotLayout.GRID_CM, 0, snap = true) } },
                onMore = { onAdjust { PlotLayout.resizedFrom(it, Corner.TOP_LEFT, -PlotLayout.GRID_CM, 0, snap = true) } },
            )
            Stepper(
                label = stringResource(R.string.adjust_top_edge),
                less = stringResource(R.string.shorter),
                more = stringResource(R.string.longer),
                onLess = { onAdjust { PlotLayout.resizedFrom(it, Corner.TOP_LEFT, 0, PlotLayout.GRID_CM, snap = true) } },
                onMore = { onAdjust { PlotLayout.resizedFrom(it, Corner.TOP_LEFT, 0, -PlotLayout.GRID_CM, snap = true) } },
            )
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.plot_done)) }
        }
    }
}

@Composable
private fun ArrowButton(@DrawableRes icon: Int, label: Int, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
        Icon(painterResource(icon), contentDescription = stringResource(label))
    }
}

@Composable
private fun Stepper(label: String, less: String, more: String, onLess: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = onLess) { Text(less) }
        FilledTonalButton(onClick = onMore) { Text(more) }
    }
}

/** Asks what kind of structure to add. Its name may be left empty, and is then the kind's own, such as House. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddStructureDialog(onDismiss: () -> Unit, onAdd: (String, StructureKind) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(StructureKind.HOUSE) }
    val kindName = stringResource(kind.label)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.structure_add_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Text(stringResource(R.string.structure_add_body), style = MaterialTheme.typography.bodyMedium)
                StructureKindChips(kind) { kind = it }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.structure_name_label)) },
                    placeholder = { Text(kindName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name.ifBlank { kindName }, kind) }) { Text(stringResource(R.string.structure_add_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A structure's name, kind and shape, with a button to delete it. */
@Composable
fun StructureDialog(structure: Structure, onDismiss: () -> Unit, onSave: (Structure) -> Unit, onDelete: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(structure.name) }
    var kind by rememberSaveable { mutableStateOf(structure.kind) }
    var shape by rememberSaveable { mutableStateOf(structure.shape) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.structure_details_title, structure.name)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.structure_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                StructureKindChips(kind) { kind = it }
                ChipGroup(
                    title = stringResource(R.string.structure_shape_label),
                    options = StructureShape.entries,
                    selected = shape,
                    label = { stringResource(if (it == StructureShape.OVAL) R.string.structure_shape_oval else R.string.structure_shape_rectangle) },
                    onChoose = { shape = it },
                )
                OutlinedButton(onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null, modifier = Modifier.padding(end = Spacing.s).size(IconSize.small))
                    Text(stringResource(R.string.structure_delete))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(structure.copy(name = name.ifBlank { structure.name }, kind = kind, shape = shape)) }) { Text(stringResource(R.string.structure_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructureKindChips(kind: StructureKind, onChoose: (StructureKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(stringResource(R.string.structure_kind_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            StructureKind.entries.forEach { k ->
                ChoiceChip(
                    selected = kind == k,
                    onClick = { onChoose(k) },
                    label = { Text(stringResource(k.label)) },
                    leadingIcon = { Icon(painterResource(k.icon), contentDescription = null, modifier = Modifier.size(IconSize.small)) },
                )
            }
        }
    }
}

/** The Plot's look: the colour of the ground, and whether the grid shows while the layout is only looked at. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotAppearanceSheet(
    background: PlotBackground,
    gridInView: Boolean,
    onDismiss: () -> Unit,
    onBackground: (PlotBackground) -> Unit,
    onGrid: (Boolean) -> Unit,
    north: NorthSide = NorthSide.TOP,
    onNorth: (NorthSide) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Text(stringResource(R.string.plot_appearance), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.xl).semantics { heading() })
            Column(Modifier.padding(horizontal = Spacing.xl)) {
                ChipGroup(
                    title = stringResource(R.string.plot_background),
                    options = PlotBackground.entries,
                    selected = background,
                    label = {
                        stringResource(
                            when (it) {
                                PlotBackground.THEME -> R.string.plot_background_theme
                                PlotBackground.GRASS -> R.string.plot_background_grass
                                PlotBackground.SOIL -> R.string.plot_background_soil
                                PlotBackground.SAND -> R.string.plot_background_sand
                                PlotBackground.PAVING -> R.string.plot_background_paving
                            },
                        )
                    },
                    onChoose = onBackground,
                )
            }
            ListItem(
                checked = gridInView,
                colors = checkedRowColors(),
                onCheckedChange = onGrid,
                // The sheet's words start 24 dp in, and a list item insets its own by 16.
                modifier = Modifier.padding(horizontal = Spacing.s),
                leadingContent = { Icon(painterResource(R.drawable.ic_grid_on), contentDescription = null) },
                supportingContent = { Text(stringResource(R.string.plot_grid_body)) },
                trailingContent = { Switch(checked = gridInView, onCheckedChange = null) },
            ) { Text(stringResource(R.string.plot_grid)) }
            Column(Modifier.padding(horizontal = Spacing.xl)) {
                ChipGroup(
                    title = stringResource(R.string.plot_north),
                    options = NorthSide.entries,
                    selected = north,
                    label = {
                        stringResource(
                            when (it) {
                                NorthSide.TOP -> R.string.plot_north_top
                                NorthSide.RIGHT -> R.string.plot_north_right
                                NorthSide.BOTTOM -> R.string.plot_north_bottom
                                NorthSide.LEFT -> R.string.plot_north_left
                            },
                        )
                    },
                    onChoose = onNorth,
                )
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl)) { Text(stringResource(R.string.plot_done)) }
        }
    }
}

/**
 * The Plot's layers, which lie over the zones as a map's layers lie over its streets: one at a time, or none. "In
 * flower later" has a month to look ahead to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlotLayersSheet(
    layer: PlotLayer?,
    laterMonths: Int,
    onDismiss: () -> Unit,
    onLayer: (PlotLayer?) -> Unit,
    onLaterMonths: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            Text(stringResource(R.string.plot_layers_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.xl).semantics { heading() })
            // The sheet's words start 24 dp in, and a group's tiles 16 dp in with their own inset inside.
            SegmentedGroup(Modifier.padding(horizontal = Spacing.s).selectableGroup()) {
                listOf<PlotLayer?>(null, PlotLayer.SUN, PlotLayer.BLOOM_NOW, PlotLayer.BLOOM_LATER).forEach { choice ->
                    row { segment ->
                        ListItem(
                            selected = layer == choice,
                            onClick = { onLayer(choice) },
                            leadingContent = { RadioButton(selected = layer == choice, onClick = null) },
                            trailingContent = { Icon(painterResource(choice.icon), contentDescription = null) },
                            supportingContent = choice.body?.let { body -> { Text(stringResource(body)) } },
                            shapes = segment.shapes,
                            colors = segment.colors,
                        ) { Text(stringResource(choice.label)) }
                    }
                }
            }
            if (layer == PlotLayer.BLOOM_LATER) {
                Column(Modifier.padding(horizontal = Spacing.xl)) {
                    ChipGroup(
                        title = stringResource(R.string.plot_layer_month),
                        options = (1..Settings.BLOOM_LATER_MOST).toList(),
                        selected = laterMonths,
                        label = { months -> if (months == 1) stringResource(R.string.plot_layer_month_next) else stringResource(R.string.plot_layer_month_ahead, months) },
                        onChoose = onLaterMonths,
                    )
                }
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl)) { Text(stringResource(R.string.plot_done)) }
        }
    }
}

private val PlotLayer?.label: Int
    get() = when (this) {
        null -> R.string.plot_layer_none
        PlotLayer.SUN -> R.string.plot_layer_sun
        PlotLayer.BLOOM_NOW -> R.string.plot_layer_bloom_now
        PlotLayer.BLOOM_LATER -> R.string.plot_layer_bloom_later
    }

private val PlotLayer?.body: Int?
    get() = when (this) {
        null -> R.string.plot_layer_none_body
        PlotLayer.SUN -> R.string.plot_sun_overlay_body
        PlotLayer.BLOOM_NOW -> R.string.plot_layer_bloom_now_body
        PlotLayer.BLOOM_LATER -> R.string.plot_layer_bloom_later_body
    }

private val PlotLayer?.icon: Int
    get() = when (this) {
        null -> R.drawable.ic_layers
        PlotLayer.SUN -> R.drawable.ic_sunny
        PlotLayer.BLOOM_NOW -> R.drawable.ic_local_florist
        PlotLayer.BLOOM_LATER -> R.drawable.ic_calendar_month
    }
