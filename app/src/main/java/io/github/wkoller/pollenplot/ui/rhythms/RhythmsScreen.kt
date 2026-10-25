package io.github.wkoller.pollenplot.ui.rhythms

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.TabScaffold

/** Pollen Rhythms: the nectar and pollen calendar and feed. Until plants exist it explains what will appear here. */
@Composable
fun RhythmsScreen(onOpenSettings: () -> Unit) {
    TabScaffold(
        title = stringResource(R.string.rhythms_title),
        subtitle = stringResource(R.string.rhythms_subtitle),
        onOpenSettings = onOpenSettings,
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                EmptyState(
                    icon = R.drawable.ic_local_florist,
                    title = stringResource(R.string.rhythms_empty_title),
                    body = stringResource(R.string.rhythms_empty_body),
                )
            }
        }
    }
}
