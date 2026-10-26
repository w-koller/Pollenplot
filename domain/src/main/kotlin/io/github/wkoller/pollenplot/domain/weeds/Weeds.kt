package io.github.wkoller.pollenplot.domain.weeds

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Which plants one entry on a weed list covers: whole species, by binomial, or a whole genus apart from some species,
 * as in "Opuntia spp. other than O. stricta".
 */
data class WeedNames(val species: Set<String>, val genera: Map<String, Set<String>> = emptyMap()) {
    val isEmpty: Boolean get() = species.isEmpty() && genera.isEmpty()

    /** Whether any of [names] is one of these plants. Names are compared by their first two words, in lower case. */
    fun matches(names: Collection<String>): Boolean = matchesBinomials(names.map(::binomial))

    /** Whether any of [binomials], already in the form [binomial] gives, is one of these plants. */
    fun matchesBinomials(binomials: Collection<String>): Boolean = binomials.any { name ->
        name in species || genera[name.substringBefore(' ')]?.let { except -> ' ' in name && name !in except } == true
    }

    /** These plants and [other]'s. A genus both cover is covered apart from the species both leave out. */
    operator fun plus(other: WeedNames): WeedNames = WeedNames(
        species + other.species,
        (genera.keys + other.genera.keys).associateWith { genus ->
            val mine = genera[genus]
            val theirs = other.genera[genus]
            if (mine != null && theirs != null) mine intersect theirs else mine ?: theirs.orEmpty()
        },
    )

    /** These plants, also known by the accepted names in [accepted] (binomial to binomial, in lower case). */
    fun withAccepted(accepted: Map<String, String>): WeedNames = copy(species = species + species.mapNotNull(accepted::get))

    companion object {
        /** A name's genus and species in lower case, without authors, subspecies, varieties or a hybrid sign. */
        fun binomial(name: String): String = name.replace('×', ' ').replace(' ', ' ').trim().split(Regex("\\s+"))
            .filter { it != "x" && it != "X" }
            .take(2).joinToString(" ").lowercase()

        /**
         * The plants a weed list's botanical name field covers. The field can hold two species, synonyms after "syn.",
         * subspecies and varieties, a hybrid, an aggregate, or a genus, with or without exceptions.
         */
        fun parse(field: String): WeedNames {
            val text = field.replace(' ', ' ').replace('×', ' ').trim()
            val genusWide = Regex("^([A-Z][a-z]+)(?:\\s+spp\\.?(?:\\s+(?:other than|except)\\s+(.*))?)?$").find(text)
            if (genusWide != null) {
                val genus = genusWide.groupValues[1]
                // Exceptions name the genus or abbreviate it, as in "O. aurantiaca, O. elata and O. stricta" or "S.babylonica".
                val except = Regex("(?:$genus|${genus.first()}\\.)\\s*([a-z][a-z-]+)").findAll(genusWide.groupValues[2])
                    .map { "${genus.lowercase()} ${it.groupValues[1]}" }.toSet()
                return WeedNames(emptySet(), mapOf(genus.lowercase() to except))
            }
            val words = text.split(Regex("[\\s,;()]+")).filter { it.isNotBlank() && it != "x" }
            val species = mutableSetOf<String>()
            var lastGenus: String? = null
            for (i in 0 until words.size - 1) {
                val word = words[i]
                val epithet = words[i + 1]
                // A genus written out, or abbreviated after it has been written out once, as in "Ruellia tweediana, R. simplex".
                val genus = when {
                    word.matches(Regex("[A-Z][a-z]+")) -> word.also { lastGenus = it }
                    word.matches(Regex("[A-Z]\\.")) && lastGenus?.first() == word.first() -> lastGenus
                    else -> null
                }
                if (genus != null && epithet.matches(Regex("[a-z][a-z-]+")) && epithet !in NOT_EPITHETS) {
                    species += "${genus.lowercase()} $epithet"
                }
            }
            return WeedNames(species)
        }

        private val NOT_EPITHETS = setOf("syn", "spp", "sp", "subsp", "ssp", "var", "cv", "agg", "aggregate", "other", "than", "and", "except")
    }
}

/** A weed on a council's list, with its legal status and the council's advice. */
data class CouncilWeed(
    val names: WeedNames,
    /** The botanical name as the council writes it, such as "Opuntia spp. other than O. stricta". */
    val botanical: String,
    val common: String,
    val otherCommon: List<String>,
    /** Queensland Biosecurity Act 2014 restricted matter categories, such as 3. */
    val restricted: List<Int>,
    /** Prohibited matter under the Biosecurity Act 2014. */
    val prohibited: Boolean,
    /** Pest vegetation under the council's own local law. */
    val councilPest: Boolean,
    /** The council's data says the weed must be reported. */
    val reportRequired: Boolean,
    val type: String?,
    val family: String?,
    val flowerColours: List<String>,
    val floweringTime: String?,
    val leafForm: List<String>,
    val leafArrangement: List<String>,
    val control: List<String>,
)

