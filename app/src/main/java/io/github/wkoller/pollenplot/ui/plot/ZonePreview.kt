package io.github.wkoller.pollenplot.ui.plot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.WaterState
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.ui.components.BadgeItem
import io.github.wkoller.pollenplot.ui.components.IconBadges
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.tone
import io.github.wkoller.pollenplot.ui.water.byHand
import io.github.wkoller.pollenplot.ui.water.nextWateringSentence
import kotlin.math.roundToInt

/** The most plants a preview lists before it says how many more there are. */
private const val PREVIEW_PLANTS = 6

/**
 * A preview of a zone held on the Plot, placed above its [tile] when there is room and below it otherwise, kept on
 * the canvas of [viewWidth] × [viewHeight] pixels. A screen reader opens it with the zone's "Preview zone" action, so
 * it has a button to close it as well.
 */
@Composable
fun ZonePreview(view: ZoneView, tile: Rect, viewWidth: Float, viewHeight: Float, water: WaterState?, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val gap = with(density) { 8.dp.toPx() }
    val margin = with(density) { 16.dp.toPx() }
    val width = with(density) { minOf(320.dp.toPx(), viewWidth - 2 * margin) }
    var height by remember { mutableIntStateOf(0) }
    val x = (tile.center.x - width / 2).coerceIn(margin, (viewWidth - margin - width).coerceAtLeast(margin))
    val y = when {
        tile.top - gap - height >= margin -> tile.top - gap - height
        tile.bottom + gap + height <= viewHeight - margin -> tile.bottom + gap
        else -> margin
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(with(density) { width.toDp() })
            .onSizeChanged { height = it.height }
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        ZonePreviewCard(view, water, onDismiss)
    }
}

/** What a zone holds: its name and kind, its plants, its next watering, its badges and the animals seen there. */
@Composable
fun ZonePreviewCard(view: ZoneView, water: WaterState?, onDismiss: () -> Unit) {
    val large = largeText()
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(view.zone.kind.icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                Text(view.zone.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(view.zone.kind.label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.zone_preview_close))
            }
        }
        if (view.plants.isEmpty()) {
            Text(stringResource(R.string.zone_preview_no_plants), style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                view.plants.take(PREVIEW_PLANTS).forEach { plant ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlantAvatar(plant.species?.form, 28.dp, thumbnail = plant.plant.thumbnail)
                        Text(plant.title, style = MaterialTheme.typography.bodyMedium, maxLines = if (large) 3 else 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val more = view.plants.size - PREVIEW_PLANTS
                if (more > 0) Text(pluralStringResource(R.plurals.zone_preview_more, more, more), style = MaterialTheme.typography.bodySmall)
            }
        }
        val watering = water?.zones?.get(view.zone.id)
        watering?.next?.let { next ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_water_drop), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(nextWateringSentence(next, water.today, water.window, watering.byHand), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (view.badges.isNotEmpty()) {
            IconBadges(view.badges.map { BadgeItem(it.icon, stringResource(it.label), it.tone) }, max = 6, size = 24.dp)
        }
        val kinds = view.sightings.map { it.key }.distinct().size
        if (kinds > 0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_raven), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(pluralStringResource(R.plurals.zone_tile_wildlife, kinds, kinds), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
