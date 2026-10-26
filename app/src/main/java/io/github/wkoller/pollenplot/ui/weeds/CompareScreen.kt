package io.github.wkoller.pollenplot.ui.weeds

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.weeds.BrisbaneWeeds
import io.github.wkoller.pollenplot.domain.weeds.CouncilWeed
import io.github.wkoller.pollenplot.domain.weeds.RecordPhoto
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.review.Cutout
import io.github.wkoller.pollenplot.weeds.WeedPhotos
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder

private sealed interface Photos {
    data object Loading : Photos

    data class Found(val photos: List<RecordPhoto>) : Photos

    data object Failed : Photos
}

/**
 * Compares a plant with photos of [species] recorded in Australia, beside the user's own photo when there is one, with
 * what the council's list says to look for and links to read more. [botanical] picks the council's entry when the
 * comparison started from it, such as a whole genus in the Weed finder.
 */
@Composable
fun CompareScreen(
    species: String,
    botanical: String?,
    detectionId: String?,
    plantId: String?,
    views: GardenViews,
    garden: GardenRepository,
    knowledge: KnowledgeRepository,
    photos: WeedPhotos,
    onBack: () -> Unit,
    onOpenFinder: () -> Unit,
) {
    val uri = LocalUriHandler.current
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val yours by produceState<String?>(null, detectionId, plantId) {
        value = detectionId?.let { garden.detection(it)?.thumbnail } ?: plantId?.let { garden.plant(it)?.thumbnail }
    }
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val found by produceState<Photos>(Photos.Loading, species, attempt) {
        value = Photos.Loading
        value = try {
            Photos.Found(photos.photos(species))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Photos.Failed
        }
    }
    val known = kb?.species(species)
    val status = known?.let(locality::weed) ?: locality.weed(species)
    val council = locality.weeds.council
    val entry = botanical?.let { b -> locality.weeds.councilWeeds.firstOrNull { it.botanical == b } } ?: status.council
    val title = known?.displayName ?: entry?.common ?: status.listedCommon ?: species

    DetailScaffold(title = stringResource(R.string.compare_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    if (title != species) Text(entry?.botanical ?: species, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                    WeedNotice(status, locality, Modifier.padding(top = 4.dp))
                }
            }
            yours?.let { thumbnail ->
                item { Heading(stringResource(R.string.compare_yours)) }
                item { Cutout(thumbnail, 160.dp) }
            }
            item { Heading(stringResource(R.string.compare_recorded)) }
            when (val shown = found) {
                Photos.Loading -> item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PollenLoadingIndicator(Modifier.size(32.dp))
                        Text(stringResource(R.string.compare_loading), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Photos.Failed -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(stringResource(R.string.compare_failed), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { attempt++ }) { Text(stringResource(R.string.compare_try_again)) }
                    }
                }
                is Photos.Found -> if (shown.photos.isEmpty()) {
                    item { Text(stringResource(R.string.compare_none), style = MaterialTheme.typography.bodyLarge) }
                } else {
                    items(shown.photos.chunked(2)) { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { photo -> PhotoTile(photo, photos, Modifier.weight(1f)) }
                            if (pair.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                    item { Text(stringResource(R.string.compare_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (entry != null && council != null) {
                item { Heading(stringResource(R.string.compare_features)) }
                item { Features(entry) }
                item { Text(stringResource(R.string.compare_features_source, council.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                Column {
                    if (council != null && entry != null && council.format == WeedSources.BRISBANE_OPEN_DATA) {
                        LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.weed_link_council, council.name)) {
                            uri.openUri(BrisbaneWeeds.profileUrl(council.site, entry))
                        }
                    }
                    locality.weeds.weedsAustralia?.let { links ->
                        LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.weed_link_weeds_australia)) {
                            uri.openUri(links.search.replace("{name}", URLEncoder.encode(species, "UTF-8")))
                        }
                        LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.weeds_identify_key)) { uri.openUri(links.identify) }
                    }
                    if (locality.weeds.councilWeeds.isNotEmpty()) {
                        LinkButton(R.drawable.ic_search, stringResource(R.string.finder_title)) { onOpenFinder() }
                    }
                }
            }
        }
    }
}

/** A section heading. The list is already inset from the screen's edges, so it has no padding of its own at the sides. */
@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() },
    )
}

/** What the council's list says to look for. */
@Composable
private fun Features(weed: CouncilWeed) {
    val words = listWords()
    fun sentence(values: List<String>) = UiFormat.joinAnd(values.mapIndexed { i, v -> if (i == 0) v else v.replaceFirstChar(Char::lowercaseChar) }, words)
    val rows = listOfNotNull(
        weed.type?.let { R.string.compare_type to it },
        (weed.leafForm + weed.leafArrangement).takeIf { it.isNotEmpty() }?.let { R.string.compare_leaves to sentence(it) },
        weed.flowerColours.takeIf { it.isNotEmpty() }?.let { R.string.compare_flowers to sentence(it) },
        weed.floweringTime?.let { R.string.compare_flowering to it },
        weed.family?.let { R.string.compare_family to it },
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(0.35f))
                Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(0.65f))
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: RecordPhoto, photos: WeedPhotos, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, photo.thumbnail) { value = photos.image(photo.thumbnail) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            image?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(
            listOfNotNull(photo.creator, photo.source, photo.licence).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