/** Brisbane City Council's weed list, from its open data (CC BY 4.0). */
object BrisbaneWeeds {
    private val json = Json { ignoreUnknownKeys = true }

    /** The weeds in the dataset, as its export (an array of rows) or a page of its records API returns them. */
    fun parse(page: String): List<CouncilWeed> {
        val root = json.parseToJsonElement(page)
        val rows = root as? JsonArray ?: root.jsonObject["results"]!!.jsonArray
        return rows.mapNotNull { row -> weed(row.jsonObject) }
    }

    private fun weed(row: JsonObject): CouncilWeed? {
        fun text(key: String) = row.text(key)?.let(::decode)?.trim()?.trimEnd('.')?.takeIf { it.isNotEmpty() && it != "NIL" && it != "None" }
        fun list(key: String) = text(key)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        val botanical = text("payload_fields_attributes_botanicalname") ?: return null
        val names = WeedNames.parse(botanical).takeUnless { it.isEmpty } ?: return null
        val category = text("categories_name").orEmpty()
        return CouncilWeed(
            names = names,
            botanical = botanical,
            common = text("payload_fields_attributes_primarycommonname") ?: return null,
            otherCommon = list("secondarycommonnames"),
            restricted = if ("Restricted Matter" in category) Regex("\\d").findAll(category.substringBefore("Restricted")).map { it.value.toInt() }.toList() else emptyList(),
            prohibited = "Prohibited Matter" in category,
            councilPest = "Council Pest" in category,
            reportRequired = text("categories_reportrequired").equals("True", ignoreCase = true),
            type = text("payload_fields_attributes_weedtype"),
            family = text("payload_fields_attributes_family"),
            flowerColours = list("flowercolour"),
            floweringTime = text("payload_fields_attributes_floweringtime"),
            leafForm = list("payload_fields_attributes_leafform"),
            leafArrangement = list("payload_fields_attributes_leafarrangement"),
            control = list("controlmethods"),
        )
    }

    /**
     * The council's page for a weed, such as weeds.brisbane.qld.gov.au/weeds/cockspur-coral-tree. The site names its
     * pages after the common name, without apostrophes.
     */
    fun profileUrl(site: String, weed: CouncilWeed): String =
        site.trimEnd('/') + "/weeds/" + weed.common.lowercase().replace("'", "").replace("’", "").replace(Regex("[^a-z0-9]+"), "-").trim('-')

    /** The open data has a few HTML entities, such as &#039; for an apostrophe. */
    fun decode(text: String): String = text
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toInt().toChar().toString() }
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&apos;", "'")
}

/** A weed on a national list, with the common name the list gives it. */
data class ListedWeed(val names: WeedNames, val common: String?)

/** National lists from the Atlas of Living Australia. */
data class NationalWeeds(
    /** Weeds of National Significance. */
    val wons: List<ListedWeed> = emptyList(),
    /** The National Alert List for Environmental Weeds: new weeds the states ask to hear about. */
    val alert: List<ListedWeed> = emptyList(),
)

/** Species lists from the Atlas of Living Australia's lists service. */
object AlaLists {
    private val json = Json { ignoreUnknownKeys = true }

    /** The weeds on one list, as its items service returns them. Items name a species, a genus or a genus with exceptions. */
    fun parse(page: String): List<ListedWeed> = json.parseToJsonElement(page).jsonArray.mapNotNull { item ->
        val row = item.jsonObject
        // The list's own spelling and the name the Atlas matched it to; either can be missing, or an identifier.
        val names = listOfNotNull(row.text("name"), row.text("scientificName"))
            .map(WeedNames::parse).fold(WeedNames(emptySet()), WeedNames::plus)
        if (names.isEmpty) null else ListedWeed(names, row.text("commonName")?.trim())
    }
}

/** A plant species recorded near the garden, and how often. */
data class RecordedSpecies(val name: String, val records: Int)

/** Plant records near the garden on the Atlas of Living Australia, within [radiusKm] since [sinceYear]. */
data class NearbyRecords(val species: List<RecordedSpecies>, val radiusKm: Int, val sinceYear: Int) {
    private val byBinomial: Map<String, Int> = species.groupingBy { WeedNames.binomial(it.name) }.fold(0) { total, s -> total + s.records }

