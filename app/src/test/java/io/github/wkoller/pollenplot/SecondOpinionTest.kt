package io.github.wkoller.pollenplot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.ai.SecondOpinionWorker
import io.github.wkoller.pollenplot.data.DetectionDraft
import io.github.wkoller.pollenplot.data.DetectionRecord
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Gemma 4's second opinions on finds waiting for review, with a stand-in for the model. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = AskTestApp::class)
class SecondOpinionTest {
    private val app: AskTestApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking { container.knowledge.await() }
    }

    private fun run(): ListenableWorker.Result = runBlocking { TestListenableWorkerBuilder<SecondOpinionWorker>(app).build().doWork() }

    /** A photo with an unsure grevillea and a rosemary the models were sure of. */
    private fun record(): List<DetectionRecord> = runBlocking {
        val image = TestPhotos.gardenPhoto()
        val photo = container.photos.savePhoto(image)
        val thumbnail = container.photos.saveThumbnail(image)
        val unsure = DetectionDraft(
            BoxBp(0, 0, 5_000, 5_000), thumbnail, flowering = false,
            listOf(
                Candidate("Grevillea banksii", "Grevillea banksii", 4_500, setOf(IdSource.BIOCAP, IdSource.AIY_PLANTS)),
                Candidate("Grevillea robusta", "Grevillea robusta", 4_000, setOf(IdSource.BIOCAP)),
            ),
        )
        val sure = DetectionDraft(
            BoxBp(5_000, 0, 10_000, 5_000), thumbnail, flowering = false,
            listOf(
                Candidate("Salvia rosmarinus", "Salvia rosmarinus", 8_000, setOf(IdSource.BIOCAP)),
                Candidate("Lavandula dentata", "Lavandula dentata", 900, setOf(IdSource.BIOCAP)),
            ),
        )
        container.garden.recordPhoto(photo, PhotoOrigin.IMPORT, DemoWildlife.clock.instant(), listOf(unsure, sure))
    }

    @Test
    fun anUnsureFindGetsASecondOpinionOnce() {
        val (unsure, sure) = record()
        app.assistant.choose = { names -> names.indexOfFirst { "Grevillea robusta" in it } }
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals("Only the unsure find was asked about", 1, app.assistant.opinions.size)
        assertTrue(app.assistant.opinions.single().toString(), app.assistant.opinions.single().any { "Grevillea robusta" in it })
        val after = runBlocking { container.garden.detection(unsure.id)!! }
        assertEquals("Gemma's vote moved its choice to the top", "Grevillea robusta", after.candidates.first().label)
        assertTrue(IdSource.GEMMA in after.candidates.first().sources)
        assertEquals("The sure find is untouched", sure.candidates, runBlocking { container.garden.detection(sure.id)!! }.candidates)
        run()
        assertEquals("Nothing is asked about twice", 1, app.assistant.opinions.size)
    }

    @Test
    fun noSecondOpinionsWhenTheyAreTurnedOff() {
        record()
        runBlocking { container.settingsStore.update { it.copy(secondOpinions = false) } }
        run()
        assertEquals(0, app.assistant.opinions.size)
    }
}
