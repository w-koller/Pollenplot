package io.github.wkoller.pollenplot

import android.content.Context
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock

/** Builds the app's shared objects once. Screens reach it through [PollenplotApp]. */
class AppContainer(context: Context, database: PollenplotDatabase? = null) {
    val appContext: Context = context.applicationContext
    val clock: Clock = PhoneClock
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsStore = SettingsStore(appContext)
    val database: PollenplotDatabase = database ?: PollenplotDatabase.create(appContext)
    val knowledge = KnowledgeRepository(appContext, scope)
    val garden = GardenRepository(
        db = this.database,
        clock = clock,
        knowledge = { knowledge.await() },
        gardenState = { settingsStore.settings.first().gardenState },
    )
    val views = GardenViews(garden, knowledge, settingsStore)

    init {
        // The search text includes knowledge base facts and badges, so it is rebuilt when the app starts and whenever
        // the garden's state changes. A garden holds hundreds of records at most, so this takes a moment.
        scope.launch {
            knowledge.await()
            garden.reindexAll()
            settingsStore.settings.map { it.gardenState }.distinctUntilChanged().drop(1).collect { garden.reindexAll() }
        }
    }
}
