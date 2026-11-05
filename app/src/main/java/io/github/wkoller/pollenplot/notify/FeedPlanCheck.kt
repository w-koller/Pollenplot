package io.github.wkoller.pollenplot.notify

import io.github.wkoller.pollenplot.data.FeedViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * Each morning's look at feeding: one notification for the zones that have fallen due a feed since the last one, and
 * for the conditioner reminder when the user asked for it. Each is told about once while it stays due.
 */
class FeedPlanCheck(private val views: FeedViews, private val settings: SettingsStore, private val notifications: Notifications) {
    suspend fun run() {
        val current = settings.current()
        val due = if (current.feedReminders) views.due.first() else emptyList()
        val conditioner = views.conditioner.first()
        val keys = due.map { it.due.key }.toSet() + listOfNotNull(conditioner?.let { "heat:${it.day}" })
        val telling = due.filter { it.due.key !in current.feedTasksTold }
        val tellHeat = conditioner?.takeIf { "heat:${it.day}" !in current.feedTasksTold }
        // A job is remembered as told about only once a notification has said so, and forgotten once it is done.
        val shown = notifications.showFeeds(telling, tellHeat, views.today())
        val told = current.feedTasksTold.intersect(keys) +
            if (shown) telling.map { it.due.key } + listOfNotNull(tellHeat?.let { "heat:${it.day}" }) else emptyList()
        if (told != current.feedTasksTold) settings.update { it.copy(feedTasksTold = told) }
    }
}
