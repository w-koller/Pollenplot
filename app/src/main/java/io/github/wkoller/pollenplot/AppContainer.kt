package io.github.wkoller.pollenplot

import android.content.Context
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.ml.AccuratePlantIdentifier
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.ml.PlantIdentifier
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.place.LocationSource
import io.github.wkoller.pollenplot.place.PhoneLocation
import io.github.wkoller.pollenplot.place.PlaceFinder
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
import io.github.wkoller.pollenplot.web.HttpWeb
import io.github.wkoller.pollenplot.web.Web
import io.github.wkoller.pollenplot.weeds.WeedLibrary
import io.github.wkoller.pollenplot.weeds.WeedPhotos
import io.github.wkoller.pollenplot.weeds.WeedSync
import io.github.wkoller.pollenplot.weeds.WeedSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock

/**
 * Builds the app's shared objects once. Screens and workers reach it through [PollenplotApp]. Tests pass an in-memory
 * [database], a fake [identifier] and [embedder] in place of the on-device models, a [gallery] of their own photos,
 * recorded [web] answers and a fixed [location].
 */
class AppContainer(
    context: Context,
    database: PollenplotDatabase? = null,
    identifier: PlantIdentifier? = null,
    gallery: Gallery? = null,
    embedder: Embedder? = null,
    web: Web? = null,
    location: LocationSource? = null,
) {
    val appContext: Context = context.applicationContext
    val clock: Clock = PhoneClock
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsStore = SettingsStore(appContext)
    val web: Web = web ?: HttpWeb()
    val weeds = WeedLibrary(
        File(appContext.filesDir, "weeds"),
        { appContext.assets.open("weeds/sources.json").bufferedReader().use { it.readText() } },
        settingsStore,
        scope,
    )
    val database: PollenplotDatabase = database ?: PollenplotDatabase.create(appContext)
    val knowledge = KnowledgeRepository(appContext, scope)
    val garden = GardenRepository(
        db = this.database,
        clock = clock,
        knowledge = { knowledge.await() },
        locality = { Locality(settingsStore.settings.first().gardenState, weeds.data.value) },
    )
    val views = GardenViews(garden, knowledge, settingsStore, weeds.data)
    val places = PlaceFinder(this.web) { weeds.sources }
    val location: LocationSource = location ?: PhoneLocation(appContext)
    val weedSync = WeedSync(this.web, weeds, places, settingsStore, { namesToMatch() }, clock)
    val weedPhotos = WeedPhotos(this.web) { weeds.sources }
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
        // the garden's state or weed lists change. A garden holds hundreds of records at most, so this takes a moment.
        scope.launch {
            knowledge.await()
            views.locality.distinctUntilChanged().collectLatest { garden.reindexAll() }
        }
        // The weed lists are brought up to date once a month on Wi-Fi, and straight away when the garden moves.
        scope.launch {
            WeedSyncWorker.schedule(appContext)
            settingsStore.settings.map { settings -> settings.gardenPlace?.let { it.latE7 to it.lonE7 } }
                .distinctUntilChanged().drop(1)
                .collect { WeedSyncWorker.runNow(appContext) }
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

    /**
     * The names whose accepted spelling the weed update looks up: the garden's plants, the knowledge base with its
     * synonyms, and the quick identifier's labels. Each is looked up once and remembered.
     */
    private suspend fun namesToMatch(): Collection<String> {
        val kb = knowledge.await()
        val labels = withContext(Dispatchers.IO) {
            runCatching { appContext.assets.open("ml/aiy_plants_v1_labels.txt").bufferedReader().use { it.readLines() } }.getOrDefault(emptyList())
        }
        return garden.plantList.first().mapNotNull { it.speciesName } + kb.allSpecies.flatMap { listOf(it.name) + it.synonyms } + labels
    }

    private companion object {
        /** Edits come in bursts, such as a whole zone's plants at once, so the meaning waits for a quiet moment. */
        const val INDEX_DELAY_MS = 5_000L
    }
}
