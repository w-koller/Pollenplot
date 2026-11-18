package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import java.time.LocalDate

/**
 * Watering as linked data: a line (a tap timer, valve or hose run) with its method, flow and the zones it waters, and
 * each watering recorded on a day for a line or a zone. The weather kept for the plan is not a record and is left out.
 */
object WaterGraph {
    fun line(line: WateringLine): List<Triple> {
        val iri = Vocab.recordIri(line.id)
        val builder = GraphBuilder()
            .type(iri, Vocab.PP + "WateringLine")
            .text(iri, Vocab.SCHEMA + "name", line.name)
            .text(iri, Vocab.PP + "waterMethod", line.method.name.lowercase())
            .integer(iri, Vocab.PP + "flowMlPerHour", line.flowMlPerHour?.toLong())
            .integer(iri, Vocab.PP + "poolingMinutes", line.poolingMinutes?.toLong())
            .text(iri, Vocab.SCHEMA + "description", line.notes.ifBlank { null })
        line.zoneIds.sorted().forEach { builder.iri(iri, Vocab.PP + "zone", Vocab.recordIri(it)) }
        return builder.build()
    }

    fun watering(id: String, lineId: String?, zoneId: String?, date: LocalDate, notes: String): List<Triple> {
        val iri = Vocab.recordIri(id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "Watering")
            .date(iri, Vocab.DWC + "eventDate", date.toString())
            .iri(iri, Vocab.PP + "line", lineId?.let(Vocab::recordIri))
            .iri(iri, Vocab.PP + "zone", zoneId?.let(Vocab::recordIri))
            .text(iri, Vocab.SCHEMA + "description", notes.ifBlank { null })
            .build()
    }
}
