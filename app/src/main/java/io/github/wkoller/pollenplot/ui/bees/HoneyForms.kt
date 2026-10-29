package io.github.wkoller.pollenplot.ui.bees

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEvent
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEventKind
import io.github.wkoller.pollenplot.domain.bees.honey.FeedKind
import io.github.wkoller.pollenplot.domain.bees.honey.Feeding
import io.github.wkoller.pollenplot.domain.bees.honey.Harvest
import io.github.wkoller.pollenplot.domain.bees.honey.HiveSign
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.bees.honey.Temperament
import io.github.wkoller.pollenplot.domain.bees.honey.Treatment
import io.github.wkoller.pollenplot.domain.bees.honey.StateMiteChecks
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaCheck
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaMethod
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A new record for a hive: an inspection, a mite check, a treatment, a feeding, a harvest or a colony event, each with
 * its own fields, on the day chosen. The fields keep what was typed while the phone is turned.
 */
@Composable
fun HoneyRecordScreen(kind: HoneyRecordKind, hiveId: String, bees: BeeRepository, settings: SettingsStore, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val uri = LocalUriHandler.current
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    // Each kind's fields; only the ones for this kind are shown and saved.
    var queenSeen by rememberSaveable { mutableStateOf(false) }
    var eggsSeen by rememberSaveable { mutableStateOf(false) }
    var brood by rememberSaveable { mutableStateOf<BroodPattern?>(null) }
    var temperament by rememberSaveable { mutableStateOf<Temperament?>(null) }
    var framesOfBees by rememberSaveable { mutableStateOf("") }
    var framesOfStores by rememberSaveable { mutableStateOf("") }
    var queenCells by rememberSaveable { mutableStateOf(QueenCells.NONE) }
    var signs by rememberSaveable { mutableStateOf(emptySet<HiveSign>()) }
    var actions by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(VarroaMethod.ALCOHOL_WASH) }
    var sample by rememberSaveable { mutableStateOf("300") }
    var mites by rememberSaveable { mutableStateOf("") }
    var reported by rememberSaveable { mutableStateOf(false) }
    var product by rememberSaveable { mutableStateOf("") }
    var ingredient by rememberSaveable { mutableStateOf("") }
    var supersRemoved by rememberSaveable { mutableStateOf(true) }
    var feed by rememberSaveable { mutableStateOf(FeedKind.SYRUP_1_TO_1) }
    var amount by rememberSaveable { mutableStateOf("") }
    var frames by rememberSaveable { mutableStateOf("") }
    var kilograms by rememberSaveable { mutableStateOf("") }
    var event by rememberSaveable { mutableStateOf(ColonyEventKind.SWARMED) }
    val date = LocalDate.ofEpochDay(day)

    val valid = when (kind) {
        HoneyRecordKind.MITE_CHECK -> mites.toIntOrNull() != null && (method == VarroaMethod.DRONE_UNCAPPING || (sample.toIntOrNull() ?: 0) > 0)
        HoneyRecordKind.TREATMENT -> product.isNotBlank()
        HoneyRecordKind.HARVEST -> kilograms.toGrams() != null
        else -> true
    }

    DetailScaffold(title = stringResource(kind.title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (kind == HoneyRecordKind.TREATMENT) R.string.honey_treatment_started else R.string.honey_date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(UiFormat.longDate(date), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = { pickingDate = true }) { Text(stringResource(R.string.honey_change_date)) }
                }
            }
            when (kind) {
                HoneyRecordKind.INSPECTION -> {
                    Toggle(stringResource(R.string.honey_queen_seen), queenSeen) { queenSeen = it }
                    Toggle(stringResource(R.string.honey_eggs_seen), eggsSeen) { eggsSeen = it }
                    ChipGroup(stringResource(R.string.honey_brood), listOf<BroodPattern?>(null) + BroodPattern.entries, brood, { stringResource(it.label) }) { brood = it }
                    ChipGroup(stringResource(R.string.honey_temperament), listOf<Temperament?>(null) + Temperament.entries, temperament, { stringResource(it.label) }) { temperament = it }
                    NumberField(stringResource(R.string.honey_frames_bees), framesOfBees) { framesOfBees = it }
                    NumberField(stringResource(R.string.honey_frames_stores), framesOfStores) { framesOfStores = it }
                    ChipGroup(stringResource(R.string.honey_queen_cells), QueenCells.entries, queenCells, { stringResource(it.label) }) { queenCells = it }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.honey_signs), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HiveSign.entries.forEach { sign ->
                                FilterChip(selected = sign in signs, onClick = { signs = if (sign in signs) signs - sign else signs + sign }, label = { Text(stringResource(sign.label)) })
                            }
                        }
                    }
                    if (signs.any { it == HiveSign.SUNKEN_CAPPINGS || it == HiveSign.ROPY_BROOD || it == HiveSign.TWISTED_LARVAE }) FoulbroodNotice()
                    TextArea(stringResource(R.string.honey_actions), actions) { actions = it }
                }
                HoneyRecordKind.MITE_CHECK -> {
                    ChipGroup(stringResource(R.string.honey_check_method), VarroaMethod.entries, method, { stringResource(it.label) }) { method = it }
                    when (method) {
                        VarroaMethod.STICKY_MAT -> NumberField(stringResource(R.string.honey_sample_days), sample) { sample = it }
                        VarroaMethod.DRONE_UNCAPPING -> Text(stringResource(R.string.honey_check_drone_note), style = MaterialTheme.typography.bodyMedium)
                        else -> NumberField(stringResource(R.string.honey_sample_bees), sample) { sample = it }
                    }
                    NumberField(stringResource(R.string.honey_mites), mites) { mites = it }
                    Toggle(stringResource(R.string.honey_reported), reported) { reported = it }
                    StateMiteChecks.of(current?.gardenState)?.let { rule ->
                        TextButton(onClick = { uri.openUri(rule.reportPage) }) {
                            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                            Text(stringResource(R.string.honey_report_bee123))
                        }
                    }
                }
                HoneyRecordKind.TREATMENT -> {
                    Text(stringResource(R.string.honey_treatment_label), style = MaterialTheme.typography.bodyLarge)
                    TextLine(stringResource(R.string.honey_treatment_product), product) { product = it }
                    TextLine(stringResource(R.string.honey_treatment_ingredient), ingredient) { ingredient = it }
                    Toggle(stringResource(R.string.honey_supers_removed), supersRemoved) { supersRemoved = it }
                }
                HoneyRecordKind.FEEDING -> {
                    ChipGroup(stringResource(R.string.honey_feed_kind), FeedKind.entries, feed, { stringResource(it.label) }) { feed = it }
                    val syrup = feed == FeedKind.SYRUP_1_TO_1 || feed == FeedKind.SYRUP_2_TO_1
                    NumberField(stringResource(if (syrup) R.string.honey_feed_amount_ml else R.string.honey_feed_amount_g), amount) { amount = it }
                }
                HoneyRecordKind.HARVEST -> {
                    NumberField(stringResource(R.string.honey_harvest_frames), frames) { frames = it }
                    OutlinedTextField(
                        value = kilograms,
                        onValueChange = { kilograms = it },
                        label = { Text(stringResource(R.string.honey_harvest_kg)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HoneyRecordKind.EVENT -> ChipGroup(stringResource(R.string.honey_event_kind), ColonyEventKind.entries, event, { stringResource(it.label) }) { event = it }
            }
            TextArea(stringResource(R.string.honey_notes), notes) { notes = it }
            Button(
                enabled = valid,
                onClick = {
                    scope.launch {
                        val id = bees.newRecordId()
                        when (kind) {
                            HoneyRecordKind.INSPECTION -> bees.saveInspection(
                                Inspection(id, hiveId, date, queenSeen, eggsSeen, brood, temperament, framesOfBees.toIntOrNull(), framesOfStores.toIntOrNull(), queenCells, signs, actions, notes),
                            )
                            HoneyRecordKind.MITE_CHECK -> bees.saveCheck(
                                VarroaCheck(id, hiveId, date, method, if (method == VarroaMethod.DRONE_UNCAPPING) 0 else sample.toIntOrNull() ?: 0, mites.toIntOrNull() ?: 0, if (reported) date else null),
                            )
                            HoneyRecordKind.TREATMENT -> bees.saveTreatment(Treatment(id, hiveId, product, ingredient, date, null, supersRemoved, notes))
                            HoneyRecordKind.FEEDING -> bees.saveFeeding(Feeding(id, hiveId, date, feed, amount.toIntOrNull() ?: 0, notes))
                            HoneyRecordKind.HARVEST -> bees.saveHarvest(Harvest(id, hiveId, date, frames.toIntOrNull() ?: 0, kilograms.toGrams() ?: 0, notes))
                            HoneyRecordKind.EVENT -> bees.saveEvent(ColonyEvent(id, hiveId, date, event, notes))
                        }
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }

    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
}

/** What an inspection's signs of foulbrood mean, and what to do. */
@Composable
internal fun FoulbroodNotice() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
            Text(stringResource(R.string.honey_foulbrood), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(checked = checked, onCheckedChange = onChange, trailingContent = { Switch(checked = checked, onCheckedChange = null) }) { Text(label) }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextLine(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TextArea(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, minLines = 2, modifier = Modifier.fillMaxWidth())
}

/** Kilograms typed with a point or a comma as whole grams, or null if it is not a positive number. */
private fun String.toGrams(): Int? {
    val value = trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() <= 0) return null
    return value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toInt()
}
