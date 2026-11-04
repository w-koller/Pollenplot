package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.PotViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Each morning's look at the potted plants: one notification for the pots that have fallen due a flush or a look at
 * repotting since the last one. Each job is told about once while it stays due.
 */
class PotCareCheck(private val views: PotViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        if (!current.potReminders) return
        val due = views.due.first()
        val keys = due.map { it.key }.toSet()
        val telling = due.filter { it.key !in current.potTasksTold }
        // A job is remembered as told about only once a notification has said so, and forgotten once it is done.
        val shown = notifications.showPotCare(telling)
        val told = current.potTasksTold.intersect(keys) + if (shown) telling.map { it.key } else emptyList()
        if (told != current.potTasksTold) settings.update { it.copy(potTasksTold = told) }
    }
}
