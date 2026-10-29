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
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTask
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.domain.irrigation.WateringWindow
import io.github.wkoller.pollenplot.domain.weather.DayForecast
import io.github.wkoller.pollenplot.domain.weeds.NearbyWeed
import io.github.wkoller.pollenplot.scan.ScanTally
import io.github.wkoller.pollenplot.ui.ListWords
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.degrees
import io.github.wkoller.pollenplot.ui.bees.label
import java.time.LocalDate

/** The app's notifications. Each kind has its own channel, so the user can turn one off and keep the rest. */
class Notifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(SCANS_CHANNEL, context.getString(R.string.notify_scans_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_scans_channel_description) },
        )
        system.createNotificationChannel(
            NotificationChannel(WEEDS_CHANNEL, context.getString(R.string.notify_weeds_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_weeds_channel_description) },
        )
        system.createNotificationChannel(
            NotificationChannel(WATERING_CHANNEL, context.getString(R.string.notify_water_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_water_channel_description) },
        )
        system.createNotificationChannel(
            NotificationChannel(HIVES_CHANNEL, context.getString(R.string.notify_hive_channel), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.notify_hive_channel_description) },
        )
    }

    /** Whether notifications can be shown at all. */
    val allowed: Boolean get() = manager.areNotificationsEnabled() && (
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )

    /** Tells the user that a scan of the gallery found plants or animals, and opens the Review queue when tapped. */
    fun showScanResult(tally: ScanTally) {
        if (tally.found == 0) return
        show(SCAN_RESULT, foundTitle(tally), foundBody(tally))
    }

    /** Tells the user what was found in the photos they added. */
    fun showAddedResult(tally: ScanTally) {
        val res = context.resources
        if (tally.found > 0) {
            show(ADDED_RESULT, foundTitle(tally), foundBody(tally))
        } else {
            show(ADDED_RESULT, res.getQuantityString(R.plurals.notify_none_title, tally.photos), context.getString(R.string.notify_none_body))
        }
    }

    private fun foundTitle(tally: ScanTally): String {
        val res = context.resources
        return when {
            tally.animals == 0 -> res.getQuantityString(R.plurals.notify_found_title, tally.plants, tally.plants)
            tally.plants == 0 -> res.getQuantityString(R.plurals.notify_found_animals_title, tally.animals, tally.animals)
            else -> res.getString(
                R.string.notify_found_both_title,
                res.getQuantityString(R.plurals.count_plants, tally.plants, tally.plants),
                res.getQuantityString(R.plurals.count_animals, tally.animals, tally.animals),
            )
        }
    }

    private fun foundBody(tally: ScanTally): String {
        val res = context.resources
        return when {
            tally.animals == 0 -> res.getQuantityString(R.plurals.notify_found_body, tally.plants)
            tally.plants == 0 -> res.getQuantityString(R.plurals.notify_found_animals_body, tally.animals)
            else -> res.getString(R.string.notify_found_both_body)
        }
    }

    /** Tells the user about weeds newly recorded near the garden, and opens the Weeds screen when tapped. */
    fun showWeedsNearby(weeds: List<NearbyWeed>) {
        if (weeds.isEmpty()) return
        val res = context.resources
        val words = ListWords(res.getString(R.string.list_and), res.getString(R.string.list_range), res.getString(R.string.list_all_year))
        val names = UiFormat.joinAnd(weeds.map { it.status.listedCommon ?: it.name }, words)
        show(
            WEEDS_NEARBY,
            res.getQuantityString(R.plurals.notify_weeds_title, weeds.size, weeds.size),
            res.getString(R.string.notify_weeds_body, names),
            channel = WEEDS_CHANNEL,
            open = MainActivity.OPEN_WEEDS,
            icon = R.drawable.ic_grass,
        )
    }

    /**
     * Tells the user that hive checks are due, each named with its hive, and opens the Bees tab when tapped. Returns
     * whether it could be shown, so that checks are only remembered as told about once they have been.
     */
    fun showHiveTasks(tasks: List<Pair<String, HiveTask>>, today: LocalDate): Boolean {
        if (tasks.isEmpty() || !allowed) return false
        val res = context.resources
        val (title, body) = if (tasks.size == 1) {
            val (hive, task) = tasks.single()
            val title = res.getString(
                when (task.kind) {
                    HiveTaskKind.INSPECT -> R.string.notify_hive_inspect_title
                    HiveTaskKind.BROOD_CHECK -> R.string.notify_hive_brood_title
                    HiveTaskKind.MITE_CHECK -> R.string.notify_hive_mites_title
                },
                hive,
            )
            title to if (task.overdue(today)) res.getString(R.string.notify_hive_overdue, UiFormat.longDate(task.due)) else res.getString(R.string.notify_hive_due_today)
        } else {
            res.getQuantityString(R.plurals.notify_hive_many_title, tasks.size, tasks.size) to
                tasks.joinToString("\n") { (hive, task) -> res.getString(R.string.honey_task_line, hive, res.getString(task.kind.label)) }
        }
        show(HIVE_TASKS, title, body, channel = HIVES_CHANNEL, open = MainActivity.OPEN_BEES, icon = R.drawable.ic_hive)
        return true
    }

    /**
     * Warns that today or tomorrow is forecast to be hot for stingless bees, and opens the guide's page on caring for a
     * hive when tapped. Returns whether it could be shown.
     */
    fun showHeat(days: List<DayForecast>, today: LocalDate): Boolean {
        if (days.isEmpty() || !allowed) return false
        val res = context.resources
        val advice = res.getString(R.string.notify_heat_body)
        val (title, body) = if (days.size == 1) {
            val day = days.single()
            res.getString(if (day.date == today) R.string.notify_heat_today else R.string.notify_heat_tomorrow, degrees(day.maxTenthsC)) to advice
        } else {
            res.getString(R.string.notify_heat_both) to
                res.getString(R.string.notify_heat_both_detail, degrees(days[0].maxTenthsC), degrees(days[1].maxTenthsC)) + " " + advice
        }
        show(HEAT, title, body, channel = HIVES_CHANNEL, open = MainActivity.OPEN_GUIDE + HEAT_GUIDE, icon = R.drawable.ic_emoji_nature)
        return true
    }

    /** Tells what watering is due today, and opens the Water tab when tapped. Returns whether it could be shown. */
    fun showWatering(due: List<DueWatering>, window: WateringWindow): Boolean {
        if (due.isEmpty() || !allowed) return false
        val res = context.resources
        fun line(item: DueWatering): String = item.minutes?.let { res.getQuantityString(R.plurals.notify_water_minutes, it, item.name, it) }
            ?: res.getString(R.string.notify_water_litres, item.name, item.litres.toString())
        val (title, lines) = if (due.size == 1) {
            res.getString(R.string.notify_water_title_one, due.single().name) to line(due.single())
        } else {
            res.getQuantityString(R.plurals.notify_water_title, due.size, due.size) to due.joinToString("\n") { line(it) }
        }
        val hours = res.getString(R.string.notify_water_window, UiFormat.timeOfDay(window.fromMinute), UiFormat.timeOfDay(window.toMinute))
        show(WATERING, title, lines + "\n" + hours, channel = WATERING_CHANNEL, open = MainActivity.OPEN_WATER, icon = R.drawable.ic_water_drop)
        return true
    }

    private fun show(
        id: Int,
        title: String,
        body: String,
        channel: String = SCANS_CHANNEL,
        open: String = MainActivity.OPEN_REVIEW,
        icon: Int = R.drawable.ic_local_florist,
    ) {
        if (!allowed) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, open)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // The permission was taken away between the check and now; the app still shows what the notification said.
        }
    }

    private companion object {
        const val SCANS_CHANNEL = "plant_scans"
        const val WEEDS_CHANNEL = "weeds_nearby"
        const val HIVES_CHANNEL = "hive_tasks"
        const val WATERING_CHANNEL = "watering"
        const val SCAN_RESULT = 1
        const val ADDED_RESULT = 2
        const val WEEDS_NEARBY = 3
        const val HIVE_TASKS = 4
        const val HEAT = 5
        const val WATERING = 6

        /** The guide page on caring for a stingless bee hive, which says how to cool one. */
        const val HEAT_GUIDE = "native/hive-care"
    }
}
