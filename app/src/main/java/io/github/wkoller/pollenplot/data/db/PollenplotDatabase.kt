package io.github.wkoller.pollenplot.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.io.File

/**
 * The user's garden records. Version 1 is 1.0.0's tables; the release candidates before it changed them in place.
 * Every change now needs a migration in [MIGRATIONS] and a schema committed in app/schemas, and there is no
 * destructive fallback.
 */
@Database(
    entities = [
        ZoneEntity::class, PlantEntity::class, PlantZoneEntity::class, PhotoEntity::class, DetectionEntity::class, CandidateEntity::class,
        FloweringEntity::class, ScannedMediaEntity::class, SearchEntity::class, EmbeddingEntity::class,
        SightingEntity::class, SightingPlantEntity::class, CareDraftEntity::class, ApiaryEntity::class, HoneyHiveEntity::class, InspectionEntity::class,
        VarroaCheckEntity::class, TreatmentEntity::class, FeedingEntity::class, HarvestEntity::class, ColonyEventEntity::class,
        NativeHiveEntity::class, NativeLogEntity::class, HotelEntity::class, HotelSectionEntity::class, HotelObservationEntity::class,
        HotelUpkeepEntity::class, WateringLineEntity::class, WateringEventEntity::class, WeatherDayEntity::class, WishEntity::class,
        CareCorrectionEntity::class, PlantPotEntity::class, PlantEventEntity::class, SoilProductEntity::class, SoilFeedEntity::class,
        SoilFeedZoneEntity::class, PlantProblemEntity::class, ProblemTreatmentEntity::class, WaterFeatureEntity::class,
        PlantHarvestEntity::class, StructureEntity::class, LibraryPlantEntity::class,
        CompostBinEntity::class, CompostBatchEntity::class, CompostEventEntity::class,
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
    abstract fun searchDao(): SearchDao
    abstract fun sightingDao(): SightingDao
    abstract fun careDraftDao(): CareDraftDao
    abstract fun honeyDao(): HoneyDao
    abstract fun nativeBeeDao(): NativeBeeDao
    abstract fun hotelDao(): HotelDao
    abstract fun waterDao(): WaterDao
    abstract fun wishDao(): WishDao
    abstract fun careCorrectionDao(): CareCorrectionDao
    abstract fun potDao(): PotDao
    abstract fun feedDao(): FeedDao
    abstract fun problemDao(): ProblemDao
    abstract fun habitatDao(): HabitatDao
    abstract fun harvestDao(): HarvestDao
    abstract fun structureDao(): StructureDao
    abstract fun libraryDao(): LibraryDao

    abstract fun compostDao(): CompostDao

    companion object {
        const val FILE_NAME = "pollenplot.db"

        /**
         * Every migration since 1.0.0. The app's own database and a restored backup from an older version are both
         * brought up to date with these.
         */
        val MIGRATIONS: Array<Migration> = emptyArray()

        /**
         * Room's identity hash of 1.0.0's tables, as in app/schemas/…/1.json. A database at version 1 with another
         * hash can only be a release candidate's.
         */
        const val RELEASED_V1_IDENTITY_HASH = "aa31e89c1f6ce9d6e5468cca258ed52d"

        /**
         * The app's database. Android's own backup restores the database of the version backed up when the app is
         * installed again, and that can be a release candidate's, whose tables at version 1 Room refuses. That
         * database is deleted, and [onDiscarded] is called, so the garden starts afresh as it would after a fresh
         * install. Any other older database is brought up to date with [MIGRATIONS].
         */
        fun create(context: Context, onDiscarded: () -> Unit = {}): PollenplotDatabase {
            if (isCandidateDatabase(context.getDatabasePath(FILE_NAME))) {
                context.deleteDatabase(FILE_NAME)
                onDiscarded()
            }
            return Room.databaseBuilder(context, PollenplotDatabase::class.java, FILE_NAME).addMigrations(*MIGRATIONS).build()
        }

        /** Whether [file] is a release candidate's database: version 1, made by Room, with tables other than 1.0.0's. */
        private fun isCandidateDatabase(file: File): Boolean {
            if (!file.exists()) return false
            return try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    if (db.version != 1) return false
                    val hash = db.rawQuery("SELECT identity_hash FROM room_master_table WHERE id = 42", null).use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                    hash != null && hash != RELEASED_V1_IDENTITY_HASH
                }
            } catch (e: SQLiteException) {
                false
            }
        }

        /** A database in memory, for tests. */
        fun inMemory(context: Context): PollenplotDatabase =
            Room.inMemoryDatabaseBuilder(context, PollenplotDatabase::class.java).allowMainThreadQueries().build()
    }
}
