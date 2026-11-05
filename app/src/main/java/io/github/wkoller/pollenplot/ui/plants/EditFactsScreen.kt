package io.github.wkoller.pollenplot.ui.plants

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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.CorrectionRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.garden.PruneTasks
import io.github.wkoller.pollenplot.domain.garden.PruningGuide
import io.github.wkoller.pollenplot.domain.plants.CareCorrections
import io.github.wkoller.pollenplot.domain.plants.CareField
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.Fertiliser
import io.github.wkoller.pollenplot.domain.plants.SizeRange
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.plants.WaterNeed
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.review.readable
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Month
import java.time.format.TextStyle

/** Each correctable fact as the knowledge base writes it, or null for none, kept across turns of the phone. */
private val DraftSaver = Saver<Map<CareField, String?>?, ArrayList<String>>(
    save = { draft -> draft?.let { ArrayList(it.map { (field, value) -> "${field.name}=${value.orEmpty()}" }) } },
    restore = { saved ->
        saved.associate { line ->
            val (name, value) = line.split('=', limit = 2)
            CareField.valueOf(name) to value.ifEmpty { null }
        }
    },
)

/**
 * The user's corrections to a plant's facts. They apply to every plant of the species, and each can go back to the
 * knowledge base's value. Safety facts are not offered.
 */
