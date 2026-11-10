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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.SightingView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.Confidence
import io.github.wkoller.pollenplot.domain.id.DetectionStatus
import io.github.wkoller.pollenplot.domain.id.ReviewPick
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.wildlife.CAMERA_GROUPS
import io.github.wkoller.pollenplot.ui.wildlife.label
import io.github.wkoller.pollenplot.ui.weeds.NativeAlternatives
import io.github.wkoller.pollenplot.ui.weeds.WeedHelp
import io.github.wkoller.pollenplot.ui.weeds.WeedNotice
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.RoleIcons
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import java.io.File
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Suggestions below this score are left off the card; the user can still search for the plant. */
internal const val MIN_SHOWN_SCORE_BP = 500

/** The card lists at most this many suggestions. */
internal const val MAX_SHOWN = 3

/** What a review card does when the user decides. The screen passes these on to the database. */
class ReviewCallbacks(
    val onAdd: (detection: DetectionRecord, speciesName: String?, customName: String?, zoneId: String?, flowering: Boolean) -> Unit,
    val onAttach: (detection: DetectionRecord, plantId: String, flowering: Boolean) -> Unit,
    val onReject: (DetectionRecord) -> Unit,
    val onUndoReject: (DetectionRecord) -> Unit,
    val onChooseAnother: (DetectionRecord) -> Unit,
    val onOpenPlant: (String) -> Unit,
    /** Compares the photo with photos of a species, for a suggestion that is a weed. */
    val onCompare: ((detection: DetectionRecord, speciesName: String) -> Unit)? = null,
    /** Adds an animal or fungus as a sighting: its scientific name if known, the name to show for it, and where. */
    val onAddSighting: ((detection: DetectionRecord, speciesName: String?, label: String, zoneId: String?, plantId: String?, behaviour: Behaviour?) -> Unit)? = null,
    /** Saves a plant found away from home to the wishlist, with the zone it might go in. */
    val onSave: ((detection: DetectionRecord, speciesName: String?, customName: String?, zoneId: String?) -> Unit)? = null,
    val onOpenWishlist: (() -> Unit)? = null,
    /** Opens a chooser of every plant in the garden, to add the find to whichever one the photo shows. */
    val onChoosePlant: ((detection: DetectionRecord, speciesName: String?, flowering: Boolean) -> Unit)? = null,
    /** Says the find shows something of another group, such as an animal where a plant was looked for. */
    val onRegroup: ((detection: DetectionRecord, group: LifeGroup) -> Unit)? = null,
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
    locality: Locality,
    zones: List<ZoneView>,
    plants: List<PlantView>,
    choice: ReviewChoice?,
    onChoose: (ReviewChoice) -> Unit,
    suggestedZoneId: String?,
    callbacks: ReviewCallbacks,
    modifier: Modifier = Modifier,
    fauna: FaunaBase? = null,
    sightings: List<SightingView> = emptyList(),
    /** The photo was taken away from home: a plant is saved to the wishlist or let go, not added to the garden. */
    field: Boolean = false,
    help: WeedHelp? = null,
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
                DetectionStatus.NEEDS_REVIEW -> if (detection.group.isWildlife) {
                    UndecidedAnimal(detection, fauna, locality, zones, plants, choice, onChoose, suggestedZoneId, callbacks)
                } else {
                    Undecided(detection, kb, locality, zones, plants, choice, onChoose, suggestedZoneId, callbacks, field, help)
                }
                DetectionStatus.SAVED -> SavedFind(detection, kb, choice, callbacks)
                DetectionStatus.CONFIRMED -> if (detection.sightingId != null) {
                    val sighting = sightings.firstOrNull { it.sighting.id == detection.sightingId }
                    if (sighting == null) {
                        Header(detection.thumbnail, stringResource(R.string.review_sighting_added), null)
                    } else {
                        Header(detection.thumbnail, sighting.title, sighting.scientificName)
                        Text(
                            sighting.zoneName?.let { stringResource(R.string.review_sighting_added_zone, it) } ?: stringResource(R.string.review_sighting_added),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
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
                    val wildlife = detection.group.isWildlife
                    val title = when {
                        field -> R.string.field_let_go_title
                        wildlife -> R.string.review_rejected_wildlife_title
                        else -> R.string.review_rejected_title
                    }
                    val body = when {
                        field -> R.string.field_let_go
                        wildlife -> R.string.review_rejected_wildlife
                        else -> R.string.review_rejected
                    }
                    Header(detection.thumbnail, stringResource(title), null)
                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
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

/** The choice a card shows: the user's, or else the best suggestion, or nothing when the models made no suggestion. */
internal fun chosen(detection: DetectionRecord, saved: ReviewChoice?): ReviewChoice? =
    saved ?: if (detection.candidates.isEmpty()) null else ReviewChoice.Suggestion(0)

/** The plant a card adds for [choice]. A suggestion the knowledge base does not know keeps the model's name. */
internal fun plantPick(detection: DetectionRecord, choice: ReviewChoice?, kb: KnowledgeBase?): ReviewPick.Plant? = when (choice) {
    is ReviewChoice.Picked -> ReviewPick.plant(detection.candidates, picked = choice.speciesName, known = { name -> kb?.species(name)?.name })
    is ReviewChoice.Suggestion -> ReviewPick.plant(detection.candidates, index = choice.index)
    null -> null
}

/** The sighting a card adds for [choice]. */
internal fun animalPick(detection: DetectionRecord, choice: ReviewChoice?, fauna: FaunaBase?): ReviewPick.Animal? = when (choice) {
    is ReviewChoice.Picked -> ReviewPick.animal(detection.candidates, fauna, picked = choice.speciesName)
    is ReviewChoice.Suggestion -> ReviewPick.animal(detection.candidates, fauna, index = choice.index)
    null -> null
}

/**
 * Why a plant starts as in flower or not: flowers were seen in the photo, the plant does not usually flower in the
 * month the photo was taken, or no flowers were seen.
 */
@Composable
private fun FlowerNote(detection: DetectionRecord, species: Species?, kb: KnowledgeBase?, hemisphere: Hemisphere) {
    val month = detection.takenAt.atZone(ZoneId.systemDefault()).month
    val months = if (kb != null && species != null) CareResolver.resolve(kb, species).care.flowering?.fromSouthern(hemisphere) else null
    val (icon, text) = when {
        detection.flowering -> R.drawable.ic_local_florist to stringResource(R.string.review_flowering_seen)
        months != null && !months.isEmpty && month !in months -> R.drawable.ic_calendar_month to stringResource(
            R.string.review_flowering_out_of_season,
            species!!.displayName,
            month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        )
        else -> R.drawable.ic_visibility_off to stringResource(R.string.review_flowering_not_seen)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Undecided(
    detection: DetectionRecord,
    kb: KnowledgeBase?,
    locality: Locality,
    zones: List<ZoneView>,
    plants: List<PlantView>,
    savedChoice: ReviewChoice?,
    onChoose: (ReviewChoice) -> Unit,
    suggestedZoneId: String?,
    callbacks: ReviewCallbacks,
    field: Boolean,
    help: WeedHelp?,
) {
    var ownZone by rememberSaveable(detection.id) { mutableStateOf<String?>(null) }
    var zoneChosen by rememberSaveable(detection.id) { mutableStateOf(false) }
    var flowering by rememberSaveable(detection.id) { mutableStateOf(detection.flowering) }

    val choice = chosen(detection, savedChoice)
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
    // A name the user searched for or typed that the knowledge base does not have.
    val pickedName = (choice as? ReviewChoice.Picked)?.speciesName?.takeIf { chosenSpecies == null }
    val zoneId = if (zoneChosen) ownZone else suggestedZoneId
    // Away from home, the plant is not being added to the garden, so a plant of the same species there is not offered.
    val existing = if (field) null else chosenSpeciesName?.let { name -> plants.firstOrNull { it.isGrowing && it.plant.speciesName == name } }

    Header(
        thumbnail = detection.thumbnail,
        title = chosenSpecies?.displayName ?: chosenCandidate?.common ?: chosenCandidate?.label ?: pickedName ?: stringResource(R.string.review_unnamed),
        subtitle = chosenSpecies?.let { species -> species.name.takeIf { it != species.displayName } }
            ?: chosenCandidate?.takeIf { chosenSpecies == null && it.common != null }?.label,
    ) {
        if (chosenCandidate != null) ConfidenceBadge(chosenCandidate.scoreBp)
        if (chosenSpecies != null && kb != null) {
            RoleIcons(locality.roles(chosenSpecies, CareResolver.resolve(kb, chosenSpecies).care), max = 4, size = 24.dp)
        } else if (chosenCandidate != null || pickedName != null) {
            Text(stringResource(R.string.review_not_in_kb), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    // The suggestion's weed status where the garden is, before it is added.
    val weed = chosenSpecies?.let(locality::weed) ?: chosenSpeciesName?.let(locality::weed)
    val isWeed = weed != null && weed.isWeed && chosenSpeciesName != null
    if (field && chosenSpecies != null && kb != null && !isWeed) {
        WildStatusLine(chosenSpecies, CareResolver.resolve(kb, chosenSpecies).care, locality)
    }
    if (isWeed) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeedNotice(weed!!, locality, Modifier.weight(1f))
            callbacks.onCompare?.let { compare ->
                TextButton(onClick = { compare(detection, chosenSpecies?.name ?: chosenSpeciesName!!) }) { Text(stringResource(R.string.review_compare)) }
            }
        }
        if (help != null) {
            NativeAlternatives(help, chosenSpecies?.let { listOf(it.name) + it.synonyms } ?: listOfNotNull(chosenSpeciesName), kb, locality)
        }
    }

    if (shown.isNotEmpty() || choice is ReviewChoice.Picked) {
        Text(stringResource(R.string.review_suggestions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            if (choice is ReviewChoice.Picked) {
                kb?.species(choice.speciesName)?.let { SpeciesChoiceRow(it, selected = true, onClick = {}) }
                    ?: ChoiceRow(choice.speciesName, stringResource(R.string.review_not_in_kb), italic = false, trailing = stringResource(R.string.review_your_choice), selected = true, onClick = {})
            }
            shown.forEach { (i, c) ->
                SuggestionRow(c, kb, selected = choice == ReviewChoice.Suggestion(i), onClick = { onChoose(ReviewChoice.Suggestion(i)) })
            }
        }
    }
    // Saying what it really is sits with the suggestions, as plain as adding it.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { callbacks.onChooseAnother(detection) }, enabled = kb != null) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.review_choose_another))
        }
        callbacks.onRegroup?.let { regroup -> AnimalInstead(onGroup = { regroup(detection, it) }) }
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

    ZoneChooser(zones, zoneId, title = if (field) stringResource(R.string.field_zone) else null) {
        ownZone = it
        zoneChosen = true
    }
    if (field) {
        FieldActions(choice != null, isWeed, chosenSpecies?.displayName ?: chosenCandidate?.common ?: chosenCandidate?.label, onSave = {
            plantPick(detection, choice, kb)?.let { callbacks.onSave?.invoke(detection, it.speciesName, it.customName, zoneId) }
        }, onLetGo = { callbacks.onReject(detection) })
        return
    }
    ListItem(
        checked = flowering,
        onCheckedChange = { flowering = it },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.review_flowering_body))
                // Why the switch started where it did, until the user changes it.
                if (flowering == detection.flowering) FlowerNote(detection, chosenSpecies, kb, locality.hemisphere)
            }
        },
        trailingContent = { Switch(checked = flowering, onCheckedChange = null) },
    ) { Text(stringResource(R.string.review_flowering)) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val onAdd = {
            plantPick(detection, choice, kb)?.let { callbacks.onAdd(detection, it.speciesName, it.customName, zoneId, flowering) }
            Unit
        }
        // With the same species already growing, adding the photo to it is the main choice and a second plant the other.
        if (existing != null) {
            OutlinedButton(onClick = onAdd, enabled = choice != null) { Text(stringResource(R.string.review_add_again)) }
        } else {
            Button(onClick = onAdd, enabled = choice != null) { Text(stringResource(R.string.review_add)) }
        }
        OutlinedButton(onClick = { callbacks.onReject(detection) }) { Text(stringResource(R.string.review_not_plant)) }
    }
    // The photo may show a plant already in the garden under another name, or a second plant of the same kind.
    callbacks.onChoosePlant?.takeIf { plants.any { it.isGrowing } }?.let { choose ->
        TextButton(onClick = { choose(detection, chosenSpeciesName, flowering) }) {
            Icon(painterResource(R.drawable.ic_potted_plant), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
            Text(stringResource(R.string.review_add_to_other))
        }
    }

}

/** Says a find looked at as a plant is an animal or a fungus, and of which kind, so it is looked at again as one. */
@Composable
private fun AnimalInstead(onGroup: (LifeGroup) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(stringResource(R.string.review_is_animal)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CAMERA_GROUPS.filter { it.isWildlife }.forEach { group ->
                DropdownMenuItem(
                    text = { Text(stringResource(group.label)) },
                    onClick = {
                        open = false
                        onGroup(group)
                    },
                )
            }
        }
    }
}

/** The cut-out and the name of what was found, with extra lines such as the confidence underneath. */
@Composable
internal fun Header(thumbnail: String, title: String, subtitle: String?, extra: @Composable () -> Unit = {}) {
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
        title = species?.displayName ?: candidate.common ?: candidate.label,
        subtitle = species?.name ?: candidate.label.takeIf { candidate.common != null } ?: stringResource(R.string.review_not_in_kb),
        italic = species != null || candidate.common != null,
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
internal fun ChoiceRow(title: String, subtitle: String, italic: Boolean, trailing: String, selected: Boolean, onClick: () -> Unit) {
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
