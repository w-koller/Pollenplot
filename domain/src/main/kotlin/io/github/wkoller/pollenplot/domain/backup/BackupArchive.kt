package io.github.wkoller.pollenplot.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/** What a backup file says about itself, in its manifest. */
@Serializable
data class BackupManifest(
    val format: String,
    val version: Int,
    /** When the backup was made, as an ISO instant such as "2026-10-28T22:00:00Z". */
    val createdAt: String,
    val appVersion: String,
    /** The schema version of the database inside, which a restore migrates from when it is older. */
    val databaseVersion: Int,
    /** How many photos and cut-outs follow, so a backup that lost some is found out. */
    val files: Int,
) {
    val created: Instant? get() = runCatching { Instant.parse(createdAt) }.getOrNull()
}

/**
 * The layout of a backup file. It is a zip that holds its manifest, the settings that belong to the garden, every
 * record as JSON-LD for people and other tools to read, the database a restore reads, and the photos and cut-outs
 * under `files/`, at their paths in the app's files folder. A backup with a password is this zip sealed with
 * [BackupSeal].
 */
object BackupArchive {
    const val FORMAT = "pollenplot-backup"
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val SETTINGS = "settings.json"
    const val GRAPH = "graph.jsonld"
    const val DATABASE = "database/pollenplot.db"
    private const val FILES = "files/"

    /** A photo or cut-out as the app keeps it: one folder and a plain file name, such as photos/<UUID>.jpg. */
    private val STORED = Regex("""(photos|thumbs)/[A-Za-z0-9][A-Za-z0-9._-]{0,127}""")
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun manifest(createdAt: Instant, appVersion: String, databaseVersion: Int, files: Int) =
        BackupManifest(FORMAT, VERSION, createdAt.toString(), appVersion, databaseVersion, files)

    fun manifestJson(manifest: BackupManifest): String = json.encodeToString(BackupManifest.serializer(), manifest)

    /** The manifest in [text], or null when it is not a Pollenplot backup's manifest. */
    fun readManifest(text: String): BackupManifest? =
        runCatching { json.decodeFromString(BackupManifest.serializer(), text) }.getOrNull()?.takeIf { it.format == FORMAT }

    /** Whether [head], the first bytes of a file, begin a zip. */
    fun isZip(head: ByteArray): Boolean = head.size >= ZIP_MAGIC.size && ZIP_MAGIC.indices.all { head[it] == ZIP_MAGIC[it] }

    /** Whether [path], relative to the app's files folder, is a photo or cut-out that a backup holds. */
    fun isStoredFile(path: String): Boolean = STORED.matches(path)

    /** The entry that holds the file at [path] in the app's files folder. */
    fun fileEntry(path: String): String {
        require(isStoredFile(path)) { "Only photos and cut-outs go in a backup" }
        return FILES + path
    }

    /**
     * The path in the app's files folder that [entry] restores to, or null for an entry a restore leaves alone. Only
     * a photo or cut-out at a plain name qualifies, so an entry can never reach another folder.
     */
    fun storedPath(entry: String): String? = entry.takeIf { it.startsWith(FILES) }?.removePrefix(FILES)?.takeIf(::isStoredFile)
}
