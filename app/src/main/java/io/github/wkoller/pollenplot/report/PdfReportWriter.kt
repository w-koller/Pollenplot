package io.github.wkoller.pollenplot.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import io.github.wkoller.pollenplot.domain.report.Report
import io.github.wkoller.pollenplot.domain.report.ReportCell
import io.github.wkoller.pollenplot.domain.report.ReportTable
import io.github.wkoller.pollenplot.ui.UiFormat
import java.io.OutputStream
import kotlin.math.max

/** Where report pages are drawn: a PDF in the app, or pictures in tests. */
interface PageSink {
    fun begin(number: Int, width: Int, height: Int): Canvas
    fun end()
}

private class PdfSink(private val document: PdfDocument) : PageSink {
    private var page: PdfDocument.Page? = null

    override fun begin(number: Int, width: Int, height: Int): Canvas =
        document.startPage(PdfDocument.PageInfo.Builder(width, height, number).create()).also { page = it }.canvas

    override fun end() {
        page?.let(document::finishPage)
        page = null
    }
}

/** Writes a garden report as an A4 PDF: its title and lines, then each section's paragraphs and tables. */
class PdfReportWriter(private val footer: String) {
    fun write(report: Report, out: OutputStream) {
        val document = PdfDocument()
        render(report, PdfSink(document))
        document.writeTo(out)
        document.close()
    }

    fun render(report: Report, sink: PageSink) {
        val writer = PageWriter(sink, footer)
        writer.text(report.title, writer.title)
        report.lines.forEach { writer.text(it, writer.body) }
        report.sections.forEach { section ->
            writer.gap()
            writer.keepTogether(60f)
            writer.text(section.title, writer.heading)
            section.paragraphs.forEach { writer.text(it, writer.body) }
            section.tables.forEach { table -> writer.table(table) }
        }
        writer.finish()
    }

    /** Lays text out down A4 pages, starting a new page when the next piece would not fit. */
    private class PageWriter(private val sink: PageSink, private val footer: String) {
        private val width = 595
        private val height = 842
        private val margin = 40f
        private val contentWidth = width - 2 * margin
        private val bottom = height - margin - 24f
        private var pageNumber = 0
        private var open = false
        private lateinit var canvas: Canvas
        private var y = margin

        val title = paint(18f, bold = true)
        val heading = paint(13f, bold = true)
        val body = paint(10f)
        private val small = paint(8f)
        private val smallBold = paint(8f, bold = true)
        private val subheading = paint(10.5f, bold = true)
        private val line = Paint().apply { color = Color.rgb(120, 120, 120); strokeWidth = 0.6f }

        init {
            newPage()
        }

        private fun paint(size: Float, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.rgb(20, 20, 20)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        private fun newPage() {
            if (open) finishPage()
            pageNumber++
            canvas = sink.begin(pageNumber, width, height)
            open = true
            y = margin
        }

        private fun finishPage() {
            val layout = layout("$footer  ·  $pageNumber", small, contentWidth.toInt())
            canvas.withTranslation(margin, height - margin) { layout.draw(this) }
            sink.end()
            open = false
        }

        fun finish() {
            if (open) finishPage()
        }

        fun gap() {
            y += 10f
        }

        fun keepTogether(space: Float) = ensure(space)

        private fun ensure(space: Float) {
            if (y + space > bottom) newPage()
        }

        private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()

        fun text(text: String, paint: TextPaint) {
            val layout = layout(text, paint, contentWidth.toInt())
            ensure(layout.height.toFloat())
            canvas.withTranslation(margin, y) { layout.draw(this) }
            y += layout.height + 4f
        }

        /** A table with its title, a bold header and a line under each row, in columns as wide as their words need. */
        fun table(table: ReportTable) {
            if (table.rows.isEmpty()) return
            keepTogether(48f)
            text(table.title, subheading)
            val cells = table.rows.map { row -> row.map(::cellText) }
            // Each column's share follows its longest text, up to a limit, and is never narrower than its longest word, so
            // words wrap between them rather than inside them.
            val weights = table.columns.indices.map { i ->
                val texts = listOf(table.columns[i]) + cells.map { it[i] }
                val longestWord = texts.flatMap { it.split(' ') }.maxOf { it.length } + 1
                max(longestWord, texts.maxOf { it.length }.coerceAtMost(40)).toFloat()
            }
            val total = weights.sum()
            val widths = weights.map { it / total * contentWidth }
            fun drawRow(row: List<String>, paint: TextPaint) {
                val layouts = row.mapIndexed { i, text -> layout(text, paint, (widths[i] - 6f).toInt().coerceAtLeast(10)) }
                val rowHeight = max(layouts.maxOf { it.height }.toFloat(), paint.textSize) + 6f
                if (y + rowHeight > bottom) {
                    newPage()
                }
                var x = margin
                layouts.forEachIndexed { i, layout ->
                    canvas.withTranslation(x + 3f, y + 3f) { layout.draw(this) }
                    x += widths[i]
                }
                y += rowHeight
                canvas.drawLine(margin, y, margin + contentWidth, y, line)
            }
            drawRow(table.columns, smallBold)
            cells.forEach { drawRow(it, small) }
            y += 8f
        }

        private fun cellText(cell: ReportCell): String = when (cell) {
            is ReportCell.Text -> cell.text
            is ReportCell.Number -> cell.value.stripTrailingZeros().toPlainString()
            is ReportCell.Day -> UiFormat.longDate(cell.date)
        }
    }
}
