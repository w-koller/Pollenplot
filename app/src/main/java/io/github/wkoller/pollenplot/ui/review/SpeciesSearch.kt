package io.github.wkoller.pollenplot.ui.review

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.NamedPlant
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plants.SpeciesRow

/**
 * Searches for the right plant when none of the suggestions is right: the knowledge base first, then the other plants
 * the models can name ([names]). A name Pollenplot does not know can still be used as it was typed.
 */
@Composable
fun SpeciesSearch(
    kb: KnowledgeBase,
    locality: Locality,
    onBack: () -> Unit,
    names: List<NamedPlant> = emptyList(),
    title: String = stringResource(R.string.picker_title),
    onPick: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val words = query.trim()
    val results = remember(words, locality) {
        if (words.isBlank()) emptyList() else kb.search(words).map { it to locality.roles(it, CareResolver.resolve(kb, it).care) }
    }
    // Plants a model can name that the knowledge base does not have, those whose name starts with the words first.
    val others = remember(words, names) {
        if (words.length < 3) {
            emptyList()
        } else {
            names.asSequence()
                .filter { it.name.contains(words, ignoreCase = true) || it.common?.contains(words, ignoreCase = true) == true }
                .filter { kb.species(it.name) == null }
                .sortedBy { !(it.name.startsWith(words, ignoreCase = true) || it.common?.startsWith(words, ignoreCase = true) == true) }
                .take(MAX_OTHERS)
                .toList()
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    DetailScaffold(title = title, onBack = onBack) { padding ->
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
                words.isBlank() -> item { Hint(stringResource(R.string.picker_hint)) }
                results.isEmpty() && others.isEmpty() -> item { Hint(stringResource(R.string.picker_none, words)) }
            }
            items(results, key = { it.first.name }) { (species, roles) -> SpeciesRow(species, roles, onClick = { onPick(species.name) }) }
            items(others, key = { "model:" + it.name }) { plant ->
                ListItem(
                    onClick = { onPick(plant.name) },
                    supportingContent = {
                        Text(
                            if (plant.common == null) stringResource(R.string.review_not_in_kb) else plant.name,
                            fontStyle = if (plant.common == null) null else FontStyle.Italic,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                ) { Text(plant.common ?: plant.name, fontStyle = if (plant.common == null) FontStyle.Italic else null, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (words.length >= 3 && kb.species(words) == null && others.none { it.name.equals(words, ignoreCase = true) }) {
                item { ListItem(onClick = { onPick(words) }) { Text(stringResource(R.string.picker_use, words)) } }
            }
        }
    }
}

/** The search lists at most this many plants from the models' lists, so typing more narrows it. */
private const val MAX_OTHERS = 30

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}
