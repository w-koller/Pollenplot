package io.github.wkoller.pollenplot

import android.app.Application

class PollenplotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = AppContainer(this)
    }
}
