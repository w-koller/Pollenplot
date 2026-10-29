package io.github.wkoller.pollenplot.domain.guide

import io.github.wkoller.pollenplot.domain.plants.AuState
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Reads a guide page: front matter between two `---` lines, then a small part of Markdown. The front matter holds
 * `title`, `summary`, `section` (honey, native, hotels or water), `order`, `lastChecked` (a date such as 2026-10-29),
 * `sources`, one per line as `  - Title | https://…`, and, for a page about one state's rules, `states`, such as qld. The text has `## ` headings, paragraphs, `- ` bullets, `1. `
 * steps and `> ` notes, separated by blank lines, with `**bold**`, `*italic*` and `[links](https://…)` inside them.
 * Anything else is refused with the line it is on, so a mistake fails the build instead of showing on a phone.
 */
object GuideParser {
    fun parse(id: String, text: String): Guide {
        val lines = text.replace("\r\n", "\n").lines()
        if (lines.firstOrNull()?.trim() != "---") fail(id, 1, "the page must start with front matter between two --- lines")
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }.takeIf { it >= 0 }?.plus(1)
            ?: fail(id, 1, "the front matter is not closed with ---")
        val fields = mutableMapOf<String, String>()
        val sources = mutableListOf<GuideSource>()
        var inSources = false
        for (index in 1 until end) {
            val line = lines[index]
            val number = index + 1
            when {
                line.isBlank() -> Unit
                inSources && line.trimStart().startsWith("- ") -> {
                    val parts = line.trimStart().removePrefix("- ").split(" | ")
                    if (parts.size != 2 || parts[0].isBlank()) fail(id, number, "a source is written as \"- Title | https://…\"")
                    sources += GuideSource(parts[0].trim(), checkedUrl(id, number, parts[1].trim()))
                }
                line.startsWith(" ") -> fail(id, number, "only sources are indented in the front matter")
                else -> {
                    val key = line.substringBefore(":", missingDelimiterValue = "").trim()
                    if (key.isEmpty()) fail(id, number, "front matter lines are written as \"key: value\"")
                    val value = line.substringAfter(":").trim()
                    inSources = key == "sources"
                    if (key == "sources") {
                        if (value.isNotEmpty()) fail(id, number, "sources go on the lines below, one per line")
                    } else {
                        if (key !in FIELDS) fail(id, number, "unknown front matter field \"$key\"")
                        if (key in fields) fail(id, number, "\"$key\" is given twice")
                        fields[key] = value
                    }
                }
            }
        }
        fun field(name: String): String = fields[name]?.takeIf { it.isNotBlank() } ?: fail(id, 1, "the front matter has no $name")
        val section = GuideSection.entries.firstOrNull { it.name.equals(field("section"), ignoreCase = true) }
            ?: fail(id, 1, "section must be honey, native, hotels or water")
        val order = field("order").toIntOrNull() ?: fail(id, 1, "order must be a whole number")
        val lastChecked = try {
            LocalDate.parse(field("lastChecked"))
        } catch (e: DateTimeParseException) {
            fail(id, 1, "lastChecked must be a date such as 2026-10-29")
        }
        if (sources.isEmpty()) fail(id, 1, "a page needs at least one source")
        val states = fields["states"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { name ->
            AuState.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: fail(id, 1, "\"$name\" is not a state or territory, such as qld")
        }.toSet()
        val blocks = blocks(id, lines, end + 1)
        if (blocks.isEmpty()) fail(id, end + 2, "the page has no text")
        return Guide(id, section, order, field("title"), field("summary"), lastChecked, sources, blocks, states)
    }

