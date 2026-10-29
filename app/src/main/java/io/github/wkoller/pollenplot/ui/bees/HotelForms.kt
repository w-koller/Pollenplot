package io.github.wkoller.pollenplot.ui.bees

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.HotelRepository
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.hotels.BeeHotel
import io.github.wkoller.pollenplot.domain.hotels.HotelMaterial
import io.github.wkoller.pollenplot.domain.hotels.HotelObservation
import io.github.wkoller.pollenplot.domain.hotels.HotelPest
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.domain.hotels.HotelUpkeep
import io.github.wkoller.pollenplot.domain.hotels.Seal
import io.github.wkoller.pollenplot.domain.hotels.UpkeepKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.plants.QuantityStepper
import io.github.wkoller.pollenplot.ui.plants.ZoneChooser
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** A hotel's sections kept across a turn of the phone, each as its material, hole width, depth and number of holes. */
private val SectionsSaver = listSaver<List<HotelSection>, String>(
    save = { sections -> sections.map { listOf(it.material.name, it.holeMm ?: "", it.depthMm ?: "", it.holes).joinToString(",") } },
    restore = { saved ->
        saved.map { line ->
            val (material, hole, depth, holes) = line.split(',')
            HotelSection(HotelMaterial.valueOf(material), hole.toIntOrNull(), depth.toIntOrNull(), holes.toInt())
        }
    },
)

/**
 * A new bee hotel, or a hotel's details to change when [hotelId] is given: its name, zone, the way it faces, its
 * height, and its sections, each with its material, hole size and number of holes. A hotel can be deleted from here.
 */
