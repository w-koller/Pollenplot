package io.github.wkoller.pollenplot.domain.wildlife

import io.github.wkoller.pollenplot.domain.weeds.WeedNames
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** An animal or fungus recorded near the garden on the Atlas of Living Australia: its binomial, common name and records. */
@Serializable
data class RecordedAnimal(val name: String, val common: String?, val group: LifeGroup, val records: Int)

/** The animals most recorded near the garden, [radiusKm] around it since [sinceYear], each group's most recorded first. */
data class NearbyAnimals(val animals: List<RecordedAnimal>, val radiusKm: Int, val sinceYear: Int) {
    private val byName: Map<String, Int> = animals.groupingBy { WeedNames.binomial(it.name) }.fold(0) { total, animal -> total + animal.records }

    /** The groups with records, in the order the app lists them. */
    val groups: List<LifeGroup> get() = LifeGroup.entries.filter { group -> animals.any { it.group == group } }

    fun inGroup(group: LifeGroup): List<RecordedAnimal> = animals.filter { it.group == group }.sortedByDescending { it.records }

    /** How many times the animal known by [name] was recorded, by its binomial. */
    fun records(name: String): Int = byName[WeedNames.binomial(name)] ?: 0

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * One group's records, faceted by common name. Each label reads "Noisy Miner|Manorina (Myzantha) melanocephala|
         * <page>|Noisy Miner|Animalia|Meliphagidae". The subgenus is dropped, subspecies count towards their species,
         * and records named only to a genus or higher are left out.
         */
        fun parse(page: String, group: LifeGroup): List<RecordedAnimal> {
            val facets = json.parseToJsonElement(page).jsonObject["facetResults"]?.jsonArray.orEmpty()
            val rows = facets.map { it.jsonObject }.firstOrNull { (it["fieldName"] as? JsonPrimitive)?.contentOrNull == FIELD }?.get("fieldResult")?.jsonArray.orEmpty()
            val found = rows.mapNotNull { row ->
                val result = row.jsonObject
                if ((result["fq"] as? JsonPrimitive)?.contentOrNull?.startsWith("-") == true) return@mapNotNull null
                val parts = (result["label"] as? JsonPrimitive)?.contentOrNull?.split('|') ?: return@mapNotNull null
                val name = parts.getOrNull(1)?.replace(SUBGENUS, " ")?.let(WeedNames::binomial)?.takeIf { ' ' in it } ?: return@mapNotNull null
                RecordedAnimal(
                    name = name.replaceFirstChar(Char::uppercaseChar),
                    common = parts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }?.let(::sentenceCase),
                    group = group,
                    records = result["count"]?.jsonPrimitive?.intOrNull ?: 0,
                )
            }
            return found.groupBy { it.name }.map { (_, same) ->
                same.first().copy(common = same.firstNotNullOfOrNull { it.common }, records = same.sumOf { it.records })
            }.sortedByDescending { it.records }
        }

        /**
         * A common name as the app writes them, "Australian white ibis" for the Atlas's "Australian White Ibis". A
         * possessive and a place stay capitalised, as in "Lewin's honeyeater" and "Common Australian crow".
         */
        fun sentenceCase(name: String): String = name.split(' ').mapIndexed { i, word ->
            if (i == 0 || '\'' in word || word in PLACES) word else word.lowercase()
        }.joinToString(" ")

        private val PLACES = setOf("Australian", "Australia", "Asian", "European", "Indian", "Pacific", "Torresian", "Queensland", "Sydney")

        /** The facet field that pairs each common name with its scientific name. */
        const val FIELD = "common_name_and_lsid"

        private val SUBGENUS = Regex("\\s*\\([^)]*\\)\\s*")
    }
}
