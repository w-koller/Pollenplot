package io.github.wkoller.pollenplot.domain.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class XlsxWriterTest {
    private val report = Report(
        title = "Garden report",
        lines = listOf("Prepared on 28 October 2026"),
        sections = listOf(
            ReportSection(
                "Honey bees", emptyList(),
                listOf(
                    ReportTable(
                        "Mite checks", listOf("Day", "Hive", "Mites per 100 bees"),
                        listOf(
                            listOf(ReportCell.Day(LocalDate.of(2026, 10, 28)), ReportCell.Text("Hive 1 & <the> \"big\" one"), ReportCell.Number(BigDecimal("1.3"))),
                            listOf(ReportCell.Day(LocalDate.of(2026, 9, 1)), ReportCell.Text("Hive 2"), ReportCell.Number(BigDecimal.ZERO)),
                        ),
                    ),
                ),
            ),
            ReportSection("Wildlife", emptyList(), listOf(ReportTable("Sightings: this year?", listOf("Animal"), listOf(listOf(ReportCell.Text("Kookaburra")))))),
        ),
    )

    private fun entries(bytes: ByteArray): Map<String, String> {
        val found = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                found[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return found
    }

    @Test
    fun `a report becomes a workbook with a sheet for each table`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(report, out)
        // A copy to open in a spreadsheet program by hand.
        java.io.File("build/report-sample.xlsx").writeBytes(out.toByteArray())
        val files = entries(out.toByteArray())
        assertTrue(files.keys.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml")))
        // Every part is well-formed XML.
        val parser = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        files.values.forEach { parser.parse(ByteArrayInputStream(it.toByteArray())) }

        val sheet = files.getValue("xl/worksheets/sheet1.xml")
        assertTrue("The header stays put", sheet.contains("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>"""))
        assertTrue("The header has filters", sheet.contains("""<autoFilter ref="A1:C3"/>"""))
        assertTrue("A day is a date cell, counted from 30 December 1899", sheet.contains("""<c r="A2" s="2"><v>46323</v></c>"""))
        assertTrue("A number is a number", sheet.contains("""<c r="C2"><v>1.3</v></c>"""))
        assertTrue(sheet.contains("Hive 1 &amp; &lt;the&gt; &quot;big&quot; one"))
        assertTrue("The second sheet's name loses the colon and question mark", files.getValue("xl/workbook.xml").contains("""name="Sightings  this year""""))
    }

    @Test
    fun `sheet names are cut to 31 characters and kept apart`() {
        assertEquals(listOf("Plants", "plants (2)", "A".repeat(31)), XlsxWriter.sheetNames(listOf("Plants", "plants", "A".repeat(40))))
        assertEquals("AA", XlsxWriter.column(26))
        assertEquals("AZ", XlsxWriter.column(51))
    }
}
