package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.bees.honey.HoneyHive
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Sighting

enum class SearchKind { ZONE, PLANT, SIGHTING, HIVE }

/**
 * A record as the search index holds it: the record's key, its kind, the title shown in results and the words it
 * should be found by. The same text is later embedded for search by meaning.
 */
data class SearchDoc(val id: String, val kind: SearchKind, val title: String, val body: String)

/** Renders records as plain sentences and keywords for the search index. */
object SearchText {
    fun zone(zone: Zone): SearchDoc = SearchDoc(
        id = zone.id,
        kind = SearchKind.ZONE,
        title = zone.name,
        body = listOfNotNull(
            words(zone.kind.name),
            zone.sun?.let { words(it.name) },
            zone.soil?.let { "${words(it.name)} soil" },
            if (zone.mulched) "mulched" else null,
            zone.notes.ifBlank { null },
        ).joinToString(". "),
    )

    fun plant(plant: GardenPlant, species: Species?, zoneName: String?, roles: List<EcoRole>): SearchDoc = SearchDoc(
        id = plant.id,
        kind = SearchKind.PLANT,
        title = plant.customName?.takeIf { it.isNotBlank() } ?: species?.displayName ?: "",
        body = listOfNotNull(
            species?.common?.joinToString(", ")?.ifBlank { null },
            species?.let { "${it.name}, genus ${it.genus}, family ${it.family}" },
            species?.form?.let { words(it.name) },
            species?.synonyms?.joinToString(", ")?.ifBlank { null },
            zoneName?.let { "in $it" },
            roles.joinToString(", ") { words(it.name) }.ifBlank { null },
            plant.notes.ifBlank { null },
        ).joinToString(". "),
    )

    /** A honey bee hive, found by its name, its kind of hive, where it is and its queen's year. */
    fun hive(hive: HoneyHive, zoneName: String?, apiaryName: String?): SearchDoc = SearchDoc(
        id = hive.id,
        kind = SearchKind.HIVE,
        title = hive.name,
        body = listOfNotNull(
            "honey bee hive",
            words(hive.type.name),
            zoneName?.let { "in $it" },
            apiaryName?.let { "apiary $it" },
            hive.queenYear?.let { "queen from $it, marked ${words(hive.queenColour!!.name)}" },
            words(hive.status.name),
            hive.notes.ifBlank { null },
        ).joinToString(". "),
    )

    /** A sighting, found by the animal's names, its group, what it was doing and where. */
    fun sighting(sighting: Sighting, animal: Animal?, zoneName: String?, plantTitle: String?): SearchDoc = SearchDoc(
        id = sighting.id,
        kind = SearchKind.SIGHTING,
        title = animal?.displayName ?: sighting.label,
        body = listOfNotNull(
            animal?.common?.joinToString(", ")?.ifBlank { null },
            animal?.let { "${it.name}, family ${it.family}" } ?: sighting.label.takeIf { it != sighting.speciesName },
            words(sighting.group.name),
            sighting.behaviour?.let { words(it.name) },
            plantTitle?.let { "on $it" },
            zoneName?.let { "in $it" },
            sighting.notes.ifBlank { null },
        ).joinToString(". "),
    )

    /** An enum name as ordinary words, so GARDEN_BED is found by "garden bed". */
    fun words(name: String): String = name.lowercase().replace('_', ' ')
}
