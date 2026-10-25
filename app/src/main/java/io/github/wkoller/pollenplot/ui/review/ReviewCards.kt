package io.github.wkoller.pollenplot.ui.review

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.Confidence
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRoles
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.RoleIcons
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import java.io.File

/** Suggestions below this score are left off the card; the user can still search for the plant. */
private const val MIN_SHOWN_SCORE_BP = 500

/** The card lists at most this many suggestions. */
private const val MAX_SHOWN = 3

/** What a review card does when the user decides. The screen passes these on to the database. */
class ReviewCallbacks(
    val onAdd: (detection: DetectionRecord, speciesName: String?, customName: String?, zoneId: String?, flowering: Boolean) -> Unit,
    val onAttach: (detection: DetectionRecord, plantId: String, flowering: Boolean) -> Unit,
    val onReject: (DetectionRecord) -> Unit,
    val onUndoReject: (DetectionRecord) -> Unit,
    val onChooseAnother: (DetectionRecord) -> Unit,
    val onOpenPlant: (String) -> Unit,
)

/**
 * One plant found in a photo: its cut-out, the suggestions for what it is, where it grows, whether it was in flower,
 * and buttons to add it or say it is not a plant. When the garden already has that species, the card offers to add the
 * photo to that plant instead. Once decided, the card says what happened; this comes from the stored detection, so it
 * stays right after the phone is turned.
 */
