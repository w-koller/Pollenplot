package io.github.wkoller.pollenplot.ui.plot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import io.github.wkoller.pollenplot.ui.UiFormat
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.WaterState
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.garden.PlotLayout
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import io.github.wkoller.pollenplot.domain.irrigation.PlannedWatering
import io.github.wkoller.pollenplot.ui.components.BadgeItem
import io.github.wkoller.pollenplot.ui.components.IconBadges
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.tone
import io.github.wkoller.pollenplot.ui.water.byHand
import io.github.wkoller.pollenplot.ui.water.icon
import io.github.wkoller.pollenplot.ui.water.nextWateringSentence
import io.github.wkoller.pollenplot.ui.water.nextWateringShort
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** The zoom limits, in density-independent pixels per centimetre of yard. */
private const val MIN_DP_PER_CM = 0.04f
private const val MAX_DP_PER_CM = 3f

/** Each zoom button press scales the view by this much. */
private const val ZOOM_STEP = 1.5f

/** A zone dragged this close to the canvas's edge pans the view, in density-independent pixels. */
private const val EDGE_DP = 48f

/** How fast the view pans at the very edge, in density-independent pixels a second. */
private const val EDGE_SPEED_DP = 600f

/** What is being dragged: a zone's body, or its corner handle. */
private enum class DragPart { MOVE, RESIZE }

/**
 * A drag in progress on one zone, in centimetres of yard so it stays under the finger while the view pans, with where
 * the finger is on the canvas.
 */
private data class ZoneDrag(val id: String, val part: DragPart, val cm: Offset, val finger: Offset)

/**
 * The backyard canvas: zones laid out in centimetres of real yard on a grid with no edges. Pinch to zoom and drag to
 * pan, or use the buttons in the corner. Holding a zone shows a preview of it until the finger lifts. In edit mode any
 * zone can be dragged, and the chosen zone's corner handle resizes it, with the place it will snap to drawn as it
 * moves; the screen's toolbar does the same with buttons.
 */
