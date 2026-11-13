package io.github.wkoller.pollenplot.ui.plot

import io.github.wkoller.pollenplot.ui.components.readable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.HabitatRepository
import io.github.wkoller.pollenplot.data.ZoneHabitatView
import io.github.wkoller.pollenplot.domain.garden.CorridorSummary
import io.github.wkoller.pollenplot.domain.garden.GroundFeature
import io.github.wkoller.pollenplot.domain.garden.HabitatLayer
import io.github.wkoller.pollenplot.domain.garden.WaterFeature
import io.github.wkoller.pollenplot.domain.garden.WaterKind
import io.github.wkoller.pollenplot.domain.garden.ZoneHabitat
import io.github.wkoller.pollenplot.domain.rhythms.RhythmPlant
import io.github.wkoller.pollenplot.domain.rhythms.RhythmsEngine
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.rhythms.label
import io.github.wkoller.pollenplot.ui.rhythms.offerMonths
import io.github.wkoller.pollenplot.ui.weeds.LinkButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** NSW Environment's page that the layers and ground features come from. */
const val BACKYARDS_FOR_NATURE = "https://www.environment.nsw.gov.au/about-us/get-involved/sydney-nature/gardens/backyards"

val HabitatLayer.label: Int
    @StringRes get() = when (this) {
        HabitatLayer.CANOPY -> R.string.habitat_layer_canopy
        HabitatLayer.MIDSTOREY -> R.string.habitat_layer_midstorey
        HabitatLayer.GROUNDCOVER -> R.string.habitat_layer_groundcover
    }

val HabitatLayer.icon: Int
    @DrawableRes get() = when (this) {
        HabitatLayer.CANOPY -> R.drawable.ic_forest
        HabitatLayer.MIDSTOREY -> R.drawable.ic_park
        HabitatLayer.GROUNDCOVER -> R.drawable.ic_grass
    }

val GroundFeature.label: Int
    @StringRes get() = when (this) {
        GroundFeature.LEAF_LITTER -> R.string.habitat_feature_leaf_litter
        GroundFeature.ROCKS_AND_LOGS -> R.string.habitat_feature_rocks_logs
        GroundFeature.DENSE_SHRUBS -> R.string.habitat_feature_dense_shrubs
    }

val GroundFeature.icon: Int
    @DrawableRes get() = when (this) {
        GroundFeature.LEAF_LITTER -> R.drawable.ic_compost
        GroundFeature.ROCKS_AND_LOGS -> R.drawable.ic_landscape
        GroundFeature.DENSE_SHRUBS -> R.drawable.ic_eco
    }

val WaterKind.label: Int
    @StringRes get() = when (this) {
        WaterKind.BIRD_BATH -> R.string.water_kind_bird_bath
        WaterKind.POND -> R.string.water_kind_pond
        WaterKind.BEE_WATER -> R.string.water_kind_bee_water
        WaterKind.OTHER -> R.string.water_kind_other
    }

val WaterKind.icon: Int
    @DrawableRes get() = when (this) {
        WaterKind.BIRD_BATH -> R.drawable.ic_raven
        WaterKind.POND -> R.drawable.ic_frog
        WaterKind.BEE_WATER -> R.drawable.ic_hive
        WaterKind.OTHER -> R.drawable.ic_water_drop
    }

/**
 * One layer or feature with whether it is there, as an icon and a word. With large text the word moves under the
 * label rather than being squeezed beside it.
 */
@Composable
private fun PresenceRow(@DrawableRes icon: Int, label: String, present: Boolean) {
    val status = stringResource(if (present) R.string.habitat_present else R.string.habitat_missing)
    val description = stringResource(R.string.habitat_row, label, status)
    val colour = if (present) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val statusIcon = if (present) R.drawable.ic_check_circle else R.drawable.ic_cancel
    val word: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(painterResource(statusIcon), contentDescription = null, tint = colour, modifier = Modifier.size(18.dp))
            Text(status, style = MaterialTheme.typography.labelLarge, color = colour)
        }
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp))
        if (largeText()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                word()
            }
        } else {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            word()
        }
    }
}

