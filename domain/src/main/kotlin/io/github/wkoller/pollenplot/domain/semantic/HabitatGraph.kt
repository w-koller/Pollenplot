package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.garden.WaterFeature

/** Water features as linked data: each a schema.org place inside its zone. */
object HabitatGraph {
    fun waterFeature(feature: WaterFeature): List<Triple> {
        val iri = Vocab.recordIri(feature.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Place")
            .type(iri, Vocab.PP + "WaterFeature")
            .iri(iri, Vocab.SCHEMA + "containedInPlace", Vocab.recordIri(feature.zoneId))
            .text(iri, Vocab.PP + "waterKind", feature.kind.name.lowercase())
            .date(iri, Vocab.PP + "cleanedOn", feature.cleanedOn?.toString())
            .text(iri, Vocab.SCHEMA + "description", feature.notes.ifBlank { null })
            .build()
    }
}
