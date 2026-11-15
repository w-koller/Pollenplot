package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * A plant the user keeps in their own library: one for each species, or for each name of their own. Every plant
 * added to the garden is entered here, so it can be added again to other zones without being looked up.
 */
@Entity(tableName = "library_plants", indices = [Index("speciesName")])
data class LibraryPlantEntity(
    @PrimaryKey val id: String,
    val speciesName: String?,
    val customName: String?,
    /** A cut-out of the plant from one of the user's photos, relative to the app's files folder. */
    val thumbnail: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_plants ORDER BY createdAt")
    fun observeAll(): Flow<List<LibraryPlantEntity>>

    @Query("SELECT * FROM library_plants WHERE speciesName = :speciesName LIMIT 1")
    suspend fun bySpecies(speciesName: String): LibraryPlantEntity?

    @Query("SELECT * FROM library_plants WHERE speciesName IS NULL AND customName = :customName LIMIT 1")
    suspend fun byName(customName: String): LibraryPlantEntity?

    @Upsert
    suspend fun upsert(plant: LibraryPlantEntity)

    @Query("DELETE FROM library_plants WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * Enters a plant in the library unless it is there already, and gives an entry without a picture this [thumbnail].
 * A plant of a known species is kept by its species; one the user named is kept by that name.
 */
suspend fun LibraryDao.remember(speciesName: String?, customName: String?, thumbnail: String?, time: Long) {
    val name = customName?.trim()?.ifBlank { null }
    val existing = if (speciesName != null) bySpecies(speciesName) else name?.let { byName(it) }
    when {
        existing == null && (speciesName != null || name != null) ->
            upsert(LibraryPlantEntity(UUID.randomUUID().toString(), speciesName, name.takeIf { speciesName == null }, thumbnail, time, time))
        existing != null && existing.thumbnail == null && thumbnail != null -> upsert(existing.copy(thumbnail = thumbnail, updatedAt = time))
    }
}
