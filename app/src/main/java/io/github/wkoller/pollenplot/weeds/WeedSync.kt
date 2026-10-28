package io.github.wkoller.pollenplot.weeds

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.wkoller.pollenplot.PollenplotApp
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.weeds.AlaLists
import io.github.wkoller.pollenplot.domain.weeds.AlaNames
import io.github.wkoller.pollenplot.domain.weeds.NearbyRecords
import io.github.wkoller.pollenplot.domain.weeds.NearbySource
import io.github.wkoller.pollenplot.domain.weeds.NearbyWeed
import io.github.wkoller.pollenplot.domain.weeds.WeedLevel
import io.github.wkoller.pollenplot.domain.weeds.WeedNames
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.domain.wildlife.NearbyAnimals
import io.github.wkoller.pollenplot.domain.wildlife.RecordedAnimal
import io.github.wkoller.pollenplot.place.PlaceFinder
import io.github.wkoller.pollenplot.web.Web
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Clock
import java.time.Year
import java.util.concurrent.TimeUnit

/** How an update went: how many sources could not be reached. Each of those kept what it had before. */
data class UpdateOutcome(val failed: Int) {
    val complete: Boolean get() = failed == 0
}

/**
 * Brings the weed information for the garden's area up to date: the council's list where the council publishes one,
 * the national lists, the plants recorded near the garden, and the accepted names that link them. Only the rounded
 * garden location and plant names are sent. [namesToMatch] gives the names the app may need to look up: the garden's
 * plants, the knowledge base and the identifier's labels.
 */
class WeedSync(
    private val web: Web,
    private val library: WeedLibrary,
    private val places: PlaceFinder,
    private val settings: SettingsStore,
    private val namesToMatch: suspend () -> Collection<String>,
    private val clock: Clock,
    /** Told about serious weeds recorded near the garden since the last update, to let the user know. */
    private val onNewWeedsNearby: (List<NearbyWeed>) -> Unit = {},
) {
    private val lock = Mutex()

    suspend fun update(): UpdateOutcome = lock.withLock {
        val sources = library.sources
        var failed = 0
        val chosen = settings.current().gardenPlace
        var place = chosen
        // The council area is looked up when the place is chosen; this catches a place chosen without a connection.
        if (chosen != null && chosen.council == null) {
            val area = attempt("the council area") { places.area(chosen) }
            if (area == null) {
                failed++
            } else {
                val found = chosen.copy(council = area.council)
                place = found
                settings.update { it.copy(gardenPlace = found, gardenState = area.state ?: it.gardenState) }
            }
        }
        val state = settings.current().gardenState
        val previous = library.record() ?: UpdateRecord()
        val listed = mutableSetOf<String>()

        val council = sources.council(place?.council, state)
        var councilId = previous.council
        if (council != null) {
            val weeds = fetch(council.data, library.councilFile(council.id)) { WeedLibrary.parseCouncil(council, it).takeIf { weeds -> weeds.isNotEmpty() } }
            if (weeds == null) failed++ else councilId = council.id
            weeds?.forEach { listed += it.names.species }
        }
        for (uid in listOf(sources.national.wons, sources.national.alert)) {
            val weeds = fetch(sources.national.list.replace("{uid}", uid), library.listFile(uid)) { AlaLists.parse(it).takeIf { weeds -> weeds.isNotEmpty() } }
            if (weeds == null) failed++
            weeds?.forEach { listed += it.names.species }
        }

        var nearby = previous.nearby
        // The weeds already known near this place, to tell which are new. The first records for a place are not news.
        val known = if (place != null && previous.nearby?.let { it.lat == place.latText && it.lon == place.lonText } == true) {
            library.current().nearbyWeeds.map { it.name }.toSet()
        } else {
            null
        }
        if (place != null) {
            val since = Year.now(clock).value - sources.nearby.years
            val query = NearbyQuery(place.latText, place.lonText, sources.nearby.radiusKm, since)
            val url = sources.nearby.url.replace("{lat}", query.lat).replace("{lon}", query.lon)
                .replace("{radius}", query.radiusKm.toString()).replace("{since}", query.sinceYear.toString())
            val records = fetch(url, library.nearbyFile) { NearbyRecords.parse(it, query.radiusKm, query.sinceYear) }
            if (records == null) failed++ else nearby = query
            if (!fetchAnimals(sources.nearby, query)) failed++
        }

        if (!matchNames(listed + namesToMatch().map(WeedNames::binomial), sources.national.names)) failed++

        library.saveRecord(
            UpdateRecord(
                updatedAt = if (failed == 0) clock.millis() else previous.updatedAt,
                lastUpdateFailed = failed > 0,
                council = councilId,
                nearby = nearby,
            ),
        )
        library.reload()
        if (known != null) {
            val fresh = library.current().nearbyWeeds.filter { it.status.level in NEWSWORTHY && it.name !in known }
            if (fresh.isNotEmpty()) onNewWeedsNearby(fresh)
        }
        UpdateOutcome(failed)
    }

    /**
     * The animals most recorded near the garden, one request for each group. They are saved only when every group
     * answered, so a failed update keeps the last full set.
     */
    private suspend fun fetchAnimals(source: NearbySource, query: NearbyQuery): Boolean {
        val template = source.animals ?: return true
        val found = mutableListOf<RecordedAnimal>()
        for ((name, taxon) in source.groups) {
            val group = LifeGroup.of(name) ?: continue
            val url = template.replace("{taxon}", taxon).replace("{lat}", query.lat).replace("{lon}", query.lon)
                .replace("{radius}", query.radiusKm.toString()).replace("{since}", query.sinceYear.toString())
            found += attempt("the $name records") { NearbyAnimals.parse(web.get(url), group) } ?: return false
        }
        library.saveAnimals(SavedAnimals(query, found))
        return true
    }

    /** Looks up the names not asked about before, a hundred at a time. */
    private suspend fun matchNames(names: Set<String>, service: String): Boolean {
        val known = library.names()
        val wanted = names.filter { ' ' in it && it !in known }
        if (wanted.isEmpty()) return true
        val found = mutableMapOf<String, String>()
        var ok = true
        for (batch in wanted.chunked(NAMES_PER_REQUEST)) {
            val answer = attempt("name matching") { AlaNames.parse(batch, web.post(service, AlaNames.request(batch))) }
            if (answer == null) {
                ok = false
                break
            }
            found += answer
        }
        if (found.isNotEmpty()) library.saveNames(known + found)
        return ok
    }

    /** Downloads [url] and saves it to [file] if [read] can make sense of it; otherwise the file keeps what it had. */
    private suspend fun <T : Any> fetch(url: String, file: File, read: (String) -> T?): T? = attempt(file.name) {
        val text = web.get(url)
        read(text)?.also { library.write(file, text) }
    }

    private suspend fun <T : Any> attempt(what: String, block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not update $what", e)
        null
    }

    private companion object {
        const val TAG = "WeedSync"
        const val NAMES_PER_REQUEST = 100

        /** Weeds the law restricts or the nation lists; a weed only on a council's own list is not worth a notification. */
        val NEWSWORTHY = setOf(WeedLevel.PROHIBITED, WeedLevel.RESTRICTED, WeedLevel.NATIONAL)
    }
}

