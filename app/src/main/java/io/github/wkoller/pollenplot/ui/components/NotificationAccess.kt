package io.github.wkoller.pollenplot.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.wkoller.pollenplot.notify.Notifications

/** Whether Pollenplot may show notifications, and how to ask for them. */
class NotificationAccess(val allowed: Boolean, val ask: () -> Unit)

/**
 * Notification access for a screen that offers notifications. Asking shows Android's own question while Android will
 * still ask, and the app's notification settings once it will not, or on phones where there is no question to ask.
 */
@Composable
fun rememberNotificationAccess(notifications: Notifications): NotificationAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var allowed by remember { mutableStateOf(notifications.allowed) }
    // Android asks twice at most. After a refusal it says whether it would ask again.
    var mayAsk by rememberSaveable { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = notifications.allowed
        mayAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == true
    }
    // The answer can change in the system's settings while the screen is away.
    LifecycleResumeEffect(notifications) {
        allowed = notifications.allowed
        onPauseOrDispose {}
    }
    return NotificationAccess(allowed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && mayAsk) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
