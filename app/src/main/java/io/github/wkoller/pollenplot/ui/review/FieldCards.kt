package io.github.wkoller.pollenplot.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.domain.garden.WildStatus
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.ui.plants.label

/**
 * What a plant found away from home would be in the user's garden, as an icon and words: native to the garden's state,
 * not on the weed lists for the garden's area, or not known yet. Then the sun and water it likes, when they are
 * recorded. A weed shows the weed notice instead.
 */
@Composable
fun WildStatusLine(species: Species, care: CareProfile, locality: Locality, modifier: Modifier = Modifier) {
    val roles = locality.roles(species, care)
    val status = WildStatus.of(roles, locality.weed(species).isWeed, locality.weeds.hasLists)
    val (icon, text) = when (status) {
        WildStatus.NATIVE_HERE -> R.drawable.ic_check_circle to if (locality.state != null) {
            stringResource(R.string.field_native_here, stringResource(locality.state.label))
        } else {
            stringResource(R.string.field_native_australia)
        }
        WildStatus.NOT_LISTED -> R.drawable.ic_info to stringResource(R.string.field_not_listed)
        WildStatus.UNKNOWN -> R.drawable.ic_info to stringResource(R.string.field_weeds_unknown)
        WildStatus.WEED -> return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
        val sun = care.sun?.sortedBy { it.ordinal }?.map { stringResource(it.label).lowercase() }
        val water = care.water?.let { stringResource(it.label).lowercase() }
        val likes = when {
            sun != null && water != null -> stringResource(R.string.field_likes_sun_water, sun.joinToString(" " + stringResource(R.string.field_or) + " "), water)
            sun != null -> stringResource(R.string.field_likes_sun, sun.joinToString(" " + stringResource(R.string.field_or) + " "))
            water != null -> stringResource(R.string.field_likes_water, water)
            else -> null
        }
        likes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * Saving a find to the wishlist, or letting it go. A weed in the garden's area asks first, because the wishlist is
 * for plants to grow.
 */
@Composable
internal fun FieldActions(canSave: Boolean, weed: Boolean, name: String?, onSave: () -> Unit, onLetGo: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.field_weed_confirm_title)) },
            text = { Text(stringResource(R.string.field_weed_confirm_body, name ?: stringResource(R.string.review_unnamed))) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onSave()
                }) { Text(stringResource(R.string.field_weed_confirm_save)) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val save = { if (weed) confirming = true else onSave() }
        if (weed) {
            OutlinedButton(onClick = save, enabled = canSave) { Text(stringResource(R.string.field_save)) }
        } else {
            Button(onClick = save, enabled = canSave) {
                Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.field_save), modifier = Modifier.padding(start = 8.dp))
            }
        }
        OutlinedButton(onClick = onLetGo) { Text(stringResource(R.string.field_let_go_button)) }
    }
}

/** A find saved to the wishlist, with a way to open it. */
@Composable
internal fun SavedFind(detection: DetectionRecord, kb: KnowledgeBase?, choice: ReviewChoice?, callbacks: ReviewCallbacks) {
    val pick = plantPick(detection, chosen(detection, choice))
    val species = pick?.speciesName?.let { kb?.species(it) }
    Header(detection.thumbnail, species?.displayName ?: pick?.customName ?: pick?.speciesName ?: stringResource(R.string.field_saved), species?.name?.takeIf { it != species.displayName })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_bookmark_filled), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.field_saved), style = MaterialTheme.typography.bodyLarge)
    }
    callbacks.onOpenWishlist?.let { open -> FilledTonalButton(onClick = open) { Text(stringResource(R.string.field_open_wishlist)) } }
}