@Composable
fun HotelEditorScreen(hotels: HotelRepository, views: GardenViews, hotelId: String?, today: LocalDate, onBack: () -> Unit, onDeleted: () -> Unit, onCreated: (String) -> Unit) {
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var started by rememberSaveable { mutableStateOf(hotelId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var zoneId by rememberSaveable { mutableStateOf<String?>(null) }
    var facing by rememberSaveable { mutableStateOf<Facing?>(null) }
    var heightCm by rememberSaveable { mutableStateOf("") }
    var installedOn by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    var sections by rememberSaveable(stateSaver = SectionsSaver) { mutableStateOf(emptyList<HotelSection>()) }
    var notes by rememberSaveable { mutableStateOf("") }
    // The section being written before it is added.
    var material by rememberSaveable { mutableStateOf(HotelMaterial.DRILLED_TIMBER) }
    var holeMm by rememberSaveable { mutableStateOf("") }
    var depthMm by rememberSaveable { mutableStateOf("") }
    var holes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(hotelId) {
        if (started || hotelId == null) return@LaunchedEffect
        val hotel = hotels.hotels.first().firstOrNull { it.id == hotelId } ?: return@LaunchedEffect
        name = hotel.name
        zoneId = hotel.zoneId
        facing = hotel.facing
        heightCm = hotel.heightMm?.let { (it / 10).toString() }.orEmpty()
        installedOn = hotel.installedOn?.toEpochDay()
        sections = hotel.sections
        notes = hotel.notes
        started = true
    }

    DetailScaffold(
        title = stringResource(if (hotelId == null) R.string.hotel_new else R.string.hotel),
        onBack = onBack,
        actions = {
            if (hotelId != null) {
                IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.hotel_delete_title)) }
            }
        },
    ) { padding ->
        if (!started) return@DetailScaffold
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.honey_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ZoneChooser(zones, zoneId) { zoneId = it }
            ChipGroup(
                stringResource(R.string.native_facing),
                listOf<Facing?>(null) + Facing.entries,
                facing,
                { it?.let { direction -> stringResource(direction.label) } ?: stringResource(R.string.native_facing_unknown) },
            ) { facing = it }
            NumberField(stringResource(R.string.hotel_height_cm), heightCm) { heightCm = it }

            Text(stringResource(R.string.hotel_sections), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            sections.forEachIndexed { index, section ->
                ListItem(
                    trailingContent = {
                        IconButton(onClick = { sections = sections.filterIndexed { i, _ -> i != index } }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.hotel_section_remove))
                        }
                    },
                ) { Text(sectionLine(section)) }
            }
            ChipGroup(stringResource(R.string.hotel_material), HotelMaterial.entries, material, { stringResource(it.label) }) { material = it }
            NumberField(stringResource(R.string.hotel_hole_mm), holeMm) { holeMm = it }
            NumberField(stringResource(R.string.hotel_depth_mm), depthMm) { depthMm = it }
            NumberField(stringResource(R.string.hotel_holes), holes) { holes = it }
            OutlinedButton(
                enabled = (holes.toIntOrNull() ?: 0) > 0,
                onClick = {
                    sections = sections + HotelSection(material, holeMm.toIntOrNull(), depthMm.toIntOrNull(), holes.toInt())
                    holeMm = ""
                    depthMm = ""
                    holes = ""
                },
            ) { Text(stringResource(R.string.hotel_section_add)) }

            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val hotel = BeeHotel(
                            id = hotelId ?: hotels.newRecordId(),
                            name = name,
                            zoneId = zoneId,
                            facing = facing,
                            heightMm = heightCm.toIntOrNull()?.times(10),
                            installedOn = installedOn?.let(LocalDate::ofEpochDay),
                            sections = sections,
                            notes = notes,
                        )
                        hotels.saveHotel(hotel)
                        withContext(Dispatchers.Main) { if (hotelId == null) onCreated(hotel.id) else onBack() }
                    }
                },
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }

    if (confirmDelete && hotelId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.hotel_delete_title)) },
            text = { Text(stringResource(R.string.hotel_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        hotels.deleteHotel(hotelId)
                        withContext(Dispatchers.Main) { onDeleted() }
                    }
                }) { Text(stringResource(R.string.honey_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** A look at a hotel: how many holes are sealed with each kind of seal, how many are busy, what came out, and pests. */
@Composable
fun HotelLookScreen(hotelId: String, hotels: HotelRepository, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var counts by rememberSaveable { mutableStateOf(Seal.entries.map { 0 }) }
    var busy by rememberSaveable { mutableStateOf("") }
    var emerged by rememberSaveable { mutableStateOf("") }
    var pests by rememberSaveable { mutableStateOf(emptySet<HotelPest>()) }
    var notes by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(day)

    DetailScaffold(title = stringResource(R.string.hotel_look), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateRow(date) { pickingDate = true }
            Text(stringResource(R.string.hotel_sealed_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Seal.entries.forEachIndexed { index, seal ->
                QuantityStepper(counts[index], title = stringResource(seal.label), min = 0, countText = { it.toString() }) { value ->
                    counts = counts.mapIndexed { i, old -> if (i == index) value else old }
                }
            }
            NumberField(stringResource(R.string.hotel_busy), busy) { busy = it }
            NumberField(stringResource(R.string.hotel_emerged), emerged) { emerged = it }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.hotel_pests), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HotelPest.entries.forEach { pest ->
                        FilterChip(selected = pest in pests, onClick = { pests = if (pest in pests) pests - pest else pests + pest }, label = { Text(stringResource(pest.label)) })
                    }
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    hotels.saveObservation(
                        HotelObservation(
                            id = hotels.newRecordId(),
                            hotelId = hotelId,
                            date = date,
                            sealed = Seal.entries.zip(counts).filter { it.second > 0 }.toMap(),
                            busy = busy.toIntOrNull(),
                            emerged = emerged.toIntOrNull(),
                            pests = pests,
                            notes = notes,
                        ),
                    )
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

/** Upkeep of a hotel, such as cleaning it out, putting in new liners or moving it. */
@Composable
fun HotelUpkeepScreen(hotelId: String, hotels: HotelRepository, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(UpkeepKind.CLEANED) }
    var notes by rememberSaveable { mutableStateOf("") }
    val date = LocalDate.ofEpochDay(day)

    DetailScaffold(title = stringResource(R.string.hotel_upkeep), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DateRow(date) { pickingDate = true }
            ChipGroup(stringResource(R.string.hotel_upkeep_kind), UpkeepKind.entries, kind, { stringResource(it.label) }) { kind = it }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    hotels.saveUpkeep(HotelUpkeep(hotels.newRecordId(), hotelId, date, kind, notes))
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

@Composable
private fun DateRow(date: LocalDate, onChange: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.honey_date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(UiFormat.longDate(date), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onChange) { Text(stringResource(R.string.honey_change_date)) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
