package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.problems.PlantProblem
import io.github.wkoller.pollenplot.domain.problems.Treatment

/**
 * Problems as linked data: each is a SOSA observation of its plant, with the problem named as its result, and each
 * treatment an event that acts on the problem.
 */
object ProblemGraph {
    fun problem(problem: PlantProblem, name: String): List<Triple> {
        val iri = Vocab.recordIri(problem.id)
        return GraphBuilder()
            .type(iri, Vocab.SOSA + "Observation")
            .type(iri, Vocab.PP + "PlantProblem")
            .iri(iri, Vocab.SOSA + "hasFeatureOfInterest", Vocab.recordIri(problem.plantId))
            .iri(iri, Vocab.SOSA + "observedProperty", Vocab.PP + "PlantHealth")
            .text(iri, Vocab.SOSA + "hasSimpleResult", name)
            .text(iri, Vocab.PP + "problemKey", problem.problemKey)
            .date(iri, Vocab.SOSA + "resultTime", problem.observedOn.toString())
            .date(iri, Vocab.PP + "resolvedOn", problem.resolvedOn?.toString())
            .text(iri, Vocab.SCHEMA + "description", problem.notes.ifBlank { null })
            .build()
    }

    fun treatment(treatment: Treatment): List<Triple> {
        val iri = Vocab.recordIri(treatment.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "Treatment")
            .iri(iri, Vocab.PP + "treats", Vocab.recordIri(treatment.problemId))
            .text(iri, Vocab.PP + "treatmentMethod", treatment.method.name.lowercase())
            .text(iri, Vocab.PP + "product", treatment.productText.ifBlank { null })
            .date(iri, Vocab.DWC + "eventDate", treatment.appliedOn.toString())
            .date(iri, Vocab.PP + "followUpOn", treatment.followUpOn?.toString())
            .text(iri, Vocab.SCHEMA + "description", treatment.notes.ifBlank { null })
            .build()
    }
}
