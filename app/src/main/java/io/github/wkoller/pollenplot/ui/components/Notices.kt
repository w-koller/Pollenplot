package io.github.wkoller.pollenplot.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import io.github.wkoller.pollenplot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The short messages that answer a press, such as "Recorded in flower today.", with Undo where the press can be taken
 * back. One host serves the whole app, so a message stays up when the screen that sent it closes, as a page does
 * after its record is deleted. Undo is never the only way back: what was done can also be changed where it is shown.
 */
class Notices(val host: SnackbarHostState, private val scope: CoroutineScope, private val undoLabel: String) {
    /** Shows [message] in place of any message still up, with an Undo button that runs [undo] when there is one. */
    fun show(message: String, undo: (suspend () -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val result = host.showSnackbar(
                message = message,
                actionLabel = undoLabel.takeIf { undo != null },
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }
}

/** The app's messages, given by the app frame. A screen shown on its own has none, and its presses say nothing. */
val LocalNotices = staticCompositionLocalOf<Notices?> { null }

/** Made once by the app frame, whose scope outlives every screen. */
@Composable
fun rememberNotices(): Notices {
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.undo)
    return remember(scope, undo) { Notices(SnackbarHostState(), scope, undo) }
}

/** Where a screen's frame shows the app's messages. */
@Composable
fun NoticeHost() {
    LocalNotices.current?.let { SnackbarHost(it.host) }
}
