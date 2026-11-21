package io.github.wkoller.pollenplot.ui.guides

import io.github.wkoller.pollenplot.ui.components.readableSides
import io.github.wkoller.pollenplot.ui.components.heroGone
import io.github.wkoller.pollenplot.ui.theme.BotanicalSpan
import io.github.wkoller.pollenplot.ui.theme.Spacing
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.wkoller.pollenplot.ui.components.SectionHeading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.guides.GuideLibrary
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.domain.guide.GuideBlock
import io.github.wkoller.pollenplot.domain.guide.Span
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.Found
import io.github.wkoller.pollenplot.ui.components.MissingRecord
import java.net.URI

/**
 * One page of a bee guide: its title large in the display face with its summary under it, like the opening of an
 * article, then the page, the day its facts were last checked and the pages they come from. The top bar names the page
 * once the title has scrolled away.
 */
@Composable
fun GuideScreen(library: GuideLibrary, id: String, onBack: () -> Unit) {
    val found by produceState<Found<Guide>?>(initialValue = null, id) { value = Found(library.guide(id)) }
    val guide = found?.value
    val uri = LocalUriHandler.current
    val list = rememberLazyListState()
    DetailScaffold(title = guide?.title.orEmpty(), onBack = onBack, titleShown = heroGone(list)) { padding ->
        if (guide == null) {
            if (found != null) MissingRecord(padding)
            return@DetailScaffold
        }
        val page = guide
        LazyColumn(
            Modifier.fillMaxSize(),
            state = list,
            contentPadding = PaddingValues(start = readableSides(), end = readableSides(), top = padding.calculateTopPadding() + Spacing.s, bottom = padding.calculateBottomPadding() + Spacing.xl),
        ) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(page.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(page.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(page.blocks) { block -> Block(block) }
            item {
                Text(
                    stringResource(R.string.guide_last_checked, UiFormat.longDate(page.lastChecked)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.l),
                )
            }
            item { SectionHeading(stringResource(R.string.guide_sources)) }
            items(page.sources) { source ->
                ListItem(
                    onClick = { uri.openUri(source.url) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                    supportingContent = { Text(runCatching { URI(source.url).host }.getOrNull().orEmpty()) },
                ) { Text(source.title) }
            }
        }
    }
}

@Composable
private fun Block(block: GuideBlock) {
    val body = MaterialTheme.typography.bodyLarge
    val gap = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s)
    when (block) {
        is GuideBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.xl, bottom = Spacing.xs).semantics { heading() },
        )
        is GuideBlock.Paragraph -> Text(styled(block.spans), style = body, modifier = gap)
        is GuideBlock.Bullets -> Column(gap, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            block.items.forEach { item -> Item("•", item) }
        }
        is GuideBlock.Steps -> Column(gap, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            block.items.forEachIndexed { index, item -> Item("${index + 1}.", item) }
        }
        // What must not be missed, in the bees' wattle gold, since every guide is about bees.
        is GuideBlock.Important -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Row(Modifier.padding(Spacing.l), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                Text(styled(block.spans), style = body)
            }
        }
    }
}

/** A list item with its bullet or number, which TalkBack reads with the item. */
@Composable
private fun Item(marker: String, spans: List<Span>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(marker, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = MARKER_WIDTH))
        Text(styled(spans), style = MaterialTheme.typography.bodyLarge)
    }
}

/** How wide a bullet or a step's number is, so the items line up. */
private val MARKER_WIDTH = 20.dp

/**
 * The runs of a line as one text, with bold, links that open in the browser, and italic, which the guides keep for
 * species names and so set in the botanical italic.
 */
@Composable
private fun styled(spans: List<Span>): AnnotatedString {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        for (span in spans) {
            val base = if (span.italic) BotanicalSpan else SpanStyle()
            val style = base.merge(SpanStyle(fontWeight = if (span.bold) FontWeight.SemiBold else null))
            val url = span.url
            if (url != null) {
                withLink(LinkAnnotation.Url(url, link)) { withStyle(style) { append(span.text) } }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
}
