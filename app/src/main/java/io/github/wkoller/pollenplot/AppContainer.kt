package io.github.wkoller.pollenplot

import android.content.Context
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import java.time.Clock

/** Builds the app's shared objects once. Screens reach it through [PollenplotApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val clock: Clock = PhoneClock
    val settingsStore = SettingsStore(appContext)
}
