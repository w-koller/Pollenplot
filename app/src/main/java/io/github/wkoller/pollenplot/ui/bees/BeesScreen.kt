package io.github.wkoller.pollenplot.ui.bees

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.ui.components.ConnectedChoice
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import kotlinx.coroutines.launch

/**
 * The three bee sections. European honey bees, Australian native bees and bee hotels each keep their own records,
 * screens and guide, so they are chosen here and never shown mixed together.
 */
enum class BeeSection(@StringRes val label: Int, @DrawableRes val icon: Int, @StringRes val emptyTitle: Int, @StringRes val emptyBody: Int) {
    HONEY(R.string.bees_section_honey, R.drawable.ic_hive, R.string.bees_honey_empty_title, R.string.bees_honey_empty_body),
    NATIVE(R.string.bees_section_native, R.drawable.ic_emoji_nature, R.string.bees_native_empty_title, R.string.bees_native_empty_body),
    HOTELS(R.string.bees_section_hotels, R.drawable.ic_cottage, R.string.bees_hotels_empty_title, R.string.bees_hotels_empty_body),
}

@Composable
fun BeesScreen(
    beeViews: BeeViews,
    settings: SettingsStore,
    onOpenSettings: () -> Unit,
    onAddHive: () -> Unit,
    onOpenHive: (String) -> Unit,
    initialSection: BeeSection = BeeSection.HONEY,
) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    val hives by beeViews.hives.collectAsStateWithLifecycle(initialValue = null)
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    TabScaffold(
        title = stringResource(R.string.bees_title),
        subtitle = stringResource(R.string.bees_subtitle),
        onOpenSettings = onOpenSettings,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                ConnectedChoice(
                    options = BeeSection.entries,
                    selected = section,
                    label = { stringResource(it.label) },
                    onChoose = { section = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val loaded = hives
            if (section == BeeSection.HONEY && loaded != null && current != null) {
                honeySection(
                    hives = loaded,
                    today = beeViews.today(),
                    state = current?.gardenState,
                    varroaEstablished = current?.varroaEstablished == true,
                    onVarroa = { on -> scope.launch { settings.update { it.copy(varroaEstablished = on) } } },
                    onAddHive = onAddHive,
                    onOpenHive = onOpenHive,
                    onOpenLink = { uri.openUri(it) },
                )
            } else if (section != BeeSection.HONEY) {
                item {
                    EmptyState(
                        icon = section.icon,
                        title = stringResource(section.emptyTitle),
                        body = stringResource(section.emptyBody),
                    )
                }
            }
        }
    }
}
