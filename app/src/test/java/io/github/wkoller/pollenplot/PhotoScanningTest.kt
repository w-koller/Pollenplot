package io.github.wkoller.pollenplot

import android.Manifest
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.TestPhotos.bottlebrush
import io.github.wkoller.pollenplot.TestPhotos.rosemary
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.LabelScore
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.scan.AddPhotosWorker
import io.github.wkoller.pollenplot.scan.GalleryAccess
import io.github.wkoller.pollenplot.scan.LibraryScanWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant

/** Gallery scans and added photos, with painted photos and a stand-in for the models. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class)
class PhotoScanningTest {
    private val app: TestApp get() = ApplicationProvider.getApplicationContext()
    private val garden get() = app.container.garden
    private val now = Instant.now()

    private val bedPhoto = TestPhotos.gardenPhoto(listOf(bottlebrush, rosemary))
    private val lawnPhoto = TestPhotos.gardenPhoto(emptyList())

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        // The bed shows a bottlebrush and rosemary; anything else shows no plant.
        app.identifier.regionsFor = { photo ->
            if (photo === bedPhoto) {
                listOf(
                    TestPhotos.region(bottlebrush, flowering = true, LabelScore("Callistemon citrinus", 7_400)),
                    TestPhotos.region(rosemary, flowering = false, LabelScore("Rosmarinus officinalis", 4_300)),
                )
            } else {
                emptyList()
            }
        }
        runBlocking {
            app.container.knowledge.await()
            app.container.settingsStore.update { it.copy(scanLibrary = true, scanSince = now.toEpochMilli(), scanBackDays = 30) }
        }
    }

    private fun scan(): ListenableWorker.Result = runBlocking { TestListenableWorkerBuilder<LibraryScanWorker>(app).build().doWork() }

    private fun keptPhotos(): Int = File(app.filesDir, "photos").listFiles()?.size ?: 0

    private fun notificationTitles(): List<String> = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications
        .map { it.extras.getString("android.title").orEmpty() }

    @Test
    fun `a scan keeps the photos that show plants and remembers every photo it looked at`() {
        app.gallery.add(1, now.minus(Duration.ofDays(2)), bedPhoto)
        app.gallery.add(2, now.minus(Duration.ofDays(1)), lawnPhoto)
        app.gallery.add(3, now.minus(Duration.ofDays(60)), bedPhoto)
        app.gallery.add(4, now.minus(Duration.ofDays(1)), bedPhoto, albumId = "downloads")

        assertEquals(ListenableWorker.Result.success(), scan())

        assertEquals("Two plants from the bed photo wait for review", 2, runBlocking { garden.pendingReviewCount.first() })
        assertEquals("Only the photo with plants is kept", 1, keptPhotos())
        assertEquals(
            "Photos older than the window and outside the camera album are left alone",
            listOf(3L, 4L),
            runBlocking { garden.unscanned(listOf(1, 2, 3, 4)) },
        )
        assertEquals(listOf("Pollenplot found 2 plants in your photos."), notificationTitles())

        // A second scan finds nothing new, so it adds nothing and says nothing.
        scan()
        assertEquals(2, runBlocking { garden.pendingReviewCount.first() })
        assertEquals(1, notificationTitles().size)
    }

    @Test
    fun `chosen albums and a longer window are scanned`() {
        app.gallery.add(3, now.minus(Duration.ofDays(60)), bedPhoto)
        app.gallery.add(4, now.minus(Duration.ofDays(1)), lawnPhoto, albumId = "downloads")
        runBlocking { app.container.settingsStore.update { it.copy(scanAlbums = setOf(FakeGallery.CAMERA, "downloads"), scanBackDays = 90) } }

        scan()

        assertEquals(emptyList<Long>(), runBlocking { garden.unscanned(listOf(3, 4)) })
        assertEquals(2, runBlocking { garden.pendingReviewCount.first() })
    }

    @Test
    fun `nothing is scanned while scanning is off or the gallery is out of reach`() {
        app.gallery.add(1, now, bedPhoto)
        runBlocking { app.container.settingsStore.update { it.copy(scanLibrary = false) } }
        scan()
        app.gallery.access = GalleryAccess.NONE
        runBlocking { app.container.settingsStore.update { it.copy(scanLibrary = true) } }
        scan()

        assertEquals(listOf(1L), runBlocking { garden.unscanned(listOf(1)) })
        assertEquals(0, keptPhotos())
    }

    @Test
    fun `added photos are looked at, and a scan does not look at them again`() {
        val bed = app.gallery.add(1, now.minus(Duration.ofDays(3)), bedPhoto)
        val lawn = app.gallery.add(2, now.minus(Duration.ofDays(3)), lawnPhoto)
        val input = AddPhotosWorker.input(listOf(bed.uri, lawn.uri))

        val result = runBlocking { TestListenableWorkerBuilder<AddPhotosWorker>(app).setInputData(input).build().doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(2, runBlocking { garden.pendingReviewCount.first() })
        assertEquals(listOf("Pollenplot found 2 plants in your photos."), notificationTitles())
        assertTrue("Both added photos count as scanned", runBlocking { garden.unscanned(listOf(1, 2)) }.isEmpty())
        scan()
        assertEquals("The scan added nothing twice", 2, runBlocking { garden.pendingReviewCount.first() })
    }

    @Test
    fun `added photos with no plants say so`() {
        val lawn = app.gallery.add(2, now, lawnPhoto)
        runBlocking {
            TestListenableWorkerBuilder<AddPhotosWorker>(app).setInputData(AddPhotosWorker.input(listOf(lawn.uri))).build().doWork()
        }
        assertEquals(listOf("No plant was found in the photo you added."), notificationTitles())
    }

    @Test
    fun `a photo the user says shows a bird is looked at as a bird without the accurate pack`() {
        val birdPhoto = TestPhotos.gardenPhoto(listOf(rosemary))
        app.identifier.animalFor = { photo, group ->
            listOf(FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, mapOf(IdSource.AIY_BIRDS to listOf(LabelScore("Manorina melanocephala", 8_000))), group))
        }
        val bird = app.gallery.add(3, now, birdPhoto)
        runBlocking {
            app.container.knowledge.awaitFauna()
            TestListenableWorkerBuilder<AddPhotosWorker>(app).setInputData(AddPhotosWorker.input(listOf(bird.uri), LifeGroup.BIRD)).build().doWork()
        }
        assertEquals(listOf(LifeGroup.BIRD), app.identifier.asked)
        assertEquals(listOf("Pollenplot found 1 animal in your photos."), notificationTitles())
    }

    @Test
    fun `a photo added as a plant is looked at as a plant even when the pack sees a bird`() {
        val photo = TestPhotos.gardenPhoto(listOf(rosemary))
        app.identifier.groupFor = { LifeGroup.BIRD }
        val added = app.gallery.add(3, now, photo)
        runBlocking {
            TestListenableWorkerBuilder<AddPhotosWorker>(app).setInputData(AddPhotosWorker.input(listOf(added.uri), LifeGroup.PLANT)).build().doWork()
        }
        assertEquals(listOf(LifeGroup.PLANT), app.identifier.asked)
    }

    @Test
    fun `wildlife photos with no animals say so`() {
        val lawn = app.gallery.add(2, now, lawnPhoto)
        runBlocking {
            TestListenableWorkerBuilder<AddPhotosWorker>(app).setInputData(AddPhotosWorker.input(listOf(lawn.uri), LifeGroup.FROG)).build().doWork()
        }
        assertEquals(listOf("No animal was found in the photo you added."), notificationTitles())
    }

    @Test
    fun `an added photo of a bird is looked at as a bird`() {
        val birdPhoto = TestPhotos.gardenPhoto(listOf(rosemary))
        app.identifier.groupFor = { photo -> if (photo === birdPhoto) LifeGroup.BIRD else LifeGroup.PLANT }
        app.identifier.animalFor = { photo, group ->
            listOf(FastPlantIdentifier.region(photo, FastPlantIdentifier.CENTRE, mapOf(IdSource.BIOCAP to listOf(LabelScore("Manorina melanocephala", 8_000))), group))
        }
        val bird = app.gallery.add(3, now, birdPhoto)
        runBlocking {
            app.container.knowledge.awaitFauna()
            TestListenableWorkerBuilder<AddPhotosWorker>(app).setInputData(AddPhotosWorker.input(listOf(bird.uri))).build().doWork()
        }
        assertEquals(listOf("Pollenplot found 1 animal in your photos."), notificationTitles())
        assertEquals(listOf(LifeGroup.BIRD), app.identifier.asked)
        val photoId = runBlocking { garden.pendingReviewPhotos.first().single() }
        val detection = runBlocking { garden.detectionsInPhoto(photoId).first().single() }
        assertEquals(LifeGroup.BIRD, detection.group)
        assertEquals("Manorina melanocephala", detection.candidates.first().speciesName)
    }
}
