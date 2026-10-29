package io.github.wkoller.pollenplot.domain.guide

import io.github.wkoller.pollenplot.domain.plants.AuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GuideParserTest {
    private val page = """
        ---
        title: Checking for Varroa mite
        summary: How to test a hive for mites.
        section: honey
        order: 3
        lastChecked: 2026-10-29
        sources:
          - Varroa mites, BeeAware | https://beeaware.org.au/archive-pest/varroa-mites/
        ---

        Varroa mites are tiny parasites of *Apis mellifera*, about **1.1 mm** long.
        They spread with drifting bees.

        ## How often

        - Every month where Varroa is established.
        - In spring, summer and autumn
          elsewhere.

        1. Collect about 300 bees.
        2. Shake them in alcohol, as [Queensland explains](https://www.business.qld.gov.au/).

        > Please report any mite you find.
    """.trimIndent()

    @Test
    fun `front matter and text are read into a page`() {
        val guide = GuideParser.parse("honey/varroa", page)
        assertEquals("Checking for Varroa mite", guide.title)
        assertEquals(GuideSection.HONEY, guide.section)
        assertEquals(3, guide.order)
        assertEquals(LocalDate.of(2026, 10, 29), guide.lastChecked)
        assertEquals(listOf(GuideSource("Varroa mites, BeeAware", "https://beeaware.org.au/archive-pest/varroa-mites/")), guide.sources)
        assertEquals(5, guide.blocks.size)
        assertEquals(
            GuideBlock.Paragraph(
                listOf(
                    Span("Varroa mites are tiny parasites of "),
                    Span("Apis mellifera", italic = true),
                    Span(", about "),
                    Span("1.1 mm", bold = true),
                    Span(" long. They spread with drifting bees."),
                ),
            ),
            guide.blocks[0],
        )
        assertEquals(GuideBlock.Heading("How often"), guide.blocks[1])
        assertEquals("A list item carries on over indented lines", listOf(Span("In spring, summer and autumn elsewhere.")), (guide.blocks[2] as GuideBlock.Bullets).items[1])
        val steps = guide.blocks[3] as GuideBlock.Steps
        assertEquals(Span("Queensland explains", url = "https://www.business.qld.gov.au/"), steps.items[1][1])
        assertEquals(GuideBlock.Important(listOf(Span("Please report any mite you find."))), guide.blocks[4])
        assertEquals(listOf("https://beeaware.org.au/archive-pest/varroa-mites/", "https://www.business.qld.gov.au/"), guide.links)
    }

    @Test
    fun `a page about one state's rules is only for gardens there`() {
        val queensland = GuideParser.parse("honey/qld", page.replace("order: 3", "order: 3\nstates: qld"))
        assertEquals(setOf(AuState.QLD), queensland.states)
        assertTrue(queensland.appliesTo(AuState.QLD))
        assertTrue(!queensland.appliesTo(AuState.NSW))
        assertTrue(!queensland.appliesTo(null))
        assertTrue("A page with no states is for everyone", GuideParser.parse("honey/varroa", page).appliesTo(null))
    }

    @Test
    fun `mistakes are refused with their line`() {
        fun refused(text: String, says: String) {
            val message = assertThrows(GuideFormatException::class.java) { GuideParser.parse("honey/test", text) }.message!!
            assertTrue(message, message.contains(says))
        }
        refused(page.replace("lastChecked: 2026-10-29", "lastChecked: 29 October"), "lastChecked")
        refused(page.replace("  - Varroa mites, BeeAware | https://beeaware.org.au/archive-pest/varroa-mites/\n", ""), "at least one source")
        refused(page.replace("https://www.business.qld.gov.au/", "http://www.business.qld.gov.au/"), "honey/test, line 21: links must be https://")
        refused(page.replace("## How often", "### How often"), "only ## headings")
        refused(page.replace("**1.1 mm**", "**1.1 mm"), "** is not closed")
        refused(page.replace("2. Shake", "3. Shake"), "numbered 1, 2, 3")
        refused(page.replace("section: honey", "section: wasps"), "section must be")
        refused(page.replace("order: 3", "order: 3\nauthor: me"), "unknown front matter field")
        refused(page.replace("order: 3", "order: 3\nstates: queensland"), "is not a state or territory")
    }
}
