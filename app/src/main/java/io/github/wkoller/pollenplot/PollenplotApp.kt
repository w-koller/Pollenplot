package io.github.wkoller.pollenplot

import android.app.Application

open class PollenplotApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        container = createContainer()
    }

    /** Tests replace this to give the app a fake identifier and gallery. */
    protected open fun createContainer(): AppContainer = AppContainer(this)
}
