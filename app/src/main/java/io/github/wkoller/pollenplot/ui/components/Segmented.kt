package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * One row's place in a [SegmentedGroup]: the shape that rounds the group's outer corners and keeps the inner ones
 * tight, and the group's colours. A `ListItem` takes both as `shapes` and `colors`.
 */
class Segment internal constructor(val shapes: ListItemShapes, val colors: ListItemColors)

/** Collects a [SegmentedGroup]'s rows, so a row left out by a condition does not leave a gap in the shapes. */
class SegmentedGroupScope internal constructor() {
    internal val rows = mutableListOf<@Composable (Segment) -> Unit>()

    fun row(content: @Composable (Segment) -> Unit) {
        rows += content
    }
}

/**
 * The rows of one section as Material 3 Expressive segmented tiles, as the phone's own settings draw them: each row a
 * tile, a small gap between them, and the group's outer corners rounded. Every list of settings uses it, so each row
 * reads as its own choice however long its words run.
 */
@Composable
fun SegmentedGroup(modifier: Modifier = Modifier, content: SegmentedGroupScope.() -> Unit) {
    val scope = SegmentedGroupScope().apply(content)
    // The tiles stand out from the page in the theme's container colour, as the phone's own settings draw them.
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val count = scope.rows.size
    Column(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        scope.rows.forEachIndexed { index, row -> row(Segment(ListItemDefaults.segmentedShapes(index, count), colors)) }
    }
}

/** A row of a [SegmentedGroup] that is not a list item, such as a set of chips, on the same tile as its neighbours. */
@Composable
fun SegmentSurface(segment: Segment, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = segment.shapes.shape,
        color = segment.colors.containerColor,
        contentColor = segment.colors.contentColor,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
    }
}

/** A setting that opens another screen, on its group's tile. */
@Composable
fun LinkRow(segment: Segment, icon: Int, title: String, body: String?, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        supportingContent = body?.let { { Text(it) } },
        shapes = segment.shapes,
        colors = segment.colors,
    ) { Text(title) }
}

/** A setting that is on or off, on its group's tile. The whole row turns it, and the switch shows which it is. */
@Composable
fun SwitchRow(segment: Segment, on: Boolean, icon: Int?, title: String, body: String?, onChange: (Boolean) -> Unit) {
    ListItem(
        checked = on,
        onCheckedChange = onChange,
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Switch(checked = on, onCheckedChange = null) },
        shapes = segment.shapes,
        colors = segment.colors,
    ) { Text(title) }
}
