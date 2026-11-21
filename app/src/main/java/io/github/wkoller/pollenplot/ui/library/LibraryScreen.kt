package io.github.wkoller.pollenplot.ui.library

import io.github.wkoller.pollenplot.ui.components.checkedRowColors
import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.components.listPadding
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.clickable
import java.io.File
import androidx.compose.ui.platform.LocalContext
import io.github.wkoller.pollenplot.ui.components.PhotoViewer
import io.github.wkoller.pollenplot.ui.plants.PlantCounts
import io.github.wkoller.pollenplot.ui.plants.PlantCount
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.R
import androidx.compose.material3.Switch
import io.github.wkoller.pollenplot.data.Locality
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.LibraryPick
import io.github.wkoller.pollenplot.data.LibraryRepository
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.library.LibraryPhoto
import io.github.wkoller.pollenplot.library.LibraryPhotos
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.LocalNotices
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.plants.AddToGardenSheet
import io.github.wkoller.pollenplot.ui.plants.ManyZonesChoice
import io.github.wkoller.pollenplot.ui.plants.QuantityStepper
import io.github.wkoller.pollenplot.ui.plants.ZonesChooser
import io.github.wkoller.pollenplot.ui.plants.eachZone
import io.github.wkoller.pollenplot.ui.review.NamesSaver
import kotlinx.coroutines.launch

/** One plant the library lists: a species from the knowledge base, or a plant of the user's own. */
internal data class LibraryRow(
    /** What the row is chosen by: the species name, or the library entry's id for a plant under the user's own name. */
    val key: String,
    val title: String,
    val speciesName: String?,
    val customName: String?,
    val form: GrowthForm?,
    /** The user's own cut-out of it, when they have one. */
    val thumbnail: String?,
    /** The library entry, when the plant is in the user's library. */
    val libraryId: String?,
)

