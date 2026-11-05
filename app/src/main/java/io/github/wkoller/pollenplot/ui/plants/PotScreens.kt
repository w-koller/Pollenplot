package io.github.wkoller.pollenplot.ui.plants

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.PotRepository
import io.github.wkoller.pollenplot.data.PotState
import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PlantEventKind
import io.github.wkoller.pollenplot.domain.garden.PlantPot
import io.github.wkoller.pollenplot.domain.garden.PotMaterial
import io.github.wkoller.pollenplot.domain.garden.PotMix
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.DateRow
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.review.readable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@get:StringRes
val PotMaterial.label: Int
    get() = when (this) {
        PotMaterial.TERRACOTTA -> R.string.pot_material_terracotta
        PotMaterial.PLASTIC -> R.string.pot_material_plastic
        PotMaterial.GLAZED -> R.string.pot_material_glazed
        PotMaterial.FABRIC -> R.string.pot_material_fabric
        PotMaterial.METAL -> R.string.pot_material_metal
        PotMaterial.TIMBER -> R.string.pot_material_timber
        PotMaterial.CONCRETE -> R.string.pot_material_concrete
    }

@get:StringRes
val PotMix.label: Int
    get() = when (this) {
        PotMix.PREMIUM -> R.string.pot_mix_premium
        PotMix.NATIVE -> R.string.pot_mix_native
        PotMix.CITRUS -> R.string.pot_mix_citrus
        PotMix.CACTUS -> R.string.pot_mix_cactus
        PotMix.ORCHID -> R.string.pot_mix_orchid
        PotMix.OTHER -> R.string.pot_mix_other
    }

@get:StringRes
val PlantEventKind.label: Int
    get() = when (this) {
        PlantEventKind.POTTED_UP -> R.string.event_potted_up
        PlantEventKind.REPOTTED -> R.string.event_repotted
        PlantEventKind.ROOT_PRUNED -> R.string.event_root_pruned
        PlantEventKind.TOP_DRESSED -> R.string.event_top_dressed
        PlantEventKind.FLUSHED -> R.string.event_flushed
    }

/**
 * A potted plant's pot: its size, material and mix, when it is due a flush or a look at repotting and why, and
 * buttons to record care and to edit the pot.
 */
@Composable
fun PotCard(state: PotState, today: LocalDate, onEdit: () -> Unit, onRecord: () -> Unit) {
    FactCard(R.drawable.ic_potted_plant, stringResource(R.string.pot_title)) {
        val pot = state.pot
        if (pot == null) {
            Text(stringResource(R.string.pot_none), style = MaterialTheme.typography.bodyLarge)
        } else {
            val size = listOfNotNull(
                pot.diameterMm?.let { stringResource(R.string.pot_size_cm, BigDecimal.valueOf(it.toLong(), 1).stripTrailingZeros().toPlainString()) },
                pot.volumeMl?.let { stringResource(R.string.pot_volume_l, BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString()) },
                pot.material?.let { stringResource(it.label) },
            )
            if (size.isNotEmpty()) Text(size.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
            val mix = listOfNotNull(pot.mix?.let { stringResource(it.label) }, pot.mixNote.ifBlank { null })
            if (mix.isNotEmpty()) Text(mix.joinToString(" · "), style = MaterialTheme.typography.bodyLarge)
        }
        if (state.tasks.isEmpty()) {
            Text(stringResource(R.string.pot_no_date), style = MaterialTheme.typography.bodyMedium)
        }
        state.tasks.sortedBy { it.kind }.forEach { task ->
            val flush = task.kind == PotTaskKind.FLUSH
            if (task.isDue(today)) {
                Badge(R.drawable.ic_warning, stringResource(if (flush) R.string.pot_flush_due else R.string.pot_repot_due), BadgeTone.WARNING)
                Text(stringResource(if (flush) R.string.pot_flush_why else R.string.pot_repot_why), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(if (flush) R.string.pot_flush_next else R.string.pot_repot_next, UiFormat.longDate(task.due)),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onRecord) { Text(stringResource(R.string.pot_record)) }
            OutlinedButton(onClick = onEdit) { Text(stringResource(if (pot == null) R.string.pot_add else R.string.pot_edit)) }
        }
    }
}

/** The care recorded for a plant, the latest first, each with a button to remove it. */
@Composable
fun PlantEventsCard(events: List<PlantEvent>, onRemove: (String) -> Unit) {
    FactCard(R.drawable.ic_history, stringResource(R.string.event_title)) {
        events.forEach { event ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.event_on, stringResource(event.kind.label), UiFormat.longDate(event.date)), style = MaterialTheme.typography.bodyLarge)
                    if (event.notes.isNotBlank()) Text(event.notes, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { onRemove(event.id) }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.event_remove))
                }
            }
        }
    }
}

