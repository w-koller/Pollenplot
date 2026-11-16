package io.github.wkoller.pollenplot.library

import io.github.wkoller.pollenplot.domain.weeds.AlaPhotos
import io.github.wkoller.pollenplot.web.Web
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

/** A photo of a species for the plant library, as a file on the phone, with the credit its licence asks for. */
data class LibraryPhoto(val file: File, val creator: String?, val source: String?, val licence: String) {
    /** The credit as one line: who took it, where the record is from, and the licence. */
    val credit: String get() = listOfNotNull(creator, source, licence).joinToString(" · ")
}

/**
 * One photo for each species in the plant library, from records on the Atlas of Living Australia. A photo is fetched
 * the first time its plant is shown and kept in [folder], in the app's cache, so it is fetched once. Only the plant's
 * name is sent. Only photos whose licence asks for a credit alone (public domain, CC0, CC BY) are kept, and a species
 * with none is remembered as having none.
 */
class LibraryPhotos(private val web: Web, private val sources: () -> WeedSources, private val folder: File) {
    private val gate = Semaphore(MAX_AT_ONCE)

    /** The photo kept for [speciesName], fetched if it has not been looked for yet. Null when there is none or no connection. */
    suspend fun photo(speciesName: String): LibraryPhoto? = withContext(Dispatchers.IO) {
        val key = key(speciesName)
        val image = File(folder, "$key.img")
        val credit = File(folder, "$key.txt")
        if (credit.exists()) return@withContext read(image, credit)
        gate.withPermit {
            if (credit.exists()) return@withPermit read(image, credit)
            try {
                val query = URLEncoder.encode(speciesName, "UTF-8").replace("+", "%20")
                val found = AlaPhotos.parse(web.get(sources().national.photos.replace("{name}", query))).firstOrNull { it.creditOnly }
                folder.mkdirs()
                if (found == null) {
                    credit.writeText(NONE)
                    null
                } else {
                    File(folder, "$key.part").let { part ->
                        part.writeBytes(web.bytes(found.thumbnail))
                        part.renameTo(image)
                    }
                    // Written last, so a photo is only counted as kept once its picture is whole.
                    credit.writeText(listOf(found.creator.orEmpty(), found.source.orEmpty(), found.licence).joinToString("\n"))
                    read(image, credit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // No connection, or the service is away: looked for again the next time the plant is shown.
                null
            }
        }
    }

    private fun read(image: File, credit: File): LibraryPhoto? {
        val lines = credit.readText().split('\n')
        if (lines.size < 3 || !image.exists()) return null
        return LibraryPhoto(image, lines[0].ifBlank { null }, lines[1].ifBlank { null }, lines[2])
    }

    private fun key(name: String): String =
        MessageDigest.getInstance("SHA-256").digest(name.lowercase().toByteArray()).take(12).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_AT_ONCE = 3
        const val NONE = "none"
    }
}
