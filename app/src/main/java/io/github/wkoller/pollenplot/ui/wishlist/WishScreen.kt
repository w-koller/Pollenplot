package io.github.wkoller.pollenplot.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.WishViews
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.garden.WishStatus
import io.github.wkoller.pollenplot.domain.garden.Wishlist
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.components.RoleBadges
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import io.github.wkoller.pollenplot.ui.plants.icon
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.tone
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.review.WildStatusLine
import io.github.wkoller.pollenplot.ui.review.readable
import io.github.wkoller.pollenplot.ui.weeds.NativeAlternatives
import io.github.wkoller.pollenplot.ui.weeds.WeedHelp
import io.github.wkoller.pollenplot.ui.weeds.WeedNotice
import kotlinx.coroutines.launch

/**
 * One plant on the wishlist: what it would be in the garden, its badges, what its zone would gain, and the details the
 * user keeps about it. Plant it opens Add a plant with the species and zone, and the wish is marked planted once the
 * plant is added.
 */
@Composable
fun WishScreen(
    id: String,
    wishViews: WishViews,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    help: WeedHelp,
    onBack: () -> Unit,
    onPlant: (speciesName: String?, zoneId: String?) -> Unit,
) {
    val view by wishViews.wish(id).collectAsStateWithLifecycle(initialValue = null)
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var place by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var status by rememberSaveable { mutableStateOf(WishStatus.WANTED) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    val current = view
    // The form starts from the stored wish once, and then keeps the user's edits.
    LaunchedEffect(current != null) {
        if (current != null && !loaded) {
            place = current.wish.placeName
            priceText = current.wish.priceCents?.let { java.math.BigDecimal.valueOf(it, 2).toPlainString() }.orEmpty()
            notes = current.wish.notes
            zoneId = current.wish.targetZoneId
            status = current.wish.status
            loaded = true
        }
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.wish_delete_title)) },
            text = { Text(stringResource(R.string.wish_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch { help.wishes.delete(id) }
                    onBack()
                }) { Text(stringResource(R.string.wish_delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    DetailScaffold(
        title = current?.title ?: stringResource(R.string.wishlist_title),
        onBack = onBack,
        actions = {
            if (current != null) {
                TextButton(onClick = { deleting = true }) { Text(stringResource(R.string.wish_delete)) }
            }
        },
    ) { padding ->
        if (current == null || !loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { PollenLoadingIndicator() }
            return@DetailScaffold
        }
        val price = Wishlist.parsePrice(priceText)
        val priceError = priceText.isNotBlank() && price == null
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                WishPicture(current.wish.thumbnail)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(current.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    current.scientificName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
                    WishStatusLabel(current.wish.status)
                }
            }
            val species = current.species
            if (current.weed.isWeed) {
                WeedNotice(current.weed, locality)
                NativeAlternatives(help, species?.let { listOf(it.name) + it.synonyms } ?: listOfNotNull(current.wish.speciesName), kb, locality)
            } else if (species != null && current.care != null) {
                WildStatusLine(species, current.care.care, locality)
            }
            if (current.roles.isNotEmpty()) RoleBadges(current.roles)
            if (current.zoneName != null && current.zoneEffect.isNotEmpty()) {
                Text(stringResource(R.string.wish_zone_effect, current.zoneName), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.zoneEffect.forEach { Badge(it.icon, stringResource(it.label), it.tone) }
                }
            }

            OutlinedTextField(
                value = place,
                onValueChange = { place = it },
                label = { Text(stringResource(R.string.wish_place)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(stringResource(R.string.wish_price)) },
                singleLine = true,
                isError = priceError,
                supportingText = if (priceError) {
                    { Text(stringResource(R.string.wish_price_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            ZoneChooser(zones, zoneId, title = stringResource(R.string.field_zone)) { zoneId = it }
            ChipGroup(stringResource(R.string.wish_status), WishStatus.entries, status, { stringResource(it.label) }) { status = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.honey_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val updated = current.wish.copy(placeName = place, priceCents = price, notes = notes, targetZoneId = zoneId, status = status)
                        scope.launch { help.wishes.update(updated) }
                        onBack()
                    },
                    enabled = !priceError,
                ) { Text(stringResource(R.string.wish_save)) }
                if (current.wish.status != WishStatus.PLANTED && (current.wish.speciesName != null || current.wish.customName != null)) {
                    OutlinedButton(onClick = { onPlant(current.wish.speciesName, zoneId) }) {
                        Icon(painterResource(R.drawable.ic_potted_plant), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.wish_plant), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
