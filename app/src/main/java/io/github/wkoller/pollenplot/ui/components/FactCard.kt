package io.github.wkoller.pollenplot.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.FactLevel

/** A card of facts with an icon, a heading, and a tag when the facts are for the genus or family or the user's. */
@Composable
fun FactCard(@DrawableRes icon: Int, title: String, level: FactLevel? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                // The tag goes under the heading when there is no room beside it, rather than squeezing a word apart.
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (level != null && level != FactLevel.SPECIES) LevelTag(level)
                }
            }
            content()
        }
    }
}

/** Says whose a fact is when it is not the species' own: the genus's, the family's or the user's correction. */
@Composable
fun LevelTag(level: FactLevel) {
    Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(
            stringResource(
                when (level) {
                    FactLevel.USER -> R.string.level_user
                    FactLevel.GENUS -> R.string.level_genus
                    else -> R.string.level_family
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
