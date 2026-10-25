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
    entities = [ZoneEntity::class, PlantEntity::class, LinkEntity::class, SearchEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PollenplotDatabase : RoomDatabase() {
    abstract fun zoneDao(): ZoneDao
    abstract fun plantDao(): PlantDao
    abstract fun linkDao(): LinkDao
    abstract fun searchDao(): SearchDao

    companion object {
        const val FILE_NAME = "pollenplot.db"

        fun create(context: Context): PollenplotDatabase =
            Room.databaseBuilder(context, PollenplotDatabase::class.java, FILE_NAME).build()

        /** A database in memory, for tests. */
        fun inMemory(context: Context): PollenplotDatabase =
            Room.inMemoryDatabaseBuilder(context, PollenplotDatabase::class.java).allowMainThreadQueries().build()
    }
}
