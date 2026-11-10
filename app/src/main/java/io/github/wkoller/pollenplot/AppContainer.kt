package io.github.wkoller.pollenplot

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import io.github.wkoller.pollenplot.ai.Assistant
import io.github.wkoller.pollenplot.ai.GardenAssistant
import io.github.wkoller.pollenplot.ai.GemmaModel
import io.github.wkoller.pollenplot.ai.SecondOpinionWorker
import io.github.wkoller.pollenplot.backup.BackupService
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.CorrectionRepository
import io.github.wkoller.pollenplot.data.FeedLibrary
import io.github.wkoller.pollenplot.data.FeedRepository
import io.github.wkoller.pollenplot.data.FeedViews
import io.github.wkoller.pollenplot.data.GardenRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.HabitatRepository
import io.github.wkoller.pollenplot.data.HabitatViews
import io.github.wkoller.pollenplot.data.HarvestRepository
import io.github.wkoller.pollenplot.data.HarvestViews
import io.github.wkoller.pollenplot.data.HotelRepository
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.data.NamedPlant
import io.github.wkoller.pollenplot.data.PhotoAnalysis
import io.github.wkoller.pollenplot.data.PotRepository
import io.github.wkoller.pollenplot.data.PotViews
import io.github.wkoller.pollenplot.data.ProblemRepository
import io.github.wkoller.pollenplot.data.ProblemViews
import io.github.wkoller.pollenplot.data.RhythmsViews
import io.github.wkoller.pollenplot.data.SearchPlaces
import io.github.wkoller.pollenplot.data.TodayItem
import io.github.wkoller.pollenplot.data.TodayViews
import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.data.WaterViews
import io.github.wkoller.pollenplot.data.WishRepository
import io.github.wkoller.pollenplot.data.WishViews
import io.github.wkoller.pollenplot.data.db.PollenplotDatabase
import io.github.wkoller.pollenplot.data.guides.GuideLibrary
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.photos.PhotoStore
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.feeding.FeedingGuide
import io.github.wkoller.pollenplot.domain.feeding.Products
import io.github.wkoller.pollenplot.domain.garden.PotTaskKind
import io.github.wkoller.pollenplot.domain.garden.PruningGuide
import io.github.wkoller.pollenplot.domain.garden.WishStatus
import io.github.wkoller.pollenplot.domain.problems.ProblemCatalogue
import io.github.wkoller.pollenplot.domain.report.XlsxWriter
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import io.github.wkoller.pollenplot.domain.semantic.DarwinCore
import io.github.wkoller.pollenplot.domain.semantic.JsonLd
import io.github.wkoller.pollenplot.domain.weeds.WeedAlternatives
import io.github.wkoller.pollenplot.download.DownloadState
import io.github.wkoller.pollenplot.ml.AccuratePack
import io.github.wkoller.pollenplot.ml.AiyModel
import io.github.wkoller.pollenplot.ml.AccuratePlantIdentifier
import io.github.wkoller.pollenplot.ml.FastPlantIdentifier
import io.github.wkoller.pollenplot.ml.Identifier
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.ml.text.Embedder
import io.github.wkoller.pollenplot.notify.FeedPlanCheck
import io.github.wkoller.pollenplot.notify.HeatAlertCheck
import io.github.wkoller.pollenplot.notify.HiveReminderCheck
import io.github.wkoller.pollenplot.notify.HiveReminderWorker
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.notify.PotCareCheck
import io.github.wkoller.pollenplot.notify.ProblemCheck
import io.github.wkoller.pollenplot.notify.PruneCheck
import io.github.wkoller.pollenplot.notify.WaterPlanCheck
import io.github.wkoller.pollenplot.place.LocationSource
import io.github.wkoller.pollenplot.place.PhoneLocation
import io.github.wkoller.pollenplot.place.PlaceFinder
import io.github.wkoller.pollenplot.report.PdfReportWriter
import io.github.wkoller.pollenplot.report.ReportBuilder
import io.github.wkoller.pollenplot.report.ReportPart
import io.github.wkoller.pollenplot.report.ReportTask
import io.github.wkoller.pollenplot.scan.AccurateScanWorker
import io.github.wkoller.pollenplot.scan.Gallery
import io.github.wkoller.pollenplot.scan.MediaStoreGallery
import io.github.wkoller.pollenplot.scan.PhotoScanner
import io.github.wkoller.pollenplot.search.MeaningIndexWorker
import io.github.wkoller.pollenplot.search.MeaningSearch
import io.github.wkoller.pollenplot.ui.ListWords
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.label
import io.github.wkoller.pollenplot.ui.weeds.WeedHelp
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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
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

    /** Whether an earlier release candidate's database, restored by Android's backup, was deleted at this start. */
    private var restoredDatabaseDiscarded = false
    val database: PollenplotDatabase = database ?: PollenplotDatabase.create(appContext) { restoredDatabaseDiscarded = true }
    val corrections = CorrectionRepository(this.database, clock)
    val knowledge = KnowledgeRepository(appContext, scope, corrections.profiles)
    val bees = BeeRepository(this.database, clock)
    val nativeBees = NativeBeeRepository(this.database, clock)
    val hotels = HotelRepository(this.database, clock)
    val garden = GardenRepository(
        db = this.database,
        clock = clock,
        knowledge = { knowledge.await() },
        locality = { settingsStore.settings.first().let { Locality(it.gardenState, weeds.data.value, it.hemisphere) } },
        fauna = { knowledge.awaitFauna() },
    )
    val water = WaterRepository(this.database, this.web, clock)
    val views = GardenViews(garden, knowledge, settingsStore, weeds.data, water.lines)
    val beeViews = BeeViews(bees, nativeBees, hotels, garden, settingsStore, clock)
    val guides = GuideLibrary(appContext)
    val rhythms = RhythmsViews(garden, views, bees, nativeBees, knowledge, settingsStore, clock)
    val habitat = HabitatRepository(this.database, clock)
    val habitatViews = HabitatViews(views, habitat, beeViews, rhythms)
    val places = PlaceFinder(this.web) { weeds.sources }
    val pots = PotRepository(this.database, clock)
    val potViews = PotViews(pots, views, clock)

    /** The built-in products and feeding schedules, read from the assets once they are first needed. */
    val feedLibrary = FeedLibrary(
        { readAsset("kb/products.json")?.let { text -> Products.parse(text).also { logProblems(it.problems) }.products }.orEmpty() },
        { readAsset("kb/feeding.json")?.let { text -> FeedingGuide.parse(text).also { logProblems(it.second) }.first } ?: FeedingGuide.EMPTY },
    )
    val feeds = FeedRepository(this.database, clock, feedLibrary)

    /** The catalogue of pests and problems, read from the assets once it is first needed. */
    private val problemCatalogue by lazy {
        readAsset("kb/problems.json")?.let { text -> ProblemCatalogue.parse(text).also { logProblems(it.second) }.first } ?: ProblemCatalogue.EMPTY
    }
    val catalogue: ProblemCatalogue get() = problemCatalogue

    /** The sourced pruning times, read from the assets once they are first needed. */
    val pruningGuide: PruningGuide by lazy {
        readAsset("kb/pruning.json")?.let { text -> PruningGuide.parse(text).also { logProblems(it.second) }.first } ?: PruningGuide.EMPTY
    }
    val waterViews = WaterViews(water, views, garden, settingsStore, clock, potViews.pottedZones)
    val location: LocationSource = location ?: PhoneLocation(appContext)
    val weedSync = WeedSync(this.web, weeds, places, settingsStore, { namesToMatch() }, clock) { notifications.showWeedsNearby(it) }
    val weedPhotos = WeedPhotos(this.web) { weeds.sources }
    val photos = PhotoStore(appContext)
    val problems = ProblemRepository(this.database, clock, { problemCatalogue }, { knowledge.await() }, photos::delete)
    val wishes = WishRepository(this.database, clock, { knowledge.await() }, photos::delete)

    val wishViews = WishViews(wishes, views, knowledge, settingsStore)
    val searchPlaces = SearchPlaces(views, beeViews, wishes, problems, habitat)

    /** Native plants to grow instead of garden weeds, read from the assets once they are first needed. */
    val weedHelp = WeedHelp(
        { appContext.assets.open("weeds/alternatives.json").bufferedReader().use { WeedAlternatives.parse(it.readText()) } },
        wishes,
        settingsStore,
    )

    val accurate = AccuratePack(appContext, scope)

    /**
     * The plants the models can name, for the search that corrects a suggestion: the accurate pack's, which have
     * common names, then the quick model's.
     */
    suspend fun plantNames(): List<NamedPlant> {
        val quick = withContext(Dispatchers.IO) {
            runCatching { appContext.assets.open(AiyModel.PLANTS.labels).bufferedReader().useLines { lines -> lines.map(String::trim).filter { it.isNotEmpty() && it != "None" }.map { NamedPlant(it) }.toList() } }
                .getOrDefault(emptyList())
        }
        return (accurate.plantNames() + quick).distinctBy { it.name.lowercase() }
    }

    /**
     * The models are loaded the first time a photo is identified, not when the app starts. With the accurate pack on
     * the phone, BioCAP names what the quick model finds and looks again where it was unsure.
     */
    private val testIdentifier = identifier
    private val fast by lazy { FastPlantIdentifier(appContext) }
    private val quickIdentifier: Identifier by lazy { testIdentifier ?: fast }
    val identifier: Identifier by lazy { testIdentifier ?: AccuratePlantIdentifier(fast) { accurate.open() } }
    val analysis = PhotoAnalysis({ this.identifier }, photos, garden, knowledge, clock, ::recordsNearby, ::gardenHemisphere, ::plantNames)
    val gallery: Gallery = gallery ?: MediaStoreGallery(appContext)
    val scanner = PhotoScanner(this.gallery, PhotoAnalysis({ quickIdentifier }, photos, garden, knowledge, clock, ::recordsNearby, ::gardenHemisphere), analysis, garden, clock)
    val notifications = Notifications(appContext)
    val hiveReminders = HiveReminderCheck(beeViews, settingsStore, notifications)
    val heatAlerts = HeatAlertCheck(nativeBees, settingsStore, this.web, notifications, clock)
    val waterPlan = WaterPlanCheck(water, waterViews, settingsStore, notifications)
    val potCare = PotCareCheck(potViews, settingsStore, notifications)
    val feedViews = FeedViews(feeds, views, water, settingsStore, feedLibrary, clock)
    val feedPlan = FeedPlanCheck(feedViews, settingsStore, notifications)
    val problemViews = ProblemViews(problems, views, garden, settingsStore, { problemCatalogue }, clock)
    val problemCheck = ProblemCheck(problemViews, settingsStore, notifications)
    val harvests = HarvestRepository(this.database, clock, garden::reindexPlant)
    val harvestViews = HarvestViews(harvests, views, pots, { pruningGuide }, clock)
    val pruneCheck = PruneCheck(harvestViews, settingsStore, notifications)
    val reports = ReportBuilder(appContext, views, bees, nativeBees, hotels, water, waterViews, rhythms, settingsStore, clock, wishViews, harvests, ::dueTasks)
    val today = TodayViews(
        water, waterViews, beeViews, garden, views, settingsStore, clock,
        combine(potViews.due, feedViews.due, feedViews.conditioner, problemViews.due, harvestViews.due) { potsDue, feedsDue, conditioner, followUps, prunes ->
            potsDue.map { TodayItem.PotCare(it.plantId, it.title, it.task.kind) } +
                feedsDue.map { TodayItem.Feed(it.due.zoneId, it.zoneName, it.due.plants) } +
                followUps.map { TodayItem.ProblemFollowUp(it.problemId, it.name, it.plantTitle) } +
                prunes.map { TodayItem.Prune(it.plantId, it.title) } +
                listOfNotNull(conditioner?.let { TodayItem.Conditioner(it.day == LocalDate.now(clock), it.maxTenthsC) })
        },
    )
    val meaning = MeaningSearch(appContext, garden, scope, embedder)
    val gemma = GemmaModel(appContext)
    val assistant: Assistant = assistant ?: GardenAssistant(appContext, gemma, views, garden, knowledge, meaning, settingsStore) { error ->
        Log.w("GardenAssistant", "Gemma 4 could not answer", error)
    }
    val backups = BackupService(appContext, this.database, settingsStore, clock, ::exportJsonLd, ::afterRestore, scope)

    init {
        // A restore that was read but not finished, or stopped part way, leaves files behind until the next start.
        scope.launch { backups.tidy() }
        // Finds already waiting for review get Gemma 4's second opinion once it is on the phone.
        scope.launch {
            gemma.state.first { it == DownloadState.Complete }
            SecondOpinionWorker.enqueue(appContext)
        }
        // The search text includes knowledge base facts and badges, so it is rebuilt when the app starts and whenever
        // the garden's state, its weed lists or the user's corrections change. A garden holds hundreds of records at
        // most, so this takes a moment.
        scope.launch {
            knowledge.await()
            combine(views.locality.distinctUntilChanged(), knowledge.knowledge.filterNotNull()) { place, kb -> place to kb }.collectLatest {
                rebuildSearch()
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
                    (settings.waterReminders && settings.gardenPlace != null && plants.isNotEmpty()) ||
                    ((settings.potReminders || settings.feedReminders || settings.problemReminders || settings.pruneReminders) && plants.isNotEmpty())
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

    /**
     * After a backup is restored, the search index is rebuilt from its records, and the finds it brings that are
     * waiting for review get the accurate pack's and Gemma 4's look when they are on the phone. Everything else follows
     * the records and settings by itself, from the weed lists for a garden that moved to the morning reminders.
     */
    private suspend fun afterRestore() {
        try {
            knowledge.await()
            rebuildSearch()
        } catch (e: RuntimeException) {
            // The index is rebuilt again when the app next starts, so search catches up then.
            Log.w("Backup", "The search index could not be rebuilt after a restore", e)
        }
        if (accurate.state.value == PackState.Ready) AccurateScanWorker.enqueue(appContext)
        if (gemma.state.value == DownloadState.Complete) SecondOpinionWorker.enqueue(appContext)
    }

    /** Every record, the garden's and the bees', as one JSON-LD document for backups and "Export data". */
    /**
     * Whether the first-launch setup opens: only for a garden with no place and no zones that has not been through it.
     * A garden that already has either never sees it, even if it is later emptied.
     */
    suspend fun setupNeeded(): Boolean {
        // The settings restored with a database that was deleted describe a garden that is gone, so they go too.
        if (restoredDatabaseDiscarded) {
            settingsStore.update { Settings() }
            restoredDatabaseDiscarded = false
        }
        val settings = settingsStore.current()
        if (settings.setupDone) return false
        if (settings.gardenPlace == null && garden.zoneList.first().isEmpty()) return true
        settingsStore.update { it.copy(setupDone = true) }
        return false
    }

    /**
     * Rebuilds the whole search index in one transaction, so a search made meanwhile sees the old index or the new one
     * and never one part cleared: the garden's rebuild clears every record, hives, hotels, wishes, problems and water features included.
     */
    private suspend fun rebuildSearch() = database.withTransaction {
        garden.reindexAll()
        bees.reindex()
        nativeBees.reindex()
        hotels.reindex()
        wishes.reindex()
        problems.reindex()
        habitat.reindex()
    }

    private fun readAsset(path: String): String? =
        runCatching { appContext.assets.open(path).bufferedReader().use { it.readText() } }.getOrElse {
            Log.w("Feeding", "$path could not be read", it)
            null
        }

    // The tests stop a faulty file from shipping, so a problem is only logged.
    private fun logProblems(problems: List<String>) {
        if (problems.isNotEmpty()) Log.w("Feeding", problems.joinToString("\n"))
    }

    suspend fun exportJsonLd(): String = JsonLd.document(garden.triples() + bees.triples() + nativeBees.triples() + hotels.triples() + wishes.triples() + corrections.triples() + pots.triples() + feeds.triples() + problems.triples() + habitat.triples() + harvests.triples()).toString()

    /** The sightings as a Darwin Core table, with the garden's place as rounded as it is kept, and its state's name. */
    suspend fun exportDarwinCore(stateName: String?): String {
        val fauna = knowledge.awaitFauna()
        val sightings = garden.sightingList.first().map { it to fauna.animal(it.speciesName ?: it.label) }
        return DarwinCore.csv(sightings, settingsStore.current().gardenPlace, stateName, clock.zone)
    }

    /**
     * A garden report of [parts] for the records from [from] to [to], written to [out] as a PDF or a spreadsheet. A PDF
     * shows the plants' cut-outs when [pictures] is set; a spreadsheet has no place for them.
     */
    suspend fun writeReport(parts: Set<ReportPart>, from: LocalDate, to: LocalDate, pdf: Boolean, out: OutputStream, pictures: Boolean = true) {
        val report = reports.build(parts, from, to, pictures = pdf && pictures)
        withContext(Dispatchers.IO) {
            if (pdf) PdfReportWriter(appContext.getString(R.string.report_footer)).write(report, out) else XlsxWriter.write(report, out)
        }
    }

    /**
     * Writes a report into the cache for sharing and returns its address for the app the user picks. Only the latest
     * report is kept there.
     */
    suspend fun reportForSharing(parts: Set<ReportPart>, from: LocalDate, to: LocalDate, pdf: Boolean, pictures: Boolean): Uri = withContext(Dispatchers.IO) {
        val folder = File(appContext.cacheDir, "reports").apply { mkdirs() }
        folder.listFiles()?.forEach { it.delete() }
        val file = File(folder, "pollenplot-report-${LocalDate.now(clock)}.${if (pdf) "pdf" else "xlsx"}")
        file.outputStream().use { writeReport(parts, from, to, pdf, it, pictures) }
        FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
    }

    /** Every job due today or before, worded as the Today card words it, for the report's Tasks part. */
    private suspend fun dueTasks(): List<ReportTask> {
        val today = LocalDate.now(clock)
        fun s(id: Int, vararg args: Any) = appContext.getString(id, *args)
        val hives = beeViews.hives.first().flatMap { view ->
            view.tasks.filter { !it.due.isAfter(today) }.map { ReportTask(s(R.string.today_hive_due, view.hive.name, s(it.kind.label).lowercase()), it.due) }
        }
        val pots = potViews.due.first().map { ReportTask(s(if (it.task.kind == PotTaskKind.FLUSH) R.string.today_pot_flush else R.string.today_pot_repot, it.title), it.task.due) }
        // The joining words are patterns themselves, so they are read without filling them in.
        val words = ListWords(appContext.getString(R.string.list_and), appContext.getString(R.string.list_range), appContext.getString(R.string.list_all_year))
        val feeds = feedViews.due.first().map { ReportTask(s(R.string.today_feed, it.zoneName, UiFormat.joinAnd(it.due.plants, words)), it.due.due) }
        val followUps = problemViews.due.first().map { due ->
            ReportTask(due.plantTitle?.let { s(R.string.today_follow_up, due.name, it) } ?: s(R.string.today_follow_up_no_plant, due.name), due.due)
        }
        val prunes = harvestViews.due.first().map { ReportTask(s(R.string.today_prune, it.title), it.since) }
        return (hives + pots + feeds + followUps + prunes).sortedBy { it.due }
    }

    /** How many times a plant has been recorded near the garden, as the last weed update found. */
    fun recordsNearby(name: String): Int = weeds.data.value.recordsNearby(name)

    /** A photo the user chose to identify, upright and small enough for the models, and when it was taken. */
    suspend fun readChosenPhoto(uri: Uri): Pair<Bitmap, Instant?>? = withContext(Dispatchers.IO) {
        gallery.load(uri, PhotoStore.MAX_SIDE)?.let { it to gallery.takenAt(uri) }
    }

    /** Keeps a photo the user chose of a problem, without looking for plants in it, and returns where it is stored. */
    suspend fun saveProblemPhoto(uri: Uri): String? = readChosenPhoto(uri)?.let { (bitmap, _) -> photos.savePhoto(bitmap).path }

    /** Lets go of a photo taken away from home with nothing saved from it, whatever the screen does meanwhile. */
    fun discardFieldPhoto(photoId: String) {
        scope.launch { wishes.discardFieldPhoto(photoId) }
    }

    /** Records that a water feature was cleaned and filled today. */
    fun waterCleaned(id: String) {
        scope.launch { habitat.cleaned(id, LocalDate.now(clock)) }
    }

    /** Marks a wish planted once its plant is in the garden. */
    fun wishPlanted(id: String) {
        scope.launch { wishes.setStatus(id, WishStatus.PLANTED) }
    }

    /** The garden's hemisphere, as the settings hold it now. */
    private suspend fun gardenHemisphere(): Hemisphere = settingsStore.settings.first().hemisphere

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
