package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R

/**
 * The frame for a main tab: a large flexible top bar that shrinks as the content scrolls, with the tab's own [actions]
 * and Settings on the right, and an optional floating action button. A [compact] tab, such as the Plot canvas, which
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
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val allActions: @Composable RowScope.() -> Unit = {
        actions()
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
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
