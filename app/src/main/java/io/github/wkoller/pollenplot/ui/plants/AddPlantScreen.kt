package io.github.wkoller.pollenplot.ui.plants

import io.github.wkoller.pollenplot.ui.components.listPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.rememberModalBottomSheetState
import io.github.wkoller.pollenplot.ui.components.ManyChipGroup
import io.github.wkoller.pollenplot.ui.review.NamesSaver
import io.github.wkoller.pollenplot.domain.garden.ManyZones
import io.github.wkoller.pollenplot.ui.components.ChipGroup
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    manyZones: ManyZones = ManyZones.ASK,
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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = listPadding(padding)) {
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
            manyZones = manyZones,
            onSave = { zoneIds, each, quantity, counts, nickname ->
                val speciesName = species?.name
                val name = if (species == null) customName else nickname
                chosenSpecies = null
                customName = null
                scope.launch {
                    val id = garden.addPlants(speciesName, name, zoneIds, each, quantity, counts = counts).first().id
                    // The database finishes on its own thread; going on to the plant has to happen on the main one.
                    withContext(Dispatchers.Main) { onAdded(id) }
                }
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
    manyZones: ManyZones = ManyZones.ASK,
    onSave: (zoneIds: List<String>, each: Boolean, quantity: Int, counts: Map<String, Int>?, nickname: String?) -> Unit,
) {
    var zoneIds by rememberSaveable(stateSaver = NamesSaver) { mutableStateOf(listOfNotNull(initialZoneId)) }
    var each by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable(stateSaver = PlantCount.Saver) { mutableStateOf(PlantCount()) }
    var nickname by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.add_sheet_title, title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            ZonesChooser(zones, zoneIds) { zoneIds = it }
            ManyZonesChoice(zoneIds.size, manyZones, each) { each = it }
            PlantCounts(zones, zoneIds, eachZone(manyZones, each), count) { count = it }
            if (askNickname) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.add_sheet_nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = {
                    val split = eachZone(manyZones, each)
                    onSave(zoneIds, split, count.total, count.countsFor(zoneIds, split), nickname.trim().ifBlank { null })
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_sheet_save))
            }
        }
    }
}

@Composable
fun ZoneChooser(zones: List<ZoneView>, zoneId: String?, title: String? = null, onChoose: (String?) -> Unit) {
    val names = zones.associate { it.zone.id to it.zone.name }
    ChipGroup(
        title = title ?: stringResource(R.string.add_sheet_zone),
        options = listOf<String?>(null) + zones.map { it.zone.id },
        selected = zoneId,
        label = { id -> id?.let(names::get) ?: stringResource(R.string.add_sheet_no_zone) },
        onChoose = onChoose,
    )
}

/** The zones a plant grows in, several at once. No zone stands for choosing none, and the first chosen stays first. */
@Composable
fun ZonesChooser(zones: List<ZoneView>, zoneIds: List<String>, title: String? = null, onChoose: (List<String>) -> Unit) {
    val names = zones.associate { it.zone.id to it.zone.name }
    ManyChipGroup(
        title = title ?: stringResource(R.string.add_sheet_zones),
        options = zones.map { it.zone.id },
        selected = zoneIds.toSet(),
        label = { names[it].orEmpty() },
        none = stringResource(R.string.add_sheet_no_zone),
        onChange = { chosen -> onChoose(zoneIds.filter { it in chosen } + chosen.filterNot { it in zoneIds }) },
    )
}

/** Whether several zones get a plant each: what [setting] says, or what the user chose when it asks each time. */
fun eachZone(setting: ManyZones, each: Boolean): Boolean = when (setting) {
    ManyZones.ASK -> each
    ManyZones.ONE_PLANT -> false
    ManyZones.PLANT_EACH -> true
}

/**
 * With [count] zones chosen, asks whether they hold one plant between them or a plant each. Nothing is asked for one
 * zone, or when the [setting] already answers.
 */
@Composable
fun ManyZonesChoice(count: Int, setting: ManyZones, each: Boolean, onEach: (Boolean) -> Unit) {
    if (count < 2 || setting != ManyZones.ASK) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ChipGroup(
            title = stringResource(R.string.many_zones_title),
            options = listOf(false, true),
            selected = each,
            label = { stringResource(if (it) R.string.many_zones_each else R.string.many_zones_one) },
            onChoose = onEach,
        )
        Text(
            stringResource(if (each) R.string.many_zones_each_body else R.string.many_zones_one_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun QuantityStepper(
    quantity: Int,
    title: String? = null,
    min: Int = 1,
    countText: @Composable (Int) -> String = { pluralStringResource(R.plurals.plant_count, it, it) },
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title ?: stringResource(R.string.add_sheet_quantity), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(quantity - 1) }, enabled = quantity > min) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.quantity_less))
        }
        Text(countText(quantity), style = MaterialTheme.typography.bodyLarge)
        FilledTonalIconButton(onClick = { onChange(quantity + 1) }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.quantity_more))
        }
    }
}
