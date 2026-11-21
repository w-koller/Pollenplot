package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.domain.plants.Taxon
import java.time.LocalDate
import java.time.Month
import io.github.wkoller.pollenplot.domain.garden.FloweringSeen
import io.github.wkoller.pollenplot.domain.garden.Structure
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.PlantFacts
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.garden.ZoneBadges
import io.github.wkoller.pollenplot.domain.irrigation.Pairing
import io.github.wkoller.pollenplot.domain.irrigation.WaterPlant
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.EcoRoles
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.plants.ResolvedCare
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.weeds.WeedStatus
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import io.github.wkoller.pollenplot.weeds.WeedData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Where the garden is, as far as badges are concerned: its state, which decides what is a local native, and the weed
 * lists for its area, which decide what is a weed there.
 */
data class Locality(val state: AuState?, val weeds: WeedData, val hemisphere: Hemisphere = Hemisphere.SOUTHERN) {
    /** A species' weed status in the garden's area, found by its name or any of its synonyms. */
    fun weed(species: Species): WeedStatus = weeds.status(listOf(species.name) + species.synonyms)

    /** The weed status of a plant the knowledge base does not have, by its scientific name. */
    fun weed(name: String): WeedStatus = weeds.status(listOf(name))

    /** A species' badges in this garden. */
    fun roles(species: Species, care: CareProfile): List<EcoRole> = EcoRoles.of(species, care, state, weedRisk = weed(species).isWeed)

    companion object {
        val NONE = Locality(null, WeedData.EMPTY)
    }
}

/** A plant as the screens show it: the record, its species and resolved care, its eco-roles and its zone. */
data class PlantView(
    val plant: GardenPlant,
    val species: Species?,
    val care: ResolvedCare?,
    val roles: List<EcoRole>,
    val zoneName: String?,
    /** Its weed status in the garden's area. */
    val weed: WeedStatus = WeedStatus.NONE,
    /**
     * The genus, family and common name of a plant the knowledge base has no record of but the models can name, whose
     * [care] then comes from its genus and family.
     */
    val taxon: Taxon? = null,
) {
    /** The name the knowledge base does not know, with what is known of it, when the plant has no record of its own. */
    val unknownName: String? get() = if (species != null) null else plant.speciesName ?: plant.customName

    /** Whether the plant's own name is just the scientific name a model gave it, rather than one the user chose. */
    private val namedByModel: Boolean
        get() = taxon != null && plant.customName.let { own -> own == null || KnowledgeBase.normalise(own) == KnowledgeBase.normalise(taxon.name) }

    /** The user's own name for the plant, or else its common name, or else its scientific name. */
    val title: String
        get() = when {
            namedByModel -> taxon?.common ?: taxon?.name.orEmpty()
            else -> plant.customName ?: species?.displayName ?: plant.speciesName.orEmpty()
        }

    /** The scientific name, shown under the title when the title is something else. */
    val scientificName: String? get() = (species?.name ?: taxon?.name)?.takeIf { it != title }

    val isGrowing: Boolean get() = plant.status == PlantStatus.GROWING
}

/** A sighting with its animal in the knowledge base, when it is there, and the names of its zone and plant. */
data class SightingView(val sighting: Sighting, val animal: Animal?, val zoneName: String?, val plantTitles: List<String>) {
    /** The plants it was on as one piece of text, or nothing when it was on none. */
    val plantTitle: String? get() = plantTitles.joinToString(", ").ifEmpty { null }

    /** The animal's common name, or the name it was recorded under. */
    val title: String get() = animal?.displayName ?: sighting.label

    val scientificName: String? get() = animal?.name?.takeIf { it != title }

    /** The kind it is of, as [io.github.wkoller.pollenplot.domain.wildlife.Biodiversity.keyOf] names it. */
    val key: String get() = animal?.name ?: KnowledgeBase.normalise(sighting.speciesName ?: sighting.label)
}

/** A zone with the plants growing in it, the zone's badges and the animals seen in it, the latest first. */
data class ZoneView(
    val zone: Zone,
    val plants: List<PlantView>,
    val badges: List<ZoneBadge>,
    val sightings: List<SightingView> = emptyList(),
    /** How many of the zone's plants are likely in flower now. */
    val inFlower: Int = 0,
    /** Which of the zone's plants are likely in flower now, by id, so each can be shown in flower. */
    val flowering: Set<String> = emptySet(),
    /** How many of the zone's plants something says the flowering of, which the flowering layers grade against. */
    val floweringKnown: Int = 0,
    /** How many of the zone's plants are likely in flower in [laterMonth], for the "In flower later" layer. */
    val inFlowerLater: Int = 0,
    /** The month the "In flower later" layer looks at. */
    val laterMonth: Month? = null,
)

