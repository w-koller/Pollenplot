package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shows that the app is working on something of unknown length, such as finding the plants in a photo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollenLoadingIndicator(modifier: Modifier = Modifier, contained: Boolean = false) {
    if (contained) ContainedLoadingIndicator(modifier) else LoadingIndicator(modifier)
}

/** Shows how far something has got, such as photos being looked at, or that it has started when [progress] is null. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollenProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null) LinearWavyProgressIndicator(modifier) else LinearWavyProgressIndicator(progress = { progress }, modifier = modifier)
}
