package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Search by meaning, with a stand-in for the text model, and Settings › Models. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = MeaningTestApp::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MeaningFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: PollenplotApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD) }
            DemoGarden.seed(app.container.garden)
            app.container.meaning.refreshIndex()
        }
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) { Surface { AppRoot(app.container) } }
        }
    }

    @Test
    fun relatedByMeaning() {
        compose.onNodeWithContentDescription("Search your garden").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("poisonous to dogs")
        waitFor(hasText("Related by meaning"))
        compose.onNodeWithText("Lantana").assertIsDisplayed()
        compose.onNodeWithText("No plant or zone in your garden has the words “poisonous to dogs”.").assertIsDisplayed()
        shot("search_related")
    }

    @Test
    fun theModelIsShownAsOnThePhone() {
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Models").performClick()
        waitFor(hasText("On your phone. Search shows related plants and zones after what your words find."))
        compose.onNodeWithText("Delete").assertIsDisplayed()
        // The accurate pack and the garden assistant can still be downloaded.
        compose.onAllNodesWithText("Download").assertCountEquals(2)
        shot("models_installed")
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(30_000) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: ComposeTimeoutException) {
            println(compose.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
