package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.SearchEntity
import io.github.wkoller.pollenplot.data.db.WaterFeatureEntity
import io.github.wkoller.pollenplot.domain.garden.WaterFeature
import io.github.wkoller.pollenplot.domain.garden.WaterKind
import io.github.wkoller.pollenplot.domain.semantic.HabitatGraph
import io.github.wkoller.pollenplot.domain.semantic.SearchText
import io.github.wkoller.pollenplot.domain.semantic.Triple
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Water set out for wildlife, kept by zone. Water features are in the garden's search index. */
class HabitatRepository(private val db: PollenplotDatabase, private val clock: Clock) {
    private val dao = db.habitatDao()
    private val search = db.searchDao()
    private val zones = db.zoneDao()

    val water: Flow<List<WaterFeature>> = dao.observeWater().map { list -> list.map { it.toDomain() } }

    suspend fun waterFeature(id: String): WaterFeature? = dao.water(id)?.toDomain()

    suspend fun addWater(zoneId: String, kind: WaterKind, notes: String): WaterFeature {
        val feature = WaterFeature(UUID.randomUUID().toString(), zoneId, kind, notes.trim(), createdAt = clock.instant())
        db.withTransaction { write(feature) }
        return feature
    }

    suspend fun updateWater(feature: WaterFeature) = db.withTransaction {
        if (dao.water(feature.id) == null) return@withTransaction
        write(feature.copy(notes = feature.notes.trim()))
    }

    /** Records that the water was cleaned and filled on [day]. */
    suspend fun cleaned(id: String, day: LocalDate) = db.withTransaction {
        val feature = dao.water(id)?.toDomain() ?: return@withTransaction
        write(feature.copy(cleanedOn = day))
    }

    suspend fun deleteWater(id: String) = db.withTransaction {
        dao.deleteWater(id)
        search.delete(id)
    }

    /** Every water feature as linked data. */
    suspend fun triples(): List<Triple> = dao.allWater().flatMap { HabitatGraph.waterFeature(it.toDomain()) }

    /** Puts every water feature in the search index again, after the garden's index has been rebuilt. */
    suspend fun reindex() = dao.allWater().forEach { index(it.toDomain()) }

    private suspend fun write(feature: WaterFeature) {
        val createdAt = dao.water(feature.id)?.createdAt ?: feature.createdAt.toEpochMilli()
        dao.upsertWater(WaterFeatureEntity(feature.id, feature.zoneId, feature.kind.name, feature.notes, feature.cleanedOn?.toEpochDay(), createdAt, clock.millis()))
        index(feature)
    }

    private suspend fun index(feature: WaterFeature) {
        val doc = SearchText.waterFeature(feature, zones.get(feature.zoneId)?.name)
        search.delete(feature.id)
        search.replace(SearchEntity(recordId = doc.id, kind = doc.kind.name, title = doc.title, body = doc.body))
    }

    private fun WaterFeatureEntity.toDomain() = WaterFeature(
        id = id,
        zoneId = zoneId,
        kind = WaterKind.entries.firstOrNull { it.name == kind } ?: WaterKind.OTHER,
        notes = notes,
        cleanedOn = cleanedOnEpochDay?.let(LocalDate::ofEpochDay),
        createdAt = Instant.ofEpochMilli(createdAt),
    )
}
