package io.github.wkoller.pollenplot

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.report.ReportPart
import io.github.wkoller.pollenplot.ui.settings.ExportScreen
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Sharing a report: it is written into the cache and handed to the share sheet through the app's file provider. The
 * spreadsheet is shared here, because Robolectric cannot draw a PDF document.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU")
class ShareReportTest {
    // The classic rule runs coroutines at once, so the report is written while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
            DemoGarden.seed(app.container.garden)
        }
    }

    @Test
    fun theBeesReportIsSharedAsASpreadsheet() {
        compose.setContent {
            PollenplotTheme(Settings()) {
                Surface {
                    ExportScreen(
                        today = app.container.beeViews.today(),
                        report = { parts, from, to, pdf, pictures, out -> app.container.writeReport(parts, from, to, pdf, out, pictures) },
                        jsonLd = { "" },
                        darwinCore = { "" },
                        onBack = {},
                        share = app.container::reportForSharing,
                        initialParts = setOf(ReportPart.HONEY, ReportPart.TASKS),
                    )
                }
            }
        }
        compose.scrollTo(hasText("Share as a spreadsheet"))
        compose.pressWhenShown(hasText("Share as a spreadsheet"))
        compose.waitUntil(WAIT_MS) {
            shadowOf(app).peekNextStartedActivity() != null || compose.onAllNodes(hasText("The report could not be made", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue("The report was made", compose.onAllNodes(hasText("The report could not be made", substring = true)).fetchSemanticsNodes().isEmpty())
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", send.type)
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        assertEquals("${app.packageName}.files", uri.authority)
        assertTrue("Only reading is granted", send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val file = File(app.cacheDir, "reports").listFiles()!!.single()
        assertTrue(file.name.endsWith(".xlsx") && file.length() > 0)
    }
}
