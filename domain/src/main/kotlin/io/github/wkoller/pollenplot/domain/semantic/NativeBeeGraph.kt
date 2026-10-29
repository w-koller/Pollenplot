package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.bees.stingless.NativeHive
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLog
import java.math.BigDecimal

/**
 * Stingless bee hives as linked data: each hive a colony of its species in its zone, and each log a schema.org event
 * about the hive, with its weight, its count of bees coming home or its honey.
 */
object NativeBeeGraph {
    fun hive(hive: NativeHive): List<Triple> {
        val iri = Vocab.recordIri(hive.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "StinglessBeeHive")
            .text(iri, Vocab.SCHEMA + "name", hive.name)
            .text(iri, Vocab.DWC + "scientificName", hive.species.scientificName)
            .text(iri, Vocab.PP + "hiveBox", words(hive.box.name))
            .text(iri, Vocab.PP + "origin", words(hive.origin.name))
            .text(iri, Vocab.PP + "facing", hive.facing?.name)
            .text(iri, Vocab.PP + "status", words(hive.status.name))
            .date(iri, Vocab.PP + "startedOn", hive.startedOn?.toString())
            .iri(iri, Vocab.SCHEMA + "containedInPlace", hive.zoneId?.let(Vocab::recordIri))
            .text(iri, Vocab.SCHEMA + "description", hive.notes)
            .build()
    }

    fun log(log: NativeLog): List<Triple> {
        val iri = Vocab.recordIri(log.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Event")
            .type(iri, Vocab.PP + "NativeHiveLog")
            .iri(iri, Vocab.SCHEMA + "about", Vocab.recordIri(log.hiveId))
            .date(iri, Vocab.SCHEMA + "startDate", log.date.toString())
            .text(iri, Vocab.PP + "logKind", words(log.kind.name))
            .integer(iri, Vocab.PP + "beesPerMinute", log.beesPerMinute?.toLong())
            .decimal(iri, Vocab.PP + "weightKilograms", log.weightGrams?.let { BigDecimal(it).movePointLeft(3).stripTrailingZeros().toPlainString() })
            .integer(iri, Vocab.PP + "honeyMillilitres", log.honeyMl?.toLong())
            .text(iri, Vocab.PP + "pest", log.pest?.name?.lowercase())
            .text(iri, Vocab.PP + "swarm", log.swarm?.name?.lowercase())
            .text(iri, Vocab.SCHEMA + "description", log.notes)
            .build()
    }

    private fun words(name: String): String = name.lowercase().replace('_', ' ')
}
