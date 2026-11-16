package io.github.wkoller.pollenplot.library

import io.github.wkoller.pollenplot.domain.weeds.AlaPhotos
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.web.Web
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * A photo of a plant, as a file on the phone, with the credit its licence asks for and the address of a larger copy
 * for when it is enlarged.
 */
data class LibraryPhoto(val file: File, val creator: String?, val source: String?, val licence: String, internal val large: String?) {
    /** The credit as one line: who took it, where the record is from, and the licence. */
    val credit: String get() = listOfNotNull(creator, source, licence).joinToString(" · ")
}

/**
 * Photos of plants from records on the Atlas of Living Australia, for the plant library and the weed lists. A plant's
 * records are looked up the first time it is shown, and up to [KEPT] photos are noted; each picture is fetched when it
 * is first needed and kept in [folder], in the app's cache, so it is fetched once. Only the plant's name is sent. Only
 * photos whose licence asks for a credit alone (public domain, CC0, CC BY) are kept, and a plant with none is
 * remembered as having none.
 */
class LibraryPhotos(private val web: Web, private val sources: () -> WeedSources, private val folder: File) {
    private val gate = Semaphore(MAX_AT_ONCE)

    /** One photo of [speciesName], or null when there is none or no connection. */
    suspend fun photo(speciesName: String): LibraryPhoto? = photos(speciesName, 1).firstOrNull()

    /** Up to [count] photos of [name], fetched if they have not been yet. Empty when there are none or no connection. */
    suspend fun photos(name: String, count: Int): List<LibraryPhoto> = withContext(Dispatchers.IO) {
        val key = key(name)
        val found = records(name, key) ?: return@withContext emptyList()
        found.take(count.coerceAtMost(KEPT)).withIndex().mapNotNull { (i, record) ->
            val image = File(folder, "$key-$i.img")
            if (!image.exists() && !fetch(record.thumbnail, image)) return@mapNotNull null
            LibraryPhoto(image, record.creator, record.source, record.licence, record.large)
        }
    }

    /** The larger copy of [photo], fetched the first time it is asked for; the smaller one when it cannot be had. */
    suspend fun large(photo: LibraryPhoto): File = withContext(Dispatchers.IO) {
        val url = photo.large ?: return@withContext photo.file
        val file = File(photo.file.parentFile, photo.file.nameWithoutExtension + "-large.img")
        if (file.exists() || fetch(url, file)) file else photo.file
    }

    /** What is noted of a photo: the credit, and where its two sizes are. */
    private data class Noted(val creator: String?, val source: String?, val licence: String, val thumbnail: String, val large: String?)

    /** The photos noted for [name], looked up when they have not been; null when the lookup could not be made. */
    private suspend fun records(name: String, key: String): List<Noted>? {
        val notes = File(folder, "$key.txt")
        read(notes)?.let { return it }
        return gate.withPermit {
            read(notes)?.let { return@withPermit it }
            try {
                val query = URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                val kept = AlaPhotos.parse(web.get(sources().national.photos.replace("{name}", query)))
                    .filter { it.creditOnly }
                    .take(KEPT)
                    .map { Noted(it.creator, it.source, it.licence, it.thumbnail, it.large.takeIf { large -> large != it.thumbnail }) }
                folder.mkdirs()
                notes.writeText(HEADER + "\n" + kept.joinToString("") { listOf(it.creator, it.source, it.licence, it.thumbnail, it.large).joinToString("\t") { part -> part.orEmpty() } + "\n" })
                kept
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No connection, or the service is away: looked up again the next time the plant is shown.
                null
            }
        }
    }

    /** The photos noted in [file], or null when there is no such file or it was written by an older version. */
    private fun read(file: File): List<Noted>? {
        if (!file.exists()) return null
        val lines = file.readLines()
        if (lines.firstOrNull() != HEADER) return null
        return lines.drop(1).filter { it.isNotBlank() }.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 5 || parts[2].isBlank() || parts[3].isBlank()) null else Noted(parts[0].ifBlank { null }, parts[1].ifBlank { null }, parts[2], parts[3], parts[4].ifBlank { null })
        }
    }

    /** Fetches [url] into [file], whole or not at all. */
    private suspend fun fetch(url: String, file: File): Boolean = gate.withPermit {
        try {
            val part = File(file.parentFile, file.name + ".part")
            part.writeBytes(web.bytes(url))
            part.renameTo(file)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    private fun key(name: String): String =
        MessageDigest.getInstance("SHA-256").digest(name.lowercase().toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_AT_ONCE = 3
        const val KEPT = 3
        const val HEADER = "photos 2"
    }
}
