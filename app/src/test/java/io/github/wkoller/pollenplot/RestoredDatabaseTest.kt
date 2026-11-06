package io.github.wkoller.pollenplot

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app finding an earlier release candidate's database where its own goes, as Android's backup leaves it when the
 * app is installed again: version 1, with other tables.
 */
class RestoredCandidateTestApp : PollenplotApp() {
    override fun createContainer(): AppContainer {
        SQLiteDatabase.openOrCreateDatabase(getDatabasePath(PollenplotDatabase.FILE_NAME).apply { parentFile?.mkdirs() }, null).use { db ->
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, 'an earlier candidate')")
            db.execSQL("CREATE TABLE zones (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("INSERT INTO zones VALUES ('a', 'Old Bed')")
            db.version = 1
        }
        return AppContainer(this)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(application = RestoredCandidateTestApp::class)
class RestoredDatabaseTest {
    private val app: RestoredCandidateTestApp get() = ApplicationProvider.getApplicationContext()

    @Test
    fun anEarlierCandidatesDatabaseStartsTheGardenAfresh() = runBlocking {
        assertTrue("Debug builds of a candidate are candidates", PollenplotDatabase.RELEASE_CANDIDATE)
        assertTrue("The garden opens, empty", app.container.garden.zoneList.first().isEmpty())

        // The settings restored with it say the garden was set up, in Taringa.
        app.container.settingsStore.update {
            it.copy(gardenPlace = GardenPlace.at(-27.49, 152.98, "Taringa", "BRISBANE CITY"), gardenState = AuState.QLD, setupDone = true)
        }
        assertTrue("Setup opens, as after a fresh install", app.container.setupNeeded())
        val settings = app.container.settingsStore.current()
        assertFalse(settings.setupDone)
        assertNull(settings.gardenPlace)
        assertNull(settings.gardenState)
    }
}
