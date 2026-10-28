package io.github.wkoller.pollenplot.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wkoller.pollenplot.PollenplotApp
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.honey.HiveReminders
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Each morning's look at the hives: a notification for the checks that have fallen due since the last one. */
class HiveReminderCheck(private val views: BeeViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        if (!current.hiveReminders) return
        val hives = views.hives.first()
        val today = views.today()
        val tasks = hives.flatMap { it.tasks }
        val names = hives.associate { it.hive.id to it.hive.name }
        val telling = HiveReminders.toTell(tasks, today, current.hiveTasksTold)
        // Checks are remembered as told about only once a notification has said so; until then, each morning tries again.
        val shown = notifications.showHiveTasks(telling.map { names[it.hiveId].orEmpty() to it }, today)
        val told = HiveReminders.remembered(tasks, today, current.hiveTasksTold, if (shown) telling else emptyList())
        if (told != current.hiveTasksTold) settings.update { it.copy(hiveTasksTold = told) }
    }
}

class HiveReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as PollenplotApp).container.hiveReminders.run()
        return Result.success()
    }

    companion object {
        private const val DAILY = "hive-reminders"

        /** The time of day the reminders are looked at, so a notification arrives with the morning. */
        private val MORNING: LocalTime = LocalTime.of(7, 0)

        /**
         * Looks at the hives each morning while [on], and stops otherwise. Robolectric tests set up WorkManager in each
         * test, after the app has started, so there is none to schedule with until then.
         */
        fun update(context: Context, on: Boolean, clock: Clock) {
            val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            if (!on) {
                manager.cancelUniqueWork(DAILY)
                return
            }
            val request = PeriodicWorkRequestBuilder<HiveReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(untilMorning(clock).toMinutes(), TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** How long until the next [MORNING] on the phone's clock. */
        internal fun untilMorning(clock: Clock): Duration {
            val now = ZonedDateTime.now(clock)
            val today = now.with(MORNING)
            return Duration.between(now, if (today.isAfter(now)) today else today.plusDays(1))
        }
    }
}
