package io.github.wkoller.pollenplot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        NativeHiveEntity::class, NativeLogEntity::class,
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

    companion object {
        const val FILE_NAME = "pollenplot.db"

        fun create(context: Context): PollenplotDatabase =
            Room.databaseBuilder(context, PollenplotDatabase::class.java, FILE_NAME).build()

        /** A database in memory, for tests. */
        fun inMemory(context: Context): PollenplotDatabase =
            Room.inMemoryDatabaseBuilder(context, PollenplotDatabase::class.java).allowMainThreadQueries().build()
    }
}
