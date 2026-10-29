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

data class ReportSection(val title: String, val paragraphs: List<String>, val tables: List<ReportTable>)

/**
 * A garden report, built once and written as a PDF for reading or a spreadsheet for working with, so both say the
 * same. The words are already in the reader's language.
 */
data class Report(val title: String, val lines: List<String>, val sections: List<ReportSection>) {
    val tables: List<ReportTable> get() = sections.flatMap { it.tables }
}
