package io.github.wkoller.pollenplot.ui.wildlife

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.SightingView
import io.github.wkoller.pollenplot.domain.wildlife.Animal
import io.github.wkoller.pollenplot.domain.wildlife.FaunaRole
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.plants.FactCard

/**
 * The animals on a plant: those seen on it in the yard, the latest first, and those the knowledge base says use it,
 * garden pests among them.
 */
@Composable
fun PlantAnimalsCard(seen: List<SightingView>, users: List<Animal>, onOpenSighting: (String) -> Unit, onOpenAnimal: (String) -> Unit) {
    FactCard(R.drawable.ic_raven, stringResource(R.string.plant_animals)) {
        if (seen.isNotEmpty()) {
            Text(stringResource(R.string.plant_animals_seen), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
            seen.take(SEEN_SHOWN).forEach { view ->
                val details = listOfNotNull(view.sighting.behaviour?.let { stringResource(it.label) }, UiFormat.longDate(view.sighting.seenAt))
                AnimalLine((view.animal?.group ?: view.sighting.group).icon, view.title, details.joinToString(" · "), pest = false) { onOpenSighting(view.sighting.id) }
            }
        }
        if (users.isNotEmpty()) {
            Text(stringResource(R.string.plant_animals_use), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
            users.forEach { animal ->
                AnimalLine(animal.group.icon, animal.displayName, animal.plantNote, pest = FaunaRole.PLANT_PEST in animal.roles) { onOpenAnimal(animal.name) }
            }
        }
    }
}

@Composable
private fun AnimalLine(@DrawableRes icon: Int, title: String, details: String?, pest: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (pest) Badge(FaunaRole.PLANT_PEST.icon, stringResource(FaunaRole.PLANT_PEST.label), FaunaRole.PLANT_PEST.tone)
            details?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** The latest sightings on a plant its page lists. */
private const val SEEN_SHOWN = 5
