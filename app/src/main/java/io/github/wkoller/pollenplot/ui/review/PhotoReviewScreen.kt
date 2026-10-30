package io.github.wkoller.pollenplot.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BatchAdd
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PhotoReview
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.id.ZoneSuggestion
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the review cards do, for both the Identify results and the Review queue. Work runs in the screen model's scope,
 * so it finishes even if the phone is turned meanwhile. A zone chosen for a plant is remembered and offered next time.
 */
class DetectionActions(private val garden: GardenRepository, private val settings: SettingsStore, private val scope: CoroutineScope) {
    fun add(detection: DetectionRecord, speciesName: String?, customName: String?, zoneId: String?, flowering: Boolean) {
        scope.launch {
            garden.confirmDetection(detection.id, speciesName, customName, zoneId, flowering = flowering)
            if (zoneId != null) settings.update { it.copy(lastZoneId = zoneId) }
        }
    }

    fun attach(detection: DetectionRecord, plantId: String, flowering: Boolean) {
        scope.launch { garden.attachDetection(detection.id, plantId, flowering) }
    }

    fun addSighting(detection: DetectionRecord, speciesName: String?, label: String, zoneId: String?, plantId: String?, behaviour: Behaviour?) {
        scope.launch {
            garden.confirmSighting(detection.id, speciesName, label, detection.group, zoneId, plantId, behaviour)
            if (zoneId != null) settings.update { it.copy(lastZoneId = zoneId) }
        }
    }

    fun reject(detection: DetectionRecord) {
        scope.launch { garden.rejectDetection(detection.id) }
    }

    fun undoReject(detection: DetectionRecord) {
        scope.launch { garden.reopenDetection(detection.id) }
    }

    fun rejectAll(detections: List<DetectionRecord>) {
        scope.launch { garden.rejectDetections(detections.map { it.id }) }
    }

    /** Adds several finds to one zone; [growing] holds the plants already growing there, by species. */
    fun addAll(adds: List<BatchAdd>, zoneId: String?, growing: Map<String, String>) {
        scope.launch {
            garden.confirmDetections(adds, zoneId, growing)
            if (zoneId != null) settings.update { it.copy(lastZoneId = zoneId) }
        }
    }
}

/** Keeps the selected finds, so a selection survives the phone being turned. */
private val SelectionSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })

/** Keeps each card's choice as text, so the choices survive the phone being turned. */
private val ChoicesSaver = listSaver<Map<String, ReviewChoice>, String>(
    save = { map -> map.flatMap { (id, choice) -> listOf(id, choice.save()) } },
    restore = { list -> list.chunked(2).mapNotNull { (id, text) -> ReviewChoice.restore(text)?.let { id to it } }.toMap() },
)

/**
 * A screen of photos and the plants found in each, one review card per plant. [reviews] is null while they load.
 * [top] and [bottom] add items around the photos, [photoHeading] goes under each photo, and [empty] is shown when there
 * is nothing to review. Choosing another plant opens a search over the whole screen. When [selectable], the finds
 * waiting for review can be selected, all the plants or all the wildlife at once, and dismissed or added to one zone.
 */
