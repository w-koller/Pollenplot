package io.github.wkoller.pollenplot.ui.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme

/**
 * Shows a preview in the four ways every screen must work: light, dark, text at twice its size, and a wide window. The
 * screen tests check whole screens the same ways under Robolectric; previews are for the pieces while they are made.
 */
@Preview(name = "Light", showBackground = true, widthDp = 411)
@Preview(name = "Dark", showBackground = true, widthDp = 411, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
@Preview(name = "Large text", showBackground = true, widthDp = 411, fontScale = 2f)
@Preview(name = "Wide", showBackground = true, widthDp = 840)
annotation class PollenPreviews

/** Pollenplot's theme on the page colour, for a preview. It follows the preview's light or dark mode. */
@Composable
fun PreviewTheme(content: @Composable () -> Unit) {
    PollenplotTheme(Settings()) {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, content = content)
    }
}