/** Records care done to a plant on a day, such as repotting it or flushing its pot. */
@Composable
fun PlantEventScreen(plantId: String, pots: PotRepository, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(PlantEventKind.REPOTTED) }
    var notes by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(day)

    DetailScaffold(title = stringResource(R.string.event_record_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ChipGroup(stringResource(R.string.event_kind), PlantEventKind.entries, kind, { stringResource(it.label) }) { kind = it }
            DateRow(date) { pickingDate = true }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    pots.addEvent(plantId, kind, date, notes)
                    // The database finishes on its own thread; going back has to happen on the main one.
                    withContext(Dispatchers.Main) { onBack() }
                }
            }) { Text(stringResource(R.string.honey_save)) }
        }
    }
    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
}

/** A plant's pot: its width and volume, material, potting mix and the mix's brand. */
@Composable
fun PotEditScreen(plantId: String, pots: PotRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var existing by rememberSaveable { mutableStateOf(false) }
    var diameter by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf("") }
    var material by rememberSaveable { mutableStateOf<PotMaterial?>(null) }
    var mix by rememberSaveable { mutableStateOf<PotMix?>(null) }
    var mixNote by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(plantId) {
        if (loaded) return@LaunchedEffect
        pots.pot(plantId)?.let { pot ->
            existing = true
            diameter = pot.diameterMm?.let { BigDecimal.valueOf(it.toLong(), 1).stripTrailingZeros().toPlainString() }.orEmpty()
            volume = pot.volumeMl?.let { BigDecimal.valueOf(it, 3).stripTrailingZeros().toPlainString() }.orEmpty()
            material = pot.material
            mix = pot.mix
            mixNote = pot.mixNote
        }
        loaded = true
    }
    val diameterMm = positive(diameter, 1)?.toInt()
    val volumeMl = positive(volume, 3)
    val diameterError = diameter.isNotBlank() && diameterMm == null
    val volumeError = volume.isNotBlank() && volumeMl == null

    DetailScaffold(title = stringResource(R.string.pot_edit_title), onBack = onBack) { padding ->
        if (!loaded) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NumberText(stringResource(R.string.pot_diameter), diameter, diameterError) { diameter = it }
            NumberText(stringResource(R.string.pot_volume), volume, volumeError) { volume = it }
            OptionalChips(stringResource(R.string.pot_material), PotMaterial.entries, material, { stringResource(it.label) }) { material = it }
            OptionalChips(stringResource(R.string.pot_mix), PotMix.entries, mix, { stringResource(it.label) }) { mix = it }
            OutlinedTextField(value = mixNote, onValueChange = { mixNote = it }, label = { Text(stringResource(R.string.pot_mix_note)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            pots.savePot(PlantPot(pots.newId(), plantId, diameterMm, volumeMl, material, mix, mixNote))
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    },
                    enabled = !diameterError && !volumeError,
                ) { Text(stringResource(R.string.honey_save)) }
                if (existing) {
                    TextButton(onClick = {
                        scope.launch {
                            pots.deletePot(plantId)
                            withContext(Dispatchers.Main) { onBack() }
                        }
                    }) { Text(stringResource(R.string.pot_remove)) }
                }
            }
        }
    }
}

/** Chips for one choice of a few, with Not recorded for none. */
@Composable
private fun <T> OptionalChips(title: String, options: List<T>, selected: T?, label: @Composable (T) -> String, onChoose: (T?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == null, onClick = { onChoose(null) }, label = { Text(stringResource(R.string.pot_not_recorded)) })
            options.forEach { option -> FilterChip(selected = option == selected, onClick = { onChoose(option) }, label = { Text(label(option)) }) }
        }
    }
}

@Composable
private fun NumberText(label: String, value: String, error: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error,
        supportingText = if (error) {
            { Text(stringResource(R.string.pot_number_error)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A number the user typed, such as "30" or "12.5", with its point moved [places] to the right, or null. */
private fun positive(text: String, places: Int): Long? {
    val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0 || value > BigDecimal(100_000)) return null
    return value.movePointRight(places).setScale(0, RoundingMode.HALF_UP).toLong().takeIf { it > 0 }
}
