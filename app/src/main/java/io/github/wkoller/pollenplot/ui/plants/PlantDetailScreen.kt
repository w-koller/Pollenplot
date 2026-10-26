package io.github.wkoller.pollenplot.ui.plants

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.plants.CareField
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.CompanionRelation
import io.github.wkoller.pollenplot.domain.plants.CompostSafe
import io.github.wkoller.pollenplot.domain.plants.Companions
import io.github.wkoller.pollenplot.domain.plants.FactLevel
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.ResolvedCare
import io.github.wkoller.pollenplot.domain.plants.Toxicity
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.RoleBadges
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.weeds.WeedCard
import kotlinx.coroutines.launch
import java.time.Month
import java.time.format.TextStyle

/** A plant's own page: what it is, its badges, everything it needs and offers, and its companions. */
@Composable
fun PlantDetailScreen(
    views: GardenViews,
    garden: GardenRepository,
    knowledge: KnowledgeRepository,
    plantId: String,
    onBack: () -> Unit,
) {
    val view by views.plant(plantId).collectAsStateWithLifecycle(initialValue = null)
    val all by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loaded = view

    DetailScaffold(
        title = loaded?.title.orEmpty(),
        onBack = onBack,
        actions = {
            IconButton(onClick = { editing = true }) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.plant_edit)) }
            IconButton(onClick = { confirmRemove = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.plant_remove)) }
        },
    ) { padding ->
        if (loaded == null) return@DetailScaffold
        val resolved = loaded.care
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(loaded) }
            if (loaded.roles.isNotEmpty()) item { RoleBadges(loaded.roles) }
            if (loaded.weed.isWeed) {
                item { WeedCard(loaded.weed, locality, scientificName = loaded.species?.name ?: loaded.plant.speciesName.orEmpty()) }
            }
            if (resolved == null) {
                item { Text(stringResource(R.string.unknown_species), style = MaterialTheme.typography.bodyLarge) }
            } else {
                facts(resolved, loaded, all, kb)
            }
            if (loaded.plant.notes.isNotBlank()) {
                item {
                    FactCard(R.drawable.ic_description, stringResource(R.string.plant_notes)) {
                        Text(loaded.plant.notes, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    if (editing && loaded != null) {
        EditPlantSheet(
            view = loaded,
            zones = zones,
            onDismiss = { editing = false },
            onSave = { updated ->
                editing = false
                scope.launch { garden.updatePlant(updated) }
            },
        )
    }
    if (confirmRemove && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.plant_remove_title, loaded.title)) },
            text = { Text(stringResource(R.string.plant_remove_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        garden.deletePlant(loaded.plant.id)
                        onBack()
                    }
                }) { Text(stringResource(R.string.plant_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Header(view: PlantView) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        PlantAvatar(view.species?.form, 96.dp, thumbnail = view.plant.thumbnail)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(view.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            view.species?.let { species ->
                if (species.displayName != view.title) Text(species.displayName, style = MaterialTheme.typography.titleMedium)
                Text(species.name, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
                Text(listOfNotNull(species.form?.let { stringResource(it.label) }, species.family).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                listOfNotNull(
                    if (view.plant.quantity > 1) pluralStringResource(R.plurals.plant_count, view.plant.quantity, view.plant.quantity) else null,
                    view.zoneName?.let { stringResource(R.string.plant_zone, it) } ?: stringResource(R.string.plant_no_zone),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Where the facts on one card came from. When every fact on the card came from the genus (or every one from the
 * family), the card carries a single tag; otherwise each borrowed fact carries its own.
 */
private class Levels(private val resolved: ResolvedCare, fields: List<CareField>) {
    private val present = fields.mapNotNull(resolved::levelOf)
    val card: FactLevel? = present.distinct().singleOrNull()?.takeIf { it != FactLevel.SPECIES }
    fun line(field: CareField): FactLevel? = if (card != null) null else resolved.levelOf(field)?.takeIf { it != FactLevel.SPECIES }
}

private fun androidx.compose.foundation.lazy.LazyListScope.facts(resolved: ResolvedCare, view: PlantView, all: List<PlantView>, kb: KnowledgeBase?) {
    val care = resolved.care
    fun levels(vararg fields: CareField) = Levels(resolved, fields.toList())

    care.description?.let { item { Text(it, style = MaterialTheme.typography.bodyLarge) } }
    item {
        val l = levels(CareField.SUN)
        FactCard(R.drawable.ic_sunny, stringResource(R.string.plant_light), l.card) {
            Line(care.sun?.let { sun -> sun.sortedBy { it.ordinal }.map { stringResource(it.label) }.joinToString(", ") }, level = l.line(CareField.SUN))
        }
    }
    item {
        val l = levels(CareField.WATER, CareField.DROUGHT)
        FactCard(R.drawable.ic_water_drop, stringResource(R.string.plant_water), l.card) {
            Line(care.water?.let { stringResource(R.string.plant_water_need, stringResource(it.label)) }, level = l.line(CareField.WATER))
            care.water?.let { Line(stringResource(R.string.plant_factor, UiFormat.fraction(it.plantFactorBp)), level = l.line(CareField.WATER)) }
            care.drought?.let { Line(stringResource(R.string.plant_drought, stringResource(toleranceLabel(it))), level = l.line(CareField.DROUGHT)) }
        }
    }
    item {
        val l = levels(CareField.SOILS, CareField.PH, CareField.FROST, CareField.SALT, CareField.FERTILISER)
        FactCard(R.drawable.ic_landscape, stringResource(R.string.plant_soil), l.card) {
            care.soils?.let { soils -> Line(stringResource(R.string.plant_soils, soils.sortedBy { it.ordinal }.map { stringResource(it.label) }.joinToString(", ")), level = l.line(CareField.SOILS)) }
            val phMin = care.phMinTenths
            val phMax = care.phMaxTenths
            if (phMin != null && phMax != null) Line(stringResource(R.string.plant_ph, UiFormat.ph(phMin), UiFormat.ph(phMax)), level = l.line(CareField.PH))
            care.frost?.let { Line(stringResource(R.string.plant_frost, stringResource(toleranceLabel(it))), level = l.line(CareField.FROST)) }
            care.salt?.let { Line(stringResource(R.string.plant_salt, stringResource(toleranceLabel(it))), level = l.line(CareField.SALT)) }
            care.fertiliser?.let { Line(stringResource(it.label), strong = true, level = l.line(CareField.FERTILISER)) }
            care.fertiliserNote?.let { Line(it) }
            if (care.soils == null && phMin == null && care.frost == null && care.salt == null && care.fertiliser == null) Line(null)
        }
    }
    item {
        val l = levels(CareField.COMPOST)
        FactCard(R.drawable.ic_compost, stringResource(R.string.plant_compost), l.card) {
            care.compostLikes?.let { Line(stringResource(it.label), level = l.line(CareField.COMPOST)) }
            care.compostSafe?.let { Line(stringResource(it.label), strong = it != CompostSafe.YES, level = l.line(CareField.COMPOST)) }
            care.compostNote?.let { Line(it) }
            if (care.compostLikes == null && care.compostSafe == null) Line(null)
        }
    }
    item { WildlifeCard(care, levels(CareField.WILDLIFE).card) }
    item { ForageCard(care, levels(CareField.FORAGE).card) }
    item {
        val l = levels(CareField.FLOWERING)
        FactCard(R.drawable.ic_calendar_month, stringResource(R.string.plant_flowering), l.card) {
            val flowering = care.flowering
            when {
                flowering == null || flowering.isEmpty -> Line(stringResource(R.string.flowering_unknown))
                else -> {
                    val words = listWords()
                    val locale = LocalConfiguration.current.locales[0]
                    MonthStrip(flowering, care.peakFlowering ?: MonthSet.NONE)
                    Line(if (flowering == MonthSet.ALL) stringResource(R.string.flowering_all_year) else stringResource(R.string.flowering_months, UiFormat.months(flowering, words, locale)))
                    care.peakFlowering?.takeIf { !it.isEmpty && it != flowering }?.let { Line(stringResource(R.string.flowering_peak, UiFormat.months(it, words, locale))) }
                }
            }
        }
    }
    item {
        val l = levels(CareField.HEIGHT, CareField.WIDTH, CareField.GROUND_COVER, CareField.PRUNING)
        FactCard(R.drawable.ic_straighten, stringResource(R.string.plant_size), l.card) {
            care.height?.let { Line(stringResource(R.string.plant_height, stringResource(R.string.plant_range_metres, UiFormat.metresFromMm(it.minMm), UiFormat.metresFromMm(it.maxMm))), level = l.line(CareField.HEIGHT)) }
            care.width?.let { Line(stringResource(R.string.plant_width, stringResource(R.string.plant_range_metres, UiFormat.metresFromMm(it.minMm), UiFormat.metresFromMm(it.maxMm))), level = l.line(CareField.WIDTH)) }
            if (care.groundCover == true) Line(stringResource(R.string.role_ground_cover), level = l.line(CareField.GROUND_COVER))
            care.pruning?.let { Line(stringResource(R.string.plant_pruning, it), level = l.line(CareField.PRUNING)) }
            if (care.height == null && care.width == null && care.pruning == null) Line(null)
        }
    }
    item {
        val l = levels(CareField.TOXICITY, CareField.EDIBLE)
        FactCard(R.drawable.ic_warning, stringResource(R.string.plant_safety), l.card) {
            care.toxicPets?.let { Line(stringResource(R.string.safety_pets, stringResource(it.label)), strong = it != Toxicity.NONE, level = l.line(CareField.TOXICITY)) }
            care.toxicPeople?.let { Line(stringResource(R.string.safety_people, stringResource(it.label)), strong = it != Toxicity.NONE, level = l.line(CareField.TOXICITY)) }
            care.toxicNote?.let { Line(it) }
            care.edible?.let {
                Line(stringResource(R.string.safety_edible, it), level = l.line(CareField.EDIBLE))
                Line(stringResource(R.string.safety_never_eat))
            }
            if (care.toxicPets == null && care.toxicPeople == null && care.edible == null) Line(null)
        }
        care.careNote?.let { note -> Text(note, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp)) }
    }
    val species = view.species
    if (species != null && kb != null) {
        item {
            val gardenSpecies = all.filter { it.isGrowing && it.plant.id != view.plant.id }.mapNotNull { it.species }
            val advice = Companions.forSpecies(kb, species, gardenSpecies)
            FactCard(R.drawable.ic_eco, stringResource(R.string.plant_companions)) {
                if (advice.isEmpty()) Line(stringResource(R.string.companion_none))
                advice.forEach { a ->
                    val other = kb.species(a.otherName)?.displayName ?: a.otherName
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(
                            painterResource(if (a.pair.relation == CompanionRelation.GOOD) R.drawable.ic_check_circle else R.drawable.ic_warning),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(if (a.pair.relation == CompanionRelation.GOOD) R.string.companion_good else R.string.companion_avoid, other),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(a.pair.reason, style = MaterialTheme.typography.bodyMedium)
                            if (a.inGarden.isNotEmpty()) {
                                Text(stringResource(R.string.companion_in_garden, a.inGarden.joinToString(", ") { it.displayName }), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                            Text(stringResource(a.pair.evidence.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.source_species), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (resolved.usesFallback && species != null) {
                Text(stringResource(R.string.source_fallback, species.genus, species.family), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WildlifeCard(care: CareProfile, level: FactLevel?) {
    FactCard(R.drawable.ic_raven, stringResource(R.string.plant_wildlife), level) {
        val w = care.wildlife
        if (w == null) {
            Line(null)
            return@FactCard
        }
        // Only the wildlife a plant offers something to is listed; a zero score is not shown as a claim.
        listOf(
            R.string.wildlife_pollinators to w.pollinator,
            R.string.wildlife_butterflies to w.butterflies,
            R.string.wildlife_birds to w.birds,
            R.string.wildlife_beneficials to w.beneficials,
            R.string.wildlife_frogs to w.frogs,
        ).filter { it.second > 0 }.forEach { (label, score) -> Line(stringResource(label, stringResource(scoreLabel(score)))) }
        if (w.butterflyHost) Line(stringResource(R.string.wildlife_butterfly_host))
    }
}

@Composable
private fun ForageCard(care: CareProfile, level: FactLevel?) {
    FactCard(R.drawable.ic_emoji_nature, stringResource(R.string.plant_forage), level) {
        val f = care.forage
        if (f == null) {
            Line(null)
            return@FactCard
        }
        Text(stringResource(R.string.forage_honey), style = MaterialTheme.typography.titleSmall)
        Line(stringResource(R.string.forage_nectar_pollen, stringResource(scoreLabel(f.honeyNectar)), stringResource(scoreLabel(f.honeyPollen))))
        Text(stringResource(R.string.forage_stingless), style = MaterialTheme.typography.titleSmall)
        Line(stringResource(R.string.forage_nectar_pollen_resin, stringResource(scoreLabel(f.stinglessNectar)), stringResource(scoreLabel(f.stinglessPollen)), stringResource(scoreLabel(f.stinglessResin))))
        Text(stringResource(R.string.forage_solitary), style = MaterialTheme.typography.titleSmall)
        Line(stringResource(R.string.forage_nectar_pollen, stringResource(scoreLabel(f.solitaryNectar)), stringResource(scoreLabel(f.solitaryPollen))))
        if (f.buzz) Line(stringResource(R.string.forage_buzz), strong = true)
    }
}

/** A card of facts with an icon, a heading, and a note when the facts are for the genus or family. */
@Composable
fun FactCard(@DrawableRes icon: Int, title: String, level: FactLevel? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                if (level == FactLevel.GENUS || level == FactLevel.FAMILY) LevelTag(level)
            }
            content()
        }
    }
}

/** One fact, with a tag when it is for the genus or family. A missing fact says so rather than leaving a gap. */
@Composable
private fun Line(text: String?, strong: Boolean = false, level: FactLevel? = null) {
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Text(
            text ?: stringResource(R.string.plant_unknown_fact),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (strong) FontWeight.SemiBold else null,
            color = if (text == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
    if (level == FactLevel.GENUS || level == FactLevel.FAMILY) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content(Modifier.weight(1f, fill = false))
            LevelTag(level)
        }
    } else {
        content(Modifier)
    }
}

@Composable
private fun LevelTag(level: FactLevel) {
    Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(
            stringResource(if (level == FactLevel.GENUS) R.string.level_genus else R.string.level_family),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** The twelve months with flowering months filled in. TalkBack reads the sentence below it instead. */
@Composable
private fun MonthStrip(flowering: MonthSet, peak: MonthSet) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Month.entries.forEach { month ->
            val on = month in flowering
            val best = month in peak
            Surface(
                color = when {
                    best -> MaterialTheme.colorScheme.secondary
                    on -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = when {
                    best -> MaterialTheme.colorScheme.onSecondary
                    on -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f).height(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(month.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else null)
                }
            }
        }
    }
}

/** Edits where a plant grows, how many there are, its name, notes and status. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPlantSheet(view: PlantView, zones: List<io.github.wkoller.pollenplot.data.ZoneView>, onDismiss: () -> Unit, onSave: (io.github.wkoller.pollenplot.domain.garden.GardenPlant) -> Unit) {
    val plant = view.plant
    var zoneId by rememberSaveable { mutableStateOf(plant.zoneId) }
    var quantity by rememberSaveable { mutableIntStateOf(plant.quantity) }
    var name by rememberSaveable { mutableStateOf(plant.customName.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(plant.notes) }
    var status by rememberSaveable { mutableStateOf(plant.status) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.edit_plant_title, view.title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
            item { ZoneChooser(zones, zoneId) { zoneId = it } }
            item { QuantityStepper(quantity) { quantity = it } }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_sheet_nickname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(stringResource(R.string.edit_notes)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ChipGroup(stringResource(R.string.status_label), PlantStatus.entries, status, { stringResource(it.label) }) { status = it } }
            item {
                val needsName = plant.speciesName == null && name.isBlank()
                Button(
                    onClick = { onSave(plant.copy(zoneId = zoneId, quantity = quantity, customName = name.trim().ifBlank { null }, notes = notes.trim(), status = status)) },
                    enabled = !needsName,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.edit_save)) }
            }
        }
    }
}
