package io.github.wkoller.pollenplot.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import io.github.wkoller.pollenplot.ui.components.HeroPictureSize
import io.github.wkoller.pollenplot.ui.components.RecordHero
import io.github.wkoller.pollenplot.ui.components.heroGone
import io.github.wkoller.pollenplot.ui.theme.IconSize
import io.github.wkoller.pollenplot.ui.theme.Spacing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.library.LibraryPhoto
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.RoleBadges
import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.plants.UnknownPlantNotice
import io.github.wkoller.pollenplot.ui.plants.facts
import io.github.wkoller.pollenplot.ui.review.WildStatusLine
import io.github.wkoller.pollenplot.ui.weeds.WeedNotice

/**
 * One plant of the plant library, shown before it is added: its picture and names, whether it is a weed where the
 * garden is or native to its state, its badges and the same fact cards as a garden plant's page, with buttons to add it
 * to the garden and to save it to the user's library. A plant under the user's own name has no facts to show.
 */
@Composable
internal fun LibraryPlantPage(
    row: LibraryRow,
    photo: LibraryPhoto?,
    kb: KnowledgeBase?,
    locality: Locality,
    all: List<PlantView>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onSave: (() -> Unit)?,
    onEnlarge: () -> Unit,
) {
    val species = row.speciesName?.let { kb?.species(it) }
    // A plant the knowledge base has no record of, but the models can name, shares its genus's and family's facts.
    val unknown = if (species == null) row.speciesName ?: row.customName else null
    val taxon = unknown?.let { kb?.taxon(it) }
    val resolved = when {
        kb == null -> null
        species != null -> CareResolver.resolve(kb, species)
        taxon != null -> CareResolver.resolveGroup(kb, taxon.genus, taxon.family)
        else -> null
    }
    val weed = species?.let(locality::weed)
    val roles = if (species != null && resolved != null) locality.roles(species, resolved.care) else emptyList()
    val list = rememberLazyListState()
    DetailScaffold(title = row.title, onBack = onBack, titleShown = heroGone(list)) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().readable(),
            state = list,
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item {
                RecordHero(
                    title = row.title,
                    scientific = row.speciesName?.takeIf { it != row.title },
                    details = listOfNotNull(
                        species?.let { stringResource(R.string.library_family, it.family) },
                        photo?.let { stringResource(R.string.library_photo_credit, it.credit) },
                    ),
                    tags = if (roles.isEmpty()) null else { { RoleBadges(roles, centred = true) } },
                ) {
                    LibraryPicture(row, photo, size = HeroPictureSize, onEnlarge = onEnlarge)
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Button(onClick = onAdd) {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(IconSize.small))
                            Text(stringResource(R.string.library_page_add), modifier = Modifier.padding(start = Spacing.s))
                        }
                        if (onSave != null) {
                            OutlinedButton(onClick = onSave) {
                                Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null, modifier = Modifier.size(IconSize.small))
                                Text(stringResource(R.string.library_page_save), modifier = Modifier.padding(start = Spacing.s))
                            }
                        }
                    }
                    if (row.libraryId != null) {
                        Text(stringResource(R.string.library_in_mine), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (species != null && resolved != null) {
                item {
                    if (weed != null && weed.isWeed) WeedNotice(weed, locality) else WildStatusLine(species, resolved.care, locality)
                }
                facts(resolved, species, null, all, kb, locality.hemisphere, onEditFacts = null, onAddPot = null) {}
            } else if (taxon != null && unknown != null) {
                item { UnknownPlantNotice(unknown, taxon.genus, taxon.family, resolved) }
                if (resolved != null) facts(resolved, null, null, all, kb, locality.hemisphere, onEditFacts = null, onAddPot = null) {}
            } else {
                item { Text(stringResource(if (row.speciesName == null) R.string.library_own_name else R.string.unknown_species), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
