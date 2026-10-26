package io.github.wkoller.pollenplot.weeds

import android.util.Log
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.weeds.AlaLists
import io.github.wkoller.pollenplot.domain.weeds.BrisbaneWeeds
import io.github.wkoller.pollenplot.domain.weeds.CouncilSource
import io.github.wkoller.pollenplot.domain.weeds.CouncilWeed
import io.github.wkoller.pollenplot.domain.weeds.NationalWeeds
import io.github.wkoller.pollenplot.domain.weeds.NearbyRecords
import io.github.wkoller.pollenplot.domain.weeds.NearbyWeed
import io.github.wkoller.pollenplot.domain.weeds.StateSource
import io.github.wkoller.pollenplot.domain.weeds.WeedResolver
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.domain.weeds.WeedStatus
import io.github.wkoller.pollenplot.domain.weeds.WeedsAustraliaLinks
import io.github.wkoller.pollenplot.domain.weeds.WeedsNearby
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/** The weed information for the garden's area: the sources that apply there and what the last update saved for them. */
data class WeedData(
    val state: StateSource?,
    /** The garden's council, when it publishes a weed list the app reads. */
    val council: CouncilSource?,
    /** The council's list, once it has been downloaded. */
    val councilWeeds: List<CouncilWeed>,
    val national: NationalWeeds,
    /** Plant records near the garden, once they have been downloaded for where the garden is now. */
    val nearby: NearbyRecords?,
    /** The weeds among [nearby], most serious first. */
    val nearbyWeeds: List<NearbyWeed>,
    /** Names the Atlas of Living Australia accepts, by binomial in lower case. */
    val accepted: Map<String, String>,
    val weedsAustralia: WeedsAustraliaLinks?,
    /** When every source was last brought up to date; null before that first happens. */
    val updatedAt: Instant?,
    /** The last update could not reach every source. */
    val lastUpdateFailed: Boolean,
) {
    /** The status of the plant known by [names], its accepted name and synonyms. */
    fun status(names: Collection<String>): WeedStatus = WeedResolver.resolve(names, councilWeeds, national, nearby, accepted)

    /** Whether any list has been downloaded, so a plant that is on none of them can be said not to be a weed. */
    val hasLists: Boolean get() = councilWeeds.isNotEmpty() || national.wons.isNotEmpty()

    companion object {
        val EMPTY = WeedData(null, null, emptyList(), NationalWeeds(), null, emptyList(), emptyMap(), null, null, false)
    }
}

/** What the last update saved: when, whose council list, and where the nearby records were looked up. */
@Serializable
internal data class UpdateRecord(
    val updatedAt: Long? = null,
    val lastUpdateFailed: Boolean = false,
    val council: String? = null,
    val nearby: NearbyQuery? = null,
)

/** Where and how widely the nearby records were looked up. */
@Serializable
internal data class NearbyQuery(val lat: String, val lon: String, val radiusKm: Int, val sinceYear: Int)

/**
 * The weed lists and nearby records kept on the phone, so the Weed identifier works without a connection. [WeedSync]
 * writes the files; this reads them for the garden's place and state, again whenever either changes or after an update.
 */
class WeedLibrary(private val directory: File, readSources: () -> String, settings: SettingsStore, scope: CoroutineScope) {
    /** Which sources apply where, from the app's assets. */
    val sources: WeedSources by lazy { WeedSources.parse(readSources()) }

    private val reloads = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val data: StateFlow<WeedData> = combine(
        settings.settings.map { it.gardenPlace to it.gardenState }.distinctUntilChanged(),
        reloads,
    ) { where, _ -> where }
        .mapLatest { (place, state) -> load(place, state) }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, WeedData.EMPTY)

    /** Reads the files again, after an update has written them. */
    fun reload() = reloads.update { it + 1 }

    internal fun councilFile(id: String) = File(directory, "council-$id.json")

    internal fun listFile(uid: String) = File(directory, "list-$uid.json")

    internal val nearbyFile get() = File(directory, "nearby.json")

    private val namesFile get() = File(directory, "names.json")

    private val recordFile get() = File(directory, "update.json")

    internal fun record(): UpdateRecord? = read(recordFile)?.let { runCatching { json.decodeFromString(UpdateRecord.serializer(), it) }.getOrNull() }

    internal fun saveRecord(record: UpdateRecord) = write(recordFile, json.encodeToString(UpdateRecord.serializer(), record))

    /** Every name looked up so far, with the binomial the Atlas accepts for it, or an empty string if it found none. */
    internal fun names(): Map<String, String> =
        read(namesFile)?.let { runCatching { json.decodeFromString(NAMES, it) }.getOrNull() }.orEmpty()

    internal fun saveNames(names: Map<String, String>) = write(namesFile, json.encodeToString(NAMES, names))

    /** Writes the whole file or nothing, so a failed update leaves the last good copy. */
    internal fun write(file: File, text: String) {
        directory.mkdirs()
        val part = File(file.path + ".part")
        part.writeText(text)
        check(part.renameTo(file)) { "Could not save ${file.name}" }
    }

    private fun read(file: File): String? = if (file.isFile) file.readText() else null

    private fun load(place: GardenPlace?, state: AuState?): WeedData {
        val record = record()
        val accepted = names().filter { (name, accepted) -> accepted.isNotEmpty() && accepted != name }
        val council = sources.council(place?.council, state)
        // A list saved for another council, before the garden moved, does not apply here.
        val councilWeeds = council?.takeIf { record?.council == it.id }
            ?.let { parse("council list") { read(councilFile(it.id))?.let { text -> parseCouncil(it, text) } } }
            .orEmpty()
            .map { it.copy(names = it.names.withAccepted(accepted)) }
        fun list(uid: String) = parse("list $uid") { read(listFile(uid))?.let(AlaLists::parse) }.orEmpty().map { it.copy(names = it.names.withAccepted(accepted)) }
        val national = NationalWeeds(wons = list(sources.national.wons), alert = list(sources.national.alert))
        val query = record?.nearby?.takeIf { place != null && it.lat == place.latText && it.lon == place.lonText }
        val nearby = query?.let { q -> parse("nearby records") { read(nearbyFile)?.let { NearbyRecords.parse(it, q.radiusKm, q.sinceYear) } } }
        return WeedData(
            state = sources.state(state),
            council = council,
            councilWeeds = councilWeeds,
            national = national,
            nearby = nearby,
            nearbyWeeds = nearby?.let { WeedsNearby.of(councilWeeds, national, it, accepted) }.orEmpty(),
            accepted = accepted,
            weedsAustralia = sources.weedsAustralia,
            updatedAt = record?.updatedAt?.let(Instant::ofEpochMilli),
            lastUpdateFailed = record?.lastUpdateFailed == true,
        )
    }

    private fun <T> parse(what: String, block: () -> T?): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "The saved $what could not be read", e)
        null
    }

    companion object {
        private const val TAG = "WeedLibrary"
        private val json = Json { ignoreUnknownKeys = true }
        private val NAMES = MapSerializer(String.serializer(), String.serializer())

        /** A council's list in the format its source names. */
        fun parseCouncil(source: CouncilSource, text: String): List<CouncilWeed> = when (source.format) {
            WeedSources.BRISBANE_OPEN_DATA -> BrisbaneWeeds.parse(text)
            else -> emptyList()
        }
    }
}
