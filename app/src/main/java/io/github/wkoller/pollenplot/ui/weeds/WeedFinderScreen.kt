package io.github.wkoller.pollenplot.ui.weeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.domain.weeds.CouncilWeed
import io.github.wkoller.pollenplot.domain.weeds.WeedFeatures
import io.github.wkoller.pollenplot.domain.weeds.WeedFinder
import io.github.wkoller.pollenplot.domain.weeds.WeedResolver
import io.github.wkoller.pollenplot.domain.weeds.WeedStatus
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.plot.ChipGroup

/**
 * Finds a weed on the council's list by what it looks like: its type, flower colour and leaves, as the council's own
 * identification tool asks. Opened from a photo, the plants the identifier suggested for it come first.
 */
@Composable
fun WeedFinderScreen(
    views: GardenViews,
    garden: GardenRepository,
    detectionId: String?,
    onBack: () -> Unit,
    onOpen: (species: String, botanical: String) -> Unit,
) {
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val weeds = locality.weeds.councilWeeds
    val council = locality.weeds.council
    val suggested by produceState(emptyList<String>(), detectionId) {
        value = detectionId?.let { garden.detection(it) }?.candidates?.map { it.speciesName ?: it.label }.orEmpty()
    }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var colour by rememberSaveable { mutableStateOf<String?>(null) }
    var leafForm by rememberSaveable { mutableStateOf<String?>(null) }
    var arrangement by rememberSaveable { mutableStateOf<String?>(null) }
    val options = remember(weeds) { WeedFinder.options(weeds) }
    val found = remember(weeds, type, colour, leafForm, arrangement, suggested) {
        WeedFinder.find(weeds, WeedFeatures(type, colour, leafForm, arrangement), suggested)
    }
    val any = stringResource(R.string.finder_any)

    DetailScaffold(title = stringResource(R.string.finder_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (council == null || weeds.isEmpty()) {
                item { Text(stringResource(R.string.finder_unavailable), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp)) }
                return@LazyColumn
            }
            item {
                Text(stringResource(R.string.finder_body, council.name), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val chips = Modifier.padding(horizontal = 16.dp)
            item { Column(chips) { ChipGroup(stringResource(R.string.compare_type), listOf<String?>(null) + options.types, type, { it ?: any }) { type = it } } }
            item { Column(chips) { ChipGroup(stringResource(R.string.finder_flower_colour), listOf<String?>(null) + options.flowerColours, colour, { it ?: any }) { colour = it } } }
            item { Column(chips) { ChipGroup(stringResource(R.string.finder_leaf_form), listOf<String?>(null) + options.leafForms, leafForm, { it ?: any }) { leafForm = it } } }
            item { Column(chips) { ChipGroup(stringResource(R.string.finder_leaf_arrangement), listOf<String?>(null) + options.leafArrangements, arrangement, { it ?: any }) { arrangement = it } } }
            item {
                Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(R.plurals.finder_count, found.size, found.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (type != null || colour != null || leafForm != null || arrangement != null) {
                        TextButton(onClick = {
                            type = null
                            colour = null
                            leafForm = null
                            arrangement = null
                        }) { Text(stringResource(R.string.finder_clear)) }
                    }
                }
            }
            items(found, key = { it.botanical + it.common }) { weed ->
                FinderRow(weed, locality, suggestedForPhoto = WeedFinder.isSuggested(weed, suggested)) { onOpen(nameOf(weed), weed.botanical) }
            }
        }
    }
}

/** The name to look up a council entry by: its first species, or its genus when it covers a whole genus. */
private fun nameOf(weed: CouncilWeed): String =
    (weed.names.species.firstOrNull() ?: weed.names.genera.keys.first()).replaceFirstChar(Char::uppercaseChar)

@Composable
private fun FinderRow(weed: CouncilWeed, locality: Locality, suggestedForPhoto: Boolean, onClick: () -> Unit) {
    // The entry's own status, rather than a species' status, since an entry can cover a whole genus.
    val status = WeedStatus(WeedResolver.levelOf(weed), council = weed)
    ListItem(
        onClick = onClick,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(weed.botanical, fontStyle = FontStyle.Italic, maxLines = 2, overflow = TextOverflow.Ellipsis)
                weedHeadline(status, locality)?.let { Text(it) }
                if (suggestedForPhoto) Text(stringResource(R.string.finder_suggested), color = MaterialTheme.colorScheme.primary)
            }
        },
    ) { Text(weed.common, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}
