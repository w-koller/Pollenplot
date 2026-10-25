package io.github.wkoller.pollenplot.ui.plot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.ui.components.BadgeItem
import io.github.wkoller.pollenplot.ui.components.IconBadges
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.tone
import kotlin.math.min
import kotlin.math.roundToInt

/** The zoom limits, in density-independent pixels per centimetre of yard. */
private const val MIN_DP_PER_CM = 0.04f
private const val MAX_DP_PER_CM = 3f

/**
 * The backyard canvas: zones laid out in centimetres of real yard. Pinch to zoom and drag to pan. In edit mode the
 * chosen zone can be dragged, and its corner handle resizes it; the screen's toolbar does the same with buttons.
 */
@Composable
fun PlotCanvas(
    zones: List<ZoneView>,
    editing: Boolean,
    selectedId: String?,
    onTapZone: (String) -> Unit,
    onChangeZone: (String, ZoneRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val density = LocalDensity.current.density
        val viewWidth = constraints.maxWidth.toFloat()
        val viewHeight = constraints.maxHeight.toFloat()
        val minScale = MIN_DP_PER_CM * density
        val maxScale = MAX_DP_PER_CM * density
        // The view transform: a point at (x, y) cm is drawn at (offset + cm × scale) pixels.
        var scale by rememberSaveable { mutableFloatStateOf(0f) }
        var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
        var offsetY by rememberSaveable { mutableFloatStateOf(0f) }
        val hasZones = zones.isNotEmpty()

        // Fit the whole plot on screen the first time it has zones.
        LaunchedEffect(hasZones, viewWidth, viewHeight) {
            val bounds = PlotLayout.bounds(zones.map { it.zone.rect }) ?: return@LaunchedEffect
            if (scale != 0f) return@LaunchedEffect
            val margin = 24 * density
            scale = min((viewWidth - 2 * margin) / bounds.widthCm, (viewHeight - 2 * margin) / bounds.heightCm).coerceIn(minScale, maxScale)
            offsetX = (viewWidth - bounds.widthCm * scale) / 2 - bounds.xCm * scale
            offsetY = margin - bounds.yCm * scale
        }
        if (scale == 0f) return@BoxWithConstraints

        val gridColour = MaterialTheme.colorScheme.outlineVariant
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        val factor = newScale / scale
                        offsetX = (offsetX - centroid.x) * factor + centroid.x + pan.x
                        offsetY = (offsetY - centroid.y) * factor + centroid.y + pan.y
                        scale = newScale
                    }
                }
                .drawBehind { if (editing) drawGrid(scale, offsetX, offsetY, gridColour) },
        ) {
            zones.forEach { view ->
                key(view.zone.id) {
                    ZoneTile(
                        view = view,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        editing = editing,
                        selected = editing && view.zone.id == selectedId,
                        onTap = { onTapZone(view.zone.id) },
                        onChange = { onChangeZone(view.zone.id, it) },
                    )
                }
            }
        }
    }
}

/** Grid lines every metre, behind the zones while the layout is being edited. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(scale: Float, offsetX: Float, offsetY: Float, colour: Color) {
    val step = 100 * scale
    if (step < 8f) return
    var x = offsetX % step
    if (x < 0) x += step
    while (x < size.width) {
        drawLine(colour, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += step
    }
    var y = offsetY % step
    if (y < 0) y += step
    while (y < size.height) {
        drawLine(colour, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += step
    }
}

@Composable
private fun ZoneTile(
    view: ZoneView,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    editing: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
    onChange: (ZoneRect) -> Unit,
) {
    val rect = view.zone.rect
    val density = LocalDensity.current
    // How far the user has dragged the zone or its corner, in pixels, until the change is saved.
    var drag by remember(rect) { mutableStateOf(Offset.Zero) }
    var grow by remember(rect) { mutableStateOf(Offset.Zero) }
    val minSide = PlotLayout.MIN_SIDE_CM * scale
    val left = offsetX + rect.xCm * scale + drag.x
    val top = offsetY + rect.yCm * scale + drag.y
    val width = (rect.widthCm * scale + grow.x).coerceAtLeast(minSide)
    val height = (rect.heightCm * scale + grow.y).coerceAtLeast(minSide)
    val widthDp = with(density) { width.toDp() }
    val heightDp = with(density) { height.toDp() }
    val (container, content) = kindColours(view.zone.kind)
    val description = stringResource(
        R.string.zone_tile_description,
        view.zone.name,
        stringResource(view.zone.kind.label),
        pluralStringResource(R.plurals.plant_count, view.plants.size, view.plants.size),
    )

    Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(widthDp, heightDp)) {
        Surface(
            onClick = onTap,
            shape = RoundedCornerShape(16.dp),
            color = container,
            contentColor = content,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = description
                    this.selected = selected
                }
                .then(
                    if (selected) {
                        Modifier.pointerInput(rect, scale) {
                            detectDragGestures(
                                onDragEnd = {
                                    onChange(rect.copy(xCm = rect.xCm + (drag.x / scale).roundToInt(), yCm = rect.yCm + (drag.y / scale).roundToInt()))
                                    drag = Offset.Zero
                                },
                                onDragCancel = { drag = Offset.Zero },
                            ) { change, amount ->
                                change.consume()
                                drag += amount
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            TileContent(view, widthDp.value, heightDp.value)
        }
        if (selected) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(10.dp, 10.dp)
                    .size(28.dp)
                    .pointerInput(rect, scale) {
                        detectDragGestures(
                            onDragEnd = {
                                onChange(
                                    rect.copy(
                                        widthCm = rect.widthCm + (grow.x / scale).roundToInt(),
                                        heightCm = rect.heightCm + (grow.y / scale).roundToInt(),
                                    ),
                                )
                                grow = Offset.Zero
                            },
                            onDragCancel = { grow = Offset.Zero },
                        ) { change, amount ->
                            change.consume()
                            grow += amount
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_open_in_full), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** What fits on a tile depends on its size on screen: just the name when zoomed out, then plants, then badges. */
@Composable
private fun TileContent(view: ZoneView, widthDp: Float, heightDp: Float) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (widthDp >= 72) Icon(painterResource(view.zone.kind.icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(view.zone.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (heightDp >= 76 && view.plants.isNotEmpty()) {
            val fit = ((widthDp - 16) / 34).toInt().coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // A light disc keeps each plant visible on any tile colour.
                view.plants.take(fit).forEach {
                    PlantAvatar(it.species?.form, 30.dp, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (heightDp >= 116 && view.badges.isNotEmpty()) {
            IconBadges(view.badges.map { BadgeItem(it.icon, stringResource(it.label), it.tone) }, size = 24.dp)
        }
    }
}

@Composable
private fun kindColours(kind: ZoneKind): Pair<Color, Color> {
    val s = MaterialTheme.colorScheme
    return when (kind) {
        ZoneKind.GARDEN_BED -> s.primaryContainer to s.onPrimaryContainer
        ZoneKind.VEG_PATCH, ZoneKind.ORCHARD, ZoneKind.HIVE_CORNER -> s.secondaryContainer to s.onSecondaryContainer
        ZoneKind.POND -> s.tertiaryContainer to s.onTertiaryContainer
        ZoneKind.LAWN, ZoneKind.POTS, ZoneKind.OTHER -> s.surfaceContainerHighest to s.onSurface
    }
}
