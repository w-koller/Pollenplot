package io.github.wkoller.pollenplot

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.backup.BackupSeal
import io.github.wkoller.pollenplot.domain.place.GardenPlace
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
import java.io.FileInputStream
import java.io.FileOutputStream

/** Saving a backup of the demo yard with a password and restoring it, on the fixed morning of 28 October 2026. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class BackupFlowTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val file get() = File(app.cacheDir, "chosen.ppbackup")
    private val uri = Uri.parse("content://io.github.wkoller.pollenplot.test.documents/chosen")

    /** Stands in for the system's file picker: the backup is saved to one file, and the same file is chosen to restore. */
    private val picker = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, uri as O)
            }
        }
    }

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app.contentResolver).registerOutputStreamSupplier(uri) { FileOutputStream(file) }
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { FileInputStream(file) }
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, gardenPlace = GardenPlace.at(-27.4987, 152.9934, "Taringa")) }
            DemoGarden.seed(app.container.garden)
        }
    }

    private fun show(settings: Settings = Settings()) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                PollenplotTheme(settings) { Surface { AppRoot(app.container) } }
            }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.scrollTo(hasText("Backup and restore"))
        compose.onNodeWithText("Backup and restore").performClick()
        waitFor(hasText("Choose a backup file"))
    }

    private fun field(label: String): SemanticsNodeInteraction = compose.onNode(hasSetTextAction() and hasText(label))

    private fun button(text: String): SemanticsNodeInteraction = compose.onNode(hasClickAction() and hasText(text))

    @Test
    fun savingABackupWithAPasswordAndRestoringIt() {
        show()
        shot("backup")
        field("Password (optional)").performTextInput("wollemi pine")
        field("Type the password again").performTextInput("wollemi")
        waitFor(hasText("The two passwords are not the same. Please type them again."))
        button("Save a backup").assertIsNotEnabled()
        field("Type the password again").performTextInput(" pine")
        button("Save a backup").assertIsEnabled().performClick()
        waitFor(hasText("The backup was saved."))
        assertTrue(BackupSeal.isSealed(file.readBytes()))
        compose.onNodeWithText("The last backup was saved on 28 October 2026.").assertExists()

        // The garden changes after the backup.
        runBlocking { app.container.garden.deleteZone(app.container.garden.zoneList.first().single { it.name == "Veg Patch" }.id) }
        compose.scrollTo(hasText("Choose a backup file"))
        button("Choose a backup file").performClick()
        waitFor(hasText("This backup is encrypted. Please enter its password."))
        field("Password").performTextInput("wollemi")
        button("Open the backup").performClick()
        waitFor(hasText("The password does not open this backup. Please check it and try again."))
        field("Password").performTextReplacement("wollemi pine")
        button("Open the backup").performClick()
        waitFor(hasText("Backup saved on 28 October 2026"))
        compose.onNodeWithText("It holds 4 zones and 15 plants.").assertExists()
        compose.scrollTo(hasText("Restore"))
        shot("backup_found")
        button("Restore").performClick()
        waitFor(hasText("Replace the records on this phone?"))
        compose.waitForIdle()
        save(compose.onNode(isDialog()).captureToImage().asAndroidBitmap(), "backup_confirm")
        button("Replace").performClick()
        waitFor(hasText("The backup was restored."))
        assertEquals(4, runBlocking { app.container.garden.zoneList.first().size })
    }

    @Test
    fun backupScreenInDarkTheme() {
        runBlocking { app.container.settingsStore.update { it.copy(lastBackupAt = DemoWildlife.clock.millis()) } }
        show(Settings(theme = ThemeChoice.DARK))
        compose.onNodeWithText("The last backup was saved on 28 October 2026.").assertExists()
        shot("backup_dark")
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        save(compose.onRoot().captureToImage().asAndroidBitmap(), name)
    }

    private fun save(bitmap: Bitmap, name: String) {
        val dir = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
