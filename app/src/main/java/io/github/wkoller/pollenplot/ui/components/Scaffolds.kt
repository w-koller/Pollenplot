package io.github.wkoller.pollenplot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R

/** Opens search, given by the app frame so every tab's top bar can offer it. A screen shown on its own has none. */
val LocalOpenSearch = staticCompositionLocalOf<(() -> Unit)?> { null }

/** A choice in a tab's More menu: its words, its icon and what it opens. */
class MoreItem(val label: String, @DrawableRes val icon: Int, val onClick: () -> Unit)

/** The choices every tab's More menu ends with, such as Ask your garden and Guides, given by the app frame. */
val LocalAppMenu = staticCompositionLocalOf<List<MoreItem>> { emptyList() }

/**
 * The frame for a main tab: a large flexible top bar that shrinks as the content scrolls, with the tab's own [actions]
 * and search, a More menu and Settings on the right, and an optional floating action button. The More menu holds the
 * tab's own [more] choices and then the app's, so what is used less often has a place that does not move the buttons
 * beside it. A tab with its own search button passes [search] as false. A [compact] tab, such as the Plot canvas, which
 * does not scroll, gets a small top bar so the content has the room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScaffold(
    title: String,
    subtitle: String?,
    onOpenSettings: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    compact: Boolean = false,
    search: Boolean = true,
    more: List<MoreItem> = emptyList(),
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val openSearch = LocalOpenSearch.current.takeIf { search }
    val allActions: @Composable RowScope.() -> Unit = {
        actions()
        openSearch?.let { open ->
            IconButton(onClick = open) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title))
            }
        }
        val menu = more + LocalAppMenu.current
        if (menu.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.label) },
                            leadingIcon = { Icon(painterResource(item.icon), contentDescription = null) },
                            onClick = {
                                open = false
                                item.onClick()
                            },
                        )
                    }
                }
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
        }
    }
    Scaffold(
        modifier = if (compact) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (compact) {
                if (subtitle != null) {
                    TopAppBar(title = { Text(title) }, subtitle = { Text(subtitle) }, actions = allActions)
                } else {
                    TopAppBar(title = { Text(title) }, actions = allActions)
                }
            } else {
                LargeFlexibleTopAppBar(
                    title = { Text(title) },
                    subtitle = subtitle?.let { { Text(it) } },
                    actions = allActions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { NoticeHost() },
        floatingActionButton = floatingActionButton,
        floatingActionButtonPosition = floatingActionButtonPosition,
        content = content,
    )
}

/** The frame for a screen reached from a tab, with a Back button, a title and optional actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                actions = actions,
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { NoticeHost() },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