/** The layers and ground features, each present or missing. */
@Composable
private fun LayersAndFeatures(layers: Set<HabitatLayer>, features: Set<GroundFeature>, water: Boolean?) {
    Column {
        HabitatLayer.entries.forEach { PresenceRow(it.icon, stringResource(it.label), it in layers) }
        GroundFeature.entries.forEach { PresenceRow(it.icon, stringResource(it.label), it in features) }
        water?.let { PresenceRow(R.drawable.ic_water_drop, stringResource(R.string.habitat_feature_water), it) }
    }
}

/**
 * A zone's Habitat card: its layers and ground features, the water set out in it with when each was cleaned, and the
 * nearest water elsewhere.
 */
@Composable
fun HabitatCard(view: ZoneHabitatView, onAddWater: () -> Unit, onOpenWater: (String) -> Unit, onCleaned: (String) -> Unit) {
    val habitat = view.habitat
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(R.plurals.habitat_count, habitat.present, habitat.present, ZoneHabitat.POSSIBLE),
            style = MaterialTheme.typography.bodyLarge,
        )
        LayersAndFeatures(habitat.layers, habitat.features, water = habitat.water.isNotEmpty())
        Text(stringResource(R.string.habitat_layers_from), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val nearZone = view.nearestWaterZone
        val nearCm = habitat.nearestWaterCm
        val waterLine = when {
            habitat.water.isNotEmpty() -> stringResource(R.string.habitat_water_here)
            nearZone != null && nearCm != null -> stringResource(R.string.habitat_water_near, nearZone, distance(nearCm))
            else -> stringResource(R.string.habitat_water_none)
        }
        Text(waterLine, style = MaterialTheme.typography.bodyLarge)
        habitat.water.forEach { feature -> WaterRow(feature, onOpen = { onOpenWater(feature.id) }, onCleaned = { onCleaned(feature.id) }) }
        OutlinedButton(onClick = onAddWater) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.habitat_add_water), modifier = Modifier.padding(start = 8.dp))
        }
        LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.habitat_source)) { uri.openUri(BACKYARDS_FOR_NATURE) }
    }
}

@Composable
private fun locale() = LocalConfiguration.current.locales[0]

/** A distance on the Plot, in metres. */
@Composable
private fun distance(cm: Int): String = stringResource(R.string.water_distance_m, UiFormat.metresFromCm(cm))

@Composable
private fun WaterRow(feature: WaterFeature, onOpen: () -> Unit, onCleaned: () -> Unit) {
    val cleaned = feature.cleanedOn?.let { stringResource(R.string.water_cleaned_on, UiFormat.longDate(it, locale())) }
        ?: stringResource(R.string.water_not_cleaned)
    ListItem(
        onClick = onOpen,
        leadingContent = { Icon(painterResource(feature.kind.icon), contentDescription = null) },
        supportingContent = { Text(cleaned) },
        trailingContent = { TextButton(onClick = onCleaned) { Text(stringResource(R.string.water_mark_cleaned)) } },
    ) { Text(stringResource(feature.kind.label)) }
}