@Composable
fun PhotoReviewScreen(
    title: String,
    reviews: List<PhotoReview>?,
    actions: DetectionActions,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
    top: LazyListScope.() -> Unit = {},
    photoHeading: @Composable (PhotoReview) -> Unit = {},
    bottom: LazyListScope.() -> Unit = {},
    empty: @Composable (PaddingValues) -> Unit = {},
    onCompare: ((detectionId: String, speciesName: String) -> Unit)? = null,
    selectable: Boolean = false,
) {
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val sightings by views.sightings.collectAsStateWithLifecycle(initialValue = emptyList())
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    var choices by rememberSaveable(stateSaver = ChoicesSaver) { mutableStateOf(emptyMap<String, ReviewChoice>()) }
    var choosingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<String>()) }
    var choosingZone by rememberSaveable { mutableStateOf(false) }
    val callbacks = remember(actions) {
        ReviewCallbacks(
            onAdd = actions::add,
            onAttach = actions::attach,
            onReject = actions::reject,
            onUndoReject = actions::undoReject,
            onChooseAnother = { choosingFor = it.id },
            onOpenPlant = onOpenPlant,
            onCompare = onCompare?.let { compare -> { detection, species -> compare(detection.id, species) } },
            onAddSighting = actions::addSighting,
        )
    }

    val searchingFor = choosingFor
    val loadedKb = kb
    // An animal is looked for among the animals, a plant among the plants.
    val searchingGroup = searchingFor?.let { id -> reviews?.flatMap { it.detections }?.firstOrNull { it.id == id }?.group }
    val loadedFauna = fauna
    if (searchingFor != null && searchingGroup != null && searchingGroup.isWildlife && loadedFauna != null) {
        BackHandler { choosingFor = null }
        FaunaSearch(loadedFauna, searchingGroup, onBack = { choosingFor = null }) { name ->
            choices = choices + (searchingFor to ReviewChoice.Picked(name))
            choosingFor = null
        }
        return
    }
    if (searchingFor != null && loadedKb != null) {
        BackHandler { choosingFor = null }
        SpeciesSearch(loadedKb, locality, onBack = { choosingFor = null }) { name ->
            choices = choices + (searchingFor to ReviewChoice.Picked(name))
            choosingFor = null
        }
        return
    }

    val pending = reviews.orEmpty().flatMap { it.detections }.filter { it.status == DetectionStatus.NEEDS_REVIEW }
    // A find decided on its own card leaves the selection.
    val selected = pending.filter { it.id in selection }
    val leaveSelection = {
        selecting = false
        selection = emptySet()
        choosingZone = false
    }
    val nothingLeft = selecting && reviews != null && pending.isEmpty()
    LaunchedEffect(nothingLeft) { if (nothingLeft) leaveSelection() }

    if (choosingZone && selected.isNotEmpty()) {
        BackHandler { choosingZone = false }
        val adds = selected.mapNotNull { detection ->
            val choice = chosen(detection, choices[detection.id])
            if (detection.group.isWildlife) {
                animalPick(detection, choice, fauna)?.let { BatchAdd.Sighting(detection.id, it.speciesName, it.label, detection.group) }
            } else {
                plantPick(detection, choice)?.let { BatchAdd.Plant(detection.id, it.speciesName, it.customName, detection.flowering) }
            }
        }
        val zoneIds = zones.map { it.zone.id }.toSet()
        AddSelectedScreen(
            adds = adds.size,
            skipped = selected.size - adds.size,
            zones = zones,
            startZoneId = current?.lastZoneId?.takeIf { it in zoneIds },
            onBack = { choosingZone = false },
        ) { zoneId ->
            val growing = plants.filter { it.isGrowing && it.plant.zoneId == zoneId && it.plant.speciesName != null }
                .associate { it.plant.speciesName!! to it.plant.id }
            actions.addAll(adds, zoneId, growing)
            leaveSelection()
        }
        return
    }
    if (selecting) BackHandler { leaveSelection() }
    // The chips that select all plants or all wildlife come in above the first photo, so the list shows them.
    val listState = rememberLazyListState()
    LaunchedEffect(selecting) { if (selecting) listState.scrollToItem(0) }

    DetailScaffold(
        title = title,
        onBack = onBack,
        actions = {
            if (selectable && pending.isNotEmpty()) {
                if (selecting) {
                    TextButton(onClick = leaveSelection) { Text(stringResource(R.string.cancel)) }
                } else {
                    TextButton(onClick = { selecting = true }) { Text(stringResource(R.string.review_select)) }
                }
            }
        },
        floatingActionButton = {
            if (selecting) {
                SelectionToolbar(
                    count = selected.size,
                    onDismiss = {
                        actions.rejectAll(selected)
                        leaveSelection()
                    },
                    onAdd = { choosingZone = true },
                )
            }
        },
    ) { padding ->
        when {
            reviews == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { PollenLoadingIndicator() }
            reviews.isEmpty() -> empty(padding)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                // Room below the last card for the selection toolbar.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + if (selecting) 112.dp else 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                top()
                if (selecting) {
                    item(key = "select-groups") {
                        SelectGroups(pending, selection, Modifier.readable()) { selection = it }
                    }
                }
                val zoneIds = zones.map { it.zone.id }.toSet()
                reviews.forEach { review ->
                    item(key = "photo-${review.photo.id}") {
                        Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MarkedPhoto(review)
                            photoHeading(review)
                        }
                    }
                    // Plants in one photo usually grow together, so the zone the photo's other plants went into comes first.
                    val photoZones = review.detections.filter { it.status == DetectionStatus.CONFIRMED }
                        .map { d -> plants.firstOrNull { it.plant.id == d.plantId }?.plant?.zoneId }
                    val suggestedZone = ZoneSuggestion.suggest(photoZones, current?.lastZoneId, zoneIds)
                    itemsIndexed(review.detections, key = { _, d -> d.id }) { i, detection ->
                        if (selecting && detection.status == DetectionStatus.NEEDS_REVIEW) {
                            SelectRow(detection, checked = detection.id in selection, Modifier.readable()) { on ->
                                selection = if (on) selection + detection.id else selection - detection.id
                            }
                        }
                        DetectionReviewCard(
                            detection = detection,
                            number = i + 1,
                            total = review.detections.size,
                            kb = kb,
                            locality = locality,
                            zones = zones,
                            plants = plants,
                            choice = choices[detection.id],
                            onChoose = { choices = choices + (detection.id to it) },
                            suggestedZoneId = suggestedZone,
                            callbacks = callbacks,
                            modifier = Modifier.readable(),
                            fauna = fauna,
                            sightings = sightings,
                        )
                    }
                }
                bottom()
            }
        }
    }
}

