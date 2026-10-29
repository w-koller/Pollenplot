package io.github.wkoller.pollenplot

import android.content.Context
import android.util.Log
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.ai.GardenAssistant
import io.github.wkoller.pollenplot.ai.GemmaModel
import io.github.wkoller.pollenplot.ai.SecondOpinionWorker
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.HotelRepository
import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.data.RhythmsViews
import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.data.WaterViews
import io.github.wkoller.pollenplot.data.guides.GuideLibrary
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.ml.AccuratePlantIdentifier
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ml.Identifier
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.report.XlsxWriter
import io.github.wkoller.pollenplot.domain.semantic.DarwinCore
import io.github.wkoller.pollenplot.domain.semantic.JsonLd
import io.github.wkoller.pollenplot.notify.HeatAlertCheck
import io.github.wkoller.pollenplot.report.ReportPart
import io.github.wkoller.pollenplot.report.ReportBuilder
import io.github.wkoller.pollenplot.report.PdfReportWriter
import io.github.wkoller.pollenplot.notify.HiveReminderCheck
import io.github.wkoller.pollenplot.notify.HiveReminderWorker
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.notify.WaterPlanCheck
import io.github.wkoller.pollenplot.place.LocationSource
import io.github.wkoller.pollenplot.place.PhoneLocation
import io.github.wkoller.pollenplot.place.PlaceFinder
import io.github.wkoller.pollenplot.scan.AccurateScanWorker
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.MediaStoreGallery
import io.github.wkoller.pollenplot.scan.PhotoScanner
import io.github.wkoller.pollenplot.search.MeaningIndexWorker
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.web.HttpWeb
import io.github.wkoller.pollenplot.web.Web
import io.github.wkoller.pollenplot.weeds.WeedLibrary
import io.github.wkoller.pollenplot.weeds.WeedPhotos
import io.github.wkoller.pollenplot.weeds.WeedSync
import io.github.wkoller.pollenplot.weeds.WeedSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate

/**
 * Builds the app's shared objects once. Screens and workers reach it through [PollenplotApp]. Tests pass an in-memory
 * [database], a fake [identifier] and [embedder] in place of the on-device models, a [gallery] of their own photos,
 * recorded [web] answers, a fixed [location], a [fixedClock] and an [assistant] in place of Gemma 4.
 */
