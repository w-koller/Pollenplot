package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.Species

enum class SearchKind { ZONE, PLANT }

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

    /** An enum name as ordinary words, so GARDEN_BED is found by "garden bed". */
    fun words(name: String): String = name.lowercase().replace('_', ' ')
}
