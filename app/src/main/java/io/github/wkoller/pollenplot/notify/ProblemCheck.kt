package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.ProblemViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/** Each morning's look at treated problems: one notification for the follow-ups that have fallen due, each told once. */
class ProblemCheck(private val views: ProblemViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        if (!current.problemReminders) return
        val due = views.due.first()
        val keys = due.map { it.key }.toSet()
        val telling = due.filter { it.key !in current.problemTasksTold }
        // A follow-up is remembered as told about only once a notification has said so, and forgotten once it is done.
        val shown = notifications.showFollowUps(telling)
        val told = current.problemTasksTold.intersect(keys) + if (shown) telling.map { it.key } else emptyList()
        if (told != current.problemTasksTold) settings.update { it.copy(problemTasksTold = told) }
    }
}
