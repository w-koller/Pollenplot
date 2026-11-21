package io.github.wkoller.pollenplot.ui.bees

import io.github.wkoller.pollenplot.ui.components.GroupedItem
import io.github.wkoller.pollenplot.ui.components.MenuAction
import io.github.wkoller.pollenplot.ui.components.RecordHero
import io.github.wkoller.pollenplot.ui.components.RecordMenu
import io.github.wkoller.pollenplot.ui.components.heroGone
import io.github.wkoller.pollenplot.ui.components.listPadding
import io.github.wkoller.pollenplot.ui.theme.Spacing
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalResources
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import kotlinx.coroutines.flow.map
import io.github.wkoller.pollenplot.ui.components.MissingRecord
import io.github.wkoller.pollenplot.ui.components.Found
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.HotelRepository
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * One bee hotel: a hero with where it is, its sections, the latest look with who the seals say is inside, a note when it
 * is worth cleaning out this spring, and every record in its group, each with its own delete button. The record menu
 * adds a look or upkeep.
 */
@Composable
fun HotelScreen(views: BeeViews, hotels: HotelRepository, hotelId: String, onBack: () -> Unit, onEdit: () -> Unit, onLook: () -> Unit, onUpkeep: () -> Unit) {
    val found by remember(hotelId) { views.hotel(hotelId).map { Found(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val view = found?.value
    val looks by hotels.observations.collectAsStateWithLifecycle(initialValue = emptyList())
    val upkeep by hotels.upkeep.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val loaded = view
    val list = rememberLazyListState()
    val resources = LocalResources.current

    DetailScaffold(
        title = loaded?.hotel?.name.orEmpty(),
        onBack = onBack,
        titleShown = heroGone(list),
        actions = { IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.hotel_edit)) } },
        floatingActionButton = if (loaded == null) null else {
            {
                RecordMenu(
                    listOf(
                        MenuAction(resources.getString(R.string.hotel_record_look), R.drawable.ic_visibility, onLook),
                        MenuAction(resources.getString(R.string.hotel_record_upkeep), R.drawable.ic_handyman, onUpkeep),
                    ),
                )
            }
        },
    ) { padding ->
        if (loaded == null) {
            if (found != null) MissingRecord(padding)
            return@DetailScaffold
        }
        val hotel = loaded.hotel
        val latest = loaded.state.latest
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = listPadding(padding)) {
            item(key = "summary") {
                RecordHero(
                    title = hotel.name,
                    details = listOfNotNull(
                        listOfNotNull(pluralStringResource(R.plurals.hotel_hole_count, hotel.holes, hotel.holes), loaded.zoneName).joinToString(" · "),
                        hotel.facing?.let { stringResource(R.string.hotel_faces, stringResource(it.label).lowercase()) },
                        hotel.heightMm?.let { stringResource(R.string.hotel_height, BigDecimal(it).movePointLeft(1).stripTrailingZeros().toPlainString()) },
                        hotel.installedOn?.let { stringResource(R.string.hotel_installed, UiFormat.longDate(it)) },
                    ),
                ) { BeeHeroPicture(R.drawable.ic_cottage) }
            }
            if (hotel.sections.isNotEmpty()) {
                item(key = "sections") { SectionHeading(stringResource(R.string.hotel_sections)) }
                itemsIndexed(hotel.sections, key = { index, _ -> "section-$index" }) { index, section ->
                    GroupedItem(index, hotel.sections.size) { Text(sectionLine(section)) }
                }
            }
            if (loaded.cleanThisSpring) {
                item(key = "clean") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                    ) {
                        Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                            Icon(painterResource(R.drawable.ic_calendar_month), contentDescription = null)
                            Text(stringResource(R.string.hotel_spring_clean), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item(key = "latest-heading") {
                SectionHeading(latest?.let { stringResource(R.string.hotel_latest, UiFormat.longDate(it.date)) } ?: stringResource(R.string.hotel_latest_none))
            }
            item(key = "latest") {
                Column(Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (latest == null) {
                        Text(stringResource(R.string.hotel_no_looks), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        latest.sealed.entries.sortedBy { it.key.ordinal }.forEach { (seal, count) ->
                            Text(pluralStringResource(R.plurals.hotel_sealed_count, count, count, stringResource(seal.label).lowercase()), style = MaterialTheme.typography.bodyLarge)
                            Text(likelyText(seal), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        latest.busy?.let { Text(pluralStringResource(R.plurals.hotel_busy_count, it, it), style = MaterialTheme.typography.bodyLarge) }
                        latest.emerged?.let { Text(pluralStringResource(R.plurals.hotel_emerged_count, it, it), style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }
            val ownLooks = looks.filter { it.hotelId == hotelId }
            item(key = "looks") { SectionHeading(stringResource(R.string.hotel_looks)) }
            if (ownLooks.isEmpty()) item(key = "looks-none") { NothingRecorded() }
            itemsIndexed(ownLooks, key = { _, look -> "look-" + look.id }) { index, look ->
                val pests = mutableListOf<String>()
                for (pest in look.pests.sortedBy { it.ordinal }) pests += stringResource(pest.label)
                Record(
                    index = index,
                    count = ownLooks.size,
                    headline = UiFormat.longDate(look.date),
                    details = listOfNotNull(
                        pluralStringResource(R.plurals.hotel_sealed_total, look.sealedHoles, look.sealedHoles),
                        look.busy?.let { pluralStringResource(R.plurals.hotel_busy_count, it, it) },
                        pests.joinToString(", ").ifBlank { null },
                        look.notes.ifBlank { null },
                    ).joinToString(" · "),
                    onDelete = { pendingDelete = { hotels.deleteObservation(look.id) } },
                )
            }
            val ownUpkeep = upkeep.filter { it.hotelId == hotelId }
            item(key = "upkeep") { SectionHeading(stringResource(R.string.hotel_upkeep)) }
            if (ownUpkeep.isEmpty()) item(key = "upkeep-none") { NothingRecorded() }
            itemsIndexed(ownUpkeep, key = { _, record -> "upkeep-" + record.id }) { index, record ->
                Record(
                    index = index,
                    count = ownUpkeep.size,
                    headline = UiFormat.longDate(record.date),
                    details = listOf(stringResource(record.kind.label), record.notes).filter { it.isNotBlank() }.joinToString(" · "),
                    onDelete = { pendingDelete = { hotels.deleteUpkeep(record.id) } },
                )
            }
        }
    }

    pendingDelete?.let { delete ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.honey_delete_record_title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch { delete() }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A section as one line, such as "Drilled timber · 8 mm wide · 120 mm deep · 24 holes". */
@Composable
internal fun sectionLine(section: HotelSection): String = listOfNotNull(
    stringResource(section.material.label),
    section.holeMm?.let { stringResource(R.string.hotel_section_size, it) },
    section.depthMm?.let { stringResource(R.string.hotel_section_depth, it) },
    pluralStringResource(R.plurals.hotel_hole_count, section.holes, section.holes),
).joinToString(" · ")

@Composable
private fun Record(index: Int, count: Int, headline: String, details: String, onDelete: () -> Unit) {
    GroupedItem(
        index = index,
        count = count,
        supporting = if (details.isBlank()) null else { { Text(details) } },
        trailing = {
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_record)) }
        },
    ) { Text(headline) }
}
