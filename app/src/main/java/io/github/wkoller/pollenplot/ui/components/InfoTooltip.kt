package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * "How it works", a button that opens a tooltip explaining how a screen works out what it shows, as a few short
 * [points]. The tooltip stays until the reader taps elsewhere, so there is time to read it, and the screen itself
 * keeps only what it shows. Tooltips still need an opt-in, so screens reach them through here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HowItWorks(points: List<String>, modifier: Modifier = Modifier, title: String = stringResource(R.string.how_it_works)) {
    val scope = rememberCoroutineScope()
    val state = rememberTooltipState(isPersistent = true)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            RichTooltip(title = { Text(title) }) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    points.forEach { point ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            // The bullet is only for the eye; a screen reader reads each point as it is.
                            Text("•", modifier = Modifier.clearAndSetSemantics {})
                            Text(point)
                        }
                    }
                }
            }
        },
        state = state,
        modifier = modifier,
    ) {
        TextButton(onClick = { scope.launch { state.show() } }) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(IconSize.small))
            Text(title, modifier = Modifier.padding(start = Spacing.s))
        }
    }
}
