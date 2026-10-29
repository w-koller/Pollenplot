package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A Material 3 Expressive connected button group for choosing one of a few options, such as a section or a theme.
 * TalkBack reads it as a group of radio buttons. The options sit side by side until a label would be cut short, as
 * with large text on a narrow phone; then they stand one above the other at full width.
 */
@Composable
fun <T> ConnectedChoice(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onChoose: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labels = options.map { label(it) }
        val fontScale = LocalDensity.current.fontScale
        var stacked by remember(labels, maxWidth, fontScale) { mutableStateOf(false) }
        if (stacked) {
            Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = option == selected,
                        onCheckedChange = { onChoose(option) },
                        modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton },
                    ) {
                        Text(labels[index])
                    }
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                options.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = option == selected,
                        onCheckedChange = { onChoose(option) },
                        modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Text(
                            labels[index],
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            onTextLayout = { if (it.hasVisualOverflow) stacked = true },
                        )
                    }
                }
            }
        }
    }
}
