package io.github.wkoller.pollenplot.ui.wildlife

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold

/** The animals seen in the yard. Until sightings exist it explains what will appear here. */
@Composable
fun WildlifeScreen(onOpenSettings: () -> Unit) {
    TabScaffold(
        title = stringResource(R.string.wildlife_title),
        subtitle = stringResource(R.string.wildlife_subtitle),
        onOpenSettings = onOpenSettings,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                EmptyState(
                    icon = R.drawable.ic_raven,
                    title = stringResource(R.string.wildlife_empty_title),
                    body = stringResource(R.string.wildlife_empty_body),
                )
            }
        }
    }
}
