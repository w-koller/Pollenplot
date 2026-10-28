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
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.wildlife.label

/**
 * Searches the animal knowledge base when none of the suggestions is right, with animals of [group] first. A name
 * Pollenplot does not know can still be used as it was typed.
 */
@Composable
fun FaunaSearch(fauna: FaunaBase, group: LifeGroup, onBack: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val words = query.trim()
    val results = remember(words, group) {
        if (words.isBlank()) {
            emptyList()
        } else {
            fauna.all
                .filter { animal -> (animal.common + animal.name + animal.synonyms).any { it.contains(words, ignoreCase = true) } }
                .sortedWith(compareBy({ it.group != group }, { it.displayName.lowercase() }))
        }
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    DetailScaffold(title = stringResource(R.string.animal_search_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.animal_search_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                )
            }
            if (words.isNotBlank() && results.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.animal_search_none, words),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(results, key = { it.name }) { animal ->
                ListItem(
                    onClick = { onPick(animal.name) },
                    supportingContent = {
                        Text(animal.name + " · " + stringResource(animal.group.label), fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                ) { Text(animal.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (words.length >= 3) {
                item {
                    ListItem(onClick = { onPick(words) }) { Text(stringResource(R.string.animal_search_use, words)) }
                }
            }
        }
    }
}
