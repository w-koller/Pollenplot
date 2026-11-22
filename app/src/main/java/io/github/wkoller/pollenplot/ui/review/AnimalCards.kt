package io.github.wkoller.pollenplot.ui.review

import io.github.wkoller.pollenplot.ui.components.FieldProblem
import io.github.wkoller.pollenplot.ui.components.formField
import io.github.wkoller.pollenplot.ui.components.rememberFormCheck
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.FaunaBase
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.wildlife.SafetyNotice
import io.github.wkoller.pollenplot.ui.wildlife.SightingFields
import io.github.wkoller.pollenplot.ui.wildlife.label

/**
 * An animal or fungus found in a photo: what the models suggest it is, what to know before going near it, where it
 * was, the plant it was on and what it was doing, and buttons to add it as a sighting or say it is not wildlife.
 */
@Composable
internal fun UndecidedAnimal(
    detection: DetectionRecord,
    fauna: FaunaBase?,
    locality: Locality,
    zones: List<ZoneView>,
    plants: List<PlantView>,
    savedChoice: ReviewChoice?,
    onChoose: (ReviewChoice) -> Unit,
    suggestedZoneId: String?,
    callbacks: ReviewCallbacks,
) {
    var ownZone by rememberSaveable(detection.id) { mutableStateOf<String?>(null) }
    var zoneChosen by rememberSaveable(detection.id) { mutableStateOf(false) }
    var plantIds by rememberSaveable(detection.id, stateSaver = NamesSaver) { mutableStateOf(emptyList<String>()) }
    var behaviours by rememberSaveable(detection.id, stateSaver = NamesSaver) { mutableStateOf(emptyList<String>()) }

    val choice = chosen(detection, savedChoice)
    val shown = detection.candidates.withIndex().filter { (i, c) -> i == 0 || c.scoreBp >= MIN_SHOWN_SCORE_BP }.take(MAX_SHOWN)
    val chosenCandidate = (choice as? ReviewChoice.Suggestion)?.let { detection.candidates.getOrNull(it.index) }
    val chosenName = when (choice) {
        is ReviewChoice.Picked -> choice.speciesName
        is ReviewChoice.Suggestion -> chosenCandidate?.let { it.speciesName ?: it.label }
        null -> null
    }
    val animal = chosenName?.let { fauna?.animal(it) }
    val zoneId = if (zoneChosen) ownZone else suggestedZoneId

    // An animal the knowledge base does not have is named by the model's common name, with its scientific name below.
    val common = chosenCandidate?.common.takeIf { animal == null }
    Header(
        thumbnail = detection.thumbnail,
        title = animal?.displayName ?: common ?: chosenName ?: stringResource(R.string.review_unnamed_animal),
        subtitle = animal?.name?.takeIf { it != animal.displayName } ?: chosenName.takeIf { common != null },
    ) {
        chosenCandidate?.let { ConfidenceBadge(it.scoreBp) }
        Text(
            listOf(
                stringResource(detection.group.label),
                when {
                    animal == null -> stringResource(R.string.review_not_in_fauna)
                    animal.isNative -> stringResource(R.string.review_native)
                    else -> stringResource(R.string.review_introduced)
                },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    animal?.let { SafetyNotice(it, locality.state) }
    if (choice !is ReviewChoice.Picked) GenusNote(detection.candidates)

    if (shown.isNotEmpty() || choice is ReviewChoice.Picked) {
        Text(stringResource(R.string.review_suggestions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            if (choice is ReviewChoice.Picked) {
                val picked = fauna?.animal(choice.speciesName)
                ChoiceRow(picked?.displayName ?: choice.speciesName, picked?.name ?: stringResource(R.string.review_not_in_fauna), italic = picked != null, trailing = stringResource(R.string.review_your_choice), selected = true, onClick = {})
            }
            shown.forEach { (i, c) ->
                AnimalSuggestionRow(c, fauna, selected = choice == ReviewChoice.Suggestion(i), onClick = { onChoose(ReviewChoice.Suggestion(i)) })
            }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OutlinedButton(onClick = { callbacks.onChooseAnother(detection) }, enabled = fauna != null) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(end = Spacing.s).size(IconSize.small))
            Text(stringResource(R.string.review_choose_another_animal))
        }
        callbacks.onRegroup?.let { regroup ->
            TextButton(onClick = { regroup(detection, LifeGroup.PLANT) }) { Text(stringResource(R.string.review_is_plant)) }
        }
    }

    SightingFields(
        zones = zones,
        plants = plants,
        zoneId = zoneId,
        plantIds = plantIds.toSet(),
        behaviours = behavioursNamed(behaviours),
        onZone = {
            ownZone = it
            zoneChosen = true
            plantIds = emptyList()
        },
        // Kept in the order chosen, so the first plant pressed stays first.
        onPlants = { chosen -> plantIds = plantIds.filter { it in chosen } + chosen.filterNot { it in plantIds } },
        onBehaviours = { chosen -> behaviours = chosen.map { it.name } },
    )

    val check = rememberFormCheck()
    val choiceProblem = stringResource(R.string.review_choose_animal).takeIf { chosenName == null }
    FieldProblem(check.shown(choiceProblem), Modifier.formField(check, CHOICE, choiceProblem, focus = false))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(
            onClick = {
                if (!check.passes(CHOICE to choiceProblem)) return@Button
                val pick = animalPick(detection, choice, fauna) ?: return@Button
                callbacks.onAddSighting?.invoke(detection, pick.speciesName, pick.label, zoneId, plantIds, behavioursNamed(behaviours))
            },
            enabled = callbacks.onAddSighting != null,
        ) { Text(stringResource(R.string.review_add_sighting)) }
        OutlinedButton(onClick = { callbacks.onReject(detection) }) { Text(stringResource(R.string.review_not_wildlife)) }
    }
}

@Composable
private fun AnimalSuggestionRow(candidate: Candidate, fauna: FaunaBase?, selected: Boolean, onClick: () -> Unit) {
    val animal = (candidate.speciesName ?: candidate.label).let { fauna?.animal(it) }
    ChoiceRow(
        title = animal?.displayName ?: candidate.common ?: candidate.label,
        subtitle = animal?.name ?: candidate.label.takeIf { candidate.common != null } ?: stringResource(R.string.review_not_in_fauna),
        italic = animal != null || candidate.common != null,
        trailing = stringResource(confidenceLabel(candidate.scoreBp)),
        selected = selected,
        onClick = onClick,
    )
}

/** Keeps a list of names, such as the plants chosen, so they survive the phone being turned. */
val NamesSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

/** The behaviours with these enum names. */
fun behavioursNamed(names: List<String>): Set<Behaviour> = Behaviour.entries.filter { it.name in names }.toSet()

private const val CHOICE = "choice"
