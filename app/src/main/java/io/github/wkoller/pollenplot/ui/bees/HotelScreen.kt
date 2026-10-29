package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.HotelRepository
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * One bee hotel: where it is, its sections, the latest look with who the seals say is inside, a note when it is worth
 * cleaning out this spring, buttons to record a look or upkeep, and every record, each with its own delete button.
 */
@Composable
fun HotelScreen(views: BeeViews, hotels: HotelRepository, hotelId: String, onBack: () -> Unit, onEdit: () -> Unit, onLook: () -> Unit, onUpkeep: () -> Unit) {
    val view by remember(hotelId) { views.hotel(hotelId) }.collectAsStateWithLifecycle(initialValue = null)
    val looks by hotels.observations.collectAsStateWithLifecycle(initialValue = emptyList())
    val upkeep by hotels.upkeep.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    val loaded = view

    DetailScaffold(
        title = loaded?.hotel?.name.orEmpty(),
        onBack = onBack,
        actions = { IconButton(onClick = onEdit) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.hotel_edit)) } },
    ) { padding ->
        if (loaded == null) return@DetailScaffold
        val hotel = loaded.hotel
        val latest = loaded.state.latest
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item(key = "summary") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(listOfNotNull(pluralStringResource(R.plurals.hotel_hole_count, hotel.holes, hotel.holes), loaded.zoneName).joinToString(" · "), style = MaterialTheme.typography.titleMedium)
                    hotel.facing?.let { Text(stringResource(R.string.hotel_faces, stringResource(it.label).lowercase()), style = MaterialTheme.typography.bodyLarge) }
                    hotel.heightMm?.let { Text(stringResource(R.string.hotel_height, BigDecimal(it).movePointLeft(1).stripTrailingZeros().toPlainString()), style = MaterialTheme.typography.bodyLarge) }
                    hotel.installedOn?.let { Text(stringResource(R.string.hotel_installed, UiFormat.longDate(it)), style = MaterialTheme.typography.bodyLarge) }
                }
            }
            if (hotel.sections.isNotEmpty()) {
                item(key = "sections") { SectionHeading(stringResource(R.string.hotel_sections)) }
                items(hotel.sections.withIndex().toList(), key = { "section-" + it.index }) { (_, section) -> ListItem { Text(sectionLine(section)) } }
            }
            if (loaded.cleanThisSpring) {
                item(key = "clean") {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            item(key = "record") {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onLook) { Text(stringResource(R.string.hotel_record_look)) }
                    OutlinedButton(onClick = onUpkeep) { Text(stringResource(R.string.hotel_record_upkeep)) }
                }
            }
            val ownLooks = looks.filter { it.hotelId == hotelId }
            item(key = "looks") { SectionHeading(stringResource(R.string.hotel_looks)) }
            if (ownLooks.isEmpty()) item(key = "looks-none") { NothingYet() }
            items(ownLooks, key = { "look-" + it.id }) { look ->
                val pests = mutableListOf<String>()
                for (pest in look.pests.sortedBy { it.ordinal }) pests += stringResource(pest.label)
                Record(
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
            if (ownUpkeep.isEmpty()) item(key = "upkeep-none") { NothingYet() }
            items(ownUpkeep, key = { "upkeep-" + it.id }) { record ->
                Record(
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
private fun NothingYet() {
    Text(stringResource(R.string.honey_nothing_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun Record(headline: String, details: String, onDelete: () -> Unit) {
    ListItem(
        supportingContent = if (details.isBlank()) null else { { Text(details) } },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.honey_delete_record)) }
        },
    ) { Text(headline) }
}
