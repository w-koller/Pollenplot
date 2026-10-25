package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.Species
import java.math.BigDecimal

/** Where a link between two records came from: the user, an AI model, or an official source. */
enum class Provenance { USER, AI, SOURCE }

/**
 * A relation between two records that is not already part of either record, such as "this sighting was on that
 * plant". [confidenceBp] is in basis points; links the user made are certain.
 */
data class Link(
    val subjectId: String,
    val predicate: String,
    val objectId: String,
    val provenance: Provenance,
    val confidenceBp: Int = 10_000,
)

/**
 * Describes garden records in standard vocabularies. A zone is a schema.org Place; a plant in the garden is a Darwin
 * Core Occurrence of its taxon, placed in its zone; eco-roles use Pollenplot's own terms.
 */
object GardenGraph {
    fun zone(zone: Zone): List<Triple> {
        val iri = Vocab.recordIri(zone.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Place")
            .type(iri, Vocab.PP + "Zone")
            .text(iri, Vocab.SCHEMA + "name", zone.name)
            .text(iri, Vocab.PP + "zoneKind", zone.kind.name.lowercase())
            .text(iri, Vocab.PP + "sun", zone.sun?.name?.lowercase())
            .text(iri, Vocab.PP + "soil", zone.soil?.name?.lowercase())
            .boolean(iri, Vocab.PP + "mulched", zone.mulched)
            // Area in square metres, the unit people expect to read in an export.
            .decimal(iri, Vocab.PP + "areaSquareMetres", BigDecimal(zone.areaCm2).movePointLeft(4).stripTrailingZeros().toPlainString())
            .text(iri, Vocab.SCHEMA + "description", zone.notes)
            .build()
    }

    fun plant(plant: GardenPlant, species: Species?, roles: List<EcoRole>): List<Triple> {
        val iri = Vocab.recordIri(plant.id)
        val builder = GraphBuilder()
            .type(iri, Vocab.DWC + "Occurrence")
            .type(iri, Vocab.PP + "GardenPlant")
            .text(iri, Vocab.DWC + "basisOfRecord", "HumanObservation")
            .text(iri, Vocab.DWC + "occurrenceID", iri)
            .text(iri, Vocab.DWC + "scientificName", species?.name)
            .text(iri, Vocab.DWC + "family", species?.family)
            .text(iri, Vocab.DWC + "genus", species?.genus)
            .text(iri, Vocab.DWC + "vernacularName", species?.common?.firstOrNull())
            .text(iri, Vocab.SCHEMA + "name", plant.customName)
            .integer(iri, Vocab.DWC + "individualCount", plant.quantity.toLong())
            .text(iri, Vocab.DWC + "occurrenceStatus", if (plant.status.name == "GROWING") "present" else "absent")
            .date(iri, Vocab.PP + "plantedOn", plant.plantedOn?.toString())
            .iri(iri, Vocab.SCHEMA + "containedInPlace", plant.zoneId?.let(Vocab::recordIri))
            .iri(iri, Vocab.DWC + "locationID", plant.zoneId?.let(Vocab::recordIri))
            .text(iri, Vocab.DWC + "occurrenceRemarks", plant.notes)
        roles.forEach { builder.iri(iri, Vocab.PP + "ecoRole", Vocab.PP + roleName(it)) }
        return builder.build()
    }

    fun link(link: Link): List<Triple> {
        val statement = Triple(Vocab.recordIri(link.subjectId), link.predicate, Term.Iri(Vocab.recordIri(link.objectId)))
        return listOf(statement)
    }

    /** An eco-role's term, such as PollinatorMagnet. */
    fun roleName(role: EcoRole): String = role.name.lowercase().split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
}
