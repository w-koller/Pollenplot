package io.github.wkoller.pollenplot.domain.semantic

import io.github.wkoller.pollenplot.domain.bees.honey.Apiary
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEvent
import io.github.wkoller.pollenplot.domain.bees.honey.FeedKind
import io.github.wkoller.pollenplot.domain.bees.honey.Feeding
import io.github.wkoller.pollenplot.domain.bees.honey.Harvest
import io.github.wkoller.pollenplot.domain.bees.honey.HoneyHive
import io.github.wkoller.pollenplot.domain.bees.honey.Inspection
import io.github.wkoller.pollenplot.domain.bees.honey.Treatment
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaCheck
import java.math.BigDecimal

/**
 * Honey bee records as linked data: apiaries as schema.org places, hives as colonies of *Apis mellifera* in their zone
 * and apiary, mite checks as SOSA observations of the hive, and everything else done to a hive as a schema.org event
 * about it. Together they are the records the Biosecurity Code of Practice asks a beekeeper to keep.
 */
object HoneyGraph {
    fun apiary(apiary: Apiary): List<Triple> {
        val iri = Vocab.recordIri(apiary.id)
        return GraphBuilder()
            .type(iri, Vocab.SCHEMA + "Place")
            .type(iri, Vocab.PP + "Apiary")
            .text(iri, Vocab.SCHEMA + "name", apiary.name)
            .text(iri, Vocab.PP + "registrationNumber", apiary.registration)
            .text(iri, Vocab.SCHEMA + "description", apiary.notes)
            .build()
    }

    fun hive(hive: HoneyHive): List<Triple> {
        val iri = Vocab.recordIri(hive.id)
        return GraphBuilder()
            .type(iri, Vocab.PP + "HoneyBeeHive")
            .text(iri, Vocab.SCHEMA + "name", hive.name)
            .text(iri, Vocab.DWC + "scientificName", "Apis mellifera")
            .text(iri, Vocab.PP + "hiveType", words(hive.type.name))
            .text(iri, Vocab.PP + "status", words(hive.status.name))
            .integer(iri, Vocab.PP + "broodBoxes", hive.broodBoxes.toLong())
            .integer(iri, Vocab.PP + "honeySupers", hive.supers.toLong())
            .integer(iri, Vocab.PP + "queenYear", hive.queenYear?.toLong())
            .text(iri, Vocab.PP + "queenColour", hive.queenColour?.name?.lowercase())
            .date(iri, Vocab.PP + "startedOn", hive.startedOn?.toString())
            .iri(iri, Vocab.SCHEMA + "containedInPlace", hive.zoneId?.let(Vocab::recordIri))
            .iri(iri, Vocab.PP + "apiary", hive.apiaryId?.let(Vocab::recordIri))
            .text(iri, Vocab.SCHEMA + "description", hive.notes)
            .build()
    }

    fun inspection(inspection: Inspection): List<Triple> {
        val iri = Vocab.recordIri(inspection.id)
        val builder = event(iri, "HiveInspection", inspection.hiveId, inspection.date.toString(), inspection.notes)
            .boolean(iri, Vocab.PP + "queenSeen", inspection.queenSeen)
            .boolean(iri, Vocab.PP + "eggsSeen", inspection.eggsSeen)
            .text(iri, Vocab.PP + "broodPattern", inspection.brood?.name?.lowercase())
            .text(iri, Vocab.PP + "temperament", inspection.temperament?.name?.lowercase())
            .integer(iri, Vocab.PP + "framesOfBees", inspection.framesOfBees?.toLong())
            .integer(iri, Vocab.PP + "framesOfStores", inspection.framesOfStores?.toLong())
            .text(iri, Vocab.PP + "queenCells", inspection.queenCells.name.lowercase())
            .text(iri, Vocab.PP + "actions", inspection.actions)
        inspection.signs.sortedBy { it.ordinal }.forEach { builder.iri(iri, Vocab.PP + "sign", Vocab.PP + term(it.name)) }
        return builder.build()
    }

