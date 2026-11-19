package io.github.wkoller.pollenplot.ui.library

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
    val resolved = species?.let { kb?.let { base -> CareResolver.resolve(base, it) } }
    val weed = species?.let(locality::weed)
    DetailScaffold(title = row.title, onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().readable(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LibraryPicture(row, photo, size = 112.dp, onEnlarge = onEnlarge)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                        row.speciesName?.takeIf { it != row.title }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic) }
                        species?.let { Text(stringResource(R.string.library_family, it.family), style = MaterialTheme.typography.bodyMedium) }
                        photo?.let { Text(stringResource(R.string.library_photo_credit, it.credit), style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAdd) {
                        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.library_page_add), modifier = Modifier.padding(start = 8.dp))
                    }
                    if (onSave != null) {
                        OutlinedButton(onClick = onSave) {
                            Icon(painterResource(R.drawable.ic_bookmark), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.library_page_save), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (row.libraryId != null) {
                    Text(stringResource(R.string.library_in_mine), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                }
            }
            if (species != null && resolved != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (weed != null && weed.isWeed) WeedNotice(weed, locality) else WildStatusLine(species, resolved.care, locality)
                        val roles = locality.roles(species, resolved.care)
                        if (roles.isNotEmpty()) RoleBadges(roles)
                    }
                }
                facts(resolved, species, null, all, kb, locality.hemisphere, onEditFacts = null, onAddPot = null) {}
            } else {
                item { Text(stringResource(if (row.speciesName == null) R.string.library_own_name else R.string.unknown_species), style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}
