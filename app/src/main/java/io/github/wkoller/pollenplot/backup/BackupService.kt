package io.github.wkoller.pollenplot.backup

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.wkoller.pollenplot.BuildConfig
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.backup.BackupArchive
import io.github.wkoller.pollenplot.domain.backup.BackupManifest
import io.github.wkoller.pollenplot.domain.backup.BackupSeal
import io.github.wkoller.pollenplot.domain.backup.SealException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** What a backup holds, counted before anything on the phone changes. */
data class BackupContents(
    val createdAt: Instant,
    val appVersion: String,
    val zones: Int,
    val plants: Int,
    val sightings: Int,
    val photos: Int,
    /** Honey bee and native bee hives together. */
    val hives: Int,
    val hotels: Int,
    val wateringLines: Int,
)

/** A backup that has been read and checked, kept aside in the app's own storage until it is restored or put away. */
class StagedBackup internal constructor(val contents: BackupContents, internal val directory: File, internal val settings: BackupSettings?)

/** A restore that did not finish. With [recordsReplaced], the records are the backup's but some photos are not in place. */
class RestoreException(val recordsReplaced: Boolean, cause: Throwable) : IOException(cause)

/** What reading a backup file found. */
sealed interface BackupRead {
    data class Ready(val backup: StagedBackup) : BackupRead

    data object NeedsPassword : BackupRead

    /** The password does not open the backup. A backup damaged at its very start looks the same. */
    data object WrongPassword : BackupRead

    data object NotABackup : BackupRead

    /** A Pollenplot backup that is incomplete or has been changed, or whose records this version cannot read. */
    data object Damaged : BackupRead

    data object NewerVersion : BackupRead

    data object NoSpace : BackupRead

    /** The file could not be read, for example because the place it is kept could not be reached. */
    data object Unreadable : BackupRead
}

/**
 * Writes backup files and restores them. A backup holds every record, the photos and cut-outs the records show, the
 * settings that belong to the garden, and the whole garden as JSON-LD (see [BackupArchive]).
 *
 * Restoring takes two steps. [read] checks the whole file and keeps what it holds aside; [restore] then puts it in
 * place of the phone's records and photos. A damaged file is therefore found before anything changes, and the records
 * are replaced in one transaction, so the phone never keeps half of one garden and half of another.
 */
