package io.github.wkoller.pollenplot

import android.content.Context
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.ml.AccuratePlantIdentifier
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.ml.PlantIdentifier
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.scan.AccurateScanWorker
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.MediaStoreGallery
import io.github.wkoller.pollenplot.scan.PhotoScanner
import io.github.wkoller.pollenplot.search.MeaningIndexWorker
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.download.DownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Builds the app's shared objects once. Screens and workers reach it through [PollenplotApp]. Tests pass an in-memory
 * [database], a fake [identifier] and [embedder] in place of the on-device models and a [gallery] of their own photos.
 */
class AppContainer(
    context: Context,
    database: PollenplotDatabase? = null,
    identifier: PlantIdentifier? = null,
    gallery: Gallery? = null,
    embedder: Embedder? = null,
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

    val accurate = AccuratePack(appContext, scope)

    /**
     * The models are loaded the first time a photo is identified, not when the app starts. With the accurate pack on
     * the phone, BioCAP names what the quick model finds and looks again where it was unsure.
     */
    private val testIdentifier = identifier
    private val fast by lazy { FastPlantIdentifier(appContext) }
    private val quickIdentifier: PlantIdentifier by lazy { testIdentifier ?: fast }
    val identifier: PlantIdentifier by lazy { testIdentifier ?: AccuratePlantIdentifier(fast) { accurate.open() } }
    val analysis = PhotoAnalysis({ this.identifier }, photos, garden, knowledge, clock)
    val gallery: Gallery = gallery ?: MediaStoreGallery(appContext)
    val scanner = PhotoScanner(this.gallery, PhotoAnalysis({ quickIdentifier }, photos, garden, knowledge, clock), analysis, garden, clock)
    val notifications = Notifications(appContext)
    val meaning = MeaningSearch(appContext, garden, scope, embedder)

    init {
        // The search text includes knowledge base facts and badges, so it is rebuilt when the app starts and whenever
        // the garden's state changes. A garden holds hundreds of records at most, so this takes a moment.
        scope.launch {
            knowledge.await()
            garden.reindexAll()
            settingsStore.settings.map { it.gardenState }.distinctUntilChanged().drop(1).collect { garden.reindexAll() }
        }
        // Once the accurate pack is on the phone, the plants waiting for review and the gallery get its second look.
        scope.launch {
            accurate.state.filter { it == PackState.Ready }.collect { AccurateScanWorker.enqueue(appContext) }
        }
        // Once the text model is on the phone, new and changed records get their meaning worked out in the background.
        scope.launch {
            @OptIn(FlowPreview::class)
            combine(garden.searchChanges.debounce(INDEX_DELAY_MS), meaning.state) { _, state -> state }
                .filter { it == DownloadState.Complete }
                .collect { MeaningIndexWorker.enqueue(appContext) }
        }
    }

    private companion object {
        /** Edits come in bursts, such as a whole zone's plants at once, so the meaning waits for a quiet moment. */
        const val INDEX_DELAY_MS = 5_000L
    }
}
