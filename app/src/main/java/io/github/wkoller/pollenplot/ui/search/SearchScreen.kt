package io.github.wkoller.pollenplot.ui.search

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.db.SearchHit
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRoles
import io.github.wkoller.pollenplot.domain.semantic.SearchKind
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.plants.SpeciesRow
import io.github.wkoller.pollenplot.ui.plants.ZoneCard
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import kotlinx.coroutines.flow.map

/**
 * Search your garden: zones and plants from the search index, and below them the plants from the knowledge base that
 * match, which can be added to the garden.
 */
@Composable
fun SearchScreen(
    garden: GardenRepository,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenZone: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
    onAddSpecies: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits by produceState(initialValue = emptyList<SearchHit>(), query) { value = garden.search(query) }
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val state by remember { settings.settings.map { it.gardenState } }.collectAsStateWithLifecycle(initialValue = null)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val loadedKb = kb
    val species = remember(query, loadedKb, state) {
        if (loadedKb == null || query.isBlank()) emptyList() else loadedKb.search(query, limit = 8).map { it to EcoRoles.of(it, CareResolver.resolve(loadedKb, it).care, state) }
    }

    DetailScaffold(title = stringResource(R.string.search_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.search_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                )
            }
            if (query.isBlank()) {
                item {
                    Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                return@LazyColumn
            }
            item { SectionHeading(stringResource(R.string.search_in_garden)) }
            if (hits.isEmpty()) {
                item { Text(stringResource(R.string.search_none, query.trim()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(hits, key = { it.recordId }) { hit ->
                // Results show as the same rows as everywhere else; the index text is only for matching.
                val plant = plants.firstOrNull { it.plant.id == hit.recordId }
                val zone = zones.firstOrNull { it.zone.id == hit.recordId }
                when {
                    plant != null -> PlantRow(plant, onClick = { onOpenPlant(hit.recordId) })
                    zone != null -> ZoneCard(zone, onClick = { onOpenZone(hit.recordId) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    else -> ListItem(
                        onClick = { if (hit.kind == SearchKind.ZONE.name) onOpenZone(hit.recordId) else onOpenPlant(hit.recordId) },
                        leadingContent = { Icon(painterResource(if (hit.kind == SearchKind.ZONE.name) R.drawable.ic_yard else R.drawable.ic_potted_plant), contentDescription = null) },
                    ) { Text(hit.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (species.isNotEmpty()) {
                item { SectionHeading(stringResource(R.string.search_could_add)) }
                items(species, key = { "species:" + it.first.name }) { (s, roles) -> SpeciesRow(s, roles, onClick = { onAddSpecies(s.name) }) }
            }
        }
    }
}