class BackupService(
    private val context: Context,
    private val database: PollenplotDatabase,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val graph: suspend () -> String,
    private val afterRestore: suspend () -> Unit,
    private val scope: CoroutineScope,
) {
    private val files: File get() = context.filesDir
    private val staging: File get() = File(context.noBackupFilesDir, STAGING)

    /** One backup is written, read or restored at a time, because they share the folders they work in. */
    private val lock = Mutex()

    /** The name a new backup file is offered under. A backup with a password is not a zip any more, so it is named apart. */
    fun fileName(sealed: Boolean): String = "pollenplot-backup-${LocalDate.now(clock)}.${if (sealed) SEALED_EXTENSION else "zip"}"

    /**
     * Writes a backup to [out], sealed with [password] when there is one, and closes [out]. [progress] runs from 0 to 1.
     * Once started, it finishes even if the screen that asked for it closes.
     */
    suspend fun write(out: OutputStream, password: String?, progress: (Float) -> Unit = {}) = withContext(NonCancellable) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                val work = File(context.cacheDir, WORK).apply {
                    deleteRecursively()
                    mkdirs()
                }
                try {
                    val copy = File(work, DATABASE_FILE)
                    val live = database.openHelper.writableDatabase
                    // A copy made by SQLite itself is whole even while the app goes on writing.
                    live.execSQL("VACUUM INTO ?", arrayOf<Any?>(copy.path))
                    val stored = storedFiles(copy)
                    val total = maxOf(1L, copy.length() + stored.sumOf { it.second.length() })
                    var done = 0L
                    val count = { bytes: Int ->
                        done += bytes
                        progress((done.toFloat() / total).coerceAtMost(1f))
                    }
                    val sink = if (password.isNullOrEmpty()) out else BackupSeal.seal(out, password)
                    val zip = ZipOutputStream(sink.buffered(BUFFER_BYTES))
                    try {
                        val manifest = BackupArchive.manifest(clock.instant(), BuildConfig.VERSION_NAME, live.version, stored.size)
                        zip.text(BackupArchive.MANIFEST, BackupArchive.manifestJson(manifest))
                        zip.text(BackupArchive.SETTINGS, BackupSettings.of(settings.current()).json())
                        zip.text(BackupArchive.GRAPH, graph())
                        zip.file(BackupArchive.DATABASE, copy, compress = true, count)
                        stored.forEach { (path, file) -> zip.file(BackupArchive.fileEntry(path), file, compress = false, count) }
                        zip.close()
                    } catch (e: Throwable) {
                        // Closing the zip would finish it, and seal its end, so part of a backup would look whole.
                        // Only the file underneath is closed, and the part written is refused when it is read.
                        runCatching { out.close() }
                        throw e
                    }
                } finally {
                    work.deleteRecursively()
                }
            }
            settings.update { it.copy(lastBackupAt = clock.millis()) }
        }
    }

    /**
     * Reads a backup from [input] and checks all of it, without changing anything on the phone. A backup that is ready
     * waits aside until it is restored with [restore] or put away with [discard]; reading another puts it away too.
     */
    suspend fun read(input: InputStream, password: String?): BackupRead = lock.withLock {
        withContext(Dispatchers.IO) { readUnlocked(input, password) }
    }

    private fun readUnlocked(input: InputStream, password: String?): BackupRead {
        clearStaging()
        val directory = File(staging, UUID.randomUUID().toString()).apply { mkdirs() }
        val result = try {
            stage(input, password, directory)
        } catch (e: Refused) {
            e.result
        } catch (e: IOException) {
            if (spaceLeft(directory) < RESERVE_BYTES) BackupRead.NoSpace else BackupRead.Unreadable
        }
        if (result !is BackupRead.Ready) directory.deleteRecursively()
        return result
    }

    /**
     * Replaces every record, photo and cut-out on the phone with the backup's, and the garden's settings with its
     * settings. When the records cannot be written, nothing on the phone changes and this throws a [RestoreException].
     * Once started, it finishes even if the screen that asked for it closes, so the settings and search index always
     * follow the records.
     */
    suspend fun restore(backup: StagedBackup) = withContext(NonCancellable) {
        lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    openStaged(File(backup.directory, DATABASE_FILE)).use(::replaceRecords)
                } catch (e: RuntimeException) {
                    throw RestoreException(recordsReplaced = false, e)
                }
                try {
                    replaceFiles(File(backup.directory, FILES))
                } catch (e: IOException) {
                    throw RestoreException(recordsReplaced = true, e)
                }
                backup.directory.deleteRecursively()
            }
            val madeAt = backup.contents.createdAt.toEpochMilli()
            settings.update { current ->
                (backup.settings?.applyTo(current) ?: current).copy(lastBackupAt = maxOf(current.lastBackupAt ?: madeAt, madeAt))
            }
            afterRestore()
        }
    }

    /** Puts a backup that was read but not restored away, in the background. */
    fun discard(backup: StagedBackup) {
        scope.launch(Dispatchers.IO) { lock.withLock { backup.directory.deleteRecursively() } }
    }

    /** Clears what an earlier read or restore left behind, for example when the app was closed part way. */
    suspend fun tidy() = lock.withLock { withContext(Dispatchers.IO) { clearStaging() } }

    private fun clearStaging() {
        staging.deleteRecursively()
        listOf(PhotoStore.PHOTOS, PhotoStore.THUMBS).forEach { File(files, it + REPLACED).deleteRecursively() }
    }

    // Writing.

    /** The photos and cut-outs that the records in [copy] show, which are the ones a backup holds. */
    private fun storedFiles(copy: File): List<Pair<String, File>> {
        val paths = openStaged(copy).use { db ->
            db.rawQuery(STORED_PATHS, null).use { cursor ->
                buildList { while (cursor.moveToNext()) if (!cursor.isNull(0)) add(cursor.getString(0)) }
            }
        }
        return paths.distinct().filter(BackupArchive::isStoredFile).map { it to File(files, it) }.filter { it.second.isFile }
    }

    private fun ZipOutputStream.text(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.file(name: String, file: File, compress: Boolean, count: (Int) -> Unit) {
        val entry = ZipEntry(name)
        if (!compress) {
            // Photos are compressed already, so they are stored as they are. A stored entry gives its size and
            // checksum before its bytes.
            val crc = CRC32()
            file.forEachBlock(BUFFER_BYTES) { buffer, read -> crc.update(buffer, 0, read) }
            entry.method = ZipEntry.STORED
            entry.size = file.length()
            entry.compressedSize = file.length()
            entry.crc = crc.value
        }
        putNextEntry(entry)
        file.forEachBlock(BUFFER_BYTES) { buffer, read ->
            write(buffer, 0, read)
            count(read)
        }
        closeEntry()
    }

    // Reading.

    private fun stage(raw: InputStream, password: String?, directory: File): BackupRead {
        val input = BufferedInputStream(raw, BUFFER_BYTES)
        input.mark(BackupSeal.MAGIC_BYTES)
        val head = ByteArray(BackupSeal.MAGIC_BYTES)
        var filled = 0
        while (filled < head.size) {
            val read = input.read(head, filled, head.size - filled)
            if (read < 0) break
            filled += read
        }
        input.reset()
        val sealed = BackupSeal.isSealed(head)
        val plain = when {
            sealed && password.isNullOrEmpty() -> return BackupRead.NeedsPassword
            sealed -> try {
                BackupSeal.open(input, password.orEmpty())
            } catch (e: SealException) {
                return if (e.wrongPassword) BackupRead.WrongPassword else BackupRead.Damaged
            }
            BackupArchive.isZip(head) -> input
            else -> return BackupRead.NotABackup
        }
        // Once a sealed backup opens with its password, or the manifest has been read, the file is certainly a backup,
        // so anything wrong from there on is damage.
        val notOurs = if (sealed) BackupRead.Damaged else BackupRead.NotABackup
        val current = database.openHelper.readableDatabase.version
        var manifest: BackupManifest? = null
        var settingsText: String? = null
        var hasDatabase = false
        var files = 0
        try {
            ZipInputStream(plain).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (spaceLeft(directory) < RESERVE_BYTES) throw Refused(BackupRead.NoSpace)
                    val name = entry.name
                    when {
                        // The manifest comes first, so another app's zip is turned away before it is unpacked.
                        manifest == null -> {
                            if (name != BackupArchive.MANIFEST) return notOurs
                            val read = BackupArchive.readManifest(zip.smallText()) ?: return notOurs
                            if (read.version > BackupArchive.VERSION || read.databaseVersion > current) return BackupRead.NewerVersion
                            manifest = read
                        }
                        name == BackupArchive.SETTINGS -> settingsText = zip.smallText()
                        name == BackupArchive.DATABASE -> {
                            zip.copyTo(File(directory, DATABASE_FILE))
                            hasDatabase = true
                        }
                        else -> BackupArchive.storedPath(name)?.let { path ->
                            zip.copyTo(File(directory, "$FILES/$path"))
                            files++
                        }
                    }
                }
            }
        } catch (e: SealException) {
            return BackupRead.Damaged
        } catch (e: ZipException) {
            return if (manifest != null) BackupRead.Damaged else notOurs
        } catch (e: EOFException) {
            return if (manifest != null) BackupRead.Damaged else notOurs
        }
        val found = manifest ?: return notOurs
        val created = found.created
        // A zip read from the start ends quietly where an entry ends, so a backup that stopped part way is found by
        // what it said would follow.
        if (!hasDatabase || created == null || files != found.files) return BackupRead.Damaged
        val staged = File(directory, DATABASE_FILE)
        if (!verify(staged)) return BackupRead.Damaged
        val contents = try {
            openStaged(staged).use { db -> if (sound(db)) contents(db, created, found.appVersion) else null }
        } catch (e: SQLiteException) {
            null
        } ?: return BackupRead.Damaged
        return BackupRead.Ready(StagedBackup(contents, directory, settingsText?.let(BackupSettings::read)))
    }

    /**
     * Opens [file] the way the app opens its own database, which brings a backup from an older version up to date and
     * checks that its tables are exactly the ones this version reads. A backup that does not pass would fail later,
     * when its records are shown.
     */
    private fun verify(file: File): Boolean {
        val version = try {
            openStaged(file).use { it.version }
        } catch (e: SQLiteException) {
            return false
        }
        // Version 0 is a database that was never set up, not a backup.
        if (version < 1) return false
        val room = Room.databaseBuilder(context, PollenplotDatabase::class.java, file.path).addMigrations(*PollenplotDatabase.MIGRATIONS).build()
        return try {
            room.openHelper.writableDatabase.version >= 1
        } catch (e: RuntimeException) {
            false
        } finally {
            room.close()
        }
    }

    /** Whether [db] reads back whole: SQLite's own check of its pages, and every link between records in place. */
    private fun sound(db: SQLiteDatabase): Boolean {
        val pages = db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        val links = db.rawQuery("PRAGMA foreign_key_check", null).use { !it.moveToFirst() }
        return pages && links
    }

    private fun contents(db: SQLiteDatabase, created: Instant, appVersion: String): BackupContents {
        fun count(table: String) = DatabaseUtils.longForQuery(db, "SELECT COUNT(*) FROM \"$table\"", null).toInt()
        return BackupContents(
            createdAt = created,
            appVersion = appVersion,
            zones = count("zones"),
            plants = count("plants"),
            sightings = count("sightings"),
            photos = count("photos"),
            hives = count("honey_hives") + count("native_hives"),
            hotels = count("hotels"),
            wateringLines = count("watering_lines"),
        )
    }

    private fun ZipInputStream.smallText(): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > TEXT_LIMIT_BYTES) throw Refused(BackupRead.Damaged)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun ZipInputStream.copyTo(target: File) {
        target.parentFile?.mkdirs()
        target.outputStream().use { out ->
            val buffer = ByteArray(BUFFER_BYTES)
            var sinceCheck = 0L
            while (true) {
                val read = read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                sinceCheck += read
                // A backup can be larger than it looks, so the space left is checked as it is unpacked.
                if (sinceCheck >= SPACE_CHECK_BYTES) {
                    sinceCheck = 0
                    if (spaceLeft(target) < RESERVE_BYTES) throw Refused(BackupRead.NoSpace)
                }
            }
        }
    }

    // Restoring.

    /**
     * Replaces every record with those in [source] in one transaction, so if any row cannot be written the phone keeps
     * its own. The search index is left out; it is rebuilt afterwards from the records.
     */
    private fun replaceRecords(source: SQLiteDatabase) {
        database.runInTransaction(
            Runnable {
                val target = database.openHelper.writableDatabase
                // The links between records are checked once all of them are in, rather than row by row.
                target.execSQL("PRAGMA defer_foreign_keys = ON")
                val tables = recordTables(target)
                tables.forEach { target.execSQL("DELETE FROM \"$it\"") }
                tables.forEach { copyTable(source, target, it) }
                // A link left hanging would stop the transaction from committing, so it is found while it can still be undone.
                target.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "The backup's records do not link up" } }
            },
        )
    }

    /** The tables that hold records: all but SQLite's and Room's own, and the search index with its shadow tables. */
    private fun recordTables(db: SupportSQLiteDatabase): List<String> {
        val tables = db.query("SELECT name, sql FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1).orEmpty()) }
        }
        val virtual = tables.filter { it.second.startsWith("CREATE VIRTUAL TABLE", ignoreCase = true) }.map { it.first }
        return tables.map { it.first }.filter { name ->
            !name.startsWith("sqlite_") && name !in ROOM_TABLES && virtual.none { name == it || name.startsWith("${it}_") }
        }
    }

    /** Copies [table]'s rows column by column, by name. */
    private fun copyTable(source: SQLiteDatabase, target: SupportSQLiteDatabase, table: String) {
        val wanted = columns(target.query("PRAGMA table_info(\"$table\")"))
        val present = columns(source.rawQuery("PRAGMA table_info(\"$table\")", null)).toSet()
        val shared = wanted.filter { it in present }
        if (shared.isEmpty()) return
        source.rawQuery("SELECT ${shared.joinToString { "\"$it\"" }} FROM \"$table\"", null).use { rows ->
            val values = ContentValues()
            while (rows.moveToNext()) {
                values.clear()
                shared.forEachIndexed { i, column ->
                    when (rows.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> values.putNull(column)
                        Cursor.FIELD_TYPE_INTEGER -> values.put(column, rows.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> values.put(column, rows.getDouble(i))
                        Cursor.FIELD_TYPE_BLOB -> values.put(column, rows.getBlob(i))
                        else -> values.put(column, rows.getString(i))
                    }
                }
                target.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    private fun columns(cursor: Cursor): List<String> = cursor.use {
        val name = it.getColumnIndexOrThrow("name")
        buildList { while (it.moveToNext()) add(it.getString(name)) }
    }

    /** Puts the backup's photos and cut-outs in place of the phone's, a folder at a time. */
    private fun replaceFiles(incoming: File) {
        listOf(PhotoStore.PHOTOS, PhotoStore.THUMBS).forEach { folder ->
            val target = File(files, folder)
            val old = File(files, folder + REPLACED)
            val next = File(incoming, folder).apply { mkdirs() }
            old.deleteRecursively()
            if (target.exists() && !target.renameTo(old)) throw IOException("The phone's photos could not be moved aside")
            if (!next.renameTo(target)) {
                old.renameTo(target)
                throw IOException("The backup's photos could not be put in place")
            }
            old.deleteRecursively()
        }
    }

    /**
     * How much can be written where [file] is, now. Lint suggests StorageManager's allocatable bytes, which count cached
     * files the system could clear, but a restore does not ask the system to clear them, so they would not help it.
     */
    @SuppressLint("UsableSpace")
    private fun spaceLeft(file: File): Long = file.usableSpace

    /** The app's own copy of a database, opened without the locale table that the framework would otherwise add. */
    private fun openStaged(file: File): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)

    /** Stops a read with [result]. */
    private class Refused(val result: BackupRead) : IOException()

    private companion object {
        const val STAGING = "restore"
        const val WORK = "backup"
        const val FILES = "files"
        const val DATABASE_FILE = "pollenplot.db"
        const val REPLACED = "-replaced"
        const val SEALED_EXTENSION = "ppbackup"
        const val BUFFER_BYTES = 64 * 1024
        const val TEXT_LIMIT_BYTES = 1024 * 1024
        const val SPACE_CHECK_BYTES = 4L * 1024 * 1024

        /** Restoring stops before the phone has less than this free, so it still has room to work. */
        const val RESERVE_BYTES = 100L * 1024 * 1024
        val ROOM_TABLES = setOf("android_metadata", "room_master_table")

        /** Every photo and cut-out that a record shows. */
        const val STORED_PATHS = "SELECT path FROM photos UNION SELECT thumbnail FROM plants " +
            "UNION SELECT thumbnail FROM detections UNION SELECT thumbnail FROM sightings"
    }
}
