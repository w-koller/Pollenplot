package io.github.wkoller.pollenplot.ui.plants

import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.components.FactCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.HarvestRepository
import io.github.wkoller.pollenplot.data.HarvestState
import io.github.wkoller.pollenplot.data.PruneState
import io.github.wkoller.pollenplot.domain.garden.Harvest
import io.github.wkoller.pollenplot.domain.garden.PruneTiming
import io.github.wkoller.pollenplot.domain.garden.SeasonHarvest
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.domain.season.Season
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.DateRow
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.weeds.LinkButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** The harvests shown on a plant's page before the rest wait for the plant's own list. */
private const val RECENT_HARVESTS = 5

/** A weight in grams as people read it: grams under a kilogram, else kilograms to one place. */
@Composable
internal fun weight(grams: Long): String = if (grams < 1_000) {
    stringResource(R.string.harvest_grams, grams.toString())
} else {
    stringResource(R.string.harvest_kilograms, BigDecimal.valueOf(grams, 3).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
}

private val Season.label: Int
    get() = when (this) {
        Season.SPRING -> R.string.harvest_spring
        Season.SUMMER -> R.string.harvest_summer
        Season.AUTUMN -> R.string.harvest_autumn
        Season.WINTER -> R.string.harvest_winter
    }

/** A season as its heading reads: "Spring 2026", or "Summer 2025–26" for one that runs into the next year. */
@Composable
private fun seasonName(total: SeasonHarvest): String {
    val year = total.start.year
    val years = if (total.crossesYear) "$year–" + "%02d".format((year + 1) % 100) else year.toString()
    return stringResource(R.string.harvest_season, stringResource(total.season.label), years)
}

/** What one harvest or one season gave: the weight and how many pieces, each when it was recorded. */
@Composable
private fun amounts(weightG: Long, count: Int): List<String> = listOfNotNull(
    weightG.takeIf { it > 0 }?.let { weight(it) },
    count.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.harvest_pieces, it, it) },
)

/**
 * A plant's harvests: the totals for each season, the months anything was picked, the latest harvests, and a button to
 * record one.
 */
