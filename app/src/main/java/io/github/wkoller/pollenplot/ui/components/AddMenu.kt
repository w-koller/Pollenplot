package io.github.wkoller.pollenplot.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.nav.AddAction

/** The add menu: a floating action button that opens into [actions], the ways of adding from this tab. */
@Composable
fun AddMenu(actions: List<AddAction>, onAdd: (AddAction) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    val openLabel = stringResource(R.string.add_menu_open)
    val closeLabel = stringResource(R.string.add_menu_close)
    val expandedState = stringResource(R.string.add_menu_expanded)
    val collapsedState = stringResource(R.string.add_menu_collapsed)
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    stateDescription = if (expanded) expandedState else collapsedState
                    contentDescription = if (expanded) closeLabel else openLabel
                },
                checked = expanded,
                onCheckedChange = { expanded = !expanded },
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) R.drawable.ic_close else R.drawable.ic_add } }
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        actions.forEach { action ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onAdd(action)
                },
                icon = { Icon(painterResource(action.icon), contentDescription = null) },
                text = { Text(stringResource(action.label)) },
            )
        }
    }
}
