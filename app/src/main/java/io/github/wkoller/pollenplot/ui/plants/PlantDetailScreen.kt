package io.github.wkoller.pollenplot.ui.plants

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.produceState
import io.github.wkoller.pollenplot.data.NamedPlant
import io.github.wkoller.pollenplot.ui.review.SpeciesSearch
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.data.FloweringRecord
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.HarvestViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.data.PotViews
import io.github.wkoller.pollenplot.data.ProblemViews
import io.github.wkoller.pollenplot.ui.problems.ProblemsCard
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.domain.garden.FloweringSeen
import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.plants.CareField
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.CompanionRelation
import io.github.wkoller.pollenplot.domain.plants.Companions
import io.github.wkoller.pollenplot.domain.plants.CompostSafe
import io.github.wkoller.pollenplot.domain.plants.FactLevel
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.ResolvedCare
import io.github.wkoller.pollenplot.domain.plants.Toxicity
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.season.MonthSet
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.assistant.CareDraftCard
import io.github.wkoller.pollenplot.ui.assistant.DraftState
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.Found
import io.github.wkoller.pollenplot.ui.components.MissingRecord
import io.github.wkoller.pollenplot.ui.components.LocalNotices
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.components.PlantAvatar
import io.github.wkoller.pollenplot.ui.components.RoleBadges
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.plot.ChipGroup
import io.github.wkoller.pollenplot.ui.weeds.NativeAlternatives
import io.github.wkoller.pollenplot.ui.weeds.WeedCard
import io.github.wkoller.pollenplot.ui.weeds.WeedHelp
import io.github.wkoller.pollenplot.ui.wildlife.PlantAnimalsCard
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate
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
    onCompare: (speciesName: String) -> Unit = {},
    /** Native alternatives for a plant that is a weed, and the wishlist to add them to. */
    help: WeedHelp? = null,
    onOpenAnimal: (String) -> Unit = {},
    onOpenSighting: (String) -> Unit = {},
    onAsk: (() -> Unit)? = null,
    assistant: Assistant? = null,
    onOpenModels: () -> Unit = {},
    /** Opens the user's corrections to the plant's facts, for a plant in the knowledge base. */
    onEditFacts: (() -> Unit)? = null,
    /** The plants the models can name beyond the knowledge base, for the search that changes what this plant is. */
    plantNames: suspend () -> List<NamedPlant> = { emptyList() },
    onOpenPhoto: (photoId: String, detectionId: String) -> Unit = { _, _ -> },
    today: LocalDate = LocalDate.now(),
    /** The plant's pot and the care recorded for it, with the screens that edit them. */
    pots: PotViews? = null,
    onEditPot: () -> Unit = {},
    onRecordCare: () -> Unit = {},
    onRemoveEvent: (PlantEvent) -> Unit = {},
    /** The problems logged for the plant, with the screens that open and log them. */
    problems: ProblemViews? = null,
    onOpenProblem: (String) -> Unit = {},
    onLogProblem: () -> Unit = {},
    /** The plant's harvests and pruning, with the screens that record them. */
    crops: HarvestViews? = null,
    onRecordHarvest: () -> Unit = {},
    onOpenHarvest: (String) -> Unit = {},
    onRecordPruning: () -> Unit = {},
) {
    val found by remember(plantId) { views.plant(plantId).map { Found(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val view = found?.value
    val finds by remember(plantId) { garden.findsOf(plantId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val flowered by remember(plantId) { garden.floweringRecords(plantId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val notices = LocalNotices.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val pot by remember(plantId, pots) { pots?.plant(plantId) ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
    val plantProblems by remember(plantId, problems) { problems?.forPlant(plantId) ?: flowOf(emptyList()) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val harvest by remember(plantId, crops) { crops?.harvest(plantId) ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
    val prune by remember(plantId, crops) { crops?.prune(plantId) ?: flowOf(null) }.collectAsStateWithLifecycle(initialValue = null)
    val draft by remember(plantId) { garden.careDraft(plantId) }.collectAsStateWithLifecycle(initialValue = null)
    var draftState by remember { mutableStateOf(DraftState.IDLE) }
    val sightings by views.sightings.collectAsStateWithLifecycle(initialValue = emptyList())
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val all by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val locality by views.locality.collectAsStateWithLifecycle(initialValue = Locality.NONE)
    var editing by rememberSaveable { mutableStateOf(false) }
    var changing by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val loaded = view

    // Changing what the plant is opens the search over the whole screen.
    val searchKb = kb
    if (changing && searchKb != null) {
        val names by produceState(emptyList<NamedPlant>()) { value = plantNames() }
        BackHandler { changing = false }
        SpeciesSearch(searchKb, locality, onBack = { changing = false }, names = names, title = stringResource(R.string.plant_change_title)) { name ->
            changing = false
            val species = searchKb.species(name)?.name
            scope.launch { garden.changeSpecies(plantId, species, name.takeIf { species == null }) }
        }
        return
    }

    DetailScaffold(
        title = loaded?.title.orEmpty(),
        onBack = onBack,
        actions = {
            onAsk?.let { ask -> IconButton(onClick = ask) { Icon(painterResource(R.drawable.ic_forum), contentDescription = stringResource(R.string.ask_open)) } }
            IconButton(onClick = { editing = true }) { Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.plant_edit)) }
            IconButton(onClick = { confirmRemove = true }) { Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.plant_remove)) }
        },
    ) { padding ->
        if (loaded == null) {
            if (found != null) MissingRecord(padding)
            return@DetailScaffold
        }
        val resolved = loaded.care
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(loaded) }
            if (loaded.roles.isNotEmpty()) item { RoleBadges(loaded.roles) }
            pot?.let { state ->
                if (state.potted) {
                    item { PotCard(state, today, onEdit = onEditPot, onRecord = onRecordCare) }
                }
                if (state.events.isNotEmpty()) item { PlantEventsCard(state.events, onRemoveEvent) }
            }
            if (problems != null) item { ProblemsCard(plantProblems, onOpenProblem, onLogProblem) }
            harvest?.takeIf { it.offered }?.let { state -> item { HarvestCard(state, onRecordHarvest, onOpenHarvest) } }
            prune?.takeIf { it.schedule != null || it.months != null || it.lastPruned != null }?.let { state -> item { PruningCard(state, onRecordPruning) } }
            if (loaded.weed.isWeed) {
                val name = loaded.species?.name ?: loaded.plant.speciesName.orEmpty()
                item { WeedCard(loaded.weed, locality, scientificName = name, onCompare = { onCompare(name) }) }
                help?.let { helper ->
                    item { NativeAlternatives(helper, loaded.species?.let { listOf(it.name) + it.synonyms } ?: listOf(name), kb, locality) }
                }
            }
            // Below a weed's card, which says what the law asks and comes first.
            if (loaded.plant.status == PlantStatus.GROWING || flowered.isNotEmpty()) {
                item {
                    SeenInFlowerCard(
                        records = flowered,
                        today = today,
                        canRecord = loaded.plant.status == PlantStatus.GROWING,
                        onRecord = { day ->
                            scope.launch {
                                val made = garden.recordFlowering(plantId, day)
                                val date = UiFormat.longDate(day)
                                if (made == null) {
                                    notices?.show(resources.getString(R.string.flowering_already, date))
                                } else {
                                    notices?.show(resources.getString(R.string.flowering_recorded, date)) { garden.removeFlowering(made.id) }
                                }
                            }
                        },
                        onRemove = { record ->
                            scope.launch {
                                garden.removeFlowering(record.id)
                                notices?.show(resources.getString(R.string.flowering_removed, UiFormat.longDate(record.day))) { garden.restoreFlowering(record) }
                            }
                        },
                    )
                }
            }
            val unknownName = loaded.plant.speciesName
            if (resolved == null && unknownName != null && assistant != null) {
                // Gemma 4 can draft the care of a plant the knowledge base does not have, kept apart from it.
                item {
                    CareDraftCard(
                        draft = draft,
                        ready = assistant.ready,
                        state = draftState,
                        onDraft = {
                            draftState = DraftState.WORKING
                            scope.launch {
                                val made = runCatching { assistant.draftCare(unknownName) }.getOrNull()
                                if (made != null) garden.saveCareDraft(plantId, made)
                                draftState = if (made != null) DraftState.IDLE else DraftState.FAILED
                            }
                        },
                        onKeep = { scope.launch { garden.keepCareDraft(plantId) } },
                        onRemove = { scope.launch { garden.deleteCareDraft(plantId) } },
                        onOpenModels = onOpenModels,
                    )
                }
            } else if (resolved == null) {
                item { Text(stringResource(R.string.unknown_species), style = MaterialTheme.typography.bodyLarge) }
            } else {
                facts(resolved, loaded, all, kb, locality.hemisphere, onEditFacts, onEditPot.takeIf { pots != null && pot?.potted == false }) {
                    if (finds.isNotEmpty()) item { PhotoHistoryCard(finds, loaded.plant.speciesName, kb) { onOpenPhoto(it.photoId, it.id) } }
                }
            }
            if (resolved == null && pots != null && pot?.potted == false) {
                // A plant the knowledge base does not have can be in a pot too.
                item {
                    OutlinedButton(onClick = onEditPot) {
                        Icon(painterResource(R.drawable.ic_potted_plant), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.pot_add), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (resolved == null && finds.isNotEmpty()) {
                item { PhotoHistoryCard(finds, loaded.plant.speciesName, kb) { onOpenPhoto(it.photoId, it.id) } }
            }
            val seen = sightings.filter { it.sighting.plantId == plantId }
            val users = (loaded.species?.name ?: loaded.plant.speciesName)?.let { fauna?.usersOf(it) }.orEmpty()
            if (seen.isNotEmpty() || users.isNotEmpty()) {
                item { PlantAnimalsCard(seen, users, onOpenSighting, onOpenAnimal) }
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
            onChangeSpecies = {
                editing = false
                changing = true
            },
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
                        // The database finishes on its own thread; going back has to happen on the main one.
                        withContext(Dispatchers.Main) { onBack() }
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

private fun androidx.compose.foundation.lazy.LazyListScope.facts(
    resolved: ResolvedCare,
    view: PlantView,
    all: List<PlantView>,
    kb: KnowledgeBase?,
    hemisphere: Hemisphere,
    onEditFacts: (() -> Unit)?,
    onAddPot: (() -> Unit)?,
    /** The plant's photo history, which goes after its facts and before where they came from. */
    history: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
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
            // The knowledge base keeps months for the Southern Hemisphere; a northern garden sees them six months on.
            val flowering = care.flowering?.fromSouthern(hemisphere)
            val peak = care.peakFlowering?.fromSouthern(hemisphere)
            val words = listWords()
            val locale = LocalConfiguration.current.locales[0]
            when {
                flowering == null || flowering.isEmpty -> Line(stringResource(R.string.flowering_unknown))
                else -> {
                    MonthStrip(flowering, peak ?: MonthSet.NONE)
                    Line(
                        if (flowering == MonthSet.ALL) stringResource(R.string.flowering_all_year) else stringResource(R.string.flowering_months, UiFormat.months(flowering, words, locale)),
                        level = l.line(CareField.FLOWERING),
                    )
                    peak?.takeIf { !it.isEmpty && it != flowering }?.let { Line(stringResource(R.string.flowering_peak, UiFormat.months(it, words, locale))) }
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
    history()
    onEditFacts?.let { edit ->
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = edit) {
                    Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.facts_edit), modifier = Modifier.padding(start = 8.dp))
                }
                // A plant outside a zone of pots can still be in one.
                onAddPot?.let { add ->
                    OutlinedButton(onClick = add) {
                        Icon(painterResource(R.drawable.ic_potted_plant), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.pot_add), modifier = Modifier.padding(start = 8.dp))
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

/** A card of facts with an icon, a heading, and a tag when the facts are for the genus or family or the user's. */
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
                // The tag goes under the heading when there is no room beside it, rather than squeezing a word apart.
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (level != null && level != FactLevel.SPECIES) LevelTag(level)
                }
            }
            content()
        }
    }
}

/**
 * One fact, with a tag when it is for the genus or family or the user corrected it. A missing fact says so rather than
 * leaving a gap.
 */
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
    if (level != null && level != FactLevel.SPECIES) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            content(Modifier)
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
            stringResource(
                when (level) {
                    FactLevel.USER -> R.string.level_user
                    FactLevel.GENUS -> R.string.level_genus
                    else -> R.string.level_family
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * The days this plant was seen in flower, in a photo or by the user, with the months of the last year they fall in.
 * A plant that is flowering now is recorded with one press, another day is chosen from the calendar, and a day
 * recorded by mistake is removed. Pollen Rhythms and the check before a spray read the same days.
 */
@Composable
private fun SeenInFlowerCard(
    records: List<FloweringRecord>,
    today: LocalDate,
    /** False for a plant that has died or been removed, whose days stay but take no more. */
    canRecord: Boolean,
    onRecord: (LocalDate) -> Unit,
    onRemove: (FloweringRecord) -> Unit,
) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    val since = today.minusYears(1)
    val recent = records.filter { it.day.isAfter(since) && !it.day.isAfter(today) }
    val seen = FloweringSeen.lastYear(records.map { it.day }, today)
    FactCard(R.drawable.ic_local_florist, stringResource(R.string.flowering_seen)) {
        if (seen.isEmpty) {
            Line(stringResource(R.string.flowering_not_seen))
        } else {
            MonthStrip(seen, MonthSet.NONE)
            Line(stringResource(R.string.flowering_seen_months, UiFormat.months(seen, listWords(), LocalConfiguration.current.locales[0])))
        }
        if (canRecord) {
            val recordedToday = records.any { it.day == today }
            val todayButton: @Composable () -> Unit = {
                if (recordedToday) {
                    Line(stringResource(R.string.flowering_today_done))
                } else {
                    Button(onClick = { onRecord(today) }) { Text(stringResource(R.string.flowering_today)) }
                }
            }
            val otherDay: @Composable () -> Unit = { TextButton(onClick = { choosing = true }) { Text(stringResource(R.string.flowering_other_day)) } }
            // With large text the second button goes under the first, rather than both being squeezed.
            if (largeText()) {
                todayButton()
                otherDay()
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    todayButton()
                    otherDay()
                }
            }
            Text(stringResource(R.string.flowering_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        recent.forEach { record ->
            val date = UiFormat.longDate(record.day)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (record.fromPhoto) R.string.flowering_day_photo else R.string.flowering_day, date),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(record) }) {
                    Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.flowering_day_remove, date))
                }
            }
        }
    }
    if (choosing) {
        PastDateDialog(
            day = today,
            today = today,
            onDismiss = { choosing = false },
            onChoose = {
                choosing = false
                onRecord(it)
            },
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
private fun EditPlantSheet(
    view: PlantView,
    zones: List<io.github.wkoller.pollenplot.data.ZoneView>,
    onDismiss: () -> Unit,
    onChangeSpecies: () -> Unit,
    onSave: (io.github.wkoller.pollenplot.domain.garden.GardenPlant) -> Unit,
) {
    val plant = view.plant
    var zoneId by rememberSaveable { mutableStateOf(plant.zoneId) }
    var quantity by rememberSaveable { mutableIntStateOf(plant.quantity) }
    var name by rememberSaveable { mutableStateOf(plant.customName.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(plant.notes) }
    var status by rememberSaveable { mutableStateOf(plant.status) }
    var plantedOn by rememberSaveable { mutableStateOf(plant.plantedOn) }
    var choosingDay by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.edit_plant_title, view.title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
            item {
                // A plant added under the wrong name is given the right one here; its photos and notes stay with it.
                OutlinedButton(onClick = onChangeSpecies) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(18.dp))
                    Text(stringResource(R.string.plant_change))
                }
            }
            item { ZoneChooser(zones, zoneId) { zoneId = it } }
            item { QuantityStepper(quantity) { quantity = it } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.planted_on), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(plantedOn?.let { UiFormat.longDate(it) } ?: stringResource(R.string.planted_unknown), style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { choosingDay = true }) { Text(stringResource(R.string.planted_choose)) }
                        if (plantedOn != null) TextButton(onClick = { plantedOn = null }) { Text(stringResource(R.string.planted_clear)) }
                    }
                }
            }
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
                    onClick = { onSave(plant.copy(zoneId = zoneId, quantity = quantity, plantedOn = plantedOn, customName = name.trim().ifBlank { null }, notes = notes.trim(), status = status)) },
                    enabled = !needsName,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.edit_save)) }
            }
        }
    }
    if (choosingDay) {
        PastDateDialog(
            day = plantedOn ?: today,
            today = today,
            onDismiss = { choosingDay = false },
            onChoose = {
                choosingDay = false
                plantedOn = it
            },
        )
    }
}
