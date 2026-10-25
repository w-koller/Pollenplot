package io.github.wkoller.pollenplot.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The user's records. Every record has a UUID key, which is also its IRI in the semantic layer, and measurements are
 * whole numbers with the unit in the column name. Enums are stored by name.
 */

@Entity(tableName = "zones")
data class ZoneEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val xCm: Int,
    val yCm: Int,
    val widthCm: Int,
    val heightCm: Int,
    val sun: String?,
    val soil: String?,
    val mulched: Boolean,
    val measuredAreaCm2: Long?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "plants",
    foreignKeys = [ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("zoneId"), Index("speciesName")],
)
data class PlantEntity(
    @PrimaryKey val id: String,
    val speciesName: String?,
    val customName: String?,
    val zoneId: String?,
    val quantity: Int,
    val plantedOnEpochDay: Long?,
    val status: String,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A relation between two records that neither record holds itself, with where it came from. */
@Entity(tableName = "links", primaryKeys = ["subjectId", "predicate", "objectId"], indices = [Index("objectId")])
data class LinkEntity(
    val subjectId: String,
    val predicate: String,
    val objectId: String,
    val provenance: String,
    val confidenceBp: Int,
    val createdAt: Long,
)

/** The full-text search index: one row per record, rebuilt whenever the record or anything it mentions changes. */
@Fts4(notIndexed = ["recordId", "kind"])
@Entity(tableName = "search")
data class SearchEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val recordId: String,
    val kind: String,
    val title: String,
    val body: String,
)

data class SearchHit(val recordId: String, val kind: String, val title: String, val body: String)