/**
 * The plant library: every plant Pollenplot knows, with a photo, and the user's own library of the plants they have
 * added or saved. A press adds one plant to the garden; Select chooses several, to add to zones together, to save, or
 * to take out of the user's library. [initialZoneId] is the zone the library was opened from, offered first.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    knowledge: KnowledgeRepository,
    views: GardenViews,
    library: LibraryRepository,
    photos: LibraryPhotos,
    settings: SettingsStore,
    initialZoneId: String?,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
) {
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val saved by library.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var mine by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(stateSaver = NamesSaver) { mutableStateOf(emptyList<String>()) }
    var choosingZones by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    // With add mode on, a press adds the plant at once, as the library did before it had pages; opened from a zone,
    // it starts on, since the user came to add a plant there.
    var addMode by rememberSaveable { mutableStateOf(initialZoneId != null) }
    // The plant whose page is open, by its row's key.
    var showing by rememberSaveable { mutableStateOf<String?>(null) }
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val gardenPlants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    // Kept outside the page, so the list is where it was when the page closes.
    val listState = rememberLazyListState()
    // The plant whose picture is shown enlarged, with the photo fetched for it when it is not the user's own.
    var viewing by remember { mutableStateOf<Pair<String, LibraryPhoto?>?>(null) }
    val scope = rememberCoroutineScope()
    val notices = LocalNotices.current
    val resources = LocalResources.current
    // A garden made before the library was has its plants entered the first time the library opens.
    LaunchedEffect(Unit) { library.takeInGarden() }

    val loadedKb = kb
    val savedBySpecies = saved.filter { it.speciesName != null }.associateBy { it.speciesName!! }
    val rows = remember(loadedKb, saved, mine, query) {
        if (loadedKb == null) {
            emptyList()
        } else if (mine) {
            saved.map { plant ->
                val species = plant.speciesName?.let(loadedKb::species)
                val title = species?.displayName ?: plant.speciesName ?: plant.customName.orEmpty()
                LibraryRow(plant.speciesName ?: plant.id, title, plant.speciesName, plant.customName, species?.form, plant.thumbnail, plant.id)
            }.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) || it.speciesName?.contains(query.trim(), ignoreCase = true) == true }
                .sortedBy { it.title.lowercase() }
        } else {
            (if (query.isBlank()) loadedKb.allSpecies else loadedKb.search(query, limit = 60)).map { species ->
                val own = savedBySpecies[species.name]
                LibraryRow(species.name, species.displayName, species.name, null, species.form, own?.thumbnail, own?.id)
            }
        }
    }
    val chosen = rows.filter { it.key in selection }
    val leaveSelection = {
        selecting = false
        selection = emptyList()
        choosingZones = false
    }

    /** Adds plants to zones and says so, with Undo for several and Open for one. */
    fun add(picks: List<LibraryRow>, zoneIds: List<String>, each: Boolean, quantity: Int, counts: Map<String, Int>?, nickname: String? = null) {
        scope.launch {
            val ids = library.addToZones(picks.map { LibraryPick(it.speciesName, nickname ?: it.customName, it.thumbnail) }, zoneIds, each, quantity, counts)
            val names = zones.associate { it.zone.id to it.zone.name }
            val place = zoneIds.mapNotNull(names::get).joinToString(", ").ifEmpty { null }
            val single = picks.singleOrNull()
            when {
                single != null && place != null -> notices?.show(resources.getString(R.string.library_added_one_zone, single.title, place), action = resources.getString(R.string.review_open)) { onOpenPlant(ids.first()) }
                single != null -> notices?.show(resources.getString(R.string.library_added_one, single.title), action = resources.getString(R.string.review_open)) { onOpenPlant(ids.first()) }
                place != null -> notices?.show(resources.getQuantityString(R.plurals.library_added_zone, picks.size, picks.size, place)) { library.undoAdd(ids) }
                else -> notices?.show(resources.getQuantityString(R.plurals.library_added, picks.size, picks.size)) { library.undoAdd(ids) }
            }
        }
    }

    if (choosingZones && chosen.isNotEmpty()) {
        BackHandler { choosingZones = false }
        AddManyScreen(chosen.size, zones, initialZoneId, current, onBack = { choosingZones = false }) { zoneIds, each, quantity, counts ->
            add(chosen, zoneIds, each, quantity, counts)
            leaveSelection()
        }
        return
    }
    if (selecting) BackHandler { leaveSelection() }

    val page = rows.firstOrNull { it.key == showing }.takeIf { !selecting }
    if (page != null) {
        BackHandler { showing = null }
        val wanted = current.libraryPhotos == true && page.thumbnail == null && page.speciesName != null
        val photo by produceState<LibraryPhoto?>(null, page.speciesName, wanted) { value = if (wanted) photos.photo(page.speciesName!!) else null }
        LibraryPlantPage(
            row = page,
            photo = photo,
            kb = loadedKb,
            locality = locality,
            all = gardenPlants,
            onBack = { showing = null },
            onAdd = { adding = page.key },
            onSave = page.speciesName?.takeIf { page.libraryId == null }?.let { name ->
                {
                    scope.launch {
                        library.save(listOf(name))
                        notices?.show(resources.getQuantityString(R.plurals.library_saved, 1, 1))
                    }
                }
            },
            onEnlarge = { viewing = page.key to photo },
        )
    } else DetailScaffold(
        title = stringResource(R.string.library_title),
        onBack = onBack,
        actions = {
            if (selecting) {
                TextButton(onClick = leaveSelection) { Text(stringResource(R.string.cancel)) }
            } else {
                TextButton(onClick = { selecting = true }) { Text(stringResource(R.string.review_select)) }
            }
        },
        floatingActionButton = {
            if (selecting) {
                HorizontalFloatingToolbar(expanded = true) {
                    if (mine) {
                        ToolbarButton(R.drawable.ic_delete, stringResource(R.string.library_remove_selected, chosen.size), chosen.isNotEmpty()) {
                            val ids = chosen.mapNotNull { it.libraryId }
                            val names = chosen.mapNotNull { it.speciesName }
                            scope.launch {
                                library.remove(ids)
                                notices?.show(resources.getQuantityString(R.plurals.library_removed, ids.size, ids.size)) { library.save(names) }
                            }
                            leaveSelection()
                        }
                    } else {
                        ToolbarButton(R.drawable.ic_bookmark, stringResource(R.string.library_save_selected, chosen.size), chosen.isNotEmpty()) {
                            val names = chosen.mapNotNull { it.speciesName }
                            scope.launch {
                                library.save(names)
                                notices?.show(resources.getQuantityString(R.plurals.library_saved, names.size, names.size))
                            }
                            leaveSelection()
                        }
                    }
                    ToolbarButton(R.drawable.ic_check, stringResource(R.string.library_add_selected, chosen.size), chosen.isNotEmpty()) { choosingZones = true }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = listPadding(padding, bottom = if (selecting) 112.dp else 24.dp),
        ) {
            item(key = "top") {
                Column(Modifier.readable().padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ChipGroup(
                        title = stringResource(R.string.library_show),
                        options = listOf(false, true),
                        selected = mine,
                        label = { stringResource(if (it) R.string.library_mine else R.string.library_all) },
                        onChoose = {
                            mine = it
                            selection = emptyList()
                        },
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.add_plant_search_label)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ListItem(
                        checked = addMode,
                        colors = checkedRowColors(),
                        onCheckedChange = { addMode = it },
                        leadingContent = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                        supportingContent = { Text(stringResource(R.string.library_add_mode_body)) },
                        trailingContent = { Switch(checked = addMode, onCheckedChange = null) },
                    ) { Text(stringResource(R.string.library_add_mode)) }
                    if (current.libraryPhotos == null) {
                        PhotosQuestion { on -> scope.launch { settings.update { it.copy(libraryPhotos = on) } } }
                    }
                    Text(
                        when {
                            selecting -> stringResource(R.string.library_select_hint)
                            mine && saved.isEmpty() -> stringResource(R.string.library_mine_empty)
                            mine && addMode -> stringResource(R.string.library_mine_hint)
                            mine -> stringResource(R.string.library_mine_hint_info)
                            addMode -> stringResource(R.string.library_all_hint)
                            else -> stringResource(R.string.library_all_hint_info)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (loadedKb != null && rows.isEmpty() && query.isNotBlank()) {
                        Text(stringResource(R.string.add_plant_none, query.trim()), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            items(rows, key = { it.key }) { row ->
                val wanted = current.libraryPhotos == true && row.thumbnail == null && row.speciesName != null
                val photo by produceState<LibraryPhoto?>(null, row.speciesName, wanted) { value = if (wanted) photos.photo(row.speciesName!!) else null }
                val picked = row.key in selection
                val details: @Composable () -> Unit = {
                    Column {
                        row.speciesName?.takeIf { it != row.title }?.let { Text(it, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        if (!mine && row.libraryId != null) Text(stringResource(R.string.library_in_mine), style = MaterialTheme.typography.labelMedium)
                        photo?.let { Text(stringResource(R.string.library_photo_credit, it.credit), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }
                if (selecting) {
                    ListItem(
                        checked = picked,
                        colors = checkedRowColors(),
                        onCheckedChange = { on -> selection = if (on) selection + row.key else selection - row.key },
                        leadingContent = { LibraryPicture(row, photo) },
                        supportingContent = details,
                        trailingContent = { Checkbox(checked = picked, onCheckedChange = null) },
                    ) { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                } else {
                    ListItem(
                        onClick = { if (addMode) adding = row.key else showing = row.key },
                        leadingContent = { LibraryPicture(row, photo, onEnlarge = { viewing = row.key to photo }) },
                        supportingContent = details,
                    ) { Text(row.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }

    viewing?.let { (key, photo) ->
        rows.firstOrNull { it.key == key }?.let { row ->
            val context = LocalContext.current
            val own = row.thumbnail?.let { File(context.filesDir, it) }
            val larger by produceState<File?>(null, photo) { value = if (own == null && photo != null) photos.large(photo) else null }
            val file = own ?: photo?.file
            if (file != null) {
                PhotoViewer(
                    file = file,
                    larger = larger,
                    title = row.title,
                    credit = if (own == null) photo?.credit else null,
                    description = stringResource(R.string.library_photo_of, row.title),
                    onDismiss = { viewing = null },
                )
            }
        }
    }

    rows.firstOrNull { it.key == adding }?.let { row ->
        AddToGardenSheet(
            title = row.title,
            zones = zones,
            initialZoneId = initialZoneId,
            askNickname = row.speciesName != null,
            onDismiss = { adding = null },
            manyZones = current.manyZones,
            onSave = { zoneIds, each, quantity, counts, nickname ->
                adding = null
                add(listOf(row), zoneIds, each, quantity, counts, nickname)
            },
        )
    }
}

/**
 * A plant's picture in the library: the user's own cut-out, else a photo fetched for the species, else its icon. With
 * [onEnlarge], a picture is its own button that opens it over the whole screen; the icon alone is not.
 */
@Composable
internal fun LibraryPicture(row: LibraryRow, photo: LibraryPhoto?, size: Dp = 56.dp, onEnlarge: (() -> Unit)? = null) {
    val enlarge = onEnlarge?.takeIf { row.thumbnail != null || photo != null }
    val label = stringResource(R.string.library_enlarge, row.title)
    val press = if (enlarge != null) Modifier.clip(MaterialTheme.shapes.medium).clickable(onClickLabel = label, role = Role.Button) { enlarge() }.semantics { contentDescription = label } else Modifier
    if (row.thumbnail != null || photo == null) {
        PlantAvatar(row.form, size, modifier = press, thumbnail = row.thumbnail)
    } else {
        Box(Modifier.size(size).then(press).clip(MaterialTheme.shapes.medium)) {
            AsyncImage(model = photo.file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Asks once whether photos of plants may be fetched, for the library and the weed lists, and says what is sent. */
@Composable
fun PhotosQuestion(onAnswer: (Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.library_photos_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.library_photos_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Button(onClick = { onAnswer(true) }) { Text(stringResource(R.string.library_photos_yes)) }
                OutlinedButton(onClick = { onAnswer(false) }) { Text(stringResource(R.string.library_photos_no)) }
            }
        }
    }
}

@Composable
private fun ToolbarButton(icon: Int, text: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null)
            Text(text)
        }
    }
}

/** The zones the chosen plants go to, how many of each, and whether several zones hold one plant or one each. */
@Composable
private fun AddManyScreen(
    count: Int,
    zones: List<io.github.wkoller.pollenplot.data.ZoneView>,
    initialZoneId: String?,
    settings: Settings,
    onBack: () -> Unit,
    onAdd: (zoneIds: List<String>, each: Boolean, quantity: Int, counts: Map<String, Int>?) -> Unit,
) {
    val known = zones.map { it.zone.id }.toSet()
    var zoneIds by rememberSaveable(stateSaver = NamesSaver) { mutableStateOf(listOfNotNull((initialZoneId ?: settings.lastZoneId)?.takeIf { it in known })) }
    var each by rememberSaveable { mutableStateOf(false) }
    var howMany by rememberSaveable(stateSaver = PlantCount.Saver) { mutableStateOf(PlantCount()) }
    DetailScaffold(title = pluralStringResource(R.plurals.library_add_title, count, count), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.l).readable(),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            ZonesChooser(zones, zoneIds) { zoneIds = it }
            ManyZonesChoice(zoneIds.size, settings.manyZones, each) { each = it }
            // The count is for each plant chosen.
            Text(stringResource(R.string.library_count_each_plant), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PlantCounts(zones, zoneIds, eachZone(settings.manyZones, each), howMany) { howMany = it }
            Button(
                onClick = {
                    val split = eachZone(settings.manyZones, each)
                    onAdd(zoneIds, split, howMany.total, howMany.countsFor(zoneIds, split))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.add_sheet_save))
            }
        }
    }
}
