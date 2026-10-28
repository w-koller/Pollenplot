package io.github.wkoller.pollenplot.ui.review

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
    var plantId by rememberSaveable(detection.id) { mutableStateOf<String?>(null) }
    var behaviour by rememberSaveable(detection.id) { mutableStateOf<String?>(null) }

    val choice = savedChoice ?: if (detection.candidates.isEmpty()) null else ReviewChoice.Suggestion(0)
    val shown = detection.candidates.withIndex().filter { (i, c) -> i == 0 || c.scoreBp >= MIN_SHOWN_SCORE_BP }.take(MAX_SHOWN)
    val chosenCandidate = (choice as? ReviewChoice.Suggestion)?.let { detection.candidates.getOrNull(it.index) }
    val chosenName = when (choice) {
        is ReviewChoice.Picked -> choice.speciesName
        is ReviewChoice.Suggestion -> chosenCandidate?.let { it.speciesName ?: it.label }
        null -> null
    }
    val animal = chosenName?.let { fauna?.animal(it) }
    val zoneId = if (zoneChosen) ownZone else suggestedZoneId

    Header(
        thumbnail = detection.thumbnail,
        title = animal?.displayName ?: chosenName ?: stringResource(R.string.review_unnamed_animal),
        subtitle = animal?.name?.takeIf { it != animal.displayName },
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
    TextButton(onClick = { callbacks.onChooseAnother(detection) }, enabled = fauna != null) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
        Text(stringResource(R.string.review_choose_another_animal))
    }

    SightingFields(
        zones = zones,
        plants = plants,
        zoneId = zoneId,
        plantId = plantId,
        behaviour = behaviour?.let { name -> Behaviour.entries.firstOrNull { it.name == name } },
        onZone = {
            ownZone = it
            zoneChosen = true
            plantId = null
        },
        onPlant = { plantId = it },
        onBehaviour = { behaviour = it?.name },
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                val label = chosenName ?: return@Button
                callbacks.onAddSighting?.invoke(detection, animal?.name, label, zoneId, plantId, behaviour?.let { name -> Behaviour.entries.firstOrNull { it.name == name } })
            },
            enabled = chosenName != null && callbacks.onAddSighting != null,
        ) { Text(stringResource(R.string.review_add_sighting)) }
        OutlinedButton(onClick = { callbacks.onReject(detection) }) { Text(stringResource(R.string.review_not_wildlife)) }
    }
}

@Composable
private fun AnimalSuggestionRow(candidate: Candidate, fauna: FaunaBase?, selected: Boolean, onClick: () -> Unit) {
    val animal = (candidate.speciesName ?: candidate.label).let { fauna?.animal(it) }
    ChoiceRow(
        title = animal?.displayName ?: candidate.label,
        subtitle = animal?.name ?: stringResource(R.string.review_not_in_fauna),
        italic = animal != null,
        trailing = stringResource(confidenceLabel(candidate.scoreBp)),
        selected = selected,
        onClick = onClick,
    )
}
