package io.github.wkoller.pollenplot.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A place where honey bee hives are kept, with the state's beekeeper registration number for it. */
@Entity(tableName = "apiaries")
data class ApiaryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val registration: String?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "honey_hives",
    foreignKeys = [
        ForeignKey(entity = ApiaryEntity::class, parentColumns = ["id"], childColumns = ["apiaryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("apiaryId"), Index("zoneId")],
)
data class HoneyHiveEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val apiaryId: String?,
    val zoneId: String?,
    val broodBoxes: Int,
    val supers: Int,
    val queenYear: Int?,
    val status: String,
    val startedOnEpochDay: Long?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A record that belongs to one hive and goes with it when the hive is deleted. */
private const val HIVE = "hiveId"

@Entity(
    tableName = "honey_inspections",
    foreignKeys = [ForeignKey(entity = HoneyHiveEntity::class, parentColumns = ["id"], childColumns = [HIVE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HIVE)],
)
data class InspectionEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val dateEpochDay: Long,
    val queenSeen: Boolean,
    val eggsSeen: Boolean,
    val brood: String?,
    val temperament: String?,
    val framesOfBees: Int?,
    val framesOfStores: Int?,
    val queenCells: String,
    /** What was seen that may need looking into, by name, separated by commas. */
    val signs: String,
    val actions: String,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "varroa_checks",
    foreignKeys = [ForeignKey(entity = HoneyHiveEntity::class, parentColumns = ["id"], childColumns = [HIVE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HIVE)],
)
data class VarroaCheckEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val dateEpochDay: Long,
    val method: String,
    val sample: Int,
    val mites: Int,
    val reportedOnEpochDay: Long?,
    val createdAt: Long,
)

@Entity(
    tableName = "treatments",
    foreignKeys = [ForeignKey(entity = HoneyHiveEntity::class, parentColumns = ["id"], childColumns = [HIVE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HIVE)],
)
data class TreatmentEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val product: String,
    val activeIngredient: String,
    val startedEpochDay: Long,
    val endedEpochDay: Long?,
    val supersRemoved: Boolean,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "feedings",
    foreignKeys = [ForeignKey(entity = HoneyHiveEntity::class, parentColumns = ["id"], childColumns = [HIVE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HIVE)],
)
data class FeedingEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val dateEpochDay: Long,
    val kind: String,
    /** Millilitres of syrup, or grams of a substitute or fondant. */
    val amount: Int,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "harvests",
    foreignKeys = [ForeignKey(entity = HoneyHiveEntity::class, parentColumns = ["id"], childColumns = [HIVE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HIVE)],
)
data class HarvestEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val dateEpochDay: Long,
    val frames: Int,
    val honeyGrams: Int,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "colony_events",
    foreignKeys = [ForeignKey(entity = HoneyHiveEntity::class, parentColumns = ["id"], childColumns = [HIVE], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HIVE)],
)
data class ColonyEventEntity(
    @PrimaryKey val id: String,
    val hiveId: String,
    val dateEpochDay: Long,
    val kind: String,
    val notes: String,
    val createdAt: Long,
)
