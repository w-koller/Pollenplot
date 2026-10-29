package io.github.wkoller.pollenplot

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.wkoller.pollenplot.backup.BackupRead
import io.github.wkoller.pollenplot.data.DetectionDraft
import io.github.wkoller.pollenplot.domain.backup.BackupArchive
import io.github.wkoller.pollenplot.domain.backup.BackupSeal
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies
import io.github.wkoller.pollenplot.domain.hotels.BeeHotel
import io.github.wkoller.pollenplot.domain.hotels.HotelMaterial
import io.github.wkoller.pollenplot.domain.hotels.HotelSection
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.id.IdSource
import io.github.wkoller.pollenplot.domain.id.PhotoOrigin
import io.github.wkoller.pollenplot.domain.irrigation.WaterMethod
import io.github.wkoller.pollenplot.domain.irrigation.WateringLine
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backups of the demo yard, made and restored on the fixed spring morning of 28 October 2026 in Brisbane. */
@RunWith(AndroidJUnit4::class)
@Config(application = WildlifeTestApp::class, qualifiers = "en-rAU")
class BackupTest {
    private val app: WildlifeTestApp get() = ApplicationProvider.getApplicationContext()
    private val container get() = app.container
    private val taringa = GardenPlace.at(-27.4987, 152.9934, "Taringa", "BRISBANE CITY")

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking {
            container.knowledge.await()
            container.knowledge.awaitFauna()
            container.settingsStore.update { it.copy(gardenState = AuState.QLD, gardenPlace = taringa, inspectEveryDays = 10) }
        }
    }

    /** The demo yard, with a photo whose grevillea was confirmed and whose bee waits for review, and a record of each kind. */
    private suspend fun seed(): List<String> {
        val ids = DemoGarden.seed(container.garden)
        val photo = container.photos.savePhoto(TestPhotos.gardenPhoto())
        val grevilleaCut = container.photos.saveThumbnail(TestPhotos.paintedPlant(TestPhotos.bottlebrush, 256))
        val beeCut = container.photos.saveThumbnail(TestPhotos.paintedPlant(TestPhotos.rosemary, 256))
        val found = container.garden.recordPhoto(
            photo, PhotoOrigin.CAMERA, DemoWildlife.clock.instant(),
            listOf(
                DetectionDraft(BoxBp(0, 0, 5_000, 5_000), grevilleaCut, true, listOf(Candidate("Grevillea", "Grevillea banksii", 7_000, setOf(IdSource.AIY_PLANTS)))),
                DetectionDraft(BoxBp(5_000, 5_000, 9_000, 9_000), beeCut, false, listOf(Candidate("Apis mellifera", "Apis mellifera", 6_000, setOf(IdSource.AIY_INSECTS))), LifeGroup.INSECT),
            ),
        )
        container.garden.confirmDetection(found[0].id, "Grevillea banksii", "Photo grevillea", ids.front, flowering = true)
        container.garden.recordSighting("Dacelo novaeguineae", "Laughing kookaburra", LifeGroup.BIRD, ids.front, null, Behaviour.CALLING, 2, DemoWildlife.clock.instant(), "")
        container.bees.addHive("Hive 1", HiveType.LANGSTROTH_8, ids.hive, null, 2025, null)
        container.nativeBees.addHive("Sugarbag", StinglessSpecies.entries.first(), NativeBox.OATH, NativeOrigin.BOUGHT, ids.hive, null, null)
        container.hotels.saveHotel(BeeHotel(container.hotels.newRecordId(), "Fence hotel", ids.hive, null, null, null, sections = listOf(HotelSection(HotelMaterial.BAMBOO, null, null, 12))))
        container.water.saveLine(WateringLine(container.water.newId(), "Citrus drip", WaterMethod.entries.first(), setOf(ids.citrus), 8_000))
        return listOf(photo.path, grevilleaCut, beeCut)
    }

    private suspend fun backup(password: String? = null): ByteArray = ByteArrayOutputStream().also { container.backups.write(it, password) }.toByteArray()

    private suspend fun read(bytes: ByteArray, password: String? = null) = container.backups.read(ByteArrayInputStream(bytes), password)

    private fun entries(bytes: ByteArray): List<String> = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        generateSequence { zip.nextEntry?.name }.toList()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    /**
     * [bytes], a backup without a password, with each entry passed through [change], which leaves an entry out by
     * giving null, and [extra] added at the end.
     */
    private fun rezip(bytes: ByteArray, extra: List<Pair<String, ByteArray>> = emptyList(), change: (String, ByteArray) -> ByteArray? = { _, b -> b }): ByteArray {
        val kept = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.mapNotNull { entry -> change(entry.name, zip.readBytes())?.let { entry.name to it } }.toList()
        }
        return zip(*(kept + extra).toTypedArray())
    }

    /** Takes [limit] bytes and then fails, as a full card or a lost connection would. */
    private class FailingStream(private val limit: Int) : OutputStream() {
        val taken = ByteArrayOutputStream()

        override fun write(b: Int) {
            if (taken.size() >= limit) throw IOException("No room left")
            taken.write(b)
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) write(b[i].toInt())
        }
    }

    private suspend fun zoneNames() = container.garden.zoneList.first().map { it.name }.sorted()

    @Test
    fun `a backup restores the records, photos and settings it was made from`() = runBlocking {
        val files = seed()
        val bytes = backup()
        val names = entries(bytes)
        assertEquals(BackupArchive.MANIFEST, names.first())
        assertTrue(names.containsAll(listOf(BackupArchive.SETTINGS, BackupArchive.GRAPH, BackupArchive.DATABASE)))
        assertEquals("The photo and both cut-outs", files.map { "files/$it" }.toSet(), names.filter { it.startsWith("files/") }.toSet())
        assertEquals(DemoWildlife.clock.millis(), container.settingsStore.current().lastBackupAt)

        // The garden changes after the backup: a zone goes, a plant comes, the garden moves and its photo is lost.
        val veg = container.garden.zoneList.first().single { it.name == "Veg Patch" }
        container.garden.deleteZone(veg.id)
        container.garden.addPlant("Banksia integrifolia", null, null)
        container.settingsStore.update { it.copy(gardenState = AuState.VIC, gardenPlace = GardenPlace.at(-37.81, 144.96, "Melbourne"), scanLibrary = true, inspectEveryDays = 7) }
        container.photos.file(files[0]).delete()

        val ready = read(bytes) as BackupRead.Ready
        with(ready.backup.contents) {
            assertEquals(4, zones)
            assertEquals("The demo yard's 15 plants and the one from the photo", 16, plants)
            assertEquals(1, photos)
            assertEquals(1, sightings)
            assertEquals(2, hives)
            assertEquals(1, hotels)
            assertEquals(1, wateringLines)
            assertEquals(DemoWildlife.clock.instant(), createdAt)
        }
        assertEquals("Reading changes nothing", listOf("Back Citrus Patch", "Front Native Strip", "Hive Corner"), zoneNames())

        container.backups.restore(ready.backup)
        assertEquals(listOf("Back Citrus Patch", "Front Native Strip", "Hive Corner", "Veg Patch"), zoneNames())
        val plants = container.garden.plantList.first()
        assertEquals(16, plants.size)
        assertFalse(plants.any { it.speciesName == "Banksia integrifolia" })
        assertEquals(files[1], plants.single { it.customName == "Photo grevillea" }.thumbnail)
        files.forEach { assertTrue("$it is back", container.photos.file(it).isFile) }
        assertEquals("The bee still waits for review", 1, container.garden.pendingReviewCount.first())
        assertEquals(1, container.bees.hives.first().size)
        assertEquals(1, container.nativeBees.hives.first().size)
        assertEquals(1, container.hotels.hotels.first().size)
        val settings = container.settingsStore.current()
        assertEquals(taringa, settings.gardenPlace)
        assertEquals(AuState.QLD, settings.gardenState)
        assertEquals(10, settings.inspectEveryDays)
        assertTrue("Scanning belongs to the phone and stays on", settings.scanLibrary)
        // The search index was rebuilt from the restored records.
        assertEquals(veg.id, container.garden.search("veg patch").single { it.kind == "ZONE" }.recordId)
        assertTrue(container.garden.search("banksia").isEmpty())
    }

    @Test
    fun `a backup with a password opens only with that password`() = runBlocking {
        seed()
        val bytes = backup("wollemi pine")
        assertTrue(BackupSeal.isSealed(bytes))
        assertFalse("The records are not readable in the file", String(bytes, Charsets.ISO_8859_1).contains("Front Native Strip"))
        assertEquals(BackupRead.NeedsPassword, read(bytes))
        assertEquals(BackupRead.WrongPassword, read(bytes, "wollemi"))
        assertEquals(16, (read(bytes, "wollemi pine") as BackupRead.Ready).backup.contents.plants)
        assertEquals("pollenplot-backup-2026-10-28.ppbackup", container.backups.fileName(sealed = true))
        assertEquals("pollenplot-backup-2026-10-28.zip", container.backups.fileName(sealed = false))
    }

    @Test
    fun `files that are not whole backups are turned away and change nothing`() = runBlocking {
        seed()
        val bytes = backup()
        val manifest = ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> zip.nextEntry; String(zip.readBytes()) }
        assertEquals(BackupRead.NotABackup, read("Just some notes about the garden.".toByteArray()))
        assertEquals(BackupRead.NotABackup, read(zip("notes.txt" to "Mulch the citrus.".toByteArray())))
        assertEquals("Another app's backup", BackupRead.NotABackup, read(zip(BackupArchive.MANIFEST to """{"format":"weight-glucose-backup","version":1}""".toByteArray())))
        assertEquals(BackupRead.Damaged, read(bytes.copyOf(bytes.size / 2)))
        assertEquals(
            BackupRead.NewerVersion,
            read(rezip(bytes) { name, b -> if (name == BackupArchive.MANIFEST) manifest.replace("\"version\": 1", "\"version\": 2").toByteArray() else b }),
        )
        assertEquals(
            BackupRead.NewerVersion,
            read(rezip(bytes) { name, b -> if (name == BackupArchive.MANIFEST) manifest.replace("\"databaseVersion\": 1", "\"databaseVersion\": 9").toByteArray() else b }),
        )
        assertEquals(BackupRead.Damaged, read(rezip(bytes) { name, b -> if (name == BackupArchive.DATABASE) b.copyOf(b.size / 2) else b }))
        assertEquals("A backup that lost a photo", BackupRead.Damaged, read(rezip(bytes) { name, b -> b.takeUnless { name.startsWith("files/thumbs/") } }))
        assertEquals("A backup without its records", BackupRead.Damaged, read(zip(BackupArchive.MANIFEST to manifest.toByteArray())))
        // A sealed backup cut short or changed is damage, once its password has opened it.
        val sealed = backup("wollemi pine")
        assertEquals(BackupRead.Damaged, read(sealed.copyOf(sealed.size - 20), "wollemi pine"))
        assertEquals(listOf("Back Citrus Patch", "Front Native Strip", "Hive Corner", "Veg Patch"), zoneNames())
    }

    @Test
    fun `a backup that stops part way is never taken for a whole one`() = runBlocking {
        seed()
        val whole = backup().size
        container.settingsStore.update { it.copy(lastBackupAt = null) }
        listOf(null, "wollemi pine").forEach { password ->
            // Part way through the photos, and just after the database, where a zip read from the start ends quietly.
            listOf(whole * 9 / 10, whole / 3).forEach { limit ->
                val out = FailingStream(limit)
                val failed = runCatching { container.backups.write(out, password) }.exceptionOrNull()
                assertTrue("$limit bytes", failed is IOException)
                assertEquals("$limit bytes", BackupRead.Damaged, read(out.taken.toByteArray(), password))
            }
        }
        assertNull("A backup that failed does not count as saved", container.settingsStore.current().lastBackupAt)
    }

    @Test
    fun `no entry in a backup is written outside its photo folders`() = runBlocking {
        seed()
        val sneaky = listOf(
            "files/../escaped.txt" to "x".toByteArray(),
            "files/photos/../../escaped.txt" to "x".toByteArray(),
            "../escaped.txt" to "x".toByteArray(),
            "files/models/escaped.txt" to "x".toByteArray(),
            "database/../escaped.txt" to "x".toByteArray(),
        )
        val ready = read(rezip(backup(), sneaky)) as BackupRead.Ready
        container.backups.restore(ready.backup)
        val root = app.filesDir.parentFile!!
        assertNull(root.walkTopDown().firstOrNull { it.name == "escaped.txt" })
        assertFalse(File(app.filesDir, "models/escaped.txt").exists())
    }
}