class AppContainer(
    context: Context,
    database: PollenplotDatabase? = null,
    identifier: Identifier? = null,
    gallery: Gallery? = null,
    embedder: Embedder? = null,
    web: Web? = null,
    location: LocationSource? = null,
    fixedClock: Clock? = null,
    assistant: Assistant? = null,
) {
    val appContext: Context = context.applicationContext
    val clock: Clock = fixedClock ?: PhoneClock
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
    val bees = BeeRepository(this.database, clock)
    val nativeBees = NativeBeeRepository(this.database, clock)
    val hotels = HotelRepository(this.database, clock)
    val garden = GardenRepository(
        db = this.database,
        clock = clock,
        knowledge = { knowledge.await() },
        locality = { Locality(settingsStore.settings.first().gardenState, weeds.data.value) },
        fauna = { knowledge.awaitFauna() },
    )
    val views = GardenViews(garden, knowledge, settingsStore, weeds.data)
    val beeViews = BeeViews(bees, nativeBees, hotels, garden, settingsStore, clock)
    val guides = GuideLibrary(appContext)
    val rhythms = RhythmsViews(garden, views, bees, nativeBees, knowledge, settingsStore, clock)
    val places = PlaceFinder(this.web) { weeds.sources }
    val water = WaterRepository(this.database, this.web, clock)
    val waterViews = WaterViews(water, views, garden, settingsStore, clock)
    val location: LocationSource = location ?: PhoneLocation(appContext)
    val weedSync = WeedSync(this.web, weeds, places, settingsStore, { namesToMatch() }, clock) { notifications.showWeedsNearby(it) }
    val weedPhotos = WeedPhotos(this.web) { weeds.sources }
    val photos = PhotoStore(appContext)

    val accurate = AccuratePack(appContext, scope)

    /**
     * The models are loaded the first time a photo is identified, not when the app starts. With the accurate pack on
     * the phone, BioCAP names what the quick model finds and looks again where it was unsure.
     */
    private val testIdentifier = identifier
    private val fast by lazy { FastPlantIdentifier(appContext) }
    private val quickIdentifier: Identifier by lazy { testIdentifier ?: fast }
    val identifier: Identifier by lazy { testIdentifier ?: AccuratePlantIdentifier(fast) { accurate.open() } }
    val analysis = PhotoAnalysis({ this.identifier }, photos, garden, knowledge, clock, ::recordsNearby)
    val gallery: Gallery = gallery ?: MediaStoreGallery(appContext)
    val scanner = PhotoScanner(this.gallery, PhotoAnalysis({ quickIdentifier }, photos, garden, knowledge, clock, ::recordsNearby), analysis, garden, clock)
    val notifications = Notifications(appContext)
    val hiveReminders = HiveReminderCheck(beeViews, settingsStore, notifications)
    val heatAlerts = HeatAlertCheck(nativeBees, settingsStore, this.web, notifications, clock)
    val waterPlan = WaterPlanCheck(water, waterViews, settingsStore, notifications)
    val reports = ReportBuilder(appContext, views, bees, nativeBees, hotels, water, waterViews, rhythms, settingsStore, clock)
    val meaning = MeaningSearch(appContext, garden, scope, embedder)
    val gemma = GemmaModel(appContext)
    val assistant: Assistant = assistant ?: GardenAssistant(appContext, gemma, views, garden, knowledge, meaning, settingsStore) { error ->
        Log.w("GardenAssistant", "Gemma 4 could not answer", error)
    }

    init {
        // Finds already waiting for review get Gemma 4's second opinion once it is on the phone.
        scope.launch {
            gemma.state.first { it == DownloadState.Complete }
            SecondOpinionWorker.enqueue(appContext)
        }
        // The search text includes knowledge base facts and badges, so it is rebuilt when the app starts and whenever
        // the garden's state or weed lists change. A garden holds hundreds of records at most, so this takes a moment.
        scope.launch {
            knowledge.await()
            views.locality.distinctUntilChanged().collectLatest {
                garden.reindexAll()
                // The rebuild clears the whole index, hives included.
                bees.reindex()
                nativeBees.reindex()
                hotels.reindex()
            }
        }
        // The weed lists are brought up to date once a month on Wi-Fi, and straight away when the garden moves.
        scope.launch {
            WeedSyncWorker.schedule(appContext)
            settingsStore.settings.map { settings -> settings.gardenPlace?.let { it.latE7 to it.lonE7 } }
                .distinctUntilChanged().drop(1)
                .collect { WeedSyncWorker.runNow(appContext) }
        }
        // The hives are looked at each morning for honey bee checks that have fallen due and hot days ahead for native
        // bees, while there is a hive to remind or warn about.
        scope.launch {
            combine(settingsStore.settings, bees.hives, nativeBees.hives, garden.plantList) { settings, honey, natives, plants ->
                (settings.hiveReminders && honey.isNotEmpty()) ||
                    (settings.heatAlerts && settings.gardenPlace != null && natives.any { it.status == NativeStatus.ACTIVE }) ||
                    (settings.waterReminders && settings.gardenPlace != null && plants.isNotEmpty())
            }
                .distinctUntilChanged()
                .collect { HiveReminderWorker.update(appContext, it, clock) }
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

    /** Every record, the garden's and the bees', as one JSON-LD document for backups and "Export data". */
    suspend fun exportJsonLd(): String = JsonLd.document(garden.triples() + bees.triples() + nativeBees.triples() + hotels.triples()).toString()

    /** The sightings as a Darwin Core table, with the garden's place as rounded as it is kept, and its state's name. */
    suspend fun exportDarwinCore(stateName: String?): String {
        val fauna = knowledge.awaitFauna()
        val sightings = garden.sightingList.first().map { it to fauna.animal(it.speciesName ?: it.label) }
        return DarwinCore.csv(sightings, settingsStore.current().gardenPlace, stateName, clock.zone)
    }

    /** A garden report of [parts] for the records from [from] to [to], written to [out] as a PDF or a spreadsheet. */
    suspend fun writeReport(parts: Set<ReportPart>, from: LocalDate, to: LocalDate, pdf: Boolean, out: OutputStream) {
        val report = reports.build(parts, from, to)
        withContext(Dispatchers.IO) {
            if (pdf) PdfReportWriter(appContext.getString(R.string.report_footer)).write(report, out) else XlsxWriter.write(report, out)
        }
    }

    /** How many times a plant has been recorded near the garden, as the last weed update found. */
    fun recordsNearby(name: String): Int = weeds.data.value.recordsNearby(name)

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
