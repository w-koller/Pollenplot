package io.github.wkoller.pollenplot.ui.identify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRoles
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plants.SpeciesRow

/** Searches the knowledge base for the right plant when none of the suggestions is right. */
@Composable
fun SpeciesSearch(kb: KnowledgeBase, state: AuState?, onBack: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query, state) {
        if (query.isBlank()) emptyList() else kb.search(query).map { it to EcoRoles.of(it, CareResolver.resolve(kb, it).care, state) }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    DetailScaffold(title = stringResource(R.string.picker_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.picker_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                )
            }
            when {
                query.isBlank() -> item { Hint(stringResource(R.string.picker_hint)) }
                results.isEmpty() -> item { Hint(stringResource(R.string.picker_none, query.trim())) }
            }
            items(results, key = { it.first.name }) { (species, roles) -> SpeciesRow(species, roles, onClick = { onPick(species.name) }) }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
