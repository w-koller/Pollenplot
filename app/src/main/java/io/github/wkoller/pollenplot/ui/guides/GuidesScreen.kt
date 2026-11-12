package io.github.wkoller.pollenplot.ui.guides

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.guides.GuideLibrary
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.domain.guide.GuideSection
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.settings.SectionHeading

/**
 * Every guide page in one list, under the part of the garden it is about. The pages are also listed where they are
 * used, on the Bees and Water tabs; this is where they are all found. A page about one state's rules is listed only
 * for a garden in [state].
 */
@Composable
fun GuidesScreen(guides: GuideLibrary, state: AuState?, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val sections by produceState(emptyList<Pair<GuideSection, List<Guide>>>(), state) {
        value = GuideSection.entries.map { section -> section to guides.section(section).filter { it.appliesTo(state) } }.filter { it.second.isNotEmpty() }
    }
    DetailScaffold(title = stringResource(R.string.guides_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            sections.forEach { (section, pages) ->
                item(key = "section-" + section.name) { SectionHeading(stringResource(section.label)) }
                items(pages, key = { it.id }) { page ->
                    ListItem(
                        onClick = { onOpen(page.id) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_menu_book), contentDescription = null) },
                        supportingContent = { Text(page.summary) },
                    ) { Text(page.title) }
                }
            }
        }
    }
}

private val GuideSection.label: Int
    get() = when (this) {
        GuideSection.HONEY -> R.string.guides_honey
        GuideSection.NATIVE -> R.string.guides_native
        GuideSection.HOTELS -> R.string.guides_hotels
        GuideSection.WATER -> R.string.guides_water
    }
