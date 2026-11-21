package io.github.wkoller.pollenplot.ui.components

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.ui.platform.LocalResources
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
import androidx.compose.material3.FloatingActionButton
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.nav.AddAction

/** The add button of a tab with one thing to add: the same button as the add menu's, which adds it at once. */
@Composable
fun AddButton(label: String, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) { Icon(painterResource(R.drawable.ic_add), contentDescription = label) }
}

/** The add menu: a floating action button that opens into [actions], the ways of adding from this tab. */
@Composable
fun AddMenu(actions: List<AddAction>, onAdd: (AddAction) -> Unit) {
    val resources = LocalResources.current
    FabMenu(
        items = actions.map { action -> MenuAction(resources.getString(action.label), action.icon) { onAdd(action) } },
        openLabel = stringResource(R.string.add_menu_open),
        closeLabel = stringResource(R.string.add_menu_close),
    )
}

/** One choice in a floating action button's menu: its words, its icon and what it does. */
class MenuAction(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/**
 * The record menu of a page such as a hive's: a floating action button that opens into [items], each a kind of record
 * to add, so the page is not a wall of buttons.
 */
@Composable
fun RecordMenu(items: List<MenuAction>) = FabMenu(items, stringResource(R.string.record_menu_open), stringResource(R.string.record_menu_close))

/** A floating action button with a plus that opens into [items] and turns into a cross to close them. */
@Composable
private fun FabMenu(items: List<MenuAction>, openLabel: String, closeLabel: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
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
        items.forEach { item ->
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    item.onClick()
                },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                text = { Text(item.label) },
            )
        }
    }
}
