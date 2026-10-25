package io.github.wkoller.pollenplot.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.ui.components.ConnectedChoice
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(store: SettingsStore, onBack: () -> Unit, onOpenAbout: () -> Unit) {
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val scope = rememberCoroutineScope()
    val update: ((Settings) -> Settings) -> Unit = { change -> scope.launch { store.update(change) } }
    DetailScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionHeading(stringResource(R.string.settings_appearance)) }
            item {
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                ConnectedChoice(
                    options = ThemeChoice.entries,
                    selected = settings.theme,
                    label = { stringResource(it.label) },
                    onChoose = { choice -> update { it.copy(theme = choice) } },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                ListItem(
                    checked = settings.wallpaperColours,
                    onCheckedChange = { on -> update { it.copy(wallpaperColours = on) } },
                    supportingContent = { Text(stringResource(R.string.settings_wallpaper_colours_body)) },
                    trailingContent = { Switch(checked = settings.wallpaperColours, onCheckedChange = null) },
                ) {
                    Text(stringResource(R.string.settings_wallpaper_colours))
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ListItem(
                    onClick = onOpenAbout,
                    leadingContent = { Icon(painterResource(R.drawable.ic_info), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.settings_about))
                }
            }
        }
    }
}

@Composable
internal fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
    )
}

private val ThemeChoice.label: Int
    get() = when (this) {
        ThemeChoice.SYSTEM -> R.string.theme_system
        ThemeChoice.LIGHT -> R.string.theme_light
        ThemeChoice.DARK -> R.string.theme_dark
    }
