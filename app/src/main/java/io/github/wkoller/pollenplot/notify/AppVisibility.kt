package io.github.wkoller.pollenplot.notify

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Whether one of the app's screens is on show, counted from the activities started and stopped. While it is, the
 * screens already say what was found in photos, so the notification bar is not told as well.
 */
class AppVisibility : Application.ActivityLifecycleCallbacks {
    @Volatile
    private var started = 0

    val visible: Boolean get() = started > 0

    override fun onActivityStarted(activity: Activity) {
        started++
    }

    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
