package io.github.wkoller.pollenplot.ui.search

import io.github.wkoller.pollenplot.ui.components.SectionHeading
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.data.db.SearchHit
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.semantic.SearchKind
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.plants.SpeciesRow
import io.github.wkoller.pollenplot.ui.plants.ZoneCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    meaning: MeaningSearch,
    onBack: () -> Unit,
    onOpenZone: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
    onAddSpecies: (String) -> Unit,
    onAskGarden: (() -> Unit)? = null,
    onOpenSighting: (String) -> Unit = {},
    onOpenHive: (String) -> Unit = {},
    onOpenNativeHive: (String) -> Unit = {},
    onOpenHotel: (String) -> Unit = {},
    onOpenWish: (String) -> Unit = {},
    onOpenProblem: (String) -> Unit = {},
    onOpenWaterFeature: (String) -> Unit = {},
    /** The zone each record belongs to, for the zone filter. */
    places: Flow<Map<String, String>> = flowOf(emptyMap()),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var zoneFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val zoneOf by places.collectAsStateWithLifecycle(initialValue = emptyMap())
    val hits by produceState(initialValue = emptyList<SearchHit>(), query) { value = garden.search(query) }
    // With the text model on the phone, records related in meaning follow the ones the words found.
    val related by produceState(initialValue = emptyList<SearchHit>(), query, hits) { value = meaning.related(query, hits.map { it.recordId }.toSet()) }
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val loadedKb = kb
    val species = remember(query, loadedKb, locality) {
        if (loadedKb == null || query.isBlank()) emptyList() else loadedKb.search(query, limit = 8).map { it to locality.roles(it, CareResolver.resolve(loadedKb, it).care) }
    }

    DetailScaffold(
        title = stringResource(R.string.search_title),
        onBack = onBack,
        actions = {
            onAskGarden?.let { ask -> IconButton(onClick = ask) { Icon(painterResource(R.drawable.ic_forum), contentDescription = stringResource(R.string.ask_garden_open)) } }
        },
    ) { padding ->
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
            val found = hits + related
            val kinds = SearchKind.entries.filter { kind -> found.any { it.kind == kind.name } }
            val foundZones = zones.filter { zone -> found.any { zoneOf[it.recordId] == zone.zone.id } }
            fun shown(hit: SearchHit) = (kindFilter == null || hit.kind == kindFilter) && (zoneFilter == null || zoneOf[hit.recordId] == zoneFilter)
            val filtered = kindFilter != null || zoneFilter != null
            if (kinds.size > 1 || foundZones.isNotEmpty() || filtered) {
                item(key = "filters") {
                    SearchFilters(
                        kinds = kinds,
                        kind = kindFilter,
                        onKind = { kindFilter = it },
                        zones = foundZones.map { it.zone.id to it.zone.name },
                        zone = zoneFilter,
                        onZone = { zoneFilter = it },
                    )
                }
            }
            val shownHits = hits.filter(::shown)
            val shownRelated = related.filter(::shown)
            item { SectionHeading(stringResource(R.string.search_in_garden)) }
            if (hits.isEmpty()) {
                item { Text(stringResource(R.string.search_none, query.trim()), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            } else if (shownHits.isEmpty()) {
                item { Text(stringResource(R.string.search_none_filtered), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
            }
            items(shownHits, key = { it.recordId }) { hit -> HitRow(hit, plants, zones, onOpenZone, onOpenPlant, onOpenSighting, onOpenHive, onOpenNativeHive, onOpenHotel, onOpenWish, onOpenProblem, onOpenWaterFeature) }
            if (shownRelated.isNotEmpty()) {
                item { SectionHeading(stringResource(R.string.search_related)) }
                items(shownRelated, key = { "related:" + it.recordId }) { hit -> HitRow(hit, plants, zones, onOpenZone, onOpenPlant, onOpenSighting, onOpenHive, onOpenNativeHive, onOpenHotel, onOpenWish, onOpenProblem, onOpenWaterFeature) }
            }
            // Plants to add are not records in a zone, so a filter leaves them out.
            if (species.isNotEmpty() && !filtered) {
                item { SectionHeading(stringResource(R.string.search_could_add)) }
                items(species, key = { "species:" + it.first.name }) { (s, roles) -> SpeciesRow(s, roles, onClick = { onAddSpecies(s.name) }) }
            }
        }
    }
}

val SearchKind.label: Int
    get() = when (this) {
        SearchKind.ZONE -> R.string.search_kind_zones
        SearchKind.PLANT -> R.string.search_kind_plants
        SearchKind.SIGHTING -> R.string.search_kind_sightings
        SearchKind.HIVE -> R.string.search_kind_hives
        SearchKind.NATIVE_HIVE -> R.string.search_kind_native_hives
        SearchKind.HOTEL -> R.string.search_kind_hotels
        SearchKind.WISH -> R.string.search_kind_wishes
        SearchKind.PROBLEM -> R.string.search_kind_problems
        SearchKind.WATER_FEATURE -> R.string.search_kind_water
    }

/** Chips that narrow the results to one kind of record, or to the records in one zone. Choosing a chip again clears it. */
@Composable
private fun SearchFilters(
    kinds: List<SearchKind>,
    kind: String?,
    onKind: (String?) -> Unit,
    zones: List<Pair<String, String>>,
    zone: String?,
    onZone: (String?) -> Unit,
) {
    Column(Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.search_filter_kind), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            kinds.forEach { option ->
                FilterChip(selected = kind == option.name, onClick = { onKind(if (kind == option.name) null else option.name) }, label = { Text(stringResource(option.label)) })
            }
        }
        if (zones.isNotEmpty() || zone != null) {
            Text(stringResource(R.string.search_filter_zone), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                zones.forEach { (id, name) ->
                    FilterChip(selected = zone == id, onClick = { onZone(if (zone == id) null else id) }, label = { Text(name) })
                }
            }
        }
    }
}

