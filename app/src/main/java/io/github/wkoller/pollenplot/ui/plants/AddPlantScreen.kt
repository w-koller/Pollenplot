package io.github.wkoller.pollenplot.ui.plants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Adds a plant by searching the knowledge base. A plant it does not know can be added under its own name and matched
 * to a species later.
 */
@Composable
fun AddPlantScreen(
    knowledge: KnowledgeRepository,
    garden: GardenRepository,
    views: GardenViews,
    initialZoneId: String?,
    initialSpecies: String?,
    onBack: () -> Unit,
    onAdded: (String) -> Unit,
) {
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    var query by rememberSaveable { mutableStateOf("") }
    var chosenSpecies by rememberSaveable { mutableStateOf(initialSpecies) }
    var customName by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initialSpecies == null) runCatching { focus.requestFocus() } }

    val loadedKb = kb
    val results = remember(query, loadedKb, locality) {
        if (loadedKb == null) emptyList() else loadedKb.search(query).map { it to locality.roles(it, CareResolver.resolve(loadedKb, it).care) }
    }

    DetailScaffold(title = stringResource(R.string.add_plant_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.add_plant_search_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(focus),
                )
            }
            when {
                query.isBlank() -> item { Hint(stringResource(R.string.add_plant_hint)) }
                results.isEmpty() -> item { Hint(stringResource(R.string.add_plant_none, query.trim())) }
            }
            items(results, key = { it.first.name }) { (species, roles) -> SpeciesRow(species, roles, onClick = { chosenSpecies = species.name }) }
            if (query.isNotBlank()) {
                item {
                    ListItem(
                        onClick = { customName = query.trim() },
                        leadingContent = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        supportingContent = { Text(stringResource(R.string.add_plant_custom_body)) },
                    ) { Text(stringResource(R.string.add_plant_custom, query.trim())) }
                }
            }
        }
    }

    val species = chosenSpecies?.let { loadedKb?.species(it) }
    val title = species?.displayName ?: customName
    if (title != null) {
        AddToGardenSheet(
            title = title,
            zones = zones,
            initialZoneId = initialZoneId,
            askNickname = species != null,
            onDismiss = {
                chosenSpecies = null
                customName = null
            },
            onSave = { zoneId, quantity, nickname ->
                val speciesName = species?.name
                val name = if (species == null) customName else nickname
                chosenSpecies = null
                customName = null
                scope.launch { onAdded(garden.addPlant(speciesName, name, zoneId, quantity).id) }
            },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

/** Asks which zone a new plant goes in, how many there are, and the user's own name for it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToGardenSheet(
    title: String,
    zones: List<ZoneView>,
    initialZoneId: String?,
    askNickname: Boolean,
    onDismiss: () -> Unit,
    onSave: (zoneId: String?, quantity: Int, nickname: String?) -> Unit,
) {
    var zoneId by rememberSaveable { mutableStateOf(initialZoneId) }
    var quantity by rememberSaveable { mutableIntStateOf(1) }
    var nickname by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.add_sheet_title, title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            ZoneChooser(zones, zoneId) { zoneId = it }
            QuantityStepper(quantity) { quantity = it }
            if (askNickname) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.add_sheet_nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(onClick = { onSave(zoneId, quantity, nickname.trim().ifBlank { null }) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.add_sheet_save))
            }
        }
    }
}

@Composable
fun ZoneChooser(zones: List<ZoneView>, zoneId: String?, onChoose: (String?) -> Unit) {
    val names = zones.associate { it.zone.id to it.zone.name }
    ChipGroup(
        title = stringResource(R.string.add_sheet_zone),
        options = listOf<String?>(null) + zones.map { it.zone.id },
        selected = zoneId,
        label = { id -> id?.let(names::get) ?: stringResource(R.string.add_sheet_no_zone) },
        onChoose = onChoose,
    )
}

@Composable
fun QuantityStepper(quantity: Int, countText: @Composable (Int) -> String = { pluralStringResource(R.plurals.plant_count, it, it) }, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.add_sheet_quantity), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(quantity - 1) }, enabled = quantity > 1) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.quantity_less))
        }
        Text(countText(quantity), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange(quantity + 1) }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.quantity_more))
        }
    }
}
