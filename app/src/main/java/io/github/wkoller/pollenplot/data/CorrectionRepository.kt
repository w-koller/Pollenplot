package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.db.CareCorrectionEntity
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.domain.plants.CareCorrection
import io.github.wkoller.pollenplot.domain.plants.CareCorrections
import io.github.wkoller.pollenplot.domain.plants.CareField
import io.github.wkoller.pollenplot.domain.plants.CareProfile
import io.github.wkoller.pollenplot.domain.semantic.CorrectionGraph
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * The user's corrections to the knowledge base, each for every plant of one species. The knowledge base carries them
 * (see [io.github.wkoller.pollenplot.data.kb.KnowledgeRepository]), so every screen, badge and calendar that resolves
 * a plant's care uses them.
 */
class CorrectionRepository(db: PollenplotDatabase, private val clock: Clock) {
    private val dao = db.careCorrectionDao()

    /** The corrections as care profiles with only the corrected facts set, by scientific name. */
    val profiles: Flow<Map<String, CareProfile>> = dao.observeAll().map { rows ->
        rows.groupBy { it.speciesName }.mapValues { (_, list) ->
            CareCorrections.decode(list.mapNotNull { row -> field(row.field)?.let { it to row.valueJson } }.toMap())
        }
    }

    /**
     * Corrects [field] for every plant of [speciesName] to its value in [care]. A [care] with no value for the field
     * clears the correction.
     */
    suspend fun set(speciesName: String, field: CareField, care: CareProfile) {
        val value = CareCorrections.encode(field, care) ?: return clear(speciesName, field)
        val id = dao.get(speciesName, field.name)?.id ?: UUID.randomUUID().toString()
        dao.upsert(CareCorrectionEntity(id, speciesName, field.name, value, clock.millis()))
    }

    /** Goes back to the knowledge base for [field] of [speciesName]. */
    suspend fun clear(speciesName: String, field: CareField) = dao.delete(speciesName, field.name)

    /** Every correction as the gardener's statement about a taxon. */
    suspend fun triples(): List<Triple> = dao.all().flatMap { row ->
        field(row.field)?.let { CorrectionGraph.correction(CareCorrection(row.id, row.speciesName, it, row.valueJson, Instant.ofEpochMilli(row.updatedAt))) }.orEmpty()
    }

    private fun field(name: String) = CareField.entries.firstOrNull { it.name == name }
}
