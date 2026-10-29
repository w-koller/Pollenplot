package io.github.wkoller.pollenplot.domain.guide

import io.github.wkoller.pollenplot.domain.plants.AuState
import java.time.LocalDate

/** The guides: the three bee sections' and the Water tab's. */
enum class GuideSection { HONEY, NATIVE, HOTELS, WATER }

/** A page a guide's facts come from. */
data class GuideSource(val title: String, val url: String)

/** A run of text with one style, and a link when [url] is set. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val url: String? = null)

/** A part of a guide page, in reading order. */
sealed interface GuideBlock {
    data class Heading(val text: String) : GuideBlock

    data class Paragraph(val spans: List<Span>) : GuideBlock

    data class Bullets(val items: List<List<Span>>) : GuideBlock

    /** Numbered steps, done in order. */
    data class Steps(val items: List<List<Span>>) : GuideBlock

    /** Something the reader must not miss, such as what to report. */
    data class Important(val spans: List<Span>) : GuideBlock
}

/**
 * One page of a bee guide: what it is about, when its facts were last checked against its sources, and its text. A
 * page comes from a Markdown file with front matter, which [GuideParser] reads.
 */
data class Guide(
    val id: String,
    val section: GuideSection,
    val order: Int,
    val title: String,
    val summary: String,
    val lastChecked: LocalDate,
    val sources: List<GuideSource>,
    val blocks: List<GuideBlock>,
    /** The states whose rules the page describes; a page for every state names none. */
    val states: Set<AuState> = emptySet(),
) {
    /** Whether the page is for a garden in [state]; a page about one state's rules is not shown elsewhere. */
    fun appliesTo(state: AuState?): Boolean = states.isEmpty() || state in states

    /** Every web address the page links to, in its text and its sources. */
    val links: List<String>
        get() = sources.map { it.url } + blocks.flatMap { block ->
            when (block) {
                is GuideBlock.Heading -> emptyList()
                is GuideBlock.Paragraph -> block.spans
                is GuideBlock.Important -> block.spans
                is GuideBlock.Bullets -> block.items.flatten()
                is GuideBlock.Steps -> block.items.flatten()
            }.mapNotNull { it.url }
        }
}

class GuideFormatException(message: String) : IllegalArgumentException(message)
