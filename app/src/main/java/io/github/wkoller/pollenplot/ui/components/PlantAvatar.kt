package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.ui.plants.icon

/**
 * A plant's picture in a clover shape. Until the plant has a photo, it shows an icon for its growth form; photo
 * cut-outs take its place once identification arrives.
 */
@Composable
fun PlantAvatar(
    form: GrowthForm?,
    size: Dp,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Box(
        modifier.size(size).clip(PollenShapes.clover()).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(form.icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.5f))
    }
}
