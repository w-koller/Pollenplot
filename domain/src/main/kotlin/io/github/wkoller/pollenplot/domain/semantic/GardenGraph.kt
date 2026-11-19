package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.Structure
import io.github.wkoller.pollenplot.domain.garden.GardenPlant
import io.github.wkoller.pollenplot.domain.garden.Zone
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.Species
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.Sighting
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Describes garden records in standard vocabularies. A zone is a schema.org Place; a plant in the garden is a Darwin
 * Core Occurrence of its taxon, placed in its zone, and a plant counted zone by zone has a pp:ZonePlanting for each
 * zone with that zone's count; eco-roles use Pollenplot's own terms.
 */
object GardenGraph {
    /** A structure is a place in the yard with its kind and footprint; it is not a zone, and nothing grows in it. */
    fun structure(structure: Structure): List<Triple> {
        val iri = Vocab.recordIri(structure.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Place")
            .type(iri, Vocab.PP + "Structure")
            .text(iri, Vocab.SCHEMA + "name", structure.name)
            .text(iri, Vocab.PP + "structureKind", structure.kind.name.lowercase())
            .decimal(iri, Vocab.PP + "areaSquareMetres", BigDecimal(structure.rect.widthCm.toLong() * structure.rect.heightCm).movePointLeft(4).stripTrailingZeros().toPlainString())
            .text(iri, Vocab.SCHEMA + "description", structure.notes)
            .build()
    }

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
            .boolean(iri, Vocab.PP + "rocksAndLogs", zone.rocksAndLogs)
            .boolean(iri, Vocab.PP + "denseShrubs", zone.denseShrubs)
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
            .date(iri, Vocab.PP + "endedOn", plant.endedOn?.toString())
            .also { graph ->
                // A plant growing in several zones is contained in each.
                plant.zoneIds.forEach {
                    graph.iri(iri, Vocab.SCHEMA + "containedInPlace", Vocab.recordIri(it))
                    graph.iri(iri, Vocab.DWC + "locationID", Vocab.recordIri(it))
                }
            }
            .text(iri, Vocab.DWC + "occurrenceRemarks", plant.notes)
        // A plant counted zone by zone says how many grow in each zone, beside its total. Each zone's count has an IRI
        // made from the plant and the zone, so it is the same in every export.
        plant.zoneIds.distinct().forEach { zoneId ->
            val count = plant.counts[zoneId] ?: return@forEach
            val planting = Vocab.recordIri(plantingId(plant.id, zoneId))
            builder
                .iri(iri, Vocab.PP + "zonePlanting", planting)
                .type(planting, Vocab.PP + "ZonePlanting")
                .iri(planting, Vocab.PP + "plantingOf", iri)
                .iri(planting, Vocab.SCHEMA + "containedInPlace", Vocab.recordIri(zoneId))
                .integer(planting, Vocab.DWC + "individualCount", count.toLong())
        }
        roles.forEach { builder.iri(iri, Vocab.PP + "ecoRole", Vocab.PP + roleName(it)) }
        return builder.build()
    }

    /** A sighting as a Darwin Core Occurrence of the animal, in its zone, with the plant it was on as an association. */
    fun sighting(sighting: Sighting, animal: Animal?): List<Triple> {
        val iri = Vocab.recordIri(sighting.id)
        val graph = GraphBuilder()
        // Each thing it was doing and each plant it was on is a statement of its own.
        sighting.behaviourWords.forEach { graph.text(iri, Vocab.DWC + "behavior", it) }
        sighting.plantIds.forEach { graph.iri(iri, Vocab.PP + "seenOn", Vocab.recordIri(it)) }
        return graph
            .type(iri, Vocab.DWC + "Occurrence")
            .type(iri, Vocab.PP + "Sighting")
            .text(iri, Vocab.DWC + "basisOfRecord", if (sighting.photoId != null) "MachineObservation" else "HumanObservation")
            .text(iri, Vocab.DWC + "occurrenceID", iri)
            .text(iri, Vocab.DWC + "scientificName", animal?.name ?: sighting.speciesName ?: sighting.label)
            .text(iri, Vocab.DWC + "family", animal?.family)
            .text(iri, Vocab.DWC + "vernacularName", animal?.common?.firstOrNull())
            .integer(iri, Vocab.DWC + "individualCount", sighting.count.toLong())
            .text(iri, Vocab.DWC + "eventDate", sighting.seenAt.toString())
            .iri(iri, Vocab.DWC + "locationID", sighting.zoneId?.let(Vocab::recordIri))
            .text(iri, Vocab.DWC + "occurrenceRemarks", sighting.notes)
            .build()
    }

    /**
     * A day a plant was seen in flower, as a SOSA observation of the plant: by the app in a photo, or by the gardener.
     */
    fun flowering(id: String, plantId: String, day: LocalDate, fromPhoto: Boolean): List<Triple> {
        val iri = Vocab.recordIri(id)
        return GraphBuilder()
            .type(iri, Vocab.SOSA + "Observation")
            .type(iri, Vocab.PP + "FloweringObservation")
            .iri(iri, Vocab.SOSA + "hasFeatureOfInterest", Vocab.recordIri(plantId))
            .iri(iri, Vocab.SOSA + "observedProperty", Vocab.PP + "Flowering")
            .text(iri, Vocab.DWC + "basisOfRecord", if (fromPhoto) "MachineObservation" else "HumanObservation")
            .date(iri, Vocab.SCHEMA + "startDate", day.toString())
            .build()
    }

    /** The id of a plant's count in one zone: a name-based UUID of the two ids, so it never changes. */
    fun plantingId(plantId: String, zoneId: String): String =
        java.util.UUID.nameUUIDFromBytes("pollenplot:zone-planting:$plantId:$zoneId".toByteArray()).toString()

    /** An eco-role's term, such as PollinatorMagnet. */
    fun roleName(role: EcoRole): String = role.name.lowercase().split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
}
