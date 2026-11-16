package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import io.github.wkoller.pollenplot.domain.garden.Structure
import io.github.wkoller.pollenplot.domain.garden.StructureKind
import io.github.wkoller.pollenplot.domain.garden.StructureShape
import io.github.wkoller.pollenplot.domain.garden.ZoneRect
import kotlinx.coroutines.flow.Flow

/** Something built in the yard, such as the house or a shed, placed on the Plot in centimetres as zones are. */
@Entity(tableName = "structures")
data class StructureEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** A `StructureKind`, such as HOUSE. */
    val kind: String,
    /** A `StructureShape`: RECTANGLE or OVAL. */
    val shape: String,
    val xCm: Int,
    val yCm: Int,
    val widthCm: Int,
    val heightCm: Int,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface StructureDao {
    @Query("SELECT * FROM structures ORDER BY createdAt")
    fun observeAll(): Flow<List<StructureEntity>>

    @Query("SELECT * FROM structures ORDER BY createdAt")
    suspend fun all(): List<StructureEntity>

    @Query("SELECT * FROM structures WHERE id = :id")
    suspend fun get(id: String): StructureEntity?

    @Upsert
    suspend fun upsert(structure: StructureEntity)

    @Query("DELETE FROM structures WHERE id = :id")
    suspend fun delete(id: String)
}

fun StructureEntity.toDomain(): Structure {
    val kind = StructureKind.entries.firstOrNull { it.name == kind } ?: StructureKind.OTHER
    return Structure(id, name, kind, StructureShape.entries.firstOrNull { it.name == shape } ?: kind.shape, ZoneRect(xCm, yCm, widthCm, heightCm), notes)
}

fun Structure.toEntity(createdAt: Long, updatedAt: Long) =
    StructureEntity(id, name, kind.name, shape.name, rect.xCm, rect.yCm, rect.widthCm, rect.heightCm, notes, createdAt, updatedAt)
