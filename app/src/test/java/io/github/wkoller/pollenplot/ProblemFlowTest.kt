package io.github.wkoller.pollenplot

import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.core.app.ActivityOptionsCompat
import io.github.wkoller.pollenplot.domain.problems.DiseaseCall
import io.github.wkoller.pollenplot.domain.problems.ProblemCatalogue
import io.github.wkoller.pollenplot.ml.PlantProblemModel
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.problems.TreatmentMethod
import io.github.wkoller.pollenplot.ui.plants.PlantDetailScreen
import io.github.wkoller.pollenplot.ui.problems.LogProblemScreen
import io.github.wkoller.pollenplot.ui.problems.ProblemScreen
import io.github.wkoller.pollenplot.ui.problems.TreatmentScreen
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
 * Problems on plants: one logged from the catalogue, a treatment that warns before a spray while plants nearby are in
 * flower, the follow-up on the Today card and in the morning notification, and search. The clock is fixed at 28
 * October 2026, when the lemon is in flower.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU-w411dp-h891dp-xxhdpi")
class ProblemFlowTest {
    // The classic rule runs coroutines at once, so flows from the database reach the screen while the test waits.
    @Suppress("DEPRECATION")
    @get:Rule
    val compose = createComposeRule()

    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val problems get() = app.container.problems
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
    fun aProblemIsLoggedAndASprayWarnsWhilePlantsFlower() {
        val ids = seed()
        show(ids.lemon)
        waitFor(hasText("Light"))
        compose.scrollTo(hasText("Record a problem"))
        compose.onNodeWithText("Record a problem").performClick()
        waitFor(hasText("What you saw"))
        compose.onNodeWithText("Aphids").performClick()
        shot("problem_log")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Light"))
        compose.scrollTo(hasText("Aphids"))
        compose.onNodeWithText("Open").assertExists()

        compose.onNodeWithText("Aphids").performClick()
        waitFor(hasText("Record a treatment"))
        compose.onNodeWithText("Source: NSW Department of Primary Industries", substring = true).assertExists()
        shot("problem")
        compose.onNodeWithText("Record a treatment").performScrollTo().performClick()
        waitFor(hasText("What you did"))
        compose.onNodeWithText("Soap spray").performClick()
        shot("treatment")
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor(hasText("Plants are in flower"))
        compose.onNodeWithText("Mum's lemon", substring = true).assertExists()
        compose.onNodeWithText("Record anyway").performClick()
        waitFor(hasText("Soap spray on 28 October 2026"))
        compose.onNodeWithText("Look again on 4 November 2026").assertExists()

        val treatment = runBlocking { problems.treatments.first { it.isNotEmpty() } }.single()
        assertEquals(TreatmentMethod.SOAP_SPRAY, treatment.method)
        assertEquals(today.plusDays(7), treatment.followUpOn)
    }

    /** Stands in for the plant problems pack: it answers every photo with [shares], through the real call's rules. */
    private class FakeProblemModel(var shares: Map<String, Int>) : PlantProblemModel {
        var looked = 0

        override suspend fun look(photo: Bitmap, catalogue: ProblemCatalogue): DiseaseCall? {
            looked++
            return DiseaseCall.of(shares, yesBp = 7_000, maybeBp = 1_000, catalogue)
        }
    }

