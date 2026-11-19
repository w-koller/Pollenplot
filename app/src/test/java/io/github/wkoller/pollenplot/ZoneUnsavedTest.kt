package io.github.wkoller.pollenplot

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Leaving a zone's edit screen with changes not saved asks first, from the top bar's Back and the back gesture alike. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ZoneUnsavedTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: PollenplotApp get() = ApplicationProvider.getApplicationContext()

    private fun openEditor(): DemoGarden.Ids {
        val ids = runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden)
        }
        compose.setContent { PollenplotTheme(Settings(gardenState = AuState.QLD)) { Surface { AppRoot(app.container) } } }
        compose.pressWhenShown(hasContentDescription("Show as a list"))
        waitFor(hasText("Hive Corner"))
        compose.onNodeWithText("Hive Corner").performClick()
        compose.pressWhenShown(hasContentDescription("Edit zone"))
        waitFor(field("Hive Corner"))
        return ids
    }

    private fun field(text: String) = hasText(text) and hasSetTextAction()

    private fun rename(to: String) {
        compose.onNode(field("Hive Corner")).performTextReplacement(to)
        // The back gesture's handler follows the form, so the change must reach the screen first.
        compose.waitForIdle()
    }

    private fun gestureBack() {
        @Suppress("UNCHECKED_CAST")
        val activity = (compose as AndroidComposeTestRule<*, ComponentActivity>).activity
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun zoneName(id: String) = runBlocking { app.container.garden.zone(id)?.name }

    @Test
    fun backWithoutChangesLeavesAtOnce() {
        openEditor()
        compose.onNodeWithContentDescription("Back").performClick()
        // Back on the zone's page, whose Edit button is there again.
        waitFor(hasContentDescription("Edit zone"))
    }

    @Test
    fun dontSaveLeavesTheZoneAsItWas() {
        val ids = openEditor()
        rename("Bee Corner")
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasText("Save your changes?"))
        compose.onNodeWithText("Don't save").performClick()
        waitFor(hasContentDescription("Edit zone"))
        assertEquals("Hive Corner", zoneName(ids.hive))
    }

    @Test
    fun theBackGestureAsksAndSaveKeepsTheChanges() {
        val ids = openEditor()
        rename("Bee Corner")
        gestureBack()
        waitFor(hasText("Save your changes?"))
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(WAIT_MS) { zoneName(ids.hive) == "Bee Corner" }
        waitFor(hasContentDescription("Edit zone"))
    }
}
