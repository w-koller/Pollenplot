package io.github.wkoller.pollenplot

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.feeding.Npk
import io.github.wkoller.pollenplot.domain.feeding.ProductKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.feeding.FeedScreen
import io.github.wkoller.pollenplot.ui.feeding.OwnProductScreen
import io.github.wkoller.pollenplot.ui.plot.ZoneDetailScreen
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

/**
 * Feeding: a feed recorded for zones with a product from the list, the warning before too much phosphorus goes on
 * Proteaceae, a zone's last feed, and citrus due a feed on the Today card and in the morning notification. The clock is
 * fixed at 28 October 2026, a feeding month for citrus.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class FeedFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val feeds get() = app.container.feeds
    private val today: LocalDate get() = LocalDate.now(app.container.clock)

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun seed(): DemoGarden.Ids = runBlocking {
        app.container.knowledge.await()
        app.container.settingsStore.update { it.copy(gardenState = AuState.QLD, setupDone = true) }
        DemoGarden.seed(app.container.garden)
    }

    @Test
    fun aFeedForTheCitrusEndsItsReminder() {
        val ids = seed()
        val before = runBlocking { app.container.today.state.first { state -> state.items.any { it is TodayItem.Feed } } }
        val due = before.items.filterIsInstance<TodayItem.Feed>().single()
        assertEquals(ids.citrus, due.zoneId)
        assertTrue("The lemon wants a feed in October", due.plants.contains("Mum's lemon"))

        show(ids.citrus)
        waitFor(hasText("No feed recorded for this zone yet."))
        compose.onNodeWithText("Record a feed").performScrollTo().performClick()
        waitFor(hasText("Seasol PowerFeed All Purpose"))
        compose.onNodeWithText("Seasol PowerFeed All Purpose").performClick()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("2 capfuls in 9 L")
        shot("feed_form")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Fed today · PowerFeed All Purpose"))
        shot("zone_fed")

        val feed = runBlocking { feeds.feeds.first { it.isNotEmpty() } }.single()
        assertEquals(setOf(ids.citrus), feed.zoneIds)
        assertEquals("2 capfuls in 9 L", feed.rateText)
        runBlocking { app.container.today.state.first { state -> state.items.none { it is TodayItem.Feed } } }
    }

    @Test
    fun tooMuchPhosphorusOnAGrevilleaAsksFirst() {
        val ids = seed()
        val product = runBlocking { feeds.addProduct("Rose food", "Garden shed", ProductKind.SLOW_RELEASE, Npk(800, 800, 800)) }
        show(ids.front, startOnFeed = true)
        waitFor(hasText("Garden shed Rose food"))
        compose.onNodeWithText("Garden shed Rose food").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Too much phosphorus?"))
        compose.onNodeWithText("Grevillea Robyn Gordon", substring = true).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue("Cancelling records nothing", runBlocking { feeds.feeds.first() }.isEmpty())

        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Record anyway"))
        compose.onNodeWithText("Record anyway").performClick()
        assertEquals(product.key, runBlocking { feeds.feeds.first { it.isNotEmpty() } }.single().productKey)
    }

    @Test
    fun aProductOfYourOwn() {
        seed()
        compose.setContent {
            PollenplotTheme(Settings(theme = ThemeChoice.DARK)) { Surface { OwnProductScreen(feeds) {} } }
        }
        waitFor(hasText("Phosphorus (P), per cent"))
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Tomato food")
        fields[2].performTextInput("12.5")
        waitFor(hasText("Please enter all three as per cent from 0 to 100, or none."))
        fields[3].performTextInput("2")
        fields[4].performTextInput("10")
        shot("own_product_dark")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        val own = runBlocking { feeds.products.first { list -> list.any { it.own } } }.single { it.own }
        assertEquals(Npk(1_250, 200, 1_000), own.npk)
    }

    @Test
    fun theMorningNotificationTellsOnce() {
        seed()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runBlocking { app.container.feedViews.due.first { it.isNotEmpty() } }
        runBlocking { app.container.feedPlan.run() }
        assertEquals(1, shadowOf(manager).allNotifications.size)
        manager.cancelAll()
        runBlocking { app.container.feedPlan.run() }
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    /** A zone's page, which opens Record a feed and Your product in its place. */
    private fun show(zoneId: String, startOnFeed: Boolean = false) {
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    var screen by remember { mutableStateOf(if (startOnFeed) "feed" else "zone") }
                    when (screen) {
                        "feed" -> FeedScreen(zoneId, feeds, app.container.views, today, onAddProduct = { screen = "product" }) { screen = "zone" }
                        "product" -> OwnProductScreen(feeds) { screen = "feed" }
                        else -> ZoneDetailScreen(
                            views = app.container.views,
                            zoneId = zoneId,
                            onBack = {},
                            onEdit = {},
                            onOpenPlant = {},
                            onAddPlant = {},
                            feeding = app.container.feedViews,
                            onRecordFeed = { screen = "feed" },
                        )
                    }
                }
            }
        }
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