/** The garden for the screens: records joined with the knowledge base, the garden's state and its weed lists. */
class GardenViews(
    repository: GardenRepository,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    weeds: Flow<WeedData>,
    /** The watering lines, which decide which plants risk overwatering. */
    lines: Flow<List<WateringLine>> = flowOf(emptyList()),
    /** Today in the garden, for which plants are in flower now. */
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    val locality: Flow<Locality> = combine(settings.settings.map { it.gardenState to it.hemisphere }.distinctUntilChanged(), weeds) { (state, hemisphere), data ->
        Locality(state, data, hemisphere)
    }

    val plants: Flow<List<PlantView>> = combine(
        repository.plantList,
        repository.zoneList,
        knowledge.knowledge,
        locality,
        lines,
    ) { plants, zones, kb, locality, lines ->
        val zoneNames = zones.associate { it.id to it.name }
        val views = plants.map { view(it, kb, locality, zoneNames) }
        // A plant sharing a line's run time with a much thirstier one gets several times what it needs.
        val overwatered = Pairing.overwatered(lines, views.filter { it.plant.status == PlantStatus.GROWING }.map { it.waterPlant() })
        if (overwatered.isEmpty()) views else views.map { if (it.plant.id in overwatered) view(it.plant, kb, locality, zoneNames, overwatering = true) else it }
    }

    /** What is built in the yard, for the Plot and the report. */
    val structures: Flow<List<Structure>> = repository.structureList

    /** Every sighting, the most recent first. */
    val sightings: Flow<List<SightingView>> = combine(repository.sightingList, repository.zoneList, plants, knowledge.fauna) { sightings, zones, plants, fauna ->
        val zoneNames = zones.associate { it.id to it.name }
        val plantTitles = plants.associate { it.plant.id to it.title }
        sightings.map { sighting ->
            SightingView(
                sighting,
                fauna?.animal(sighting.speciesName ?: sighting.label),
                sighting.zoneId?.let(zoneNames::get),
                sighting.plantIds.mapNotNull(plantTitles::get),
            )
        }
    }

    /** The garden's place and how many months ahead the "In flower later" layer looks. */
    private val bloomAhead: Flow<Pair<Locality, Int>> = combine(locality, settings.settings.map { it.bloomLaterMonths }.distinctUntilChanged()) { place, months -> place to months }

    val zones: Flow<List<ZoneView>> = combine(repository.zoneList, plants, sightings, repository.floweringList, bloomAhead) { zones, plants, sightings, flowering, (locality, monthsAhead) ->
        // A plant growing in several zones is in each zone's list.
        val byZone = plants.filter { it.isGrowing }.flatMap { view -> view.plant.zoneIds.map { it to view } }.groupBy({ it.first }, { it.second })
        val seenIn = sightings.groupBy { it.sighting.zoneId }
        // In flower now: this month among its flowering months, turned for the garden's hemisphere, or seen lately.
        val day = today()
        val seenFlowering = flowering.groupBy({ it.first }, { it.second })
        val expected = { view: PlantView -> view.care?.care?.flowering?.fromSouthern(locality.hemisphere) }
        val growing = plants.filter { it.isGrowing }
        val flowering = growing.filter { view ->
            FloweringSeen.inFlowerNow(expected(view), seenFlowering[view.plant.id].orEmpty(), day)
        }.map { it.plant.id }.toSet()
        val known = growing.filter { FloweringSeen.known(expected(it), seenFlowering[it.plant.id].orEmpty()) }.map { it.plant.id }.toSet()
        // Later is judged from the flowering months and from the months seen in flower in the last year.
        val later = day.plusMonths(monthsAhead.toLong()).month
        val floweringLater = growing.filter { view ->
            FloweringSeen.inFlowerIn(expected(view), FloweringSeen.lastYear(seenFlowering[view.plant.id].orEmpty(), day), later)
        }.map { it.plant.id }.toSet()
        zones.map { zone ->
            val inZone = byZone[zone.id].orEmpty()
            val seen = seenIn[zone.id].orEmpty()
            val groups = seen.map { it.animal?.group ?: it.sighting.group }.toSet()
            ZoneView(
                zone,
                inZone,
                ZoneBadges.of(inZone.map { PlantFacts(it.care?.care?.water, it.roles, it.species?.form) }, groups),
                seen,
                inFlower = inZone.count { it.plant.id in flowering },
                flowering = inZone.map { it.plant.id }.filter { it in flowering }.toSet(),
                floweringKnown = inZone.count { it.plant.id in known },
                inFlowerLater = inZone.count { it.plant.id in floweringLater },
                laterMonth = later,
            )
        }
    }

    fun plant(id: String): Flow<PlantView?> = plants.map { list -> list.firstOrNull { it.plant.id == id } }

    fun zone(id: String): Flow<ZoneView?> = zones.map { list -> list.firstOrNull { it.zone.id == id } }

    companion object {
        fun view(plant: GardenPlant, kb: KnowledgeBase?, locality: Locality, zoneNames: Map<String, String>, overwatering: Boolean = false): PlantView {
            val species = plant.speciesName?.let { kb?.species(it) }
            // A plant the knowledge base has no record of takes its genus's and family's facts, when the models'
            // table of names says what they are.
            val unknown = if (species == null) plant.speciesName ?: plant.customName else null
            val taxon = unknown?.let { kb?.taxon(it) }
            val care = when {
                kb == null -> null
                species != null -> CareResolver.resolve(kb, species)
                taxon != null -> CareResolver.resolveGroup(kb, taxon.genus, taxon.family)
                else -> null
            }
            val weed = species?.let(locality::weed) ?: (taxon?.name ?: plant.speciesName)?.let(locality::weed) ?: WeedStatus.NONE
            val roles = when {
                care != null -> EcoRoles.of(species, care.care, locality.state, weedRisk = weed.isWeed, overwatering = overwatering)
                weed.isWeed -> listOf(EcoRole.WEED_RISK)
                else -> emptyList()
            }
            // A plant counted zone by zone says how many grow in each, as "Front (2), Back (3)".
            val places = plant.zoneIds.mapNotNull { id -> zoneNames[id]?.let { name -> plant.counts[id]?.takeIf { plant.zoneIds.size > 1 }?.let { "$name ($it)" } ?: name } }
            return PlantView(plant, species, care, roles, places.joinToString(", ").ifEmpty { null }, weed, taxon)
        }
    }
}

/** The plant as watering sees it: its zone and water need, for pairing on a line. */
private fun PlantView.waterPlant() = WaterPlant(
    id = plant.id,
    title = title,
    zoneId = plant.zoneIds.firstOrNull(),
    plantFactorBp = care?.care?.water?.plantFactorBp,
    widthMm = null,
    form = species?.form,
    quantity = plant.quantity,
    soil = null,
)
