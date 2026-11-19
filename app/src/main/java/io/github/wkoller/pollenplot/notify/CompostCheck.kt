package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.CompostViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/** Each morning's look at the compost: one notification for the bins due a turn or a look, each job told once. */
class CompostCheck(private val views: CompostViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        if (!current.compostReminders) return
        val due = views.due.first()
        val keys = due.map { it.key }.toSet()
        val telling = due.filter { it.key !in current.compostTasksTold }
        // A job is remembered as told about only once a notification has said so, and forgotten once it is done.
        val shown = notifications.showCompost(telling)
        val told = current.compostTasksTold.intersect(keys) + if (shown) telling.map { it.key } else emptyList()
        if (told != current.compostTasksTold) settings.update { it.copy(compostTasksTold = told) }
    }
}
