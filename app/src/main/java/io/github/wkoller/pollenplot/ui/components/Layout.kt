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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing

/** Keeps content to a readable width on wide screens. */
fun Modifier.readable() = fillMaxWidth().wrapContentWidth().widthIn(max = Spacing.readableWidth)

/**
 * The space to leave on each side of a list so its items keep to a readable width on a wide screen, while the list
 * still scrolls from anywhere across the window. On a phone it is nothing.
 */
@Composable
fun readableSides(): Dp {
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    return ((width - Spacing.readableWidth) / 2).coerceAtLeast(0.dp)
}

/**
 * The room the scaffold around a screen leaves at the end of a list for its floating action button, or nothing when it
 * has none. [TabScaffold] and [DetailScaffold] give it, so every list inside them clears the button by itself.
 */
val LocalFabClearance = staticCompositionLocalOf { 0.dp }

/**
 * A list's content padding inside a scaffold: the scaffold's top and bottom, [readableSides] on each side, and [bottom]
 * more at the end so the last row clears the edge of the screen, or clears the floating action button when there is one.
 */
@Composable
fun listPadding(padding: PaddingValues, bottom: Dp = Spacing.xl): PaddingValues = PaddingValues(
    start = readableSides(),
    end = readableSides(),
    top = padding.calculateTopPadding(),
    bottom = padding.calculateBottomPadding() + maxOf(bottom, LocalFabClearance.current),
)

/** The heading of a part of a list or a form. */
@Composable
fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = Spacing.s).semantics { heading() },
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
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
    ChoiceChip(
        selected = on,
        onClick = onClick,
        label = { Text(text) },
        leadingIcon = if (on) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(IconSize.small)) }
        } else {
            null
        },
    )
}

/** A labelled group of filter chips for choosing one of a few options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChipGroup(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onChoose: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            options.forEach { option -> ChoiceChip(selected = option == selected, onClick = { onChoose(option) }, label = { Text(label(option)) }) }
        }
    }
}
