package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.provider.Settings.Global
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.compost.CompostEventKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** A compost bin through its three steps, its reminders on the Today card and its morning notification. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class CompostFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val compost get() = app.container.compost
    private val today = LocalDate.of(2026, 10, 28)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        // The bin's picture stays still with the phone's animations off, so the screen can settle under test.
        Global.putFloat(app.contentResolver, Global.ANIMATOR_DURATION_SCALE, 0f)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun aBinIsFilledThenWaitsAndIsTurned() {
        show()
        compose.fromMore("Compost")
        waitFor(hasText("No compost bins yet"))
        compose.pressWhenShown(hasContentDescription("Add a compost bin"))
        waitFor(hasText("Empty and ready for a new fill"))
        compose.pressWhenShown(hasText("Start a fill"))
        waitFor(hasText("Filling"))
        // The steps are told in words, not only by colour.
        waitFor(hasContentDescription("Fill, now"))
        shot("compost_filling")

        compose.pressWhenShown(hasText("Full, nothing more to add"))
        waitFor(hasText("Warming up"))
        waitFor(hasContentDescription("Fill, done"))
        waitFor(hasText("Next turn: 11 November 2026"))
        compose.pressWhenShown(hasText("Turned today"))
        compose.waitUntil(WAIT_MS) { runBlocking { compost.events.first() }.any { it.kind == CompostEventKind.TURNED } }
        shot("compost_waiting")
    }

    @Test
    fun aBinDueATurnIsOnTheTodayCardAndInTheMorningsNotification() {
        runBlocking {
            val bin = compost.addBin("Back bin")
            val batch = compost.startBatch(bin.id, today.minusDays(40))
            compost.filled(batch.id, today.minusDays(20))
        }
        show()
        waitFor(hasText("Please turn Back bin."))
        compose.pressWhenShown(hasContentDescription("Record Back bin as turned today"))
        compose.waitUntil(WAIT_MS) { runBlocking { compost.events.first() }.any { it.kind == CompostEventKind.TURNED } }

        // Marked turned today, the next turn is two weeks away, so the morning has nothing more to say.
        runBlocking { app.container.compostCheck.run() }
        val manager = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
        assertTrue(manager.allNotifications.none { it.extras.getString("android.title")?.contains("compost") == true })
    }

    @Test
    fun theMorningNotificationTellsOfABinThatMayBeReady() {
        runBlocking {
            val bin = compost.addBin("Front bin")
            val batch = compost.startBatch(bin.id, today.minusDays(90))
            compost.filled(batch.id, today.minusDays(60))
            compost.addEvent(batch.id, CompostEventKind.TURNED, today.minusDays(5))
            app.container.compostCheck.run()
        }
        val manager = shadowOf(app.getSystemService(android.app.NotificationManager::class.java))
        val shown = manager.allNotifications.single { it.extras.getCharSequence("android.title").toString() == "1 compost job" }
        assertEquals("Front bin may be ready to empty.", shown.extras.getCharSequence("android.text").toString())
        assertEquals("pot_care", shown.channelId)
        // Told once: the next morning says nothing new.
        runBlocking { assertEquals(1, app.container.settingsStore.settings.first().compostTasksTold.size) }
    }

    @Test
    fun aBinInTheDark() {
        runBlocking {
            val bin = compost.addBin("Back bin")
            val batch = compost.startBatch(bin.id, today.minusDays(30))
            compost.filled(batch.id, today.minusDays(10))
            compost.addEvent(batch.id, CompostEventKind.TEMPERATURE, today, tenthsC = 585)
        }
        show(Settings(gardenState = AuState.QLD, theme = ThemeChoice.DARK))
        compose.fromMore("Compost")
        compose.pressWhenShown(hasText("Back bin"))
        waitFor(hasText("Hot"))
        waitFor(hasText("58.5 °C in the middle, 28 October 2026"))
        shot("compost_hot_dark")
    }

    private fun show(settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent { PollenplotTheme(settings) { Surface { AppRoot(app.container) } } }
        waitFor(hasContentDescription("More"))
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