    /** A mite check, observing the hive's mite load by its method, with the count and the rate worked out from it. */
    fun check(check: VarroaCheck): List<Triple> {
        val iri = Vocab.recordIri(check.id)
        return GraphBuilder()
            .type(iri, Vocab.SOSA + "Observation")
            .type(iri, Vocab.PP + "MiteCheck")
            .iri(iri, Vocab.SOSA + "hasFeatureOfInterest", Vocab.recordIri(check.hiveId))
            .iri(iri, Vocab.SOSA + "observedProperty", Vocab.PP + "MiteLoad")
            .iri(iri, Vocab.SOSA + "usedProcedure", Vocab.PP + term(check.method.name))
            .date(iri, Vocab.SCHEMA + "startDate", check.date.toString())
            .integer(iri, Vocab.PP + "sampleSize", check.sample.toLong())
            .integer(iri, Vocab.PP + "mitesCounted", check.mites.toLong())
            .decimal(iri, Vocab.PP + "mitesPer100Bees", check.mitesPer100BeesTenths?.let(::tenths))
            .decimal(iri, Vocab.PP + "mitesPerDay", check.mitesPerDayTenths?.let(::tenths))
            .date(iri, Vocab.PP + "reportedOn", check.reportedOn?.toString())
            .build()
    }

    fun treatment(treatment: Treatment): List<Triple> {
        val iri = Vocab.recordIri(treatment.id)
        return event(iri, "HiveTreatment", treatment.hiveId, treatment.started.toString(), treatment.notes)
            .date(iri, Vocab.SCHEMA + "endDate", treatment.ended?.toString())
            .text(iri, Vocab.PP + "product", treatment.product)
            .text(iri, Vocab.PP + "activeIngredient", treatment.activeIngredient)
            .boolean(iri, Vocab.PP + "supersRemoved", treatment.supersRemoved)
            .build()
    }

    /** A feeding, its amount in millilitres for syrup and in grams for anything else. */
    fun feeding(feeding: Feeding): List<Triple> {
        val iri = Vocab.recordIri(feeding.id)
        val syrup = feeding.kind == FeedKind.SYRUP_1_TO_1 || feeding.kind == FeedKind.SYRUP_2_TO_1
        return event(iri, "HiveFeeding", feeding.hiveId, feeding.date.toString(), feeding.notes)
            .text(iri, Vocab.PP + "feed", feeding.kind.name.lowercase())
            .integer(iri, Vocab.PP + if (syrup) "amountMillilitres" else "amountGrams", feeding.amount.toLong().takeIf { it > 0 })
            .build()
    }

    fun harvest(harvest: Harvest): List<Triple> {
        val iri = Vocab.recordIri(harvest.id)
        return event(iri, "HoneyHarvest", harvest.hiveId, harvest.date.toString(), harvest.notes)
            .integer(iri, Vocab.PP + "frames", harvest.frames.toLong().takeIf { it > 0 })
            .integer(iri, Vocab.PP + "honeyGrams", harvest.honeyGrams.toLong())
            .build()
    }

    fun event(event: ColonyEvent): List<Triple> {
        val iri = Vocab.recordIri(event.id)
        return event(iri, "ColonyEvent", event.hiveId, event.date.toString(), event.notes)
            .text(iri, Vocab.PP + "eventKind", event.kind.name.lowercase())
            .build()
    }

    /** Something done to a hive on a day, as a schema.org event about the hive. */
    private fun event(iri: String, type: String, hiveId: String, date: String, notes: String) = GraphBuilder()
        .type(iri, Vocab.SCHEMA + "Event")
        .type(iri, Vocab.PP + type)
        .iri(iri, Vocab.SCHEMA + "about", Vocab.recordIri(hiveId))
        .date(iri, Vocab.SCHEMA + "startDate", date)
        .text(iri, Vocab.SCHEMA + "description", notes)

    /** A name such as SUNKEN_CAPPINGS as a term, SunkenCappings. */
    private fun term(name: String): String = name.lowercase().split('_').joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun words(name: String): String = name.lowercase().replace('_', ' ')

    private fun tenths(value: Int): String = BigDecimal(value).movePointLeft(1).stripTrailingZeros().toPlainString()
}
