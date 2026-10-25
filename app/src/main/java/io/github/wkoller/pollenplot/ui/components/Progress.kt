package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Shows that the app is working on something of unknown length, such as finding the plants in a photo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PollenLoadingIndicator(modifier: Modifier = Modifier, contained: Boolean = false) {
    if (contained) ContainedLoadingIndicator(modifier) else LoadingIndicator(modifier)
}
