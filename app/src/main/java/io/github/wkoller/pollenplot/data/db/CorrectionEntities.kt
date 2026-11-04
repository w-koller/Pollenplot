package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * A fact the user corrected for every plant of a species, one row for each species and fact. The value is written as
 * the knowledge base writes that field (see `CareCorrections`).
 */
@Entity(tableName = "care_corrections", indices = [Index(value = ["speciesName", "field"], unique = true)])
data class CareCorrectionEntity(
    @PrimaryKey val id: String,
    val speciesName: String,
    /** A `CareField`, such as WATER. */
    val field: String,
    val valueJson: String,
    val updatedAt: Long,
)

@Dao
interface CareCorrectionDao {
    @Query("SELECT * FROM care_corrections")
    fun observeAll(): Flow<List<CareCorrectionEntity>>

    @Query("SELECT * FROM care_corrections")
    suspend fun all(): List<CareCorrectionEntity>

    @Query("SELECT * FROM care_corrections WHERE speciesName = :speciesName AND field = :field")
    suspend fun get(speciesName: String, field: String): CareCorrectionEntity?

    @Upsert
    suspend fun upsert(correction: CareCorrectionEntity)

    @Query("DELETE FROM care_corrections WHERE speciesName = :speciesName AND field = :field")
    suspend fun delete(speciesName: String, field: String)
}
