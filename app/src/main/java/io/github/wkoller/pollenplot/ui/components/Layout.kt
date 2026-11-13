package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp

/** Keeps content to a readable width on wide screens. */
fun Modifier.readable() = fillMaxWidth().wrapContentWidth().widthIn(max = READABLE_WIDTH)

/**
 * The space to leave on each side of a list so its items keep to a readable width on a wide screen, while the list
 * still scrolls from anywhere across the window. On a phone it is nothing.
 */
@Composable
fun readableSides(): Dp {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return ((width - READABLE_WIDTH) / 2).coerceAtLeast(0.dp)
}

private val READABLE_WIDTH = 640.dp

/** The heading of a part of a list or a form. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
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
