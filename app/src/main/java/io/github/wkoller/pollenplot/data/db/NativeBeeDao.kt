package io.github.wkoller.pollenplot.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The native bee records: stingless bee hives and what happens at each. */
@Dao
interface NativeBeeDao {
    @Query("SELECT * FROM native_hives ORDER BY name")
    fun observeHives(): Flow<List<NativeHiveEntity>>

    @Query("SELECT * FROM native_hives")
    suspend fun hives(): List<NativeHiveEntity>

    @Query("SELECT * FROM native_hives WHERE id = :id")
    suspend fun hive(id: String): NativeHiveEntity?

    @Upsert
    suspend fun upsertHive(hive: NativeHiveEntity)

    @Query("DELETE FROM native_hives WHERE id = :id")
    suspend fun deleteHive(id: String)

    // The row order breaks ties between records saved in the same millisecond, the latest first.
    @Query("SELECT * FROM native_logs ORDER BY dateEpochDay DESC, createdAt DESC, rowid DESC")
    fun observeLogs(): Flow<List<NativeLogEntity>>

    @Upsert
    suspend fun upsertLog(log: NativeLogEntity)

    @Query("DELETE FROM native_logs WHERE id = :id")
    suspend fun deleteLog(id: String)
}
