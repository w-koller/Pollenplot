package io.github.wkoller.pollenplot.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A colony of stingless bees in a box, in a zone of the garden. */
@Entity(
    tableName = "native_hives",
    foreignKeys = [ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("zoneId")],
)
data class NativeHiveEntity(
    @PrimaryKey val id: String,
    val name: String,
    val species: String,
    val box: String,
    val origin: String,
    val zoneId: String?,
    val facing: String?,
    val status: String,
    val startedOnEpochDay: Long?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Something seen or done at a native hive on a day, which goes with the hive when it is deleted. */
@Entity(
    tableName = "native_logs",
    foreignKeys = [ForeignKey(entity = NativeHiveEntity::class, parentColumns = ["id"], childColumns = ["hiveId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("hiveId")],
)
data class NativeLogEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val dateEpochDay: Long,
    val kind: String,
    val beesPerMinute: Int?,
    val weightGrams: Int?,
    val honeyMl: Int?,
    val pest: String?,
    val swarm: String?,
    val notes: String,
    val createdAt: Long,
)