@Composable
fun DetectionReviewCard(
    detection: DetectionRecord,
    number: Int,
    total: Int,
    kb: KnowledgeBase?,
    state: AuState?,
    zones: List<ZoneView>,
    plants: List<PlantView>,
    choice: ReviewChoice?,
    onChoose: (ReviewChoice) -> Unit,
    suggestedZoneId: String?,
    callbacks: ReviewCallbacks,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (total > 1) {
                Text(stringResource(R.string.review_plant_number, number, total), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            when (detection.status) {
                DetectionStatus.NEEDS_REVIEW -> Undecided(detection, kb, state, zones, plants, choice, onChoose, suggestedZoneId, callbacks)
                DetectionStatus.CONFIRMED -> {
                    val plant = plants.firstOrNull { it.plant.id == detection.plantId }
                    Header(detection.thumbnail, plant?.title ?: stringResource(R.string.review_added), plant?.scientificName)
                    Text(
                        plant?.zoneName?.let { stringResource(R.string.review_added_to_zone, it) } ?: stringResource(R.string.review_added),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (plant != null) {
                        FilledTonalButton(onClick = { callbacks.onOpenPlant(plant.plant.id) }) { Text(stringResource(R.string.review_open_plant)) }
                    }
                }
                DetectionStatus.REJECTED -> {
                    Header(detection.thumbnail, stringResource(R.string.review_rejected_title), null)
                    Text(stringResource(R.string.review_rejected), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { callbacks.onUndoReject(detection) }) { Text(stringResource(R.string.review_undo)) }
                }
            }
        }
    }
}

/**
 * What the user says a found plant is: one of the suggestions, or a plant they searched for. The screen keeps each
 * card's choice, saved as text so it survives the phone being turned.
 */
sealed interface ReviewChoice {
    data class Suggestion(val index: Int) : ReviewChoice

    data class Picked(val speciesName: String) : ReviewChoice

    fun save(): String = when (this) {
        is Suggestion -> "s:$index"
        is Picked -> "p:$speciesName"
    }

    companion object {
        fun restore(text: String?): ReviewChoice? = when {
            text == null -> null
            text.startsWith("s:") -> text.removePrefix("s:").toIntOrNull()?.let(::Suggestion)
            text.startsWith("p:") -> Picked(text.removePrefix("p:"))
            else -> null
        }
    }
}

@Composable
private fun Undecided(
    detection: DetectionRecord,
    kb: KnowledgeBase?,
    state: AuState?,
    zones: List<ZoneView>,
    plants: List<PlantView>,
    savedChoice: ReviewChoice?,
    onChoose: (ReviewChoice) -> Unit,
    suggestedZoneId: String?,
    callbacks: ReviewCallbacks,
) {
    var ownZone by rememberSaveable(detection.id) { mutableStateOf<String?>(null) }
    var zoneChosen by rememberSaveable(detection.id) { mutableStateOf(false) }
    var flowering by rememberSaveable(detection.id) { mutableStateOf(detection.flowering) }

    // The best suggestion is chosen until the user chooses something else.
    val choice = savedChoice ?: if (detection.candidates.isEmpty()) null else ReviewChoice.Suggestion(0)
    val shown = detection.candidates.withIndex()
        .filter { (i, c) -> i == 0 || c.scoreBp >= MIN_SHOWN_SCORE_BP }
        .take(MAX_SHOWN)
    val chosenCandidate = (choice as? ReviewChoice.Suggestion)?.let { detection.candidates.getOrNull(it.index) }
    val chosenSpeciesName = when (choice) {
        is ReviewChoice.Picked -> choice.speciesName
        is ReviewChoice.Suggestion -> chosenCandidate?.speciesName
        null -> null
    }
    val chosenSpecies = chosenSpeciesName?.let { kb?.species(it) }
    val zoneId = if (zoneChosen) ownZone else suggestedZoneId
    val existing = chosenSpeciesName?.let { name -> plants.firstOrNull { it.isGrowing && it.plant.speciesName == name } }

    Header(
        thumbnail = detection.thumbnail,
        title = chosenSpecies?.displayName ?: chosenCandidate?.label ?: stringResource(R.string.review_unnamed),
        subtitle = chosenSpecies?.let { species -> species.name.takeIf { it != species.displayName } },
    ) {
        if (chosenCandidate != null) ConfidenceBadge(chosenCandidate.scoreBp)
        if (chosenSpecies != null && kb != null) {
            RoleIcons(EcoRoles.of(chosenSpecies, CareResolver.resolve(kb, chosenSpecies).care, state), max = 4, size = 24.dp)
        } else if (chosenCandidate != null) {
            Text(stringResource(R.string.review_not_in_kb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (shown.isNotEmpty() || choice is ReviewChoice.Picked) {
        Text(stringResource(R.string.review_suggestions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            if (choice is ReviewChoice.Picked) {
                kb?.species(choice.speciesName)?.let { SpeciesChoiceRow(it, selected = true, onClick = {}) }
            }
            shown.forEach { (i, c) ->
                SuggestionRow(c, kb, selected = choice == ReviewChoice.Suggestion(i), onClick = { onChoose(ReviewChoice.Suggestion(i)) })
            }
        }
    }
    TextButton(onClick = { callbacks.onChooseAnother(detection) }, enabled = kb != null) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        Text(stringResource(R.string.review_choose_another))
    }

    if (existing != null) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    existing.zoneName?.let { stringResource(R.string.review_existing_title, existing.title, it) }
                        ?: stringResource(R.string.review_existing_title_no_zone, existing.title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(stringResource(R.string.review_existing_body), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { callbacks.onAttach(detection, existing.plant.id, flowering) }) { Text(stringResource(R.string.review_add_to_existing)) }
            }
        }
    }

    ZoneChooser(zones, zoneId) {
        ownZone = it
        zoneChosen = true
    }
    ListItem(
        checked = flowering,
        onCheckedChange = { flowering = it },
        supportingContent = { Text(stringResource(R.string.review_flowering_body)) },
        trailingContent = { Switch(checked = flowering, onCheckedChange = null) },
    ) { Text(stringResource(R.string.review_flowering)) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val onAdd = {
            // A suggestion the knowledge base does not know is kept under the model's name, to match up later.
            val custom = if (chosenSpeciesName == null) chosenCandidate?.label else null
            callbacks.onAdd(detection, chosenSpeciesName, custom, zoneId, flowering)
        }
        // With the same species already growing, adding the photo to it is the main choice and a second plant the other.
        if (existing != null) {
            OutlinedButton(onClick = onAdd, enabled = choice != null) { Text(stringResource(R.string.review_add_again)) }
        } else {
            Button(onClick = onAdd, enabled = choice != null) { Text(stringResource(R.string.review_add)) }
        }
        OutlinedButton(onClick = { callbacks.onReject(detection) }) { Text(stringResource(R.string.review_not_plant)) }
    }

}

/** The cut-out and the name of what was found, with extra lines such as the confidence underneath. */
@Composable
private fun Header(thumbnail: String, title: String, subtitle: String?, extra: @Composable () -> Unit = {}) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Cutout(thumbnail, 112.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
            extra()
        }
    }
}

/** A cut-out from a photo, shown whole in a rounded square so the user can see what the models looked at. */
@Composable
fun Cutout(thumbnail: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val file = remember(thumbnail) { File(context.filesDir, thumbnail) }
    Box(modifier.size(size).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun SuggestionRow(candidate: Candidate, kb: KnowledgeBase?, selected: Boolean, onClick: () -> Unit) {
    val species = candidate.speciesName?.let { kb?.species(it) }
    ChoiceRow(
        title = species?.displayName ?: candidate.label,
        subtitle = species?.name ?: stringResource(R.string.review_not_in_kb),
        italic = species != null,
        trailing = stringResource(confidenceLabel(candidate.scoreBp)),
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun SpeciesChoiceRow(species: Species, selected: Boolean, onClick: () -> Unit) {
    ChoiceRow(species.displayName, species.name, italic = true, trailing = stringResource(R.string.review_your_choice), selected = selected, onClick = onClick)
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, italic: Boolean, trailing: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = if (italic) FontStyle.Italic else null,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How sure a suggestion is, as an icon and a word. */
@Composable
fun ConfidenceBadge(scoreBp: Int) {
    when (Confidence.of(scoreBp)) {
        Confidence.HIGH -> Badge(R.drawable.ic_check_circle, stringResource(R.string.confidence_high), BadgeTone.PLANT)
        Confidence.MEDIUM -> Badge(R.drawable.ic_info, stringResource(R.string.confidence_medium), BadgeTone.WILDLIFE)
        Confidence.LOW -> Badge(R.drawable.ic_warning, stringResource(R.string.confidence_low), BadgeTone.WARNING)
    }
}

fun confidenceLabel(scoreBp: Int): Int = when (Confidence.of(scoreBp)) {
    Confidence.HIGH -> R.string.confidence_high
    Confidence.MEDIUM -> R.string.confidence_medium
    Confidence.LOW -> R.string.confidence_low
}