@Composable
fun EditFactsScreen(
    plantId: String,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    corrections: CorrectionRepository,
    /** The sourced pruning times, which give the pruning months a correction replaces. */
    pruning: PruningGuide = PruningGuide.EMPTY,
    onBack: () -> Unit,
) {
    val view by views.plant(plantId).collectAsStateWithLifecycle(initialValue = null)
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf<Map<CareField, String?>?>(null) }
    var sizeError by rememberSaveable { mutableStateOf(setOf<CareField>()) }
    // Counts the times a size went back to the knowledge base, so its typed text starts afresh.
    var resets by rememberSaveable { mutableStateOf(0) }

    val loadedKb = kb
    val species = view?.species
    DetailScaffold(title = stringResource(R.string.facts_title), onBack = onBack) { padding ->
        if (view == null || loadedKb == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { PollenLoadingIndicator() }
            return@DetailScaffold
        }
        if (species == null) {
            Text(stringResource(R.string.facts_unavailable), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(padding).padding(16.dp))
            return@DetailScaffold
        }
        val own = CareResolver.resolve(loadedKb, species, corrected = false).care
        val current = CareResolver.resolve(loadedKb, species).care
        val values = draft ?: CareCorrections.EDITABLE.associateWith { CareCorrections.encode(it, current) }
        fun value(field: CareField): CareProfile = values[field]?.let { CareCorrections.decode(mapOf(field to it)) } ?: CareProfile()
        fun change(field: CareField, care: CareProfile) {
            draft = values + (field to CareCorrections.encode(field, care))
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.facts_body, species.displayName), style = MaterialTheme.typography.bodyLarge)
            CareCorrections.EDITABLE.forEach { field ->
                HorizontalDivider()
                val corrected = values[field] != CareCorrections.encode(field, own)
                FactSection(
                    title = stringResource(field.title),
                    corrected = corrected,
                    kbValue = if (field == CareField.PRUNING_MONTHS) {
                        PruneTasks.months(pruning.forPlant(species.name, species.genus, species.family), null, own.flowering, locality.hemisphere)
                            ?.let { UiFormat.months(it, listWords(), LocalConfiguration.current.locales[0]) }
                    } else {
                        describe(field, own, locality.hemisphere)
                    },
                    onUseKb = {
                        change(field, own)
                        sizeError = sizeError - field
                        resets++
                    },
                ) {
                    val care = value(field)
                    when (field) {
                        CareField.SUN -> ManyChips(Sun.entries, care.sun.orEmpty(), { stringResource(it.label) }) { change(field, CareProfile(sun = it.ifEmpty { null })) }
                        CareField.WATER -> OneChip(WaterNeed.entries, care.water, { stringResource(it.label) }) { change(field, CareProfile(water = it)) }
                        CareField.FROST -> OneChip((0..3).toList(), care.frost, { stringResource(toleranceLabel(it)) }) { change(field, CareProfile(frost = it)) }
                        CareField.SOILS -> ManyChips(Soil.entries, care.soils.orEmpty(), { stringResource(it.label) }) { change(field, CareProfile(soils = it.ifEmpty { null })) }
                        CareField.FERTILISER -> OneChip(Fertiliser.entries, care.fertiliser, { stringResource(it.label) }) { change(field, CareProfile(fertiliser = it)) }
                        CareField.FLOWERING -> {
                            // Shown in the garden's own months, kept for the Southern Hemisphere like the knowledge base.
                            val shown = care.flowering?.fromSouthern(locality.hemisphere)?.months.orEmpty().toSet()
                            val locale = LocalConfiguration.current.locales[0]
                            ManyChips(Month.entries, shown, { it.getDisplayName(TextStyle.SHORT, locale) }) { months ->
                                change(field, CareProfile(flowering = MonthSet.of(months).takeIf { !it.isEmpty }?.toSouthern(locality.hemisphere)))
                            }
                        }
                        CareField.PRUNING_MONTHS -> {
                            val shown = care.pruningMonths?.fromSouthern(locality.hemisphere)?.months.orEmpty().toSet()
                            val locale = LocalConfiguration.current.locales[0]
                            ManyChips(Month.entries, shown, { it.getDisplayName(TextStyle.SHORT, locale) }) { months ->
                                change(field, CareProfile(pruningMonths = MonthSet.of(months).takeIf { !it.isEmpty }?.toSouthern(locality.hemisphere)))
                            }
                        }
                        CareField.HEIGHT, CareField.WIDTH -> SizeFields(
                            key = "${field.name}:$resets",
                            range = if (field == CareField.HEIGHT) care.height else care.width,
                            error = field in sizeError,
                        ) { range, valid ->
                            sizeError = if (valid) sizeError - field else sizeError + field
                            if (valid) change(field, if (field == CareField.HEIGHT) CareProfile(height = range) else CareProfile(width = range))
                        }
                        else -> Unit
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch {
                        CareCorrections.EDITABLE.forEach { field ->
                            val target = values[field]
                            when (target) {
                                CareCorrections.encode(field, current) -> Unit
                                null, CareCorrections.encode(field, own) -> corrections.clear(species.name, field)
                                else -> corrections.set(species.name, field, CareCorrections.decode(mapOf(field to target)))
                            }
                        }
                        onBack()
                    }
                },
                enabled = sizeError.isEmpty(),
            ) { Text(stringResource(R.string.facts_save)) }
        }
    }
}

private val CareField.title: Int
    get() = when (this) {
        CareField.SUN -> R.string.facts_light
        CareField.WATER -> R.string.facts_water
        CareField.FROST -> R.string.facts_frost
        CareField.SOILS -> R.string.facts_soils
        CareField.FERTILISER -> R.string.facts_fertiliser
        CareField.FLOWERING -> R.string.facts_flowering
        CareField.HEIGHT -> R.string.facts_height
        CareField.PRUNING_MONTHS -> R.string.facts_pruning_months
        else -> R.string.facts_width
    }

/** One correctable fact: its heading, whether the user corrected it, the editor, and the knowledge base's value. */
@Composable
private fun FactSection(title: String, corrected: Boolean, kbValue: String?, onUseKb: () -> Unit, editor: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (corrected) Badge(R.drawable.ic_edit, stringResource(R.string.facts_corrected), BadgeTone.WILDLIFE)
        }
        editor()
        Text(
            kbValue?.let { stringResource(R.string.facts_kb_value, it) } ?: stringResource(R.string.facts_kb_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (corrected) TextButton(onClick = onUseKb) { Text(stringResource(R.string.facts_use_kb)) }
    }
}

