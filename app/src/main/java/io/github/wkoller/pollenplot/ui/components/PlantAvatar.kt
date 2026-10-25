package io.github.wkoller.pollenplot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.ui.plants.icon
import java.io.File

/**
 * A plant's picture in a clover shape: its cut-out from a photo when it has one, otherwise an icon for its growth form
 * on a coloured background. [thumbnail] is the path the app stored, relative to its files folder.
 */
@Composable
fun PlantAvatar(
    form: GrowthForm?,
    size: Dp,
    modifier: Modifier = Modifier,
    thumbnail: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val context = LocalContext.current
    val file = remember(thumbnail) { thumbnail?.let { File(context.filesDir, it) } }
    Box(
        modifier.size(size).clip(PollenShapes.clover()).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(form.icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(size * 0.5f))
        if (file != null) {
            // The icon stays underneath, so it shows while the picture loads or if the file has gone.
            AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}
