package io.github.wkoller.pollenplot.domain.report

import java.math.BigDecimal
import java.time.LocalDate

/** A cell of a report table: words, a number or a day, so a spreadsheet can sort and sum it. */
sealed interface ReportCell {
    data class Text(val text: String) : ReportCell

    data class Number(val value: BigDecimal) : ReportCell

    data class Day(val date: LocalDate) : ReportCell
}

data class ReportTable(val title: String, val columns: List<String>, val rows: List<List<ReportCell>>) {
    init {
        require(rows.all { it.size == columns.size }) { "Every row of $title needs ${columns.size} cells" }
    }
}

/** A zone where it lies in the yard, in centimetres from the top left of the Plot, with its name. */
data class PlotZone(val name: String, val xCm: Int, val yCm: Int, val widthCm: Int, val heightCm: Int)

/** A picture kept by the app, as a file, with the words under it. */
data class ReportPicture(val path: String, val caption: String)

/** A picture in a report. The PDF draws it; a spreadsheet has no place for it and leaves it out. */
sealed interface ReportFigure {
    /** The Plot drawn to scale: each zone in its place. */
    data class Plot(val title: String, val zones: List<PlotZone>) : ReportFigure

    /** Pictures in a grid, such as the plants' cut-outs. */
    data class Pictures(val title: String, val items: List<ReportPicture>) : ReportFigure
}

data class ReportSection(
    val title: String,
    val paragraphs: List<String>,
    val tables: List<ReportTable>,
    val figures: List<ReportFigure> = emptyList(),
) {
    /** Whether nothing was recorded for it: no words, no rows and no pictures. */
    val isEmpty: Boolean get() = paragraphs.isEmpty() && tables.all { it.rows.isEmpty() } && figures.isEmpty()
}

/**
 * A garden report, built once and written as a PDF for reading or a spreadsheet for working with, so both say the
 * same. The words are already in the reader's language.
 */
data class Report(val title: String, val lines: List<String>, val sections: List<ReportSection>) {
    val tables: List<ReportTable> get() = sections.flatMap { it.tables }
}
