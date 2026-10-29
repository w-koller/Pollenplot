package io.github.wkoller.pollenplot.report

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.withTranslation
import io.github.wkoller.pollenplot.domain.report.Report
import io.github.wkoller.pollenplot.domain.report.ReportCell
import io.github.wkoller.pollenplot.domain.report.ReportFigure
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

/** The tallest the Plot is drawn, in points, so a long narrow yard leaves room on its page. */
private const val PLOT_MAX_HEIGHT = 300f

/** How many pictures go in a row. */
private const val PER_ROW = 4

/** The space a table cell keeps around its words, 3 points on each side. */
private const val CELL_PADDING = 6f

/** The most of the page's width one column takes when others need room too. */
private const val MAX_COLUMN_SHARE = 0.35f

/**
 * Writes a garden report as an A4 PDF: its title and lines, then each section's paragraphs, the Plot drawn to scale,
 * its tables and its pictures.
 */
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
            // The Plot gives the overview before the tables, and the pictures come after them.
            section.figures.filterIsInstance<ReportFigure.Plot>().forEach { writer.plot(it) }
            section.tables.forEach { table -> writer.table(table) }
            section.figures.filterIsInstance<ReportFigure.Pictures>().forEach { writer.pictures(it) }
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
        private val zoneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 240, 224) }
        private val zoneEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 110, 80)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(160, 160, 160)
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        private val picturePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

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

        private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int = Int.MAX_VALUE): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(maxLines)
                .setEllipsize(if (maxLines == Int.MAX_VALUE) null else TextUtils.TruncateAt.END)
                .build()

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
            val widths = columnWidths(table.columns, cells)
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

        /** The Plot to scale, as wide as the page and no taller than [PLOT_MAX_HEIGHT], each zone with its name. */
        fun plot(plot: ReportFigure.Plot) {
            val left = plot.zones.minOf { it.xCm }
            val top = plot.zones.minOf { it.yCm }
            val spanX = (plot.zones.maxOf { it.xCm + it.widthCm } - left).coerceAtLeast(1)
            val spanY = (plot.zones.maxOf { it.yCm + it.heightCm } - top).coerceAtLeast(1)
            val scale = minOf(contentWidth / spanX, PLOT_MAX_HEIGHT / spanY)
            val height = spanY * scale
            keepTogether(height + 32f)
            text(plot.title, subheading)
            val originY = y
            plot.zones.forEach { zone ->
                val box = RectF(
                    margin + (zone.xCm - left) * scale,
                    originY + (zone.yCm - top) * scale,
                    margin + (zone.xCm - left + zone.widthCm) * scale,
                    originY + (zone.yCm - top + zone.heightCm) * scale,
                )
                canvas.drawRoundRect(box, 6f, 6f, zoneFill)
                canvas.drawRoundRect(box, 6f, 6f, zoneEdge)
                val inner = (box.width() - 8f).toInt()
                if (inner >= 12) {
                    val name = layout(zone.name, small, inner, maxLines = ((box.height() - 8f) / small.fontSpacing).toInt().coerceAtLeast(1))
                    canvas.withTranslation(box.left + 4f, box.top + 4f) { name.draw(this) }
                }
            }
            y = originY + height + 10f
        }

        /** Pictures in rows of [PER_ROW], each fitted into a square with its caption under it. */
        fun pictures(figure: ReportFigure.Pictures) {
            val cell = contentWidth / PER_ROW
            val side = cell - 12f
            keepTogether(side + 64f)
            text(figure.title, subheading)
            figure.items.chunked(PER_ROW).forEach { row ->
                val captions = row.map { layout(it.caption, small, side.toInt(), maxLines = 2) }
                val rowHeight = side + 4f + captions.maxOf { it.height } + 10f
                ensure(rowHeight)
                row.forEachIndexed { i, item ->
                    val x = margin + i * cell
                    picture(item.path, RectF(x, y, x + side, y + side))
                    canvas.withTranslation(x, y + side + 4f) { captions[i].draw(this) }
                }
                y += rowHeight
            }
            y += 4f
        }

        /** A picture fitted into [box]; a file that cannot be read leaves an empty frame. */
        private fun picture(path: String, box: RectF) {
            // Twice the size it is drawn at keeps it sharp in print without making the PDF large.
            val bitmap = decode(path, (box.width() * 2).toInt())
            if (bitmap == null) {
                canvas.drawRect(box, frame)
                return
            }
            val fit = minOf(box.width() / bitmap.width, box.height() / bitmap.height)
            val width = bitmap.width * fit
            val height = bitmap.height * fit
            canvas.drawBitmap(bitmap, null, RectF(box.centerX() - width / 2, box.centerY() - height / 2, box.centerX() + width / 2, box.centerY() + height / 2), picturePaint)
            bitmap.recycle()
        }

        /** A picture decoded at no less than [maxSide] pixels across, halved while it stays larger; null if it cannot be read. */
        private fun decode(path: String, maxSide: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }

        /**
         * Each column at least as wide as its longest word, measured in the font it is drawn in, so words wrap between
         * them rather than inside them. The width left over goes to the columns whose text wants more, up to
         * [MAX_COLUMN_SHARE] of the page each. Only when the words alone are wider than the page are they squeezed.
         */
        private fun columnWidths(columns: List<String>, cells: List<List<String>>): List<Float> {
            fun longestWord(text: String, paint: TextPaint) = text.split(' ').maxOf { paint.measureText(it) }
            val least = columns.indices.map { i ->
                maxOf(longestWord(columns[i], smallBold), cells.maxOfOrNull { longestWord(it[i], small) } ?: 0f) + CELL_PADDING
            }
            val wanted = columns.indices.map { i ->
                val whole = maxOf(smallBold.measureText(columns[i]), cells.maxOfOrNull { small.measureText(it[i]) } ?: 0f) + CELL_PADDING
                maxOf(least[i], minOf(whole, contentWidth * MAX_COLUMN_SHARE))
            }
            val leastTotal = least.sum()
            if (leastTotal >= contentWidth) return least.map { it / leastTotal * contentWidth }
            val room = contentWidth - leastTotal
            val more = wanted.zip(least) { want, min -> want - min }
            val moreTotal = more.sum()
            return if (moreTotal <= room) {
                // Every column gets what it wants, and what is still left is shared out by width.
                val spare = room - moreTotal
                wanted.map { it + spare * it / wanted.sum() }
            } else {
                least.mapIndexed { i, min -> min + room * more[i] / moreTotal }
            }
        }

        private fun cellText(cell: ReportCell): String = when (cell) {
            is ReportCell.Text -> cell.text
            is ReportCell.Number -> cell.value.stripTrailingZeros().toPlainString()
            is ReportCell.Day -> UiFormat.longDate(cell.date)
        }
    }
}
