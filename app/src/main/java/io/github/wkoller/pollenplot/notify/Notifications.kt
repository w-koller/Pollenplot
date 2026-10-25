package io.github.wkoller.pollenplot.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.wkoller.pollenplot.MainActivity
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.scan.ScanTally

/** The app's notifications. Each kind has its own channel, so the user can turn one off and keep the rest. */
class Notifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(SCANS_CHANNEL, context.getString(R.string.notify_scans_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_scans_channel_description) },
        )
    }

    /** Whether notifications can be shown at all. */
    val allowed: Boolean get() = manager.areNotificationsEnabled() && (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )

    /** Tells the user that a scan of the gallery found plants, and opens the Review queue when tapped. */
    fun showScanResult(tally: ScanTally) {
        if (tally.plants == 0) return
        val res = context.resources
        show(
            SCAN_RESULT,
            res.getQuantityString(R.plurals.notify_found_title, tally.plants, tally.plants),
            res.getQuantityString(R.plurals.notify_found_body, tally.plants),
        )
    }

    /** Tells the user what was found in the photos they added. */
    fun showAddedResult(tally: ScanTally) {
        val res = context.resources
        if (tally.plants > 0) {
            show(ADDED_RESULT, res.getQuantityString(R.plurals.notify_found_title, tally.plants, tally.plants), res.getQuantityString(R.plurals.notify_found_body, tally.plants))
        } else {
            show(ADDED_RESULT, res.getQuantityString(R.plurals.notify_none_title, tally.photos), context.getString(R.string.notify_none_body))
        }
    }

    private fun show(id: Int, title: String, body: String) {
        if (!allowed) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_REVIEW)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notification = NotificationCompat.Builder(context, SCANS_CHANNEL)
            .setSmallIcon(R.drawable.ic_local_florist)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // The permission was taken away between the check and now; the Review queue still holds the plants.
        }
    }

    private companion object {
        const val SCANS_CHANNEL = "plant_scans"
        const val SCAN_RESULT = 1
        const val ADDED_RESULT = 2
    }
}
