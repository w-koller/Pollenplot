package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Bee hotels, their sections, what was seen at them and their upkeep. */
@Dao
interface HotelDao {
    @Query("SELECT * FROM hotels ORDER BY name")
    fun observeHotels(): Flow<List<HotelEntity>>

    @Query("SELECT * FROM hotels")
    suspend fun hotels(): List<HotelEntity>

    @Query("SELECT * FROM hotels WHERE id = :id")
    suspend fun hotel(id: String): HotelEntity?

    @Upsert
    suspend fun upsertHotel(hotel: HotelEntity)

    @Query("DELETE FROM hotels WHERE id = :id")
    suspend fun deleteHotel(id: String)

    @Query("SELECT * FROM hotel_sections ORDER BY hotelId, position")
    fun observeSections(): Flow<List<HotelSectionEntity>>

    @Query("SELECT * FROM hotel_sections WHERE hotelId = :hotelId ORDER BY position")
    suspend fun sections(hotelId: String): List<HotelSectionEntity>

    @Query("DELETE FROM hotel_sections WHERE hotelId = :hotelId")
    suspend fun deleteSections(hotelId: String)

    @Insert
    suspend fun insertSections(sections: List<HotelSectionEntity>)

    // The row order breaks ties between records saved in the same millisecond, the latest first.
    @Query("SELECT * FROM hotel_observations ORDER BY dateEpochDay DESC, createdAt DESC, rowid DESC")
    fun observeObservations(): Flow<List<HotelObservationEntity>>

    @Upsert
    suspend fun upsertObservation(observation: HotelObservationEntity)

    @Query("DELETE FROM hotel_observations WHERE id = :id")
    suspend fun deleteObservation(id: String)

    @Query("SELECT * FROM hotel_upkeep ORDER BY dateEpochDay DESC, createdAt DESC, rowid DESC")
    fun observeUpkeep(): Flow<List<HotelUpkeepEntity>>

    @Upsert
    suspend fun upsertUpkeep(upkeep: HotelUpkeepEntity)

    @Query("DELETE FROM hotel_upkeep WHERE id = :id")
    suspend fun deleteUpkeep(id: String)
}
