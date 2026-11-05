package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.PlantEvent
import io.github.wkoller.pollenplot.domain.garden.PlantPot

/**
 * Pots and the care done to plants as linked data: a pot belongs to its plant, with its size, material and mix, and
 * each event is something done to the plant on a day, as a Darwin Core event names its date.
 */
object PotGraph {
    fun pot(pot: PlantPot): List<Triple> {
        val iri = Vocab.recordIri(pot.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "Pot")
            .iri(iri, Vocab.PP + "potOf", Vocab.recordIri(pot.plantId))
            .integer(iri, Vocab.PP + "diameterMm", pot.diameterMm?.toLong())
            .integer(iri, Vocab.PP + "volumeMl", pot.volumeMl)
            .text(iri, Vocab.PP + "potMaterial", pot.material?.name?.lowercase())
            .text(iri, Vocab.PP + "pottingMix", pot.mix?.name?.lowercase())
            .text(iri, Vocab.SCHEMA + "description", pot.mixNote.ifBlank { null })
            .build()
    }

    fun event(event: PlantEvent): List<Triple> {
        val iri = Vocab.recordIri(event.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "PlantEvent")
            .iri(iri, Vocab.PP + "plant", Vocab.recordIri(event.plantId))
            .text(iri, Vocab.PP + "eventKind", event.kind.name.lowercase())
            .date(iri, Vocab.DWC + "eventDate", event.date.toString())
            .text(iri, Vocab.SCHEMA + "description", event.notes.ifBlank { null })
            .build()
    }
}
