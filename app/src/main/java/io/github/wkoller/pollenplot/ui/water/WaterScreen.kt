package io.github.wkoller.pollenplot.ui.water

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold

/** Watering plans for each zone. Until watering lines exist it explains what will appear here. */
@Composable
fun WaterScreen(onOpenSettings: () -> Unit) {
    TabScaffold(
        title = stringResource(R.string.water_title),
        subtitle = stringResource(R.string.water_subtitle),
        onOpenSettings = onOpenSettings,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                EmptyState(
                    icon = R.drawable.ic_water_drop,
                    title = stringResource(R.string.water_empty_title),
                    body = stringResource(R.string.water_empty_body),
                )
            }
        }
    }
}