@Composable
fun PlotCanvas(
    zones: List<ZoneView>,
    editing: Boolean,
    selectedId: String?,
    onTapZone: (String) -> Unit,
    onChangeZone: (String, ZoneRect) -> Unit,
    modifier: Modifier = Modifier,
    water: WaterState? = null,
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
        var drag by remember { mutableStateOf<ZoneDrag?>(null) }
        // Where a dropped zone landed, shown until the saved zone comes back, so it does not jump back for a frame.
        var landed by remember { mutableStateOf<Pair<String, ZoneRect>?>(null) }
        LaunchedEffect(zones) { landed = null }
        // The zone being previewed, and whether a finger holds the preview open rather than a screen reader.
        var previewId by remember { mutableStateOf<String?>(null) }
        var previewHeld by remember { mutableStateOf(false) }
        var fingerDown by remember { mutableStateOf(false) }
        val hasZones = zones.isNotEmpty()

        fun fit() {
            val bounds = PlotLayout.bounds(zones.map { it.zone.rect }) ?: return
            val margin = 24 * density
            scale = min((viewWidth - 2 * margin) / bounds.widthCm, (viewHeight - 2 * margin) / bounds.heightCm).coerceIn(minScale, maxScale)
            offsetX = (viewWidth - bounds.widthCm * scale) / 2 - bounds.xCm * scale
            offsetY = (viewHeight - bounds.heightCm * scale) / 2 - bounds.yCm * scale
        }

        fun zoomBy(factor: Float) {
            val newScale = (scale * factor).coerceIn(minScale, maxScale)
            val applied = newScale / scale
            offsetX = (offsetX - viewWidth / 2) * applied + viewWidth / 2
            offsetY = (offsetY - viewHeight / 2) * applied + viewHeight / 2
            scale = newScale
        }

        // Fit the whole plot on screen the first time it has zones.
        LaunchedEffect(hasZones, viewWidth, viewHeight) {
            if (hasZones && scale == 0f) fit()
        }
        // A zone dragged near an edge pans the view towards it, and the drag keeps up so the zone stays under the finger.
        val dragging = drag != null
        LaunchedEffect(dragging) {
            if (!dragging) return@LaunchedEffect
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val seconds = (now - last) / 1e9f
                last = now
                val current = drag ?: break
                val edge = EDGE_DP * density
                val speed = EDGE_SPEED_DP * density * seconds
                val dx = edgePush(current.finger.x, viewWidth, edge) * speed
                val dy = edgePush(current.finger.y, viewHeight, edge) * speed
                if (dx != 0f || dy != 0f) {
                    offsetX -= dx
                    offsetY -= dy
                    drag = current.copy(cm = current.cm + Offset(dx / scale, dy / scale))
                }
            }
        }
        if (scale == 0f) return@BoxWithConstraints

        val gridColour = MaterialTheme.colorScheme.outlineVariant
        val majorColour = MaterialTheme.colorScheme.outline
        val extentColour = MaterialTheme.colorScheme.primary
        val bounds = PlotLayout.bounds(zones.map { it.zone.rect })
        val currentZones by rememberUpdatedState(zones)
        Box(
            Modifier
                .fillMaxSize()
                // Watches every touch before the zones do, so a preview closes when the finger lifts and follows it
                // from zone to zone.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            fingerDown = event.changes.any { it.pressed }
                            if (previewId == null || !previewHeld) continue
                            if (!fingerDown) {
                                previewId = null
                            } else {
                                val at = event.changes.first().position
                                zoneAt(currentZones, (at.x - offsetX) / scale, (at.y - offsetY) / scale)?.let { previewId = it }
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                        val factor = newScale / scale
                        offsetX = (offsetX - centroid.x) * factor + centroid.x + pan.x
                        offsetY = (offsetY - centroid.y) * factor + centroid.y + pan.y
                        scale = newScale
                    }
                }
                .drawBehind {
                    drawGrid(scale, offsetX, offsetY, gridColour, majorColour)
                    if (editing && bounds != null) drawExtent(bounds, scale, offsetX, offsetY, extentColour)
                },
        ) {
            zones.forEach { view ->
                key(view.zone.id) {
                    val id = view.zone.id
                    val shownView = landed?.takeIf { it.first == id }?.let { view.copy(zone = view.zone.copy(rect = it.second)) } ?: view
                    ZoneTile(
                        view = shownView,
                        scale = scale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        editing = editing,
                        selected = editing && id == selectedId,
                        drag = drag?.takeIf { it.id == id },
                        onTap = { onTapZone(id) },
                        onPreview = {
                            previewId = id
                            previewHeld = fingerDown
                        },
                        onDrag = { part, amount, finger ->
                            // Dragging a zone that is not chosen yet chooses it first.
                            if (drag == null && id != selectedId) onTapZone(id)
                            val cm = (drag?.takeIf { it.id == id && it.part == part }?.cm ?: Offset.Zero) + amount / scale
                            drag = ZoneDrag(id, part, cm, finger)
                        },
                        onDragEnd = {
                            drag?.takeIf { it.id == id }?.let { current ->
                                val moved = dragged(view.zone.rect, current)
                                landed = id to PlotLayout.snapped(moved)
                                onChangeZone(id, moved)
                            }
                            drag = null
                        },
                        onNudge = { right, down -> onChangeZone(id, PlotLayout.nudged(view.zone.rect, right, down)) },
                        water = water,
                    )
                }
            }
            drag?.let { current ->
                zones.firstOrNull { it.zone.id == current.id }?.let { view ->
                    val landing = PlotLayout.snapped(dragged(view.zone.rect, current))
                    DragReadout(view.zone.rect, landing, current.part, Modifier.align(Alignment.TopCenter).padding(12.dp))
                }
            }
            CanvasControls(
                onFit = ::fit,
                onZoomIn = { zoomBy(ZOOM_STEP) },
                onZoomOut = { zoomBy(1 / ZOOM_STEP) },
                modifier = Modifier.align(if (editing) Alignment.TopStart else Alignment.BottomStart).padding(12.dp),
            )
            previewId?.let { id ->
                zones.firstOrNull { it.zone.id == id }?.let { view ->
                    val rect = view.zone.rect
                    val tile = Rect(offsetX + rect.xCm * scale, offsetY + rect.yCm * scale, offsetX + rect.rightCm * scale, offsetY + rect.bottomCm * scale)
                    ZonePreview(view, tile, viewWidth, viewHeight, water, onDismiss = { previewId = null })
                }
            }
        }
    }
}

/** The zone whose rectangle holds the point at [xCm], [yCm], the one drawn on top when they overlap. */
private fun zoneAt(zones: List<ZoneView>, xCm: Float, yCm: Float): String? =
    zones.lastOrNull { val r = it.zone.rect; xCm >= r.xCm && xCm < r.rightCm && yCm >= r.yCm && yCm < r.bottomCm }?.zone?.id

/** How hard a finger at [position] along a side of [length] pushes the view: from -1 at one edge to 1 at the other. */
private fun edgePush(position: Float, length: Float, edge: Float): Float = when {
    position < edge -> -((edge - position) / edge).coerceAtMost(1f)
    position > length - edge -> ((position - (length - edge)) / edge).coerceAtMost(1f)
    else -> 0f
}

