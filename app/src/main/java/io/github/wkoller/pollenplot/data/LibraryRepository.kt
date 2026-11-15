package io.github.wkoller.pollenplot.data

import androidx.room.withTransaction
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.db.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock

/** A plant in the user's library: a species, or a plant under the user's own name, with a cut-out when there is one. */
data class LibraryPlant(val id: String, val speciesName: String?, val customName: String?, val thumbnail: String?)

/** One plant to add to the garden from the library. */
data class LibraryPick(val speciesName: String?, val customName: String?, val thumbnail: String? = null)

/**
 * The user's own library of plants. [GardenRepository] enters each plant as it is added to the garden; here the
 * library is read, added to by hand and taken from, and several of its plants are added to zones at once.
 */
class LibraryRepository(private val db: PollenplotDatabase, private val garden: GardenRepository, private val clock: Clock) {
    private val dao = db.libraryDao()

    val plants: Flow<List<LibraryPlant>> = dao.observeAll().map { list -> list.map { LibraryPlant(it.id, it.speciesName, it.customName, it.thumbnail) } }

    /** Keeps species in the library without adding them to the garden. */
    suspend fun save(speciesNames: Collection<String>) = db.withTransaction {
        val time = clock.millis()
        speciesNames.forEach { dao.remember(it, null, null, time) }
    }

    suspend fun remove(ids: Collection<String>) = db.withTransaction { ids.forEach { dao.delete(it) } }

    /** Enters the plants already in the garden, for a garden made before the library was. */
    suspend fun takeInGarden() = db.withTransaction {
        val time = clock.millis()
        db.plantDao().all().forEach { dao.remember(it.speciesName, it.customName, it.thumbnail, time) }
    }

    /**
     * Adds each of [picks] to [zoneIds]: one plant in all of them, or with [each] one in every zone, [quantity] in
     * each zone. Returns the plants made, so the whole addition can be undone.
     */
    suspend fun addToZones(picks: List<LibraryPick>, zoneIds: List<String>, each: Boolean, quantity: Int): List<String> = db.withTransaction {
        picks.flatMap { pick -> garden.addPlants(pick.speciesName, pick.customName, zoneIds, each, quantity, thumbnail = pick.thumbnail) }.map { it.id }
    }

    suspend fun undoAdd(plantIds: List<String>) = db.withTransaction { plantIds.forEach { garden.deletePlant(it) } }
}
