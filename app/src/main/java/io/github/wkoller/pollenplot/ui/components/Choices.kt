package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A filter chip for a choice in a form or a filter. Chosen, it is leaf green, the colour of a choice made, rather than
 * Material's default container, which in Field Guide is the bees' wattle gold. A chip with a meaning of its own, such as
 * the Wildlife tab's groups of animals, passes its [colors].
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    colors: SelectableChipColors = choiceChipColors(),
) = FilterChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    colors = colors,
)

/** A chosen chip's colours: leaf green with its words and tick in the colour that goes on it. */
@Composable
fun choiceChipColors(): SelectableChipColors = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * A row in a form that turns something on or off, plainer than the settings' SwitchRow. The whole row is pressable, the
 * switch says which way it is, and while it is on the row is leaf green, like a chosen chip.
 */
@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        checked = checked,
        onCheckedChange = onChange,
        modifier = modifier,
        colors = checkedRowColors(),
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) { Text(label) }
}

/**
 * The colours of a list item that can be checked, such as a row with a switch or a tick: leaf green while it is on,
 * rather than Material's default, the bees' wattle gold.
 */
@Composable
fun checkedRowColors(): ListItemColors = ListItemDefaults.colors(
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
