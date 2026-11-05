package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.Harvest

/** Harvests from plants as linked data: each a schema.org event about its plant, with what was picked. */
object HarvestGraph {
    fun harvest(harvest: Harvest): List<Triple> {
        val iri = Vocab.recordIri(harvest.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Event")
            .type(iri, Vocab.PP + "PlantHarvest")
            .iri(iri, Vocab.SCHEMA + "about", Vocab.recordIri(harvest.plantId))
            .date(iri, Vocab.SCHEMA + "startDate", harvest.date.toString())
            .integer(iri, Vocab.PP + "weightGrams", harvest.weightG?.toLong())
            .integer(iri, Vocab.PP + "count", harvest.count?.toLong())
            .integer(iri, Vocab.PP + "rating", harvest.rating?.toLong())
            .text(iri, Vocab.SCHEMA + "description", harvest.notes.ifBlank { null })
            .build()
    }
}