@Composable
fun HarvestCard(state: HarvestState, onRecord: () -> Unit, onOpen: (String) -> Unit) {
    FactCard(R.drawable.ic_restaurant, stringResource(R.string.harvest_title)) {
        if (state.harvests.isEmpty()) {
            Text(stringResource(R.string.harvest_none), style = MaterialTheme.typography.bodyLarge)
        }
        val words = listWords()
        state.seasons.forEach { total ->
            val gave = amounts(total.weightG, total.count).takeIf { it.isNotEmpty() }?.let { stringResource(R.string.harvest_gave, UiFormat.joinAnd(it, words)) }.orEmpty()
            Text(pluralStringResource(R.plurals.harvest_season_line, total.harvests, seasonName(total), total.harvests, gave), style = MaterialTheme.typography.bodyLarge)
        }
        if (!state.months.isEmpty) {
            val locale = LocalConfiguration.current.locales[0]
            Text(
                if (state.months == MonthSet.ALL) stringResource(R.string.harvest_months_all) else stringResource(R.string.harvest_months, UiFormat.months(state.months, words, locale)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        state.harvests.take(RECENT_HARVESTS).forEach { harvest ->
            val details = amounts((harvest.weightG ?: 0).toLong(), harvest.count ?: 0) +
                listOfNotNull(harvest.rating?.let { stringResource(R.string.harvest_rated, it) })
            ListItem(
                onClick = { onOpen(harvest.id) },
                supportingContent = (details + listOfNotNull(harvest.notes.ifBlank { null })).takeIf { it.isNotEmpty() }?.let { lines -> { Text(lines.joinToString(" · ")) } },
            ) { Text(UiFormat.longDate(harvest.date)) }
        }
        Button(onClick = onRecord) { Text(stringResource(R.string.harvest_record)) }
    }
}

/** A plant's pruning: when to prune and why, the page that says so, whether a prune is due, and the last one. */
@Composable
fun PruningCard(state: PruneState, onRecord: () -> Unit) {
    val uri = LocalUriHandler.current
    val locale = LocalConfiguration.current.locales[0]
    FactCard(R.drawable.ic_content_cut, stringResource(R.string.prune_title)) {
        if (state.due) Badge(R.drawable.ic_content_cut, stringResource(R.string.prune_due), BadgeTone.WARNING)
        state.months?.let { months ->
            Text(
                if (months == MonthSet.ALL) stringResource(R.string.prune_months_all) else stringResource(R.string.prune_months, UiFormat.months(months, listWords(), locale)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        when {
            state.own -> Text(stringResource(R.string.prune_own), style = MaterialTheme.typography.bodyMedium)
            state.schedule?.timing == PruneTiming.AFTER_FLOWERING ->
                Text(
                    stringResource(
                        when {
                            state.months != null -> R.string.prune_after_flowering
                            state.floweringAllYear -> R.string.prune_after_flowering_all_year
                            else -> R.string.prune_after_flowering_unknown
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            state.schedule?.timing == PruneTiming.AFTER_HARVEST -> Text(stringResource(R.string.prune_after_harvest), style = MaterialTheme.typography.bodyMedium)
            state.schedule == null && state.months == null -> Text(stringResource(R.string.prune_no_time), style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            state.lastPruned?.let { stringResource(R.string.prune_last, UiFormat.longDate(it)) } ?: stringResource(R.string.prune_never),
            style = MaterialTheme.typography.bodyLarge,
        )
        state.schedule?.takeIf { !state.own }?.let { schedule ->
            LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.prune_source, schedule.sourceTitle)) { uri.openUri(schedule.sourceUrl) }
        }
        Button(onClick = onRecord) { Text(stringResource(R.string.prune_record)) }
    }
}

/** Records a harvest from a plant, or with [harvestId] changes or deletes one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HarvestScreen(plantId: String, harvestId: String?, harvests: HarvestRepository, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Harvest?>(null) }
    var initialised by rememberSaveable { mutableStateOf(false) }
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var kilograms by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(harvestId) {
        if (harvestId == null) return@LaunchedEffect
        val harvest = harvests.harvest(harvestId) ?: return@LaunchedEffect onBack()
        original = harvest
        if (!initialised) {
            day = harvest.date.toEpochDay()
            kilograms = harvest.weightG?.let { BigDecimal.valueOf(it.toLong(), 3).stripTrailingZeros().toPlainString() }.orEmpty()
            count = harvest.count?.toString().orEmpty()
            rating = harvest.rating
            notes = harvest.notes
            initialised = true
        }
    }
    if (harvestId != null && original == null) return
    val date = LocalDate.ofEpochDay(day)
    val grams = grams(kilograms)
    val pieces = count.trim().toIntOrNull()?.takeIf { it > 0 }
    val weightError = kilograms.isNotBlank() && grams == null
    val countError = count.isNotBlank() && pieces == null

    DetailScaffold(title = stringResource(if (harvestId == null) R.string.harvest_record_title else R.string.harvest_edit_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateRow(date) { pickingDate = true }
            OutlinedTextField(
                value = kilograms,
                onValueChange = { kilograms = it },
                label = { Text(stringResource(R.string.harvest_weight)) },
                supportingText = { Text(stringResource(if (weightError) R.string.harvest_weight_error else R.string.harvest_weight_help)) },
                isError = weightError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = count,
                onValueChange = { count = it },
                label = { Text(stringResource(R.string.harvest_how_many)) },
                supportingText = if (countError) {
                    { Text(stringResource(R.string.harvest_count_error)) }
                } else {
                    null
                },
                isError = countError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.harvest_rating), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.harvest_rating_help), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { stars ->
                        FilterChip(
                            selected = rating == stars,
                            onClick = { rating = if (rating == stars) null else stars },
                            label = { Text(stringResource(R.string.harvest_rating_choice, stars)) },
                        )
                    }
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.harvest_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    scope.launch {
                        val stored = original
                        if (stored == null) {
                            harvests.add(plantId, date, grams, pieces, rating, notes)
                        } else {
                            harvests.update(stored.copy(date = date, weightG = grams, count = pieces, rating = rating, notes = notes))
                        }
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
                enabled = !weightError && !countError,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.harvest_save)) }
            if (original != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(R.string.harvest_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.harvest_delete_title)) },
            text = { Text(stringResource(R.string.harvest_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        original?.let { harvests.delete(it.id) }
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text(stringResource(R.string.harvest_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Kilograms as typed, such as "1.25", in grams, or null for nothing or something that is not a weight. */
internal fun grams(kilograms: String): Int? {
    val text = kilograms.trim().replace(',', '.')
    if (text.isEmpty()) return null
    val value = text.toBigDecimalOrNull() ?: return null
    val grams = value.movePointRight(3).setScale(0, RoundingMode.HALF_UP)
    return grams.toInt().takeIf { grams.signum() > 0 && grams < BigDecimal(1_000_000) }
}
