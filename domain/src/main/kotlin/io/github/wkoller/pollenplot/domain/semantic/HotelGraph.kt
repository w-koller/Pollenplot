package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.hotels.BeeHotel
import io.github.wkoller.pollenplot.domain.hotels.HotelObservation
import io.github.wkoller.pollenplot.domain.hotels.HotelUpkeep

/**
 * Bee hotels as linked data: each hotel in its zone with its holes, each look at it a SOSA observation of the hotel
 * with the holes sealed by each kind of seal, and each piece of upkeep a schema.org event about it.
 */
object HotelGraph {
    fun hotel(hotel: BeeHotel): List<Triple> {
        val iri = Vocab.recordIri(hotel.id)
        val builder = GraphBuilder()
            .type(iri, Vocab.PP + "BeeHotel")
            .text(iri, Vocab.SCHEMA + "name", hotel.name)
            .iri(iri, Vocab.SCHEMA + "containedInPlace", hotel.zoneId?.let(Vocab::recordIri))
            .text(iri, Vocab.PP + "facing", hotel.facing?.name)
            .integer(iri, Vocab.PP + "heightMillimetres", hotel.heightMm?.toLong())
            .date(iri, Vocab.PP + "installedOn", hotel.installedOn?.toString())
            .integer(iri, Vocab.PP + "holes", hotel.holes.toLong())
            .text(iri, Vocab.SCHEMA + "description", hotel.notes)
        hotel.sections.forEachIndexed { index, section ->
            val part = "$iri#section-${index + 1}"
            builder.iri(iri, Vocab.PP + "section", part)
                .type(part, Vocab.PP + "HotelSection")
                .text(part, Vocab.PP + "material", section.material.name.lowercase())
                .integer(part, Vocab.PP + "holeMillimetres", section.holeMm?.toLong())
                .integer(part, Vocab.PP + "depthMillimetres", section.depthMm?.toLong())
                .integer(part, Vocab.PP + "holes", section.holes.toLong())
        }
        return builder.build()
    }

    fun observation(observation: HotelObservation): List<Triple> {
        val iri = Vocab.recordIri(observation.id)
        val builder = GraphBuilder()
            .type(iri, Vocab.SOSA + "Observation")
            .type(iri, Vocab.PP + "HotelObservation")
            .iri(iri, Vocab.SOSA + "hasFeatureOfInterest", Vocab.recordIri(observation.hotelId))
            .date(iri, Vocab.SCHEMA + "startDate", observation.date.toString())
            .integer(iri, Vocab.PP + "sealedHoles", observation.sealedHoles.toLong())
            .integer(iri, Vocab.PP + "busyHoles", observation.busy?.toLong())
            .integer(iri, Vocab.PP + "emerged", observation.emerged?.toLong())
            .text(iri, Vocab.SCHEMA + "description", observation.notes)
        observation.sealed.entries.sortedBy { it.key.ordinal }.forEach { (seal, count) ->
            builder.integer(iri, Vocab.PP + "sealedWith" + seal.name.lowercase().replaceFirstChar { it.uppercase() }, count.toLong())
        }
        observation.pests.sortedBy { it.ordinal }.forEach { builder.text(iri, Vocab.PP + "pest", it.name.lowercase()) }
        return builder.build()
    }

    fun upkeep(upkeep: HotelUpkeep): List<Triple> {
        val iri = Vocab.recordIri(upkeep.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Event")
            .type(iri, Vocab.PP + "HotelUpkeep")
            .iri(iri, Vocab.SCHEMA + "about", Vocab.recordIri(upkeep.hotelId))
            .date(iri, Vocab.SCHEMA + "startDate", upkeep.date.toString())
            .text(iri, Vocab.PP + "upkeepKind", upkeep.kind.name.lowercase())
            .text(iri, Vocab.SCHEMA + "description", upkeep.notes)
            .build()
    }
}
