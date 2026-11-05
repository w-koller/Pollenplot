package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.HarvestViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/** Each morning's look at pruning: one notification for the plants that have fallen due a prune, each told once a season. */
class PruneCheck(private val views: HarvestViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        if (!current.pruneReminders) return
        val due = views.due.first()
        val keys = due.map { it.key }.toSet()
        val telling = due.filter { it.key !in current.pruneTasksTold }
        // A prune is remembered as told about only once a notification has said so, and forgotten once it is done.
        val shown = notifications.showPruning(telling)
        val told = current.pruneTasksTold.intersect(keys) + if (shown) telling.map { it.key } else emptyList()
        if (told != current.pruneTasksTold) settings.update { it.copy(pruneTasksTold = told) }
    }
}
