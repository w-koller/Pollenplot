package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A watering line, with the zones it waters as their keys separated by commas. */
@Entity(tableName = "watering_lines")
data class WateringLineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val method: String,
    val zoneIds: String,
    val flowMlPerHour: Int?,
    val notes: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** A watering someone said they did, for a line or for a zone watered by hand. */
@Entity(tableName = "watering_events")
data class WateringEventEntity(
    @PrimaryKey val id: String,
    val lineId: String?,
    val zoneId: String?,
    val dateEpochDay: Long,
    val notes: String,
    val createdAt: Long,
)

/** A day of weather for the garden, kept so the plan still works without a connection. */
@Entity(tableName = "weather_days")
data class WeatherDayEntity(
    @PrimaryKey val dateEpochDay: Long,
    val et0Um: Int,
    val rainUm: Int,
    val rainChanceBp: Int?,
    val maxTenthsC: Int?,
    val fetchedAt: Long,
)

@Dao
interface WaterDao {
    @Query("SELECT * FROM watering_lines ORDER BY name")
    fun observeLines(): Flow<List<WateringLineEntity>>

    @Query("SELECT * FROM watering_lines WHERE id = :id")
    suspend fun line(id: String): WateringLineEntity?

    @Upsert
    suspend fun upsertLine(line: WateringLineEntity)

    @Query("DELETE FROM watering_lines WHERE id = :id")
    suspend fun deleteLine(id: String)

    @Query("DELETE FROM watering_events WHERE lineId = :lineId")
    suspend fun deleteEventsFor(lineId: String)

    @Query("SELECT * FROM watering_events ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeEvents(): Flow<List<WateringEventEntity>>

    @Upsert
    suspend fun upsertEvent(event: WateringEventEntity)

    @Query("DELETE FROM watering_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("SELECT * FROM weather_days ORDER BY dateEpochDay")
    fun observeWeather(): Flow<List<WeatherDayEntity>>

    @Upsert
    suspend fun upsertWeather(days: List<WeatherDayEntity>)

    @Query("DELETE FROM weather_days WHERE dateEpochDay < :before")
    suspend fun deleteWeatherBefore(before: Long)
}