/** Where the weed update is: not running, waiting for a connection, or running. */
enum class UpdateState { IDLE, WAITING, RUNNING }

/** Runs [WeedSync] in the background: once a month on Wi-Fi, and straight away when the garden moves or the user asks. */
class WeedSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val outcome = (applicationContext as PollenplotApp).container.weedSync.update()
        return if (outcome.complete || runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
    }

    companion object {
        private const val MONTHLY = "weed-update"
        private const val NOW = "weed-update-now"
        private const val MAX_ATTEMPTS = 3

        /**
         * The monthly update on Wi-Fi. The first one runs as soon as the phone is on Wi-Fi. Robolectric tests set up
         * WorkManager in each test, after the app has started, so there is none to schedule with until then.
         */
        fun schedule(context: Context) {
            val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val request = PeriodicWorkRequestBuilder<WeedSyncWorker>(30, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            manager.enqueueUniquePeriodicWork(MONTHLY, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** An update on any connection: the lists are small, and the user is waiting for them. */
        fun runNow(context: Context) {
            val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val request = OneTimeWorkRequestBuilder<WeedSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        /** Whether an update is running, or one the user asked for is waiting for a connection. */
        fun state(context: Context): Flow<UpdateState> {
            val manager = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return flowOf(UpdateState.IDLE)
            return combine(manager.getWorkInfosForUniqueWorkFlow(MONTHLY), manager.getWorkInfosForUniqueWorkFlow(NOW)) { monthly, now ->
                when {
                    (monthly + now).any { it.state == WorkInfo.State.RUNNING } -> UpdateState.RUNNING
                    now.any { it.state == WorkInfo.State.ENQUEUED } -> UpdateState.WAITING
                    else -> UpdateState.IDLE
                }
            }
        }
    }
}
