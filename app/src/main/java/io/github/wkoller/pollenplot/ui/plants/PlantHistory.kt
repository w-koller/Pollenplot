package io.github.wkoller.pollenplot.ui.plants

import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.components.FactCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.StoredPhoto
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.LocalNotices
import io.github.wkoller.pollenplot.ui.review.PlantChooser
import io.github.wkoller.pollenplot.ui.review.ConfidenceBadge
import io.github.wkoller.pollenplot.ui.review.Cutout
import io.github.wkoller.pollenplot.ui.review.confidenceLabel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The suggestion that matched the plant the find was added to, or else the top one. */
private fun DetectionRecord.matched(speciesName: String?): Candidate? =
    candidates.firstOrNull { speciesName != null && it.speciesName == speciesName } ?: candidates.firstOrNull()

private fun Candidate.title(kb: KnowledgeBase?) = speciesName?.let { kb?.species(it)?.displayName } ?: common ?: label

/**
 * The photos a plant was found in, the newest first, each with its date, how sure the models were and what else they
 * suggested. Choosing one opens the photo.
 */
@Composable
fun PhotoHistoryCard(finds: List<DetectionRecord>, speciesName: String?, kb: KnowledgeBase?, onOpen: (DetectionRecord) -> Unit) {
    FactCard(R.drawable.ic_photo_library, stringResource(R.string.history_title)) {
        Text(stringResource(R.string.history_body), style = MaterialTheme.typography.bodyMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(finds, key = { it.id }) { find ->
                val match = find.matched(speciesName)
                val others = find.candidates.filter { it != match }.take(2).map { it.title(kb) }
                Column(
                    Modifier.width(120.dp).clickable(onClickLabel = stringResource(R.string.history_open), role = Role.Button) { onOpen(find) },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Cutout(find.thumbnail, 120.dp)
                    Text(stringResource(R.string.history_item, UiFormat.shortDate(find.takenAt)), style = MaterialTheme.typography.labelLarge)
                    match?.let { ConfidenceBadge(it.scoreBp) }
                    if (others.isNotEmpty()) {
                        Text(stringResource(R.string.history_also, others.joinToString(", ")), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * One photo a plant was found in, with the plant's region outlined, the day it was taken and every suggestion. With
 * the garden's [views], the photo can be made the plant's picture, or moved to the plant it really shows.
 */
@Composable
fun PlantPhotoScreen(
    garden: GardenRepository,
    knowledge: KnowledgeRepository,
    photoId: String,
    detectionId: String?,
    views: GardenViews? = null,
    onBack: () -> Unit,
) {
    var photo by remember { mutableStateOf<StoredPhoto?>(null) }
    var find by remember { mutableStateOf<DetectionRecord?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val plants by remember(views) { views?.plants ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var moving by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val notices = LocalNotices.current
    val resources = LocalResources.current
    val owner = find?.plantId?.let { id -> plants.firstOrNull { it.plant.id == id } }

    val moved = find
    if (moving && moved != null && owner != null) {
        BackHandler { moving = false }
        PlantChooser(plants.filter { it.plant.id != owner.plant.id }, speciesName = null, onBack = { moving = false }, title = stringResource(R.string.photo_move_title)) { plantId ->
            moving = false
            val to = plants.firstOrNull { it.plant.id == plantId }?.title.orEmpty()
            scope.launch {
                garden.moveDetection(moved.id, plantId)
                notices?.show(resources.getString(R.string.photo_moved, to)) { garden.moveDetection(moved.id, owner.plant.id) }
                // The photo has left the plant whose page is underneath, so the screen closes.
                withContext(Dispatchers.Main) { onBack() }
            }
        }
        return
    }
    // A photo in the history does not change while it is open, so it is read once rather than watched.
    LaunchedEffect(photoId, detectionId) {
        photo = garden.photo(photoId)
        find = detectionId?.let { garden.detection(it) }
        loaded = true
    }
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    DetailScaffold(title = stringResource(R.string.photo_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val shown = photo
            if (shown == null) {
                if (loaded) item { Text(stringResource(R.string.photo_missing), style = MaterialTheme.typography.bodyLarge) }
                return@LazyColumn
            }
            item { OutlinedPhoto(shown, find?.box, Modifier.readable()) }
            find?.let { record ->
                item { Text(stringResource(R.string.photo_taken, UiFormat.longDate(record.takenAt)), style = MaterialTheme.typography.bodyLarge) }
                if (owner != null) {
                    item {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (owner.plant.thumbnail == record.thumbnail) {
                                Text(stringResource(R.string.photo_is_picture), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                            } else {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        garden.setPicture(owner.plant.id, record.id)
                                        notices?.show(resources.getString(R.string.photo_picture_set))
                                    }
                                }) { Text(stringResource(R.string.photo_use_picture)) }
                            }
                            TextButton(onClick = { moving = true }) { Text(stringResource(R.string.photo_move)) }
                        }
                    }
                }
                if (record.candidates.isNotEmpty()) {
                    item { Text(stringResource(R.string.photo_suggestions), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    items(record.candidates) { candidate ->
                        val species = candidate.speciesName?.let { kb?.species(it) }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(candidate.title(kb), style = MaterialTheme.typography.bodyLarge)
                            (species?.name ?: candidate.label.takeIf { candidate.common != null })?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(stringResource(confidenceLabel(candidate.scoreBp)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** The whole photo, with an outline around [box] when there is one. */
@Composable
private fun OutlinedPhoto(photo: StoredPhoto, box: BoxBp?, modifier: Modifier) {
    val context = LocalContext.current
    val file = remember(photo.path) { File(context.filesDir, photo.path) }
    val ratio = if (photo.height > 0) photo.width.toFloat() / photo.height else 4f / 3f
    BoxWithConstraints(modifier.aspectRatio(ratio).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (box != null) {
            val full = BoxBp.FULL.toFloat()
            Box(
                Modifier
                    .offset(maxWidth * (box.left / full), maxHeight * (box.top / full))
                    .size(maxWidth * ((box.right - box.left) / full), maxHeight * ((box.bottom - box.top) / full))
                    .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
            )
        }
    }
}
