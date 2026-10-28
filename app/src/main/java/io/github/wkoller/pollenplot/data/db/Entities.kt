package io.github.wkoller.pollenplot.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
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
    /** The plant's cut-out picture, relative to the app's files folder. */
    val thumbnail: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A photo the app has kept, downscaled into its own storage so records survive if the original is deleted. */
@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey val id: String,
    /** Relative to the app's files folder. */
    val path: String,
    val width: Int,
    val height: Int,
    val origin: String,
    val takenAt: Long,
    val addedAt: Long,
    val latE7: Int?,
    val lonE7: Int?,
)

/**
 * An animal or fungus seen in the garden. [plantId] is the plant it was on, and [lifeGroup] its group, such as BIRD.
 */
@Entity(
    tableName = "sightings",
    foreignKeys = [
        ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = PhotoEntity::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("zoneId"), Index("plantId"), Index("photoId"), Index("speciesName"), Index("seenAt")],
)
data class SightingEntity(
    @PrimaryKey val id: String,
    val speciesName: String?,
    val label: String,
    val lifeGroup: String,
    val zoneId: String?,
    val plantId: String?,
    val behaviour: String?,
    val count: Int,
    val photoId: String?,
    /** Relative to the app's files folder. */
    val thumbnail: String?,
    val seenAt: Long,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * One part of a photo that seems to show a plant or an animal, with its cut-out and what the models suggested. It
 * waits for review until the user confirms it, as a plant in the garden or a sighting, or says it is neither.
 */
@Entity(
    tableName = "detections",
    foreignKeys = [
        ForeignKey(entity = PhotoEntity::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = SightingEntity::class, parentColumns = ["id"], childColumns = ["sightingId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("photoId"), Index("plantId"), Index("sightingId"), Index("status")],
)
data class DetectionEntity(
    @PrimaryKey val id: String,
    val photoId: String,
    /** The region, in basis points of the photo's width and height. */
    val leftBp: Int,
    val topBp: Int,
    val rightBp: Int,
    val bottomBp: Int,
    val thumbnail: String,
    val flowering: Boolean,
    val status: String,
    val plantId: String?,
    val createdAt: Long,
    /** What it seems to show, such as PLANT or BIRD. */
    val lifeGroup: String = "PLANT",
    /** The sighting it became, for an animal. */
    val sightingId: String? = null,
    /** Gemma 4 has given its second opinion, or the suggestions were sure enough not to need one. */
    val secondOpinion: Boolean = false,
)

/** One suggestion for a detection, in rank order. */
@Entity(
    tableName = "candidates",
    primaryKeys = ["detectionId", "rank"],
    foreignKeys = [ForeignKey(entity = DetectionEntity::class, parentColumns = ["id"], childColumns = ["detectionId"], onDelete = ForeignKey.CASCADE)],
)
data class CandidateEntity(
    val detectionId: String,
    val rank: Int,
    val label: String,
    val speciesName: String?,
    val scoreBp: Int,
    /** The models that suggested it, by name, separated by commas. */
    val sources: String,
    /** A common name from the model, for a label the knowledge base does not have. */
    val common: String? = null,
)

/** A plant seen in flower on a date, from a photo or from the user. Pollen Rhythms compares these with the calendar. */
@Entity(
    tableName = "flowering",
    foreignKeys = [ForeignKey(entity = PlantEntity::class, parentColumns = ["id"], childColumns = ["plantId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("plantId")],
)
data class FloweringEntity(
    @PrimaryKey val id: String,
    val plantId: String,
    val observedOnEpochDay: Long,
    val photoId: String?,
    val source: String,
)

/** A photo in the phone's gallery that a scan has looked at, so it is not looked at again. */
@Entity(tableName = "scanned_media")
data class ScannedMediaEntity(
    /** The photo's ID in the phone's media store. */
    @PrimaryKey val mediaId: Long,
    val takenAt: Long,
    val scannedAt: Long,
    /** PLANTS, NO_PLANT or FAILED. */
    val outcome: String,
    /** The copy the app kept, when the photo showed plants. */
    val photoId: String?,
    /** Whether the accurate pack has looked at it too, which matters most for photos the quick model saw no plant in. */
    val accurate: Boolean = false,
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

/**
 * The full-text search index: one row per record, rebuilt whenever the record or anything it mentions changes. The
 * Porter stemmer lets "weeds" find "weed" and "flowering" find "flowers".
 */
@Fts4(notIndexed = ["recordId", "kind"], tokenizer = FtsOptions.TOKENIZER_PORTER)
@Entity(tableName = "search")
data class SearchEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val recordId: String,
    val kind: String,
    val title: String,
    val body: String,
)

data class SearchHit(val recordId: String, val kind: String, val title: String, val body: String)

/**
 * A record's meaning as a vector from the on-phone text model, for search by meaning. [textHash] is the hash of the
 * text it was made from, so the vector is made again only when the record's text changes.
 */
@Entity(tableName = "embeddings")
data class EmbeddingEntity(
    @PrimaryKey val recordId: String,
    val model: String,
    val textHash: Int,
    /** Little-endian 32-bit floats. */
    val vector: ByteArray,
) {
    override fun equals(other: Any?) = other is EmbeddingEntity && recordId == other.recordId && model == other.model &&
        textHash == other.textHash && vector.contentEquals(other.vector)

    override fun hashCode() = recordId.hashCode()
}
