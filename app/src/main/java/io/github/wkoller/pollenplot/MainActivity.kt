package io.github.wkoller.pollenplot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.ui.nav.AppRoot
import io.github.wkoller.pollenplot.ui.theme.PollenplotTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** A screen a notification asked to open, until the app has opened it. */
    private val opening = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After the phone is turned, the screen the notification asked for is already open.
        if (savedInstanceState == null) opening.value = intent.getStringExtra(EXTRA_OPEN)
        val container = (application as PollenplotApp).container
        setContent {
            val settings by container.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
            val open by opening.collectAsStateWithLifecycle()
            val loaded = settings ?: return@setContent
            PollenplotTheme(loaded) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(container, open = open, onOpened = { opening.value = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        opening.value = intent.getStringExtra(EXTRA_OPEN)
    }

    companion object {
        /** Names a screen to open, for notifications. */
        const val EXTRA_OPEN = "open"
        const val OPEN_REVIEW = "review"
        const val OPEN_WEEDS = "weeds"
        const val OPEN_BEES = "bees"
        const val OPEN_WATER = "water"

        /** Opens a guide page, named after this prefix, such as "guide:native/hive-care". */
        const val OPEN_GUIDE = "guide:"
    }
}
