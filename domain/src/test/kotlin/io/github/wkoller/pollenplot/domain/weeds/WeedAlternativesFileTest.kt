package io.github.wkoller.pollenplot.domain.weeds

import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBaseFilesTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the native alternatives to weeds that the app ships against its knowledge base. */
class WeedAlternativesFileTest {
    private val alternatives = WeedAlternatives.parse(File(System.getProperty("weeds.dir"), "alternatives.json").readText())
    private val kb = KnowledgeBaseFilesTest.load().knowledgeBase

    @Test
    fun `the file has no problems`() {
        val problems = alternatives.problems()
        assertTrue("Problems in alternatives.json:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every alternative is a species in the knowledge base, and every weed is not one of its own alternatives`() {
        val missing = alternatives.weeds.flatMap { weed -> weed.alternatives.map { it.species } }.distinct().filter { kb.species(it) == null }
        assertTrue("Alternatives the knowledge base does not have: $missing", missing.isEmpty())
        val circular = alternatives.weeds.filter { weed -> weed.alternatives.any { WeedNames.binomial(it.species) == WeedNames.binomial(weed.weed) } }
        assertTrue("Weeds suggested instead of themselves: ${circular.map { it.weed }}", circular.isEmpty())
    }

    @Test
    fun `the sources are Grow Me Instead booklets or government pages, and every state has one`() {
        alternatives.sources.forEach { source ->
            val host = source.url.removePrefix("https://").substringBefore('/')
            assertTrue("${source.key} links to $host", host.endsWith("growmeinstead.com.au") || host.endsWith(".gov.au"))
        }
        val covered = alternatives.sources.flatMap { it.auStates }.toSet()
        assertTrue("States without a source: ${AuState.entries - covered}", covered == AuState.entries.toSet())
    }
}