/** The zone's rectangle with a drag applied, before it is snapped to the grid. */
private fun dragged(rect: ZoneRect, drag: ZoneDrag): ZoneRect = when (drag.part) {
    DragPart.MOVE -> rect.copy(xCm = rect.xCm + drag.cm.x.roundToInt(), yCm = rect.yCm + drag.cm.y.roundToInt())
    DragPart.RESIZE -> rect.copy(
        widthCm = (rect.widthCm + drag.cm.x.roundToInt()).coerceAtLeast(PlotLayout.MIN_SIDE_CM),
        heightCm = (rect.heightCm + drag.cm.y.roundToInt()).coerceAtLeast(PlotLayout.MIN_SIDE_CM),
    )
}

/** Grid lines every metre, with a stronger line every five, so distances can be read at any zoom. */
private fun DrawScope.drawGrid(scale: Float, offsetX: Float, offsetY: Float, minor: Color, major: Color) {
    for (step in listOf(100, 500)) {
        val spacing = step * scale
        if (spacing < 8f) continue
        val colour = if (step == 100) minor else major
        val firstX = kotlin.math.floor(-offsetX / spacing).toInt()
        val firstY = kotlin.math.floor(-offsetY / spacing).toInt()
        var x = offsetX + firstX * spacing
        while (x < size.width) {
            drawLine(colour, Offset(x, 0f), Offset(x, size.height), strokeWidth = if (step == 100) 1f else 2f)
            x += spacing
        }
        var y = offsetY + firstY * spacing
        while (y < size.height) {
            drawLine(colour, Offset(0f, y), Offset(size.width, y), strokeWidth = if (step == 100) 1f else 2f)
            y += spacing
        }
    }
}

/** A dashed outline one grid step around every zone, the part of the yard laid out so far, while editing. */
private fun DrawScope.drawExtent(bounds: ZoneRect, scale: Float, offsetX: Float, offsetY: Float, colour: Color) {
    val pad = PlotLayout.GRID_CM
    drawRect(
        colour,
        topLeft = Offset(offsetX + (bounds.xCm - pad) * scale, offsetY + (bounds.yCm - pad) * scale),
        size = Size((bounds.widthCm + 2 * pad) * scale, (bounds.heightCm + 2 * pad) * scale),
        style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
    )
}

/** Zoom in, zoom out and the whole plot, for anyone who cannot pinch and for finding the zones again. */
@Composable
private fun CanvasControls(onFit: () -> Unit, onZoomIn: () -> Unit, onZoomOut: () -> Unit, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp, modifier = modifier) {
        Row {
            IconButton(onClick = onZoomOut) { Icon(painterResource(R.drawable.ic_zoom_out), contentDescription = stringResource(R.string.plot_zoom_out)) }
            IconButton(onClick = onZoomIn) { Icon(painterResource(R.drawable.ic_zoom_in), contentDescription = stringResource(R.string.plot_zoom_in)) }
            IconButton(onClick = onFit) { Icon(painterResource(R.drawable.ic_fit_screen), contentDescription = stringResource(R.string.plot_show_all)) }
        }
    }
}

