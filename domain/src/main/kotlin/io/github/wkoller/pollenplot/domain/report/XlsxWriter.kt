package io.github.wkoller.pollenplot.domain.report

import java.io.OutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes a report as an Excel workbook (.xlsx) with the Java zip library alone: one sheet for each table, with a bold
 * header row that stays put while scrolling, filters on the header, numbers as numbers and days as dates.
 */
object XlsxWriter {
    /** Excel counts days from 30 December 1899. */
    private val EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private const val MAX_SHEET_NAME = 31

    fun write(report: Report, out: OutputStream) {
        val tables = report.tables.ifEmpty { listOf(ReportTable(report.title, listOf(report.title), report.lines.map { listOf(ReportCell.Text(it)) })) }
        val names = sheetNames(tables.map { it.title })
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, xml: String) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(tables.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(names))
            entry("xl/_rels/workbook.xml.rels", workbookRels(tables.size))
            entry("xl/styles.xml", STYLES)
            tables.forEachIndexed { index, table -> entry("xl/worksheets/sheet${index + 1}.xml", sheet(table)) }
        }
    }

    /** Names Excel accepts: at most 31 characters, none of : \ / ? * [ ], and each once. */
    fun sheetNames(titles: List<String>): List<String> {
        val used = mutableSetOf<String>()
        return titles.map { title ->
            val clean = title.replace(Regex("""[:\\/?*\[\]]"""), " ").trim().ifEmpty { "Sheet" }.take(MAX_SHEET_NAME)
            var name = clean
            var count = 2
            while (name.lowercase() in used) {
                val suffix = " ($count)"
                name = clean.take(MAX_SHEET_NAME - suffix.length) + suffix
                count++
            }
            used += name.lowercase()
            name
        }
    }

    private fun sheet(table: ReportTable): String = buildString {
        val lastColumn = column(table.columns.size - 1)
        val lastRow = table.rows.size + 1
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<sheetViews><sheetView workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        append("""<cols>""")
        table.columns.forEachIndexed { index, _ -> append("""<col min="${index + 1}" max="${index + 1}" width="${columnWidth(table, index)}" customWidth="1"/>""") }
        append("""</cols><sheetData>""")
        append("""<row r="1">""")
        table.columns.forEachIndexed { index, name -> append(textCell("${column(index)}1", name, style = 1)) }
        append("</row>")
        table.rows.forEachIndexed { rowIndex, row ->
            val r = rowIndex + 2
            append("""<row r="$r">""")
            row.forEachIndexed { index, cell ->
                val ref = "${column(index)}$r"
                append(
                    when (cell) {
                        is ReportCell.Text -> textCell(ref, cell.text, style = 0)
                        is ReportCell.Number -> """<c r="$ref"><v>${cell.value.toPlainString()}</v></c>"""
                        is ReportCell.Day -> """<c r="$ref" s="2"><v>${ChronoUnit.DAYS.between(EPOCH, cell.date)}</v></c>"""
                    },
                )
            }
            append("</row>")
        }
        append("</sheetData>")
        append("""<autoFilter ref="A1:$lastColumn$lastRow"/>""")
        append("</worksheet>")
    }

    private fun textCell(ref: String, text: String, style: Int): String =
        """<c r="$ref" t="inlineStr"${if (style != 0) " s=\"$style\"" else ""}><is><t xml:space="preserve">${escape(text)}</t></is></c>"""

    /** A column's width in characters, from its longest cell, between 8 and 60. */
    private fun columnWidth(table: ReportTable, index: Int): Int {
        val longest = (listOf(table.columns[index]) + table.rows.map { row ->
            when (val cell = row[index]) {
                is ReportCell.Text -> cell.text
                is ReportCell.Number -> cell.value.toPlainString()
                is ReportCell.Day -> "00 Mmm 0000"
            }
        }).maxOf { it.length }
        return (longest + 2).coerceIn(8, 60)
    }

    /** A column's letters: 0 is A, 25 is Z, 26 is AA. */
    fun column(index: Int): String {
        var n = index + 1
        val letters = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            letters.insert(0, 'A' + rem)
            n = (n - 1) / 26
        }
        return letters.toString()
    }

    fun escape(text: String): String = buildString {
        for (ch in text) {
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                // XML 1.0 has no place for most control characters, so they are left out.
                ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    private fun contentTypes(sheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheets) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("</Types>")
    }

    private fun workbook(names: List<String>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        names.forEachIndexed { index, name -> append("""<sheet name="${escape(name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(sheets: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheets) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        append("""<Relationship Id="rId${sheets + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    /** Three cell styles: plain, bold for the header, and a day such as 28 Oct 2026. */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="d mmm yyyy"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs></styleSheet>"""
}
