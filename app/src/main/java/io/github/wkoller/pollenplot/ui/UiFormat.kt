package io.github.wkoller.pollenplot.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.season.MonthSet
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** Number, list and month formatting for the screens. Values are stored as whole units and shown in everyday ones. */
object UiFormat {
    /** Centimetres as metres with one decimal place, such as "4.5". Whole metres drop the decimal, such as "4". */
    fun metresFromCm(cm: Int): String = decimal(BigDecimal(cm).movePointLeft(2), 1)

    /** Millimetres as metres, such as "1.5" or "30". */
    fun metresFromMm(mm: Int): String = decimal(BigDecimal(mm).movePointLeft(3), 1)

    /** Square centimetres as square metres, such as "12" or "4.5". */
    fun squareMetres(cm2: Long): String = decimal(BigDecimal(cm2).movePointLeft(4), 1)

    /** Basis points as a fraction, such as 2500 as "0.25". */
    fun fraction(bp: Int): String = decimal(BigDecimal(bp).movePointLeft(4), 2)

    /** pH in tenths, such as 55 as "5.5". */
    fun ph(tenths: Int): String = decimal(BigDecimal(tenths).movePointLeft(1), 1)

    /** Parses square metres typed by the user into square centimetres, or null if it is not a positive number. */
    fun parseSquareMetres(text: String): Long? {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.movePointRight(4).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun decimal(value: BigDecimal, places: Int): String =
        value.setScale(places, RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

    /** "a", "a and b", "a, b and c", using the words in [words]. */
    fun joinAnd(items: List<String>, words: ListWords = ListWords.ENGLISH): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> String.format(words.and, items.dropLast(1).joinToString(", "), items.last())
    }

    /**
     * Months as runs, such as "September to November" or "November to February", in the order the runs start in the
     * calendar. Runs can wrap past December, as flowering often does in the south.
     */
    fun months(set: MonthSet, words: ListWords = ListWords.ENGLISH, locale: Locale = Locale.getDefault()): String {
        fun name(month: Month) = month.getDisplayName(TextStyle.FULL, locale)
        if (set == MonthSet.ALL) return words.allYear
        val runs = Month.entries
            .filter { it in set && it.minus(1) !in set }
            .map { start ->
                var end = start
                while (end.plus(1) in set && end.plus(1) != start) end = end.plus(1)
                if (end == start) name(start) else String.format(words.range, name(start), name(end))
            }
        return joinAnd(runs, words)
    }
}

/** The joining words for lists and month ranges, read from strings.xml on screens. */
data class ListWords(val and: String, val range: String, val allYear: String) {
    companion object {
        val ENGLISH = ListWords(and = "%1\$s and %2\$s", range = "%1\$s to %2\$s", allYear = "all year")
    }
}

@Composable
fun listWords(): ListWords = ListWords(
    and = stringResource(R.string.list_and),
    range = stringResource(R.string.list_range),
    allYear = stringResource(R.string.list_all_year),
)