/** Where a dragged zone will land: how far it moves, or its new size. */
@Composable
private fun DragReadout(from: ZoneRect, to: ZoneRect, part: DragPart, modifier: Modifier) {
    val text = when (part) {
        DragPart.RESIZE -> stringResource(R.string.plot_drag_size, UiFormat.metresFromCm(to.widthCm), UiFormat.metresFromCm(to.heightCm))
        DragPart.MOVE -> {
            val across = to.xCm - from.xCm
            val down = to.yCm - from.yCm
            listOfNotNull(
                across.takeIf { it != 0 }?.let { stringResource(if (it > 0) R.string.plot_drag_right else R.string.plot_drag_left, UiFormat.metresFromCm(abs(it))) },
                down.takeIf { it != 0 }?.let { stringResource(if (it > 0) R.string.plot_drag_down else R.string.plot_drag_up, UiFormat.metresFromCm(abs(it))) },
            ).joinToString(", ").ifEmpty { stringResource(R.string.plot_drag_none) }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = CircleShape,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoneTile(
    view: ZoneView,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    editing: Boolean,
    selected: Boolean,
    drag: ZoneDrag?,
    onTap: () -> Unit,
    onPreview: () -> Unit,
    onDrag: (DragPart, Offset, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onNudge: (right: Int, down: Int) -> Unit,
    water: WaterState?,
) {
    val rect = view.zone.rect
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val shown = drag?.let { dragged(rect, it) } ?: rect
    val left = offsetX + shown.xCm * scale
    val top = offsetY + shown.yCm * scale
    val widthDp = with(density) { (shown.widthCm * scale).toDp() }
    val heightDp = with(density) { (shown.heightCm * scale).toDp() }
    val (container, content) = kindColours(view.zone.kind)
    val kinds = view.sightings.map { it.key }.distinct().size
    val watering = water?.zones?.get(view.zone.id)
    val next = watering?.next
    val description = stringResource(
        R.string.zone_tile_description,
        view.zone.name,
        stringResource(view.zone.kind.label),
        pluralStringResource(R.plurals.plant_count, view.plants.size, view.plants.size),
    ) + (if (kinds > 0) ", " + pluralStringResource(R.plurals.zone_tile_wildlife, kinds, kinds) else "") +
        (next?.let { ". " + nextWateringSentence(it, water.today, water.window, watering.byHand) } ?: "")
    val moves = listOf(
        stringResource(R.string.move_left) to (-1 to 0),
        stringResource(R.string.move_up) to (0 to -1),
        stringResource(R.string.move_down) to (0 to 1),
        stringResource(R.string.move_right) to (1 to 0),
    )
    val previewLabel = stringResource(R.string.zone_preview)
    val shape = RoundedCornerShape(16.dp)
    // The view pans under a drag near the edge, so the gesture reads where the tile is now rather than restarting.
    val corner by rememberUpdatedState(Offset(left, top))
    val farCorner by rememberUpdatedState(Offset(left + shown.widthCm * scale, top + shown.heightCm * scale))
    val dragTo by rememberUpdatedState(onDrag)
    val dropped by rememberUpdatedState(onDragEnd)

    // Where the snapped zone will land, drawn under the moving one.
    if (drag != null) {
        val landing = PlotLayout.snapped(shown)
        Box(
            Modifier
                .offset { IntOffset((offsetX + landing.xCm * scale).roundToInt(), (offsetY + landing.yCm * scale).roundToInt()) }
                .size(with(density) { (landing.widthCm * scale).toDp() }, with(density) { (landing.heightCm * scale).toDp() })
                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape),
        )
    }
    Box(Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }.size(widthDp, heightDp)) {
        Surface(
            shape = shape,
            color = container,
            contentColor = content,
            border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .combinedClickable(
                    onClick = onTap,
                    onLongClickLabel = if (editing) null else previewLabel,
                    onLongClick = if (editing) {
                        null
                    } else {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPreview()
                        }
                    },
                )
                .semantics {
                    contentDescription = description
                    this.selected = selected
                    if (selected) customActions = moves.map { (label, step) -> CustomAccessibilityAction(label) { onNudge(step.first, step.second); true } }
                }
                .then(
                    if (editing) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(onDragEnd = { dropped() }, onDragCancel = { dropped() }) { change, amount ->
                                change.consume()
                                dragTo(DragPart.MOVE, amount, change.position + corner)
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            TileContent(view, kinds, next?.let { it to nextWateringShort(it, water.today, water.window) }, widthDp.value, heightDp.value)
        }
        if (selected) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(10.dp, 10.dp)
                    .size(28.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { dropped() }, onDragCancel = { dropped() }) { change, amount ->
                            change.consume()
                            // The handle sits on the tile's bottom right corner, which is about where the finger is.
                            dragTo(DragPart.RESIZE, amount, farCorner + change.position - Offset(size.width / 2f, size.height / 2f))
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

/**
 * What fits on a tile depends on its size on screen: just the name when zoomed out, then the next watering, then
 * plants, then badges, then how many [kinds] of animals have been seen there.
 */
@Composable
private fun TileContent(view: ZoneView, kinds: Int, next: Pair<PlannedWatering, String>?, widthDp: Float, heightDp: Float) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (widthDp >= 72) Icon(painterResource(view.zone.kind.icon), contentDescription = null, modifier = Modifier.size(16.dp))
            Text(view.zone.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // The next watering takes a line, so what comes after it needs that much more room.
        val watering = next?.takeIf { heightDp >= 56 }
        val room = if (watering != null) heightDp - WATERING_ROW_DP else heightDp
        if (watering != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(watering.first.icon()), contentDescription = null, modifier = Modifier.size(14.dp))
                Text(watering.second, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (room >= 76 && view.plants.isNotEmpty()) {
            val fit = ((widthDp - 16) / 34).toInt().coerceAtLeast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // A light disc keeps each plant visible on any tile colour.
                view.plants.take(fit).forEach {
                    PlantAvatar(it.species?.form, 30.dp, thumbnail = it.plant.thumbnail, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, contentColor = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (room >= 116 && view.badges.isNotEmpty()) {
            IconBadges(view.badges.map { BadgeItem(it.icon, stringResource(it.label), it.tone) }, size = 24.dp)
        }
        if (room >= 148 && kinds > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_raven), contentDescription = null, modifier = Modifier.size(16.dp))
                Text(pluralStringResource(R.plurals.zone_tile_wildlife, kinds, kinds), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The height of the next watering's line on a tile, with the space after it. */
private const val WATERING_ROW_DP = 22f

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
