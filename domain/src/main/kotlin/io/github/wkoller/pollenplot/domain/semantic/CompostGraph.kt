package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.compost.CompostBatch
import io.github.wkoller.pollenplot.domain.compost.CompostBin
import io.github.wkoller.pollenplot.domain.compost.CompostEvent

/**
 * Compost bins as linked data: each bin a schema.org place inside its zone, each fill of it a batch with the days it
 * was started, filled and emptied, and each thing done to a batch an action on it.
 */
object CompostGraph {
    fun bin(bin: CompostBin): List<Triple> {
        val iri = Vocab.recordIri(bin.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Place")
            .type(iri, Vocab.PP + "CompostBin")
            .text(iri, Vocab.SCHEMA + "name", bin.name)
            .iri(iri, Vocab.SCHEMA + "containedInPlace", bin.zoneId?.let(Vocab::recordIri))
            .integer(iri, Vocab.PP + "turnEveryDays", bin.turnEveryDays.toLong())
            .text(iri, Vocab.SCHEMA + "description", bin.notes.ifBlank { null })
            .build()
    }

    fun batch(batch: CompostBatch): List<Triple> {
        val iri = Vocab.recordIri(batch.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "CompostBatch")
            .iri(iri, Vocab.PP + "inBin", Vocab.recordIri(batch.binId))
            .date(iri, Vocab.SCHEMA + "startDate", batch.startedOn.toString())
            .date(iri, Vocab.PP + "filledOn", batch.filledOn?.toString())
            .date(iri, Vocab.SCHEMA + "endDate", batch.emptiedOn?.toString())
            .text(iri, Vocab.SCHEMA + "description", batch.notes.ifBlank { null })
            .build()
    }

    fun event(event: CompostEvent): List<Triple> {
        val iri = Vocab.recordIri(event.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Action")
            .type(iri, Vocab.PP + "CompostEvent")
            .iri(iri, Vocab.SCHEMA + "object", Vocab.recordIri(event.batchId))
            .text(iri, Vocab.PP + "compostEventKind", event.kind.name.lowercase())
            .date(iri, Vocab.SCHEMA + "startTime", event.date.toString())
            .decimal(iri, Vocab.PP + "temperatureCelsius", event.tenthsC?.let { java.math.BigDecimal(it).movePointLeft(1).toPlainString() })
            .text(iri, Vocab.SCHEMA + "description", event.notes.ifBlank { null })
            .build()
    }
}
