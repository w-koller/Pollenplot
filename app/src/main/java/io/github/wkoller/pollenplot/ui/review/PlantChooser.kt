package io.github.wkoller.pollenplot.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.PlantView
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PlantAvatar

/**
 * Every plant growing in the garden, to add a find to: plants of the find's species first, then the rest by name. It
 * opens over the whole screen, like the species search.
 */
@Composable
fun PlantChooser(plants: List<PlantView>, speciesName: String?, onBack: () -> Unit, onChoose: (plantId: String) -> Unit) {
    val growing = plants.filter { it.isGrowing }.sortedBy { it.title.lowercase() }
    val (same, others) = growing.partition { speciesName != null && it.plant.speciesName == speciesName }
    DetailScaffold(title = stringResource(R.string.plant_chooser_title), onBack = onBack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(if (growing.isEmpty()) R.string.plant_chooser_empty else R.string.plant_chooser_body),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.readable().padding(16.dp),
                )
            }
            listOf(R.string.plant_chooser_same to same, R.string.plant_chooser_others to others).filter { it.second.isNotEmpty() }.forEach { (heading, list) ->
                // Headings only help when there are two groups.
                if (same.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(heading),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.readable().padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
                        )
                    }
                }
                items(list, key = { it.plant.id }) { view ->
                    ListItem(
                        onClick = { onChoose(view.plant.id) },
                        modifier = Modifier.readable(),
                        leadingContent = { PlantAvatar(view.species?.form, 48.dp, thumbnail = view.plant.thumbnail) },
                        supportingContent = {
                            Column {
                                view.scientificName?.let { Text(it, fontStyle = FontStyle.Italic) }
                                view.zoneName?.let { Text(stringResource(R.string.plant_zone, it)) }
                            }
                        },
                    ) { Text(view.title) }
                }
            }
        }
    }
}
