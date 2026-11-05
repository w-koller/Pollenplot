package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.feeding.ConditionerBeforeHeat
import io.github.wkoller.pollenplot.domain.feeding.FeedDue
import io.github.wkoller.pollenplot.domain.feeding.FeedPlanner
import io.github.wkoller.pollenplot.domain.feeding.FeedPlant
import io.github.wkoller.pollenplot.domain.feeding.FeedSchedule
import io.github.wkoller.pollenplot.domain.feeding.Product
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate

/** A zone's last feed, for the "Fed 12 days ago" line. */
data class LastFed(val days: Long, val product: Product?)

/** A zone due a feed, with its name. */
data class ZoneFeedDue(val zoneName: String, val due: FeedDue)

/** A hot day ahead with no conditioner given lately, for the user's own reminder. */
data class ConditionerReminder(val day: LocalDate, val maxTenthsC: Int)

/** Feeds joined with the garden: each zone's last feed, the zones due a feed, and the conditioner reminder. */
class FeedViews(feeds: FeedRepository, private val views: GardenViews, water: WaterRepository, settings: SettingsStore, private val library: FeedLibrary, private val clock: Clock) {
    val lastFed: Flow<Map<String, LastFed>> = combine(feeds.feeds, feeds.products) { list, products ->
        val today = LocalDate.now(clock)
        val byKey = products.associateBy { it.key }
        list.flatMap { feed -> feed.zoneIds.map { it to feed } }.groupBy({ it.first }, { it.second })
            .mapValues { (_, zoneFeeds) -> zoneFeeds.maxBy { it.date }.let { LastFed(FeedPlanner.daysSince(it.date, today), byKey[it.productKey]) } }
    }

    val due: Flow<List<ZoneFeedDue>> = combine(views.plants, views.zones, feeds.feeds, feeds.kinds, settings.settings) { plants, zones, list, kinds, current ->
        val names = zones.associate { it.zone.id to it.zone.name }
        val feedPlants = plants.filter { it.isGrowing && it.plant.zoneId != null }.map { view ->
            val species = view.species
            FeedPlant(view.title, view.plant.zoneId!!, library.guide.forPlant(species?.name ?: view.plant.speciesName, species?.genus, species?.family))
        }
        FeedPlanner.due(feedPlants, list, kinds, LocalDate.now(clock), current.hemisphere)
            .mapNotNull { due -> names[due.zoneId]?.let { ZoneFeedDue(it, due) } }
    }

    val conditioner: Flow<ConditionerReminder?> = combine(water.weather, feeds.feeds, feeds.kinds, settings.settings) { weather, list, kinds, current ->
        if (!current.conditionerBeforeHeat) return@combine null
        ConditionerBeforeHeat.remind(weather.map { it.date to it.maxTenthsC }, list, kinds, LocalDate.now(clock))?.let { ConditionerReminder(it.first, it.second) }
    }

    fun zoneLastFed(zoneId: String): Flow<LastFed?> = lastFed.map { it[zoneId] }

    fun today(): LocalDate = LocalDate.now(clock)

    /** The feeding schedules the plants growing in a zone follow, each with its source. */
    fun zoneSchedules(zoneId: String): Flow<List<FeedSchedule>> = views.plants.map { plants ->
        plants.filter { it.isGrowing && it.plant.zoneId == zoneId }
            .mapNotNull { library.guide.forPlant(it.species?.name ?: it.plant.speciesName, it.species?.genus, it.species?.family) }
            .distinct()
    }
}