    /** The text after the front matter, from the 0-based line [start]. */
    private fun blocks(id: String, lines: List<String>, start: Int): List<GuideBlock> {
        val blocks = mutableListOf<GuideBlock>()
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            val number = index + 1
            when {
                line.isBlank() -> index++
                line.startsWith("## ") -> {
                    val heading = line.removePrefix("## ").trim()
                    if (heading.isEmpty()) fail(id, number, "a heading needs words")
                    blocks += GuideBlock.Heading(heading)
                    index++
                }
                line.startsWith("#") -> fail(id, number, "only ## headings are used; the title is in the front matter")
                line.startsWith("- ") -> {
                    val (items, next) = items(id, lines, index, "- ")
                    blocks += GuideBlock.Bullets(items)
                    index = next
                }
                STEP.containsMatchIn(line) -> {
                    val (items, next) = items(id, lines, index, null)
                    blocks += GuideBlock.Steps(items)
                    index = next
                }
                line.startsWith(">") -> {
                    val text = StringBuilder()
                    while (index < lines.size && lines[index].startsWith(">")) {
                        text.append(' ').append(lines[index].removePrefix(">").trim())
                        index++
                    }
                    blocks += GuideBlock.Important(spans(id, number, text.toString().trim()))
                }
                line.startsWith(" ") -> fail(id, number, "text is not indented, except to carry on a list item")
                else -> {
                    val text = StringBuilder()
                    while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines[index])) {
                        text.append(' ').append(lines[index].trim())
                        index++
                    }
                    blocks += GuideBlock.Paragraph(spans(id, number, text.toString().trim()))
                }
            }
        }
        return blocks
    }

    private fun startsBlock(line: String) = line.startsWith("#") || line.startsWith("- ") || line.startsWith(">") || STEP.containsMatchIn(line)

    /**
     * A list from line [start]: each item starts with [marker], or with its number for steps, and carries on over
     * indented lines. Steps must be numbered from 1 in order.
     */
    private fun items(id: String, lines: List<String>, start: Int, marker: String?): Pair<List<List<Span>>, Int> {
        val items = mutableListOf<List<Span>>()
        var index = start
        while (index < lines.size) {
            val line = lines[index]
            val number = index + 1
            val body = if (marker != null) {
                if (!line.startsWith(marker)) break
                line.removePrefix(marker)
            } else {
                val match = STEP.find(line) ?: break
                if (match.groupValues[1].toInt() != items.size + 1) fail(id, number, "steps are numbered 1, 2, 3 in order")
                line.substring(match.range.last + 1)
            }
            val text = StringBuilder(body.trim())
            index++
            while (index < lines.size && lines[index].startsWith("  ") && lines[index].isNotBlank()) {
                text.append(' ').append(lines[index].trim())
                index++
            }
            items += spans(id, number, text.toString())
        }
        return items to index
    }

    /** The styled runs in a line of text. */
    internal fun spans(id: String, number: Int, text: String): List<Span> {
        val spans = mutableListOf<Span>()
        val plain = StringBuilder()
        fun flush() {
            if (plain.isNotEmpty()) spans += Span(plain.toString())
            plain.clear()
        }
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val close = text.indexOf("**", i + 2).takeIf { it > i + 2 } ?: fail(id, number, "** is not closed")
                    flush()
                    spans += Span(text.substring(i + 2, close), bold = true)
                    i = close + 2
                }
                text[i] == '*' -> {
                    val close = text.indexOf('*', i + 1).takeIf { it > i + 1 } ?: fail(id, number, "* is not closed")
                    flush()
                    spans += Span(text.substring(i + 1, close), italic = true)
                    i = close + 1
                }
                text[i] == '[' -> {
                    val middle = text.indexOf("](", i).takeIf { it > i + 1 } ?: fail(id, number, "a link is written as [words](https://…)")
                    val close = text.indexOf(')', middle).takeIf { it > middle + 2 } ?: fail(id, number, "a link's address is not closed with )")
                    flush()
                    spans += Span(text.substring(i + 1, middle), url = checkedUrl(id, number, text.substring(middle + 2, close)))
                    i = close + 1
                }
                else -> {
                    plain.append(text[i])
                    i++
                }
            }
        }
        flush()
        return spans
    }

    private fun checkedUrl(id: String, number: Int, url: String): String {
        if (!url.startsWith("https://") || url.any { it.isWhitespace() }) fail(id, number, "links must be https:// addresses, without spaces")
        return url
    }

    private fun fail(id: String, number: Int, message: String): Nothing = throw GuideFormatException("$id, line $number: $message")

    private val FIELDS = setOf("title", "summary", "section", "order", "lastChecked", "states")
    private val STEP = Regex("""^(\d+)\. """)
}
