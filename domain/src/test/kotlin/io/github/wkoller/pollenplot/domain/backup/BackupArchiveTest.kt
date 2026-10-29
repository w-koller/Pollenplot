package io.github.wkoller.pollenplot.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupArchiveTest {
    private val photo = "photos/3f0c9d2e-8a4b-4c1d-9e2f-0a1b2c3d4e5f.jpg"
    private val thumb = "thumbs/7a6b5c4d-3e2f-4a1b-8c9d-0e1f2a3b4c5d.webp"

    @Test
    fun `photos and cut-outs go in under files and come back to the same place`() {
        assertEquals("files/$photo", BackupArchive.fileEntry(photo))
        assertEquals(photo, BackupArchive.storedPath(BackupArchive.fileEntry(photo)))
        assertEquals(thumb, BackupArchive.storedPath("files/$thumb"))
    }

    @Test
    fun `no entry can restore outside the photo folders`() {
        listOf(
            "files/photos/../databases/pollenplot.db",
            "files/../shared_prefs/x.xml",
            "files/photos/..",
            "files/photos/.hidden",
            "files/photos/",
            "files/photos/sub/folder.jpg",
            "files/models/biocap.onnx",
            "files/weeds/brisbane.json",
            "files//photos/x.jpg",
            "/files/photos/x.jpg",
            "photos/x.jpg",
            "files/photos/x.jpg\u0000.png",
            "files/photos/" + "a".repeat(200),
            BackupArchive.DATABASE,
            BackupArchive.MANIFEST,
        ).forEach { assertNull(it, BackupArchive.storedPath(it)) }
        assertFalse(BackupArchive.isStoredFile("datastore/settings.preferences_pb"))
    }

    @Test
    fun `the manifest says what made the backup and when`() {
        val manifest = BackupArchive.manifest(Instant.parse("2026-10-28T22:00:00Z"), "0.1.0", 1, files = 3)
        val read = BackupArchive.readManifest(BackupArchive.manifestJson(manifest))!!
        assertEquals(manifest, read)
        assertEquals(Instant.parse("2026-10-28T22:00:00Z"), read.created)
        assertEquals(BackupArchive.VERSION, read.version)
        assertEquals(3, read.files)
    }

    @Test
    fun `another app's manifest is not a backup`() {
        assertNull(BackupArchive.readManifest("""{"format":"weight-glucose-backup","version":1,"createdAt":"x","appVersion":"1","databaseVersion":1}"""))
        assertNull(BackupArchive.readManifest("not json"))
        assertNull(BackupArchive.readManifest("""{"format":"pollenplot-backup"}"""))
        // A manifest from a later version can add fields.
        val later = BackupArchive.readManifest(
            """{"format":"pollenplot-backup","version":2,"createdAt":"2027-01-01T00:00:00Z","appVersion":"2.0.0","databaseVersion":4,"files":0,"extra":true}""",
        )!!
        assertEquals(2, later.version)
    }

    @Test
    fun `a zip is told from a sealed backup by its first bytes`() {
        assertTrue(BackupArchive.isZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
        assertFalse(BackupArchive.isZip("PPSEALED".toByteArray()))
        assertFalse(BackupArchive.isZip(byteArrayOf(0x50, 0x4B)))
    }
}
