package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.CalendarKind
import io.github.wkoller.pollenplot.data.CalendarViews
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.calendar.ReminderMode
import kotlinx.coroutines.flow.first

/**
 * The to-do list a weekly or monthly reminder schedule sends: on Monday or on the first of the month, one notification
 * of the jobs in the period ahead whose reminders are on, sent once for each period. Watering is left out, because it
 * is told on the day.
 */
class CalendarListCheck(private val views: CalendarViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        val schedule = current.reminderSchedule
        if (!schedule.isList) return
        val today = views.today()
        if (!schedule.isReminderDay(today)) return
        val period = schedule.listPeriod(today)
        val key = period.start.toString()
        if (current.listToldFor == key) return
        val items = views.items(period.endInclusive).first().filter { it.date in period && current.wants(it.kind) }
        if (items.isEmpty()) return
        val shown = notifications.showList(items, monthly = current.reminderMode == ReminderMode.MONTHLY_LIST)
        if (shown) settings.update { it.copy(listToldFor = key) }
    }

    private fun Settings.wants(kind: CalendarKind): Boolean = when (kind) {
        CalendarKind.WATER -> false
        CalendarKind.HIVE -> hiveReminders
        CalendarKind.POT -> potReminders
        CalendarKind.FEED -> feedReminders
        CalendarKind.PROBLEM -> problemReminders
        CalendarKind.PRUNE -> pruneReminders
        CalendarKind.COMPOST -> compostReminders
        CalendarKind.HOTEL -> true
    }
}
