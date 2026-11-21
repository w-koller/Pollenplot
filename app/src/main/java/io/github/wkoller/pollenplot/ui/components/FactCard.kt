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
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Shape
import io.github.wkoller.pollenplot.ui.theme.Spacing
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
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.plants.FactLevel

/**
 * A card of facts with an icon, a heading, and a tag when the facts are for the genus or family or the user's. Inside a
 * [FactGroup] it takes the [shape] the group gives it.
 */
@Composable
fun FactCard(@DrawableRes icon: Int, title: String, level: FactLevel? = null, shape: Shape = MaterialTheme.shapes.large, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                // The tag goes under the heading when there is no room beside it, rather than squeezing a word apart.
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
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

/**
 * Cards that belong together under a [heading], joined into one group: the group's outer corners are large and the
 * corners between its cards small, with a narrow gap, so it reads as one thing in parts. Each of [cards] draws its card
 * in the shape it is given.
 */
@Composable
fun FactGroup(heading: String?, cards: List<@Composable (Shape) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        heading?.let {
            Text(
                it,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.s, bottom = Spacing.xs).semantics { heading() },
            )
        }
        cards.forEachIndexed { index, card -> card(groupShape(index, cards.size)) }
    }
}

/** The shape of the card at [index] among [count] joined ones: large corners outside the group, small ones inside. */
@Composable
fun groupShape(index: Int, count: Int): Shape {
    val outer = MaterialTheme.shapes.large
    val inner = CornerSize(Spacing.xs)
    return when {
        count <= 1 -> outer
        index == 0 -> outer.copy(bottomStart = inner, bottomEnd = inner)
        index == count - 1 -> outer.copy(topStart = inner, topEnd = inner)
        else -> MaterialTheme.shapes.extraSmall
    }
}

/** Says whose a fact is when it is not the species' own: the genus's, the family's or the user's correction. */
@Composable
fun LevelTag(level: FactLevel) {
    Surface(shape = MaterialTheme.shapes.small, border = BorderStroke(Spacing.hairline, MaterialTheme.colorScheme.outline)) {
        Text(
            stringResource(
                when (level) {
                    FactLevel.USER -> R.string.level_user
                    FactLevel.GENUS -> R.string.level_genus
                    else -> R.string.level_family
                },
            ),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xxs),
        )
    }
}
