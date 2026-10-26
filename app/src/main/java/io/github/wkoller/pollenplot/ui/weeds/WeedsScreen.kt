package io.github.wkoller.pollenplot.ui.weeds

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.weeds.BrisbaneWeeds
import io.github.wkoller.pollenplot.domain.weeds.NearbyWeed
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.plants.PlantRow
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import io.github.wkoller.pollenplot.weeds.UpdateState
import io.github.wkoller.pollenplot.weeds.WeedSyncWorker
import java.net.URLEncoder

/**
 * The weeds for the garden's area: the plants in the garden that are weeds there, the weeds recorded near it, and
 * where the information comes from.
 */
@Composable
fun WeedsScreen(
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onCheckWeed: () -> Unit,
    onSetLocation: () -> Unit,
) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val place by remember { settings.settings }.collectAsStateWithLifecycle(initialValue = null)
    val update by remember { WeedSyncWorker.state(context) }.collectAsStateWithLifecycle(initialValue = UpdateState.IDLE)
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val weeds = locality.weeds
    val growingWeeds = plants.filter { it.isGrowing && it.weed.isWeed }.sortedBy { it.weed.level.ordinal }

    DetailScaffold(title = stringResource(R.string.weeds_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val updated = weeds.updatedAt
                    Text(
                        if (updated != null) stringResource(R.string.weeds_updated, UiFormat.longDate(updated)) else stringResource(R.string.weeds_never_updated),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (weeds.lastUpdateFailed && update == UpdateState.IDLE) {
                        Text(stringResource(R.string.weeds_update_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    when (update) {
                        UpdateState.RUNNING -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            PollenLoadingIndicator(Modifier.size(32.dp))
                            Text(stringResource(R.string.weeds_updating), style = MaterialTheme.typography.bodyMedium)
                        }
                        UpdateState.WAITING -> Text(stringResource(R.string.weeds_update_waiting), style = MaterialTheme.typography.bodyMedium)
                        UpdateState.IDLE -> OutlinedButton(onClick = { WeedSyncWorker.runNow(context) }) {
                            Icon(painterResource(R.drawable.ic_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.weeds_update_now), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Button(onClick = onCheckWeed) {
                        Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.weeds_check), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (place?.gardenPlace == null) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.weeds_no_location), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = onSetLocation) {
                            Icon(painterResource(R.drawable.ic_location_on), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.weeds_set_location), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

            item { SectionHeading(stringResource(R.string.weeds_in_garden)) }
            if (growingWeeds.isEmpty()) {
                item {
                    Text(
                        stringResource(if (weeds.hasLists) R.string.weeds_in_garden_none else R.string.weed_lists_missing),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                items(growingWeeds, key = { "plant-" + it.plant.id }) { view ->
                    Column {
                        PlantRow(view, onClick = { onOpenPlant(view.plant.id) })
                        WeedNotice(view.weed, locality, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                    }
                }
            }

            val nearby = weeds.nearby
            if (nearby != null) {
                item { SectionHeading(stringResource(R.string.weeds_nearby)) }
                item {
                    Text(
                        if (weeds.nearbyWeeds.isEmpty()) {
                            stringResource(R.string.weeds_nearby_none, nearby.radiusKm, nearby.sinceYear)
                        } else {
                            stringResource(R.string.weeds_nearby_body, nearby.radiusKm, nearby.sinceYear)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(weeds.nearbyWeeds, key = { "nearby-" + it.name }) { weed ->
                    NearbyWeedRow(weed, commonName = kb?.species(weed.name)?.common?.firstOrNull() ?: weed.status.listedCommon, locality = locality) {
                        val council = weeds.council
                        val entry = weed.status.council
                        if (council != null && entry != null && council.format == WeedSources.BRISBANE_OPEN_DATA) {
                            uri.openUri(BrisbaneWeeds.profileUrl(council.site, entry))
                        } else {
                            weeds.weedsAustralia?.let { uri.openUri(it.search.replace("{name}", URLEncoder.encode(weed.name, "UTF-8"))) }
                        }
                    }
                }
            }

            item { SectionHeading(stringResource(R.string.weeds_identify_heading)) }
            weeds.weedsAustralia?.let { links ->
                item {
                    ListItem(
                        onClick = { uri.openUri(links.identify) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                        supportingContent = { Text(stringResource(R.string.weeds_identify_key_body)) },
                    ) { Text(stringResource(R.string.weeds_identify_key)) }
                }
            }
            weeds.council?.let { council ->
                item {
                    ListItem(
                        onClick = { uri.openUri(council.site) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                    ) { Text(stringResource(R.string.weeds_council_site, council.name)) }
                }
            }

            item { SectionHeading(stringResource(R.string.weeds_sources_heading)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val sources = listOfNotNull(
                        weeds.council?.let { stringResource(R.string.weeds_source_council, it.name) },
                        stringResource(R.string.weeds_source_wons),
                        stringResource(R.string.weeds_source_alert),
                        nearby?.let { stringResource(R.string.weeds_source_nearby, it.radiusKm) },
                    )
                    sources.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    weeds.state?.let { agency ->
                        LinkButton(R.drawable.ic_open_in_new, agency.agency) { uri.openUri(agency.url) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyWeedRow(weed: NearbyWeed, commonName: String?, locality: Locality, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (commonName != null) Text(weed.name, fontStyle = FontStyle.Italic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                weedHeadline(weed.status, locality)?.let { Text(it) }
                Text(pluralStringResource(R.plurals.weeds_records, weed.records, weed.records), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
    ) {
        Text(commonName ?: weed.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontStyle = if (commonName == null) FontStyle.Italic else null, modifier = Modifier.fillMaxWidth())
    }
}
