package io.github.wkoller.pollenplot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * The user's garden records. It stays at version 1 until the first tagged release; after that every change needs a
 * migration and a schema committed in app/schemas, and there is no destructive fallback.
 */
@Database(
    entities = [
        ZoneEntity::class, PlantEntity::class, PhotoEntity::class, DetectionEntity::class, CandidateEntity::class,
        FloweringEntity::class, ScannedMediaEntity::class, LinkEntity::class, SearchEntity::class, EmbeddingEntity::class,
        SightingEntity::class, CareDraftEntity::class, ApiaryEntity::class, HoneyHiveEntity::class, InspectionEntity::class,
        VarroaCheckEntity::class, TreatmentEntity::class, FeedingEntity::class, HarvestEntity::class, ColonyEventEntity::class,
        NativeHiveEntity::class, NativeLogEntity::class, HotelEntity::class, HotelSectionEntity::class, HotelObservationEntity::class,
        HotelUpkeepEntity::class, WateringLineEntity::class, WateringEventEntity::class, WeatherDayEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PollenplotDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun plantDao(): PlantDao
    abstract fun photoDao(): PhotoDao
    abstract fun detectionDao(): DetectionDao
    abstract fun floweringDao(): FloweringDao
    abstract fun scannedMediaDao(): ScannedMediaDao
    abstract fun embeddingDao(): EmbeddingDao
    abstract fun linkDao(): LinkDao
    abstract fun searchDao(): SearchDao
    abstract fun sightingDao(): SightingDao
    abstract fun careDraftDao(): CareDraftDao
    abstract fun honeyDao(): HoneyDao
    abstract fun nativeBeeDao(): NativeBeeDao
    abstract fun hotelDao(): HotelDao
    abstract fun waterDao(): WaterDao

    companion object {
        const val FILE_NAME = "pollenplot.db"

        /**
         * Every migration since the first tagged release. The app's own database and a restored backup from an older
         * version are both brought up to date with these.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): PollenplotDatabase =
            Room.databaseBuilder(context, PollenplotDatabase::class.java, FILE_NAME).addMigrations(*MIGRATIONS).build()

        /** A database in memory, for tests. */
        fun inMemory(context: Context): PollenplotDatabase =
            Room.inMemoryDatabaseBuilder(context, PollenplotDatabase::class.java).allowMainThreadQueries().build()
    }
}
