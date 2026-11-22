package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Forms keep their Save button pressable. A press with something missing says what, next to it, puts the cursor in the
 * first field that needs it, and saves nothing; the message goes as soon as the field is filled in.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h1600dp-xhdpi")
class FormCheckFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun aNewHiveSaysWhatItStillNeeds() {
        show()
        compose.onNodeWithText("Bees").performClick()
        waitFor(hasText("You have no honey bee hives yet."))
        compose.onAllNodesWithText("Add a hive").onFirst().performClick()
        waitFor(hasText("Name"))

        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Please give the hive a name."))
        val name = compose.onNode(hasSetTextAction() and hasText("Name"))
        // A screen reader hears the field's own message, and the cursor is put in it.
        name.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "Please give the hive a name."))
        name.assertIsFocused()
        assertTrue("Nothing was saved", runBlocking { app.container.bees.hives.first() }.isEmpty())
        shot("form_check_hive")

        name.performTextInput("Hive 1")
        waitUntilGone("Please give the hive a name.")

        // A new apiary needs its own name too.
        compose.onNodeWithText("New apiary").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Please give the new apiary a name."))
        assertTrue("Still nothing saved", runBlocking { app.container.bees.hives.first() }.isEmpty())

        compose.onNode(hasSetTextAction() and hasText("Apiary name")).performTextInput("Back fence")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.bees.hives.first() }.isNotEmpty() }
        assertEquals("Hive 1", runBlocking { app.container.bees.hives.first() }.single().name)
    }

    @Test
    fun aWateringLineSaysEverythingItNeeds() {
        show()
        compose.onNodeWithText("Water").performClick()
        compose.pressWhenShown(hasContentDescription("Add a watering line"))
        waitFor(hasText("Name"))

        compose.onNodeWithText("Save").performScrollTo().performClick()
        // Every problem shows at once, so one press tells the whole story.
        waitFor(hasText("Please give the line a name."))
        waitFor(hasText("Please choose at least one zone this line waters."))
        assertTrue(runBlocking { app.container.water.lines.first() }.isEmpty())

        compose.onNode(hasSetTextAction() and hasText("Name")).performTextInput("Citrus line")
        compose.onNodeWithText("Back Citrus Patch").performClick()
        waitUntilGone("Please choose at least one zone this line waters.")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { app.container.water.lines.first() }.isNotEmpty() }
    }

    @Test
    fun aHotelSectionNeedsItsHoles() {
        show()
        compose.onNodeWithText("Bees").performClick()
        waitFor(hasText("Bee hotels"))
        compose.onNodeWithText("Bee hotels").performClick()
        compose.pressWhenShown(hasText("Add a hotel"))
        waitFor(hasText("Name"))

        compose.onNodeWithText("Add this section").performScrollTo().performClick()
        waitFor(hasText("Please enter how many holes this section has."))
        compose.onNodeWithText("Number of holes").performScrollTo().performTextInput("24")
        compose.onNodeWithText("Add this section").performScrollTo().performClick()
        waitFor(hasText("24 holes", substring = true))
        // The fields are cleared for the next section, which has not been asked for yet.
        compose.onNodeWithText("Please enter how many holes this section has.").assertDoesNotExist()
    }

    private fun show() {
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface { AppRoot(app.container) }
            }
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitUntilGone(text: String) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
