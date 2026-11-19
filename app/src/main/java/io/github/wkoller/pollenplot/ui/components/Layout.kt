package io.github.wkoller.pollenplot.ui.components

import io.github.wkoller.pollenplot.R
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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

/**
 * A list's content padding inside a scaffold: the scaffold's top and bottom, [readableSides] on each side, and [bottom]
 * more at the end so the last row clears the edge of the screen.
 */
@Composable
fun listPadding(padding: PaddingValues, bottom: Dp = 24.dp): PaddingValues = PaddingValues(
    start = readableSides(),
    end = readableSides(),
    top = padding.calculateTopPadding(),
    bottom = padding.calculateBottomPadding() + bottom,
)

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

/**
 * A labelled group of filter chips for choosing any number of options, each marked with a tick when chosen. [none]
 * names a first chip that stands for choosing nothing, such as Not sure: pressing it clears the others, and it is the
 * one chosen while nothing else is.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ManyChipGroup(title: String?, options: List<T>, selected: Set<T>, label: @Composable (T) -> String, none: String? = null, onChange: (Set<T>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (none != null) TickChip(none, selected.isEmpty()) { onChange(emptySet()) }
            options.forEach { option ->
                val on = option in selected
                TickChip(label(option), on) { onChange(if (on) selected - option else selected + option) }
            }
        }
    }
}

@Composable
private fun TickChip(text: String, on: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = on,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (on) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
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
