package io.github.wkoller.pollenplot.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.assistant.DraftedCare
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.plants.FactCard
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.plants.toleranceLabel

/** Where drafting a plant's care is: not asked, being written, or failed. */
enum class DraftState { IDLE, WORKING, FAILED }

/**
 * The care of a plant the knowledge base does not have: an offer to have Gemma 4 draft it, or the draft, always marked
 * as written by Gemma 4, which the user can keep or remove. A draft never becomes part of the knowledge base.
 */
@Composable
fun CareDraftCard(
    draft: Pair<DraftedCare, Boolean>?,
    ready: Boolean,
    state: DraftState,
    onDraft: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    onOpenModels: () -> Unit,
) {
    if (draft == null) {
        FactCard(R.drawable.ic_forum, stringResource(R.string.draft_heading)) {
            Text(stringResource(R.string.draft_none), style = MaterialTheme.typography.bodyLarge)
            when {
                !ready -> {
                    Text(stringResource(R.string.draft_needs_gemma), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onOpenModels) { Text(stringResource(R.string.ask_open_models)) }
                }
                state == DraftState.WORKING -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    PollenLoadingIndicator(Modifier.size(32.dp))
                    Text(stringResource(R.string.draft_working), style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    Text(stringResource(if (state == DraftState.FAILED) R.string.draft_failed else R.string.draft_offer), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onDraft) { Text(stringResource(R.string.draft_make)) }
                }
            }
        }
        return
    }
    val (care, kept) = draft
    val sun = mutableListOf<String>()
    for (light in care.sun) sun += stringResource(light.label)
    val soils = mutableListOf<String>()
    for (soil in care.soils) soils += stringResource(soil.label)
    val words = listWords()
    val locale = LocalConfiguration.current.locales[0]
    FactCard(R.drawable.ic_forum, stringResource(R.string.draft_title)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.draft_badge), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sun.isNotEmpty()) Line(stringResource(R.string.draft_light, sun.joinToString(", ")))
            care.water?.let { Line(stringResource(R.string.plant_water_need, stringResource(it.label))) }
            care.drought?.let { Line(stringResource(R.string.plant_drought, stringResource(toleranceLabel(it)))) }
            care.frost?.let { Line(stringResource(R.string.plant_frost, stringResource(toleranceLabel(it)))) }
            if (soils.isNotEmpty()) Line(stringResource(R.string.plant_soils, soils.joinToString(", ")))
            if (care.flowering.isNotEmpty()) Line(stringResource(R.string.flowering_months, UiFormat.months(care.floweringMonths, words, locale)))
            care.heightMm.takeIf { it.size == 2 }?.let { (low, high) ->
                Line(stringResource(R.string.plant_height, stringResource(R.string.plant_range_metres, UiFormat.metresFromMm(low), UiFormat.metresFromMm(high))))
            }
            care.widthMm.takeIf { it.size == 2 }?.let { (low, high) ->
                Line(stringResource(R.string.plant_width, stringResource(R.string.plant_range_metres, UiFormat.metresFromMm(low), UiFormat.metresFromMm(high))))
            }
            care.nativeToAustralia?.let { Line(stringResource(if (it) R.string.draft_native else R.string.draft_not_native)) }
            care.toxicToPets?.let { Line(stringResource(R.string.safety_pets, stringResource(it.label))) }
            care.note?.let { Line(it) }
        }
        if (kept) Text(stringResource(R.string.draft_kept), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!kept) Button(onClick = onKeep) { Text(stringResource(R.string.draft_keep)) }
            OutlinedButton(onClick = onRemove) { Text(stringResource(R.string.draft_remove)) }
        }
    }
}

@Composable
private fun Line(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)
