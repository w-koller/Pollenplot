package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.CalendarKind
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.calendar.ReminderMode
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** The calendar of every reminder, with recorded weather, on the fixed spring morning of Wednesday 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WaterTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class CalendarFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: WaterTestApp get() = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 10, 28)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            val place = GardenPlace.at(-27.4987, 152.9934, "Taringa")
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, gardenPlace = place, setupDone = true) }
            DemoGarden.seed(app.container.garden)
            app.container.bees.addHive("Hive 1", HiveType.LANGSTROTH_8, null, null, 2025, today)
            val bin = app.container.compost.addBin("Back bin")
            val batch = app.container.compost.startBatch(bin.id, today.minusDays(30))
            app.container.compost.filled(batch.id, today.minusDays(10))
            app.container.water.refreshWeather(place)
        }
    }

    @Test
    fun theCalendarHoldsJobsFromAcrossTheGarden() {
        val items = runBlocking { app.container.calendar.items(today.plusMonths(2)).first { it.isNotEmpty() } }
        val kinds = items.map { it.kind }.toSet()
        assertTrue("Feeding, hives, watering and compost are all there: $kinds", kinds.containsAll(listOf(CalendarKind.FEED, CalendarKind.HIVE, CalendarKind.WATER, CalendarKind.COMPOST)))
        assertTrue("The compost turn is four days ahead", items.any { it.kind == CalendarKind.COMPOST && it.date == today.plusDays(4) })
        assertTrue("Nothing is before today", items.none { it.date.isBefore(today) })
    }

    @Test
    fun aDayOpensWithItsJobsAndAJobOpensItsPage() {
        show()
        compose.pressWhenShown(hasContentDescription("Open the calendar"))
        waitFor(hasText("October 2026"))
        // Today says how many jobs it has in words, not only with its outline.
        waitFor(hasContentDescription("Today. ", substring = true))
        shot("calendar_month")
        compose.pressWhenShown(hasContentDescription("Today. ", substring = true))
        waitFor(hasText("Please inspect Hive 1."))
        waitFor(hasText("Honey bee hives"))
        shot("calendar_day")
        compose.pressWhenShown(hasText("Please inspect Hive 1."))
        waitFor(hasText("Hive 1"))
    }

    @Test
    fun theToDoListGathersTheWeek() {
        show()
        compose.pressWhenShown(hasContentDescription("Open the calendar"))
        compose.pressWhenShown(hasText("To-do list"))
        scrollTo(hasText("Sunday 1 November 2026"))
        scrollTo(hasText("Please turn Back bin."))
        shot("calendar_todo")
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.pressWhenShown(hasText("This month"))
        // The rest of October: the hive's inspection, and not the turn on 1 November.
        scrollTo(hasText("Please inspect Hive 1."))
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText("Sunday 1 November 2026")).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aListScheduleOpensTheCalendarOnItsList() {
        runBlocking { app.container.settingsStore.update { it.copy(reminderMode = ReminderMode.MONTHLY_LIST) } }
        show(Settings(gardenState = AuState.QLD, theme = ThemeChoice.DARK))
        compose.pressWhenShown(hasContentDescription("Open the calendar"))
        waitFor(hasText("This month"))
        scrollTo(hasText("Please inspect Hive 1."))
        shot("calendar_todo_dark")
    }

    @Test
    fun remindersCanWaitForChosenDays() {
        show()
        compose.pressWhenShown(hasContentDescription("Settings"))
        scrollTo(hasText("Weekends"))
        compose.pressWhenShown(hasText("Weekends"))
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.settingsStore.settings.first().reminderMode == ReminderMode.WEEKENDS } }
        waitFor(hasText("Pollenplot gathers the jobs that can wait for Saturday or Sunday morning."))
        compose.pressWhenShown(hasText("Chosen days"))
        compose.pressWhenShown(hasText("Tue"))
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.settingsStore.settings.first().reminderDays.isNotEmpty() } }
        shot("settings_reminder_days")
    }

    private fun show(settings: Settings = Settings(gardenState = AuState.QLD)) {
        compose.setContent { PollenplotTheme(settings) { Surface { AppRoot(app.container) } } }
        waitFor(hasContentDescription("More"))
    }

    /** Scrolls the screen's list until [matcher] is in it, as a lazy list composes only what is on the screen. */
    private fun scrollTo(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(WAIT_MS) { runCatching { compose.onNode(hasScrollAction()).performScrollToNode(matcher) }.isSuccess }
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
