package io.github.wkoller.pollenplot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.wkoller.pollenplot.ui.theme.animationsOn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.theme.Spacing

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
 *
 * With large text, the shrunken bar has the title beside four or five buttons, so its title keeps to two lines and its
 * subtitle to one, ending in an ellipsis rather than breaking a word; the full words are in the bar as it opens out. The
 * compact bar, which never opens out, leaves its subtitle out then.
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
    floatingActionButton: (@Composable () -> Unit)? = null,
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val openSearch = LocalOpenSearch.current.takeIf { search }
    val large = largeText()
    // Kept here rather than beside the button: a tab that becomes compact, as the Plot does once its zones load, draws
    // another top bar, and a menu opened just before would otherwise close and forget it was open.
    var open by rememberSaveable { mutableStateOf(false) }
    val allActions: @Composable RowScope.() -> Unit = {
        actions()
        openSearch?.let { open ->
            IconButton(onClick = open) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title))
            }
        }
        val appMenu = LocalAppMenu.current
        val menu = more + appMenu
        if (menu.isNotEmpty()) {
            Box {
                IconButton(onClick = { open = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = stringResource(R.string.more))
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEachIndexed { index, item ->
                        // The tab's own choices come first, then a line, then the ones every tab has.
                        if (index == more.size && more.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
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
                if (subtitle != null && !large) {
                    TopAppBar(title = { Text(title) }, subtitle = { Text(subtitle) }, actions = allActions)
                } else {
                    TopAppBar(title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, actions = allActions)
                }
            } else {
                val squeezed by remember(large, scrollBehavior) { derivedStateOf { large && scrollBehavior.collapsed() } }
                LargeFlexibleTopAppBar(
                    title = { Text(title, maxLines = if (squeezed) 2 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis) },
                    subtitle = subtitle?.let { { Text(it, maxLines = if (squeezed) 1 else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis) } },
                    actions = allActions,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        snackbarHost = { NoticeHost() },
        floatingActionButton = floatingActionButton ?: {},
        floatingActionButtonPosition = floatingActionButtonPosition,
    ) { padding -> FabRoom(floatingActionButton != null) { content(padding) } }
}

/**
 * The frame for a screen reached from a tab, with a Back button, a title and optional actions. A record's page that
 * opens with a [RecordHero] passes [titleShown], usually from [heroGone]: the bar then leaves the name to the hero, and
 * fades it in on one line once the hero has scrolled away. While it is hidden TalkBack does not read it, because the
 * hero's heading says the same.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: (@Composable () -> Unit)? = null,
    titleShown: (() -> Boolean)? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { if (titleShown == null) Text(title) else HeroBarTitle(title, titleShown) },
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
        floatingActionButton = floatingActionButton ?: {},
    ) { padding -> FabRoom(floatingActionButton != null) { content(padding) } }
}

/**
 * A page's name in the top bar, shown only once the page's hero, which also names it, has scrolled away. It keeps to one
 * line, or two with large text, so a long name is not cut to its first word.
 */
@Composable
private fun HeroBarTitle(title: String, shown: () -> Boolean) {
    val visible = shown()
    val spec = if (animationsOn()) MaterialTheme.motionScheme.fastEffectsSpec<Float>() else snap()
    val alpha by animateFloatAsState(if (visible) 1f else 0f, spec, label = "title")
    Text(
        title,
        maxLines = if (largeText()) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }.then(if (visible) Modifier else Modifier.clearAndSetSemantics { }),
    )
}

/** Whether the bar has shrunk more than half way. */
@OptIn(ExperimentalMaterial3Api::class)
private fun TopAppBarScrollBehavior.collapsed() = state.collapsedFraction > 0.5f

/** Gives the lists inside a scaffold with a floating action button the room to clear it, through [listPadding]. */
@Composable
private fun FabRoom(hasFab: Boolean, content: @Composable () -> Unit) {
    if (hasFab) CompositionLocalProvider(LocalFabClearance provides Spacing.fabClearance, content = content) else content()
}
