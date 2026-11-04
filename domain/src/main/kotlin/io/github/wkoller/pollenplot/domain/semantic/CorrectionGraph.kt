package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.plants.CareCorrection
import java.time.ZoneOffset

/**
 * The user's corrections to the knowledge base as linked data: statements by the gardener about a taxon, each naming
 * the fact and the value written as the knowledge base writes it, so they stay apart from the knowledge base's own.
 */
object CorrectionGraph {
    fun correction(correction: CareCorrection): List<Triple> {
        val iri = Vocab.recordIri(correction.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "CareCorrection")
            .text(iri, Vocab.DWC + "scientificName", correction.speciesName)
            .text(iri, Vocab.PP + "careField", correction.field.name.lowercase())
            .text(iri, Vocab.PP + "correctedValue", correction.value)
            .date(iri, Vocab.SCHEMA + "dateModified", correction.updatedAt.atOffset(ZoneOffset.UTC).toLocalDate().toString())
            .build()
    }
}
