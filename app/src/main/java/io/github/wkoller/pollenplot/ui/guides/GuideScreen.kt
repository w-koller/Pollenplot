package io.github.wkoller.pollenplot.ui.guides

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import io.github.wkoller.pollenplot.ui.settings.SectionHeading
import java.net.URI

/** One page of a bee guide, then the day its facts were last checked and the pages they come from. */
@Composable
fun GuideScreen(library: GuideLibrary, id: String, onBack: () -> Unit) {
    val guide by produceState<Guide?>(initialValue = null, id) { value = library.guide(id) }
    val uri = LocalUriHandler.current
    DetailScaffold(title = guide?.title.orEmpty(), onBack = onBack) { padding ->
        val page = guide ?: return@DetailScaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            items(page.blocks) { block -> Block(block) }
            item {
                Text(
                    stringResource(R.string.guide_last_checked, UiFormat.longDate(page.lastChecked)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
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
    val gap = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    when (block) {
        is GuideBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp).semantics { heading() },
        )
        is GuideBlock.Paragraph -> Text(styled(block.spans), style = body, modifier = gap)
        is GuideBlock.Bullets -> Column(gap, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEach { item -> Item("•", item) }
        }
        is GuideBlock.Steps -> Column(gap, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, item -> Item("${index + 1}.", item) }
        }
        is GuideBlock.Important -> Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null)
                Text(styled(block.spans), style = body)
            }
        }
    }
}

/** A list item with its bullet or number, which TalkBack reads with the item. */
@Composable
private fun Item(marker: String, spans: List<Span>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(marker, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(min = 20.dp))
        Text(styled(spans), style = MaterialTheme.typography.bodyLarge)
    }
}

/** The runs of a line as one text, with bold, italic and links that open in the browser. */
@Composable
private fun styled(spans: List<Span>): AnnotatedString {
    val link = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        for (span in spans) {
            val style = SpanStyle(fontWeight = if (span.bold) FontWeight.SemiBold else null, fontStyle = if (span.italic) FontStyle.Italic else null)
            val url = span.url
            if (url != null) {
                withLink(LinkAnnotation.Url(url, link)) { withStyle(style) { append(span.text) } }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
}