    /** Hands back a photo whenever the screen opens the photo picker. */
    private val picker = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                @Suppress("UNCHECKED_CAST")
                dispatchResult(requestCode, Uri.parse("content://media/external/images/media/7") as O)
            }
        }
    }

    @Test
    fun aPhotoOffersTheProblemsItLooksLikeAndTheUserChooses() {
        val ids = seed()
        val model = FakeProblemModel(mapOf("healthy" to 500, "powdery-mildew" to 7_600, "downy-mildew" to 1_400, "botrytis" to 500))
        val photo = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                    Surface {
                        LogProblemScreen(
                            ids.lemon,
                            app.container.catalogue,
                            problems,
                            today,
                            savePhoto = { "photos/leaf.jpg" },
                            lookForProblems = { model.look(photo, app.container.catalogue) },
                        ) {}
                    }
                }
            }
        }
        waitFor(hasText("What you saw"))
        compose.onNodeWithText("Add a photo").performScrollTo().performClick()
        waitFor(hasText("Looks like"))
        compose.onNodeWithText("Powdery mildew, likely").assertExists()
        compose.onNodeWithText("Downy mildew, possible").assertExists()
        assertEquals("A share below the floor is not offered", 1, compose.onAllNodesWithText(", possible", substring = true).fetchSemanticsNodes().size)
        shot("problem_looks_like")
        compose.onNodeWithText("Powdery mildew, likely").performScrollTo().performClick()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        val saved = runBlocking { problems.problems.first { it.isNotEmpty() } }.single()
        assertEquals("The user's choice is saved, not the model's", "powdery-mildew", saved.problemKey)
        assertEquals("photos/leaf.jpg", saved.photo)
        assertEquals(1, model.looked)
    }

    @Test
    fun aHealthyPhotoSaysNoProblemFound() {
        val ids = seed()
        val model = FakeProblemModel(mapOf("healthy" to 9_100, "powdery-mildew" to 900))
        val photo = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides picker) {
                PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                    Surface {
                        LogProblemScreen(ids.lemon, app.container.catalogue, problems, today, savePhoto = { "photos/leaf.jpg" }, lookForProblems = { model.look(photo, app.container.catalogue) }) {}
                    }
                }
            }
        }
        waitFor(hasText("What you saw"))
        compose.onNodeWithText("Add a photo").performScrollTo().performClick()
        waitFor(hasText("No problem found", substring = true))
        compose.onNodeWithText("Looks like").assertDoesNotExist()
        compose.onNodeWithText("Save").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun aFollowUpReachesTodayTheNotificationAndSearch() {
        val ids = seed()
        val problem = runBlocking { problems.add(ids.lemon, "citrus-leafminer", null, today.minusDays(10), null, "") }
        runBlocking { problems.addTreatment(problem.id, TreatmentMethod.WHITE_OIL, "Eco-Oil", today.minusDays(7), today, "Under the leaves.") }
        val items = runBlocking { app.container.today.state.first { state -> state.items.any { it is TodayItem.ProblemFollowUp } } }.items
        assertEquals("Citrus leafminer", items.filterIsInstance<TodayItem.ProblemFollowUp>().single().problem)
        assertTrue(runBlocking { app.container.garden.search("eco oil") }.any { it.recordId == problem.id && it.kind == "PROBLEM" })

        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runBlocking { app.container.problemCheck.run() }
        assertEquals(1, shadowOf(manager).allNotifications.size)
        manager.cancelAll()
        runBlocking { app.container.problemCheck.run() }
        assertEquals("Told once while it stays due", 0, shadowOf(manager).allNotifications.size)

        // Resolving it ends the follow-up, and removing the plant takes the problem out of search.
        runBlocking { problems.setResolved(problem.id, today) }
        runBlocking { app.container.problemViews.due.first { it.isEmpty() } }
        runBlocking { app.container.garden.deletePlant(ids.lemon) }
        assertTrue(runBlocking { app.container.garden.search("eco oil") }.none { it.recordId == problem.id })
        assertTrue(runBlocking { problems.problems.first() }.isEmpty())
    }

    @Test
    fun aProblemInTheDark() {
        val ids = seed()
        val problem = runBlocking { problems.add(ids.grevillea, null, "Galls on the stems", today.minusDays(3), null, "Small swellings near the tips.") }
        compose.setContent {
            PollenplotTheme(Settings(theme = ThemeChoice.DARK)) {
                Surface { ProblemScreen(problem.id, app.container.problemViews, problems, onBack = {}, onRecordTreatment = {}) }
            }
        }
        waitFor(hasText("Galls on the stems"))
        compose.onAllNodesWithText("No treatments recorded yet.").onFirst().assertExists()
        shot("problem_dark")
    }

    /** A plant's page, which opens the problem screens in its place. */
    private fun show(plantId: String) {
        compose.setContent {
            PollenplotTheme(Settings(gardenState = AuState.QLD)) {
                Surface {
                    var screen by remember { mutableStateOf("plant") }
                    var problemId by remember { mutableStateOf("") }
                    when (screen) {
                        "log" -> LogProblemScreen(plantId, app.container.catalogue, problems, today, savePhoto = { null }) { screen = "plant" }
                        "problem" -> ProblemScreen(problemId, app.container.problemViews, problems, onBack = { screen = "plant" }, onRecordTreatment = { screen = "treat" })
                        "treat" -> TreatmentScreen(problemId, plantId, app.container.problemViews, problems, today) { screen = "problem" }
                        else -> PlantDetailScreen(
                            views = app.container.views,
                            garden = app.container.garden,
                            knowledge = app.container.knowledge,
                            plantId = plantId,
                            onBack = {},
                            onEditFacts = {},
                            today = today,
                            problems = app.container.problemViews,
                            onOpenProblem = {
                                problemId = it
                                screen = "problem"
                            },
                            onLogProblem = { screen = "log" },
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