/** Keeps content to a readable width on wide screens. */
fun Modifier.readable() = fillMaxWidth().wrapContentWidth().widthIn(max = 640.dp)

/** The photo, with a numbered marker in the middle of each plant found, matching the numbers on the cards. */
@Composable
fun MarkedPhoto(review: PhotoReview) {
    val context = LocalContext.current
    val file = remember(review.photo.path) { File(context.filesDir, review.photo.path) }
    val ratio = if (review.photo.height > 0) review.photo.width.toFloat() / review.photo.height else 4f / 3f
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (review.detections.size > 1) {
            val marker = 32.dp
            review.detections.forEachIndexed { i, detection ->
                val x = maxWidth * ((detection.box.left + detection.box.right) / 2f / BoxBp.FULL) - marker / 2
                val y = maxHeight * ((detection.box.top + detection.box.bottom) / 2f / BoxBp.FULL) - marker / 2
                val number = (i + 1).toString()
                // An outline keeps each number clear of the leaves behind it.
                Box(
                    Modifier.offset(x, y).size(marker).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clearAndSetSemantics { contentDescription = number },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Chips that select every plant, or every animal and fungus, waiting for review; choosing one again clears them. */
@Composable
private fun SelectGroups(pending: List<DetectionRecord>, selection: Set<String>, modifier: Modifier, onSelect: (Set<String>) -> Unit) {
    val plants = pending.filter { it.group == LifeGroup.PLANT }.map { it.id }.toSet()
    val wildlife = pending.filter { it.group.isWildlife }.map { it.id }.toSet()
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            plants to stringResource(R.string.review_select_plants, plants.size),
            wildlife to stringResource(R.string.review_select_wildlife, wildlife.size),
        ).filter { (ids, _) -> ids.isNotEmpty() }.forEach { (ids, label) ->
            val all = selection.containsAll(ids)
            FilterChip(
                selected = all,
                onClick = { onSelect(if (all) selection - ids else selection + ids) },
                label = { Text(label) },
                leadingIcon = if (all) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/** A checkbox above a card that waits for review, with words for a screen reader. */
@Composable
private fun SelectRow(detection: DetectionRecord, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(
        modifier.heightIn(min = 48.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            stringResource(if (detection.group.isWildlife) R.string.review_select_animal else R.string.review_select_plant),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

/** What to do with the selected finds: dismiss them, or add them to a zone. */
@Composable
private fun SelectionToolbar(count: Int, onDismiss: () -> Unit, onAdd: () -> Unit) {
    HorizontalFloatingToolbar(expanded = true) {
        TextButton(onClick = onDismiss, enabled = count > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = null)
                Text(stringResource(R.string.review_dismiss_selected, count))
            }
        }
        TextButton(onClick = onAdd, enabled = count > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null)
                Text(stringResource(R.string.review_add_selected, count))
            }
        }
    }
}

/**
 * The zone the selected finds go to. Each is added with the suggestion or species shown on its card; finds without
 * one stay in the queue.
 */
@Composable
private fun AddSelectedScreen(adds: Int, skipped: Int, zones: List<ZoneView>, startZoneId: String?, onBack: () -> Unit, onAdd: (String?) -> Unit) {
    var zoneId by rememberSaveable { mutableStateOf(startZoneId) }
    DetailScaffold(title = stringResource(R.string.review_add_selected_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(pluralStringResource(R.plurals.review_add_selected_body, adds, adds), style = MaterialTheme.typography.bodyLarge)
            if (skipped > 0) {
                Text(pluralStringResource(R.plurals.review_add_selected_skipped, skipped, skipped), style = MaterialTheme.typography.bodyLarge)
            }
            ZoneChooser(zones, zoneId) { zoneId = it }
            Button(onClick = { onAdd(zoneId) }, enabled = adds > 0) {
                Text(pluralStringResource(R.plurals.review_add_selected_button, adds, adds))
            }
        }
    }
}
