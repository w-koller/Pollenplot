package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun get(id: String): ZoneEntity?

    @Upsert
    suspend fun upsert(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface PlantDao {
    @Query("SELECT * FROM plants ORDER BY createdAt")
    fun observeAll(): Flow<List<PlantEntity>>

    @Query("SELECT * FROM plants ORDER BY createdAt")
    suspend fun all(): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE zoneId = :zoneId")
    suspend fun inZone(zoneId: String): List<PlantEntity>

    @Query("SELECT * FROM plants WHERE id = :id")
    suspend fun get(id: String): PlantEntity?

    @Upsert
    suspend fun upsert(plant: PlantEntity)

    @Query("DELETE FROM plants WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links")
    suspend fun all(): List<LinkEntity>

    @Upsert
    suspend fun upsert(link: LinkEntity)

    @Query("DELETE FROM links WHERE subjectId = :id OR objectId = :id")
    suspend fun deleteFor(id: String)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM search WHERE recordId = :recordId")
    suspend fun delete(recordId: String)

    @Insert
    suspend fun insert(entry: SearchEntity)

    @Transaction
    suspend fun replace(entry: SearchEntity) {
        delete(entry.recordId)
        insert(entry)
    }

    @Query("DELETE FROM search")
    suspend fun clear()

    /** [match] is an FTS4 query such as `lemon* tree*`. */
    @Query("SELECT recordId, kind, title, body FROM search WHERE search MATCH :match LIMIT :limit")
    suspend fun search(match: String, limit: Int): List<SearchHit>
}
