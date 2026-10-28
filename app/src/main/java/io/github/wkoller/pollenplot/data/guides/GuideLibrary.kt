package io.github.wkoller.pollenplot.data.guides

import android.content.Context
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.domain.guide.GuideParser
import io.github.wkoller.pollenplot.domain.guide.GuideSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The bee guides the app ships in `assets/guides/<section>/`, one Markdown page per file, each read once. A page's id
 * is its section and file name, such as `honey/varroa`. `GuideFilesTest` reads the same files on every build.
 */
class GuideLibrary(private val context: Context) {
    private val lock = Mutex()
    private val read = mutableMapOf<GuideSection, List<Guide>>()

    /** A section's pages in their order. */
    suspend fun section(section: GuideSection): List<Guide> = lock.withLock {
        read.getOrPut(section) {
            withContext(Dispatchers.IO) {
                val folder = "guides/" + section.name.lowercase()
                context.assets.list(folder).orEmpty().filter { it.endsWith(".md") }.map { name ->
                    val text = context.assets.open("$folder/$name").bufferedReader().use { it.readText() }
                    GuideParser.parse(section.name.lowercase() + "/" + name.removeSuffix(".md"), text)
                }.sortedBy { it.order }
            }
        }
    }

    suspend fun guide(id: String): Guide? {
        val section = GuideSection.entries.firstOrNull { it.name.equals(id.substringBefore('/'), ignoreCase = true) } ?: return null
        return section(section).firstOrNull { it.id == id }
    }
}
