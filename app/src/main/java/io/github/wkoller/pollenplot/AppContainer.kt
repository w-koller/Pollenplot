package io.github.wkoller.pollenplot

import android.content.Context
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ml.PlantIdentifier
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.MediaStoreGallery
import io.github.wkoller.pollenplot.scan.PhotoScanner
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

/**
 * Builds the app's shared objects once. Screens and workers reach it through [PollenplotApp]. Tests pass an in-memory
 * [database], a fake [identifier] in place of the on-device models and a [gallery] of their own photos.
 */
class AppContainer(
    context: Context,
    database: PollenplotDatabase? = null,
    identifier: PlantIdentifier? = null,
    gallery: Gallery? = null,
) {
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
    val photos = PhotoStore(appContext)

    /** The models are loaded the first time a photo is identified, not when the app starts. */
    val identifier: PlantIdentifier by lazy { identifier ?: FastPlantIdentifier(appContext) }
    val analysis = PhotoAnalysis({ this.identifier }, photos, garden, knowledge, clock)
    val gallery: Gallery = gallery ?: MediaStoreGallery(appContext)
    val scanner = PhotoScanner(this.gallery, analysis, garden, clock)
    val notifications = Notifications(appContext)

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
