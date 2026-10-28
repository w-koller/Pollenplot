package io.github.wkoller.pollenplot.ui.wildlife

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.components.PictureAvatar
import io.github.wkoller.pollenplot.ui.plants.QuantityStepper
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.review.FaunaSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

/**
 * One sighting, to change or delete, or a new one without a photo when [sightingId] is null, starting in [startZoneId].
 * The form starts from the stored sighting and keeps the user's changes until they save.
 */
@Composable
fun SightingScreen(
    views: GardenViews,
    knowledge: KnowledgeRepository,
    garden: GardenRepository,
    clock: Clock,
    sightingId: String?,
    startZoneId: String?,
    onBack: () -> Unit,
    onOpenAnimal: (String) -> Unit,
) {
    val sightings by views.sightings.collectAsStateWithLifecycle(initialValue = null)
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val scope = rememberCoroutineScope()
    val today = remember(clock) { LocalDate.now(clock) }
    val existing = sightingId?.let { id -> sightings?.firstOrNull { it.sighting.id == id } }

    var started by rememberSaveable { mutableStateOf(sightingId == null) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var groupName by rememberSaveable { mutableStateOf<String?>(null) }
    var zoneId by rememberSaveable { mutableStateOf(startZoneId) }
    var plantId by rememberSaveable { mutableStateOf<String?>(null) }
    var behaviourName by rememberSaveable { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var notes by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var searching by rememberSaveable { mutableStateOf(sightingId == null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(existing != null) {
        val sighting = existing?.sighting
        if (started || sighting == null) return@LaunchedEffect
        name = sighting.speciesName ?: sighting.label
        groupName = sighting.group.name
        zoneId = sighting.zoneId
        plantId = sighting.plantId
        behaviourName = sighting.behaviour?.name
        count = sighting.count
        notes = sighting.notes
        started = true
    }

    val loadedFauna = fauna
    val animal = name?.let { loadedFauna?.animal(it) }
    val group = animal?.group ?: groupName?.let(LifeGroup::of) ?: LifeGroup.OTHER
    if (searching && loadedFauna != null) {
        // A new sighting starts by choosing the animal; going back from that leaves the screen.
        val leave = { if (name == null && sightingId == null) onBack() else searching = false }
        BackHandler { leave() }
        FaunaSearch(loadedFauna, group, onBack = leave) { picked ->
            name = picked
            searching = false
        }
        return
    }

    DetailScaffold(
        title = stringResource(if (sightingId == null) R.string.sighting_new_title else R.string.sighting_title),
        onBack = onBack,
        actions = {
            if (existing != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.sighting_delete)) }
            }
        },
    ) { padding ->
        if (sightingId != null && sightings != null && existing == null) {
            Text(stringResource(R.string.sighting_missing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(padding).padding(16.dp))
            return@DetailScaffold
        }
        if (!started) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PictureAvatar(
                    group.icon,
                    96.dp,
                    thumbnail = existing?.sighting?.thumbnail,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val title = animal?.displayName ?: name ?: stringResource(R.string.review_unnamed_animal)
                    Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    animal?.name?.takeIf { it != title }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic) }
                    Text(stringResource(group.label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            existing?.let { Text(stringResource(R.string.sighting_seen_at, UiFormat.longDate(it.sighting.seenAt), UiFormat.time(it.sighting.seenAt)), style = MaterialTheme.typography.bodyLarge) }
            animal?.let { SafetyNotice(it, locality.state) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { searching = true }, enabled = loadedFauna != null) {
                    Text(stringResource(if (name == null) R.string.sighting_choose_animal else R.string.sighting_change_animal))
                }
                val chosen = name
                if (existing != null && chosen != null) {
                    TextButton(onClick = { onOpenAnimal(animal?.name ?: KnowledgeBase.normalise(chosen)) }) { Text(stringResource(R.string.sighting_about_animal)) }
                }
            }
            if (name != null && animal == null) {
                // Pollenplot doesn't know this animal, so the user says which group to file it under.
                ChipGroup(
                    title = stringResource(R.string.sighting_group),
                    options = CAMERA_GROUPS.filter { it.isWildlife } + LifeGroup.OTHER,
                    selected = group,
                    label = { stringResource(it.label) },
                ) { groupName = it.name }
            }
            if (existing == null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.sighting_when), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(UiFormat.longDate(LocalDate.ofEpochDay(day)), style = MaterialTheme.typography.bodyLarge)
                        TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.sighting_change_date)) }
                    }
                }
            }
            SightingFields(
                zones = zones,
                plants = plants,
                zoneId = zoneId,
                plantId = plantId,
                behaviour = behaviourName?.let { n -> Behaviour.entries.firstOrNull { it.name == n } },
                onZone = {
                    zoneId = it
                    plantId = null
                },
                onPlant = { plantId = it },
                onBehaviour = { behaviourName = it?.name },
            )
            QuantityStepper(count, countText = { it.toString() }) { count = it }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.sighting_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val chosen = name ?: return@Button
                    val behaviour = behaviourName?.let { n -> Behaviour.entries.firstOrNull { it.name == n } }
                    scope.launch {
                        val stored = existing?.sighting
                        if (stored == null) {
                            val date = LocalDate.ofEpochDay(day)
                            val seenAt = if (date == today) clock.instant() else date.atTime(LocalTime.NOON).atZone(clock.zone).toInstant()
                            garden.recordSighting(animal?.name, animal?.displayName ?: chosen, group, zoneId, plantId, behaviour, count, seenAt, notes)
                        } else {
                            // The name it was recorded under stays unless the user chose another animal.
                            val label = if (chosen == (stored.speciesName ?: stored.label)) stored.label else animal?.displayName ?: chosen
                            garden.updateSighting(
                                stored.copy(
                                    speciesName = animal?.name,
                                    label = label,
                                    group = group,
                                    zoneId = zoneId,
                                    plantId = plantId,
                                    behaviour = behaviour,
                                    count = count,
                                    notes = notes.trim(),
                                ),
                            )
                        }
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
                enabled = name != null,
            ) { Text(stringResource(if (existing == null) R.string.sighting_add else R.string.sighting_save)) }
        }
    }

    if (pickingDate) {
        PastDateDialog(LocalDate.ofEpochDay(day), today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sighting_delete_title)) },
            text = { Text(stringResource(R.string.sighting_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        garden.deleteSighting(existing.sighting.id)
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text(stringResource(R.string.sighting_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