/** The honey bee hives, stingless bee hives and bee hotels kept in a zone, each opening its page. */
@Composable
fun ZoneBees(view: ZoneHabitatView, onOpenHive: (String) -> Unit, onOpenNativeHive: (String) -> Unit, onOpenHotel: (String) -> Unit) {
    Column {
        view.hives.forEach { hive ->
            ListItem(
                onClick = { onOpenHive(hive.hive.id) },
                leadingContent = { Icon(painterResource(R.drawable.ic_hive), contentDescription = null) },
                supportingContent = { Text(stringResource(R.string.zone_bee_honey)) },
            ) { Text(hive.hive.name) }
        }
        view.nativeHives.forEach { hive ->
            ListItem(
                onClick = { onOpenNativeHive(hive.hive.id) },
                leadingContent = { Icon(painterResource(R.drawable.ic_native_bee), contentDescription = null) },
                supportingContent = { Text(stringResource(R.string.zone_bee_stingless)) },
            ) { Text(hive.hive.name) }
        }
        view.hotels.forEach { hotel ->
            ListItem(
                onClick = { onOpenHotel(hotel.hotel.id) },
                leadingContent = { Icon(painterResource(R.drawable.ic_cottage), contentDescription = null) },
                supportingContent = { Text(stringResource(R.string.zone_bee_hotel)) },
            ) { Text(hotel.hotel.name) }
        }
        if (view.beesNeedWater) {
            Text(stringResource(R.string.zone_bees_need_water), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

/** The months each group of bees finds nectar and pollen on the zone's plants, as Pollen Rhythms works them out. */
@Composable
fun ZoneBeeFood(plants: List<RhythmPlant>) {
    val lines = RhythmsEngine.lanes(plants).filter { it.fed }.map { lane -> lane.group to RhythmsEngine.offers(plants, lane.group) }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (lines.isEmpty()) Text(stringResource(R.string.zone_bee_food_none), style = MaterialTheme.typography.bodyLarge)
        lines.forEach { (group, offers) ->
            Text(
                stringResource(R.string.rhythms_offers_line, stringResource(group.label), offerMonths(offers.nectar), offerMonths(offers.pollen)),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** The yard as a corridor for wildlife, for the Wildlife tab: each layer and feature, and whether any zone has it. */
@Composable
fun CorridorCard(summary: CorridorSummary) {
    val uri = LocalUriHandler.current
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (summary.zones == 0) {
            Text(stringResource(R.string.corridor_no_zones), style = MaterialTheme.typography.bodyLarge)
            return@Column
        }
        Text(stringResource(R.string.corridor_intro), style = MaterialTheme.typography.bodyLarge)
        LayersAndFeatures(summary.layers, summary.features, water = summary.waterFeatures > 0)
        Text(pluralStringResource(R.plurals.corridor_layered, summary.layeredZones, summary.layeredZones, summary.zones), style = MaterialTheme.typography.bodyLarge)
        Text(pluralStringResource(R.plurals.corridor_water, summary.waterFeatures, summary.waterFeatures), style = MaterialTheme.typography.bodyLarge)
        LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.habitat_source)) { uri.openUri(BACKYARDS_FOR_NATURE) }
    }
}

/** Adds water for wildlife to a zone, or with [featureId] changes or deletes one. */
@Composable
fun WaterFeatureScreen(habitat: HabitatRepository, zoneId: String?, featureId: String?, today: LocalDate, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<WaterFeature?>(null) }
    var initialised by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(WaterKind.BIRD_BATH) }
    var notes by rememberSaveable { mutableStateOf("") }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(featureId) {
        if (featureId == null) return@LaunchedEffect
        val feature = habitat.waterFeature(featureId) ?: return@LaunchedEffect onBack()
        original = feature
        if (!initialised) {
            kind = feature.kind
            notes = feature.notes
            initialised = true
        }
    }
    if (featureId != null && original == null) return
    val title = stringResource(if (featureId == null) R.string.water_feature_add_title else R.string.water_feature_title)

    DetailScaffold(title = title, onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.water_feature_kind), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                WaterKind.entries.forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = kind == option, onClick = { kind = option }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = kind == option, onClick = null)
                        Icon(painterResource(option.icon), contentDescription = null)
                        Text(stringResource(option.label), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (kind == WaterKind.BEE_WATER) Text(stringResource(R.string.water_feature_bee_help), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.water_feature_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            original?.let { feature ->
                Text(
                    feature.cleanedOn?.let { stringResource(R.string.water_cleaned_on, UiFormat.longDate(it, locale())) } ?: stringResource(R.string.water_not_cleaned),
                    style = MaterialTheme.typography.bodyLarge,
                )
                OutlinedButton(onClick = {
                    scope.launch {
                        habitat.cleaned(feature.id, today)
                        original = habitat.waterFeature(feature.id)
                    }
                }) { Text(stringResource(R.string.water_mark_cleaned)) }
            }
            Button(
                onClick = {
                    scope.launch {
                        val feature = original
                        if (feature == null) {
                            habitat.addWater(zoneId ?: return@launch, kind, notes)
                        } else {
                            habitat.updateWater(feature.copy(kind = kind, notes = notes))
                        }
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.water_feature_save)) }
            if (original != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(R.string.water_feature_delete), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.water_feature_delete_title)) },
            text = { Text(stringResource(R.string.water_feature_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        original?.let { habitat.deleteWater(it.id) }
                        withContext(Dispatchers.Main) { onBack() }
                    }
                }) { Text(stringResource(R.string.water_feature_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