/** What the knowledge base says for [field], in words, or null when it has nothing. */
@Composable
private fun describe(field: CareField, care: CareProfile, hemisphere: Hemisphere): String? {
    val words = listWords()
    val locale = LocalConfiguration.current.locales[0]
    return when (field) {
        CareField.SUN -> care.sun?.sortedBy { it.ordinal }?.map { stringResource(it.label) }?.let { UiFormat.joinAnd(it, words) }
        CareField.WATER -> care.water?.let { stringResource(it.label) }
        CareField.FROST -> care.frost?.let { stringResource(toleranceLabel(it)) }
        CareField.SOILS -> care.soils?.sortedBy { it.ordinal }?.map { stringResource(it.label) }?.let { UiFormat.joinAnd(it, words) }
        CareField.FERTILISER -> care.fertiliser?.let { stringResource(it.label) }
        CareField.FLOWERING -> care.flowering?.fromSouthern(hemisphere)?.let { UiFormat.months(it, words, locale) }
        CareField.HEIGHT -> care.height?.let { stringResource(R.string.plant_range_metres, UiFormat.metresFromMm(it.minMm), UiFormat.metresFromMm(it.maxMm)) }
        CareField.WIDTH -> care.width?.let { stringResource(R.string.plant_range_metres, UiFormat.metresFromMm(it.minMm), UiFormat.metresFromMm(it.maxMm)) }
        else -> null
    }
}

@Composable
private fun <T> OneChip(options: List<T>, selected: T?, label: @Composable (T) -> String, onChoose: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option -> FilterChip(selected = option == selected, onClick = { onChoose(option) }, label = { Text(label(option)) }) }
    }
}

@Composable
private fun <T> ManyChips(options: List<T>, selected: Set<T>, label: @Composable (T) -> String, onChange: (Set<T>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val on = option in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - option else selected + option) },
                label = { Text(label(option)) },
                leadingIcon = if (on) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Two sizes in metres, smallest first. They keep what the user types, and report a range once both read as metres; a
 * new [key] (the knowledge base's value chosen again) starts them afresh from [range].
 */
@Composable
private fun SizeFields(key: String, range: SizeRange?, error: Boolean, onChange: (SizeRange?, valid: Boolean) -> Unit) {
    var from by rememberSaveable(key) { mutableStateOf(range?.let { UiFormat.metresFromMm(it.minMm) }.orEmpty()) }
    var to by rememberSaveable(key) { mutableStateOf(range?.let { UiFormat.metresFromMm(it.maxMm) }.orEmpty()) }
    fun report() {
        val min = millimetres(from)
        val max = millimetres(to)
        when {
            from.isBlank() && to.isBlank() -> onChange(null, true)
            min != null && max != null && min <= max -> onChange(SizeRange(min, max), true)
            else -> onChange(null, false)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // With large text the two fields go one above the other, so neither label is cut short.
        val stacked = largeText()
        val fieldModifier = if (stacked) Modifier.fillMaxWidth() else Modifier
        val fields: @Composable (Modifier) -> Unit = { weight ->
            OutlinedTextField(
                value = from,
                onValueChange = {
                    from = it
                    report()
                },
                label = { Text(stringResource(R.string.facts_from)) },
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = fieldModifier.then(weight),
            )
            OutlinedTextField(
                value = to,
                onValueChange = {
                    to = it
                    report()
                },
                label = { Text(stringResource(R.string.facts_to)) },
                singleLine = true,
                isError = error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = fieldModifier.then(weight),
            )
        }
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { fields(Modifier) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { fields(Modifier.weight(1f)) }
        }
        if (error) Text(stringResource(R.string.facts_size_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/** Metres typed by the user, such as "1.5", in millimetres, or null when it is not a size. */
private fun millimetres(text: String): Int? {
    val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (value.signum() < 0 || value > BigDecimal(200)) return null
    return value.movePointRight(3).setScale(0, RoundingMode.HALF_UP).toInt()
}
