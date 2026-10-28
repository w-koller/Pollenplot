package io.github.wkoller.pollenplot.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PhotoReview
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.id.ZoneSuggestion
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
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
}

/** Keeps each card's choice as text, so the choices survive the phone being turned. */
private val ChoicesSaver = listSaver<Map<String, ReviewChoice>, String>(
    save = { map -> map.flatMap { (id, choice) -> listOf(id, choice.save()) } },
    restore = { list -> list.chunked(2).mapNotNull { (id, text) -> ReviewChoice.restore(text)?.let { id to it } }.toMap() },
)

/**
 * A screen of photos and the plants found in each, one review card per plant. [reviews] is null while they load.
 * [top] and [bottom] add items around the photos, [photoHeading] goes under each photo, and [empty] is shown when there
 * is nothing to review. Choosing another plant opens a search over the whole screen.
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

    DetailScaffold(title = title, onBack = onBack) { padding ->
        when {
            reviews == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { PollenLoadingIndicator() }
            reviews.isEmpty() -> empty(padding)
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                top()
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
