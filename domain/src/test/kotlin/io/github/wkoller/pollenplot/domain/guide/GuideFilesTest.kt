package io.github.wkoller.pollenplot.domain.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** The guide pages the app ships, read as the app reads them, so a mistake in one fails the build. */
class GuideFilesTest {
    private val dir = File(System.getProperty("guides.dir") ?: error("guides.dir is set by domain/build.gradle.kts"))

    private val pages: List<Pair<File, Guide>> = dir.listFiles { f -> f.isDirectory }!!.sortedBy { it.name }.flatMap { folder ->
        folder.listFiles { f -> f.extension == "md" }!!.sortedBy { it.name }.map { file -> file to GuideParser.parse("${folder.name}/${file.nameWithoutExtension}", file.readText()) }
    }

    @Test
    fun `every page reads and sits in its section's folder`() {
        assertTrue("The honey bee guide has pages", pages.any { it.second.section == GuideSection.HONEY })
        pages.forEach { (file, guide) -> assertEquals(file.path, file.parentFile.name, guide.section.name.lowercase()) }
    }

    @Test
    fun `pages in a section have their own places`() {
        pages.groupBy { it.second.section }.forEach { (section, inSection) ->
            val orders = inSection.map { it.second.order }
            assertEquals("$section has two pages in one place: $orders", orders.size, orders.toSet().size)
        }
    }

    @Test
    fun `each page was checked against its sources on a day that has passed`() {
        pages.forEach { (file, guide) ->
            assertTrue(file.path, !guide.lastChecked.isAfter(LocalDate.now()))
            assertTrue("${file.path} needs a summary that is a sentence", guide.summary.endsWith("."))
        }
    }
}