/** A search result, shown as the same row as everywhere else; the index text is only for matching. */
@Composable
private fun HitRow(
    hit: SearchHit,
    plants: List<PlantView>,
    zones: List<ZoneView>,
    onOpenZone: (String) -> Unit,
    onOpenPlant: (String) -> Unit,
    onOpenSighting: (String) -> Unit,
    onOpenHive: (String) -> Unit,
    onOpenNativeHive: (String) -> Unit,
    onOpenHotel: (String) -> Unit,
    onOpenWish: (String) -> Unit,
    onOpenProblem: (String) -> Unit,
    onOpenWaterFeature: (String) -> Unit,
) {
    val plant = plants.firstOrNull { it.plant.id == hit.recordId }
    val zone = zones.firstOrNull { it.zone.id == hit.recordId }
    when {
        plant != null -> PlantRow(plant, onClick = { onOpenPlant(hit.recordId) })
        zone != null -> ZoneCard(zone, onClick = { onOpenZone(hit.recordId) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        else -> ListItem(
            onClick = {
                when (hit.kind) {
                    SearchKind.ZONE.name -> onOpenZone(hit.recordId)
                    SearchKind.SIGHTING.name -> onOpenSighting(hit.recordId)
                    SearchKind.HIVE.name -> onOpenHive(hit.recordId)
                    SearchKind.NATIVE_HIVE.name -> onOpenNativeHive(hit.recordId)
                    SearchKind.HOTEL.name -> onOpenHotel(hit.recordId)
                    SearchKind.WISH.name -> onOpenWish(hit.recordId)
                    SearchKind.PROBLEM.name -> onOpenProblem(hit.recordId)
                    SearchKind.WATER_FEATURE.name -> onOpenWaterFeature(hit.recordId)
                    else -> onOpenPlant(hit.recordId)
                }
            },
            leadingContent = {
                val icon = when (hit.kind) {
                    SearchKind.ZONE.name -> R.drawable.ic_yard
                    SearchKind.SIGHTING.name -> R.drawable.ic_raven
                    SearchKind.HIVE.name -> R.drawable.ic_hive
                    SearchKind.NATIVE_HIVE.name -> R.drawable.ic_emoji_nature
                    SearchKind.HOTEL.name -> R.drawable.ic_cottage
                    SearchKind.WISH.name -> R.drawable.ic_bookmark
                    SearchKind.PROBLEM.name -> R.drawable.ic_bug_report
                    SearchKind.WATER_FEATURE.name -> R.drawable.ic_water_drop
                    else -> R.drawable.ic_potted_plant
                }
                Icon(painterResource(icon), contentDescription = null)
            },
        ) { Text(hit.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
