package io.github.wkoller.pollenplot.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A bee and insect hotel in a zone of the garden. */
@Entity(
    tableName = "hotels",
    foreignKeys = [ForeignKey(entity = ZoneEntity::class, parentColumns = ["id"], childColumns = ["zoneId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("zoneId")],
)
data class HotelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val zoneId: String?,
    val facing: String?,
    val heightMm: Int?,
    val installedOnEpochDay: Long?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A hotel's records, which go with it when it is deleted. */
private const val HOTEL = "hotelId"

/** A part of a hotel with holes of one kind, in the order the keeper listed them. */
@Entity(
    tableName = "hotel_sections",
    foreignKeys = [ForeignKey(entity = HotelEntity::class, parentColumns = ["id"], childColumns = [HOTEL], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HOTEL)],
)
data class HotelSectionEntity(
    @PrimaryKey val id: String,
    val hotelId: String,
    val position: Int,
    val material: String,
    val holeMm: Int?,
    val depthMm: Int?,
    val holes: Int,
)

/** A look at a hotel. [sealed] is written as SEAL=count pairs, such as "LEAF=3,RESIN=2"; [pests] as names. */
@Entity(
    tableName = "hotel_observations",
    foreignKeys = [ForeignKey(entity = HotelEntity::class, parentColumns = ["id"], childColumns = [HOTEL], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HOTEL)],
)
data class HotelObservationEntity(
    @PrimaryKey val id: String,
    val hotelId: String,
    val dateEpochDay: Long,
    val sealed: String,
    val busy: Int?,
    val emerged: Int?,
    val pests: String,
    val notes: String,
    val createdAt: Long,
)

@Entity(
    tableName = "hotel_upkeep",
    foreignKeys = [ForeignKey(entity = HotelEntity::class, parentColumns = ["id"], childColumns = [HOTEL], onDelete = ForeignKey.CASCADE)],
    indices = [Index(HOTEL)],
)
data class HotelUpkeepEntity(
    @PrimaryKey val id: String,
    val hotelId: String,
    val dateEpochDay: Long,
    val kind: String,
    val notes: String,
    val createdAt: Long,
)
