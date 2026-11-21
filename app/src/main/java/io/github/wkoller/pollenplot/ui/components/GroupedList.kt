package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.ui.theme.Spacing

/**
 * One row of a grouped list, as Material 3 Expressive draws one: rows on a tonal surface joined into a group, its first
 * and last rounded on the outside and a narrow gap between them. [index] and [count] place the row in its group, so a
 * page's records of one kind read as one block. The row keeps the list's side margins itself.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GroupedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    // On the page's paper the rows need a tonal surface of their own to read as one group.
    val colours = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val placed = modifier
        .padding(horizontal = Spacing.l)
        .padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp)
    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            modifier = placed,
            colors = colours,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = headline,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            modifier = placed,
            colors = colours,
            leadingContent = leading,
            trailingContent = trailing,
            supportingContent = supporting,
            content = headline,
        )
    }
}