    /** The records of the plant known by [binomials]. */
    fun of(binomials: Collection<String>): Int = binomials.toSet().sumOf { byBinomial[it] ?: 0 }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** The species facet of an occurrence search, without the records that name no species. */
        fun parse(page: String, radiusKm: Int, sinceYear: Int): NearbyRecords {
            val facets = json.parseToJsonElement(page).jsonObject["facetResults"]?.jsonArray.orEmpty()
            val species = facets.map { it.jsonObject }.firstOrNull { it.text("fieldName") == "species" }?.get("fieldResult")?.jsonArray.orEmpty()
                .mapNotNull { result ->
                    val row = result.jsonObject
                    val name = row.text("label")?.takeIf { row.text("fq")?.startsWith("-") != true && ' ' in it } ?: return@mapNotNull null
                    RecordedSpecies(name, row["count"]?.jsonPrimitive?.intOrNull ?: 0)
                }
            return NearbyRecords(species, radiusKm, sinceYear)
        }
    }
}

/** How seriously a plant is a weed where the garden is, most serious first. */
enum class WeedLevel { PROHIBITED, RESTRICTED, NATIONAL, COUNCIL, LISTED, NONE }

/** One plant's weed status for the garden's area, combined from the sources that apply there. */
data class WeedStatus(
    val level: WeedLevel,
    /** The council's entry, when the garden's council publishes a list that names the plant. */
    val council: CouncilWeed? = null,
    val wons: Boolean = false,
    val alert: Boolean = false,
    /** A common name from the lists, for plants the knowledge base does not have. */
    val listedCommon: String? = null,
    /** How many times the plant has been recorded near the garden, when that is known. */
    val nearbyRecords: Int? = null,
) {
    val isWeed: Boolean get() = level != WeedLevel.NONE

    /** The council's data says it must be reported, as prohibited matter and some restricted matter must be. */
    val mustReport: Boolean get() = council?.reportRequired == true || council?.prohibited == true

    /** On the National Alert List, whose weeds the states ask to hear about, without a duty to report it. */
    val askedToReport: Boolean get() = alert && !mustReport

    companion object {
        val NONE = WeedStatus(WeedLevel.NONE)
    }
}

/** Combines the weed sources for the garden's area into one status for a plant. */
object WeedResolver {
    /**
     * The status of the plant known by [names] (its accepted name and synonyms). [council] is the list of the garden's
     * council, if it publishes one. [accepted] maps names to the names the Atlas of Living Australia accepts, by
     * binomial in lower case, so a plant is found under either.
     */
    fun resolve(
        names: Collection<String>,
        council: List<CouncilWeed>,
        national: NationalWeeds,
        nearby: NearbyRecords? = null,
        accepted: Map<String, String> = emptyMap(),
    ): WeedStatus {
        val binomials = names.map(WeedNames::binomial).toSet().let { it + it.mapNotNull(accepted::get) }
        // A species can be on the list twice, such as under a whole genus and under its own name; the most serious counts.
        val entry = council.filter { it.names.matchesBinomials(binomials) }.minByOrNull { levelOf(it).ordinal }
        val wons = national.wons.firstOrNull { it.names.matchesBinomials(binomials) }
        val alert = national.alert.firstOrNull { it.names.matchesBinomials(binomials) }
        val level = listOfNotNull(
            entry?.let(::levelOf),
            if (wons != null || alert != null) WeedLevel.NATIONAL else null,
        ).minByOrNull { it.ordinal } ?: WeedLevel.NONE
        return WeedStatus(
            level = level,
            council = entry,
            wons = wons != null,
            alert = alert != null,
            listedCommon = entry?.common ?: wons?.common ?: alert?.common,
            nearbyRecords = nearby?.of(binomials),
        )
    }

    /** How serious one council entry is, for an entry shown on its own, such as a whole genus in the Weed finder. */
    fun levelOf(weed: CouncilWeed): WeedLevel = when {
        weed.prohibited -> WeedLevel.PROHIBITED
        weed.restricted.isNotEmpty() -> WeedLevel.RESTRICTED
        weed.councilPest -> WeedLevel.COUNCIL
        else -> WeedLevel.LISTED
    }
}

/** A weed recorded near the garden: the species as the records name it, its status and how often it was recorded. */
data class NearbyWeed(val name: String, val status: WeedStatus, val records: Int)

object WeedsNearby {
    /** The species near the garden that are weeds there, most serious first and then most often recorded. */
    fun of(council: List<CouncilWeed>, national: NationalWeeds, nearby: NearbyRecords, accepted: Map<String, String> = emptyMap()): List<NearbyWeed> =
        nearby.species
            .map { recorded -> NearbyWeed(recorded.name, WeedResolver.resolve(listOf(recorded.name), council, national, accepted = accepted), recorded.records) }
            .filter { it.status.isWeed }
            .sortedWith(compareBy<NearbyWeed> { it.status.level.ordinal }.thenByDescending { it.records }.thenBy { it.name })
}

private fun JsonObject.text(key: String): String? = this[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
