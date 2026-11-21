package io.github.wkoller.pollenplot.data

import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.assistant.GardenPulse
import io.github.wkoller.pollenplot.domain.assistant.MidSentence
import io.github.wkoller.pollenplot.domain.assistant.PartOfDay
import io.github.wkoller.pollenplot.domain.assistant.PulseFollowUp
import io.github.wkoller.pollenplot.domain.assistant.PulseGap
import io.github.wkoller.pollenplot.domain.assistant.PulseHotDay
import io.github.wkoller.pollenplot.domain.assistant.PulsePlant
import io.github.wkoller.pollenplot.domain.assistant.PulseVisitor
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.season.Seasons
import io.github.wkoller.pollenplot.domain.wildlife.Biodiversity
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month

/**
 * What the garden is doing now, for the Ask screen's greeting and questions and for the summary the model is given:
 * read once when it is wanted, because Pollen Rhythms works through every species to find the bees' gaps.
 */
class AskViews(
    private val views: GardenViews,
    private val rhythms: RhythmsViews,
    private val knowledge: KnowledgeRepository,
    private val water: WaterRepository,
    private val bees: BeeRepository,
    private val natives: NativeBeeRepository,
    private val problems: ProblemViews,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun current(): GardenPulse {
        val today = LocalDate.now(clock)
        val current = settings.current()
        val plants = views.plants.first()
        val titles = plants.associate { it.plant.id to it.title }
        val zones = plants.associate { it.plant.id to it.zoneName }
        val rhythm = rhythms.state.first()

        val inFlower = rhythm.nowFlowering.map { PulsePlant(it.id, it.title, zones[it.id]) }

        val fauna = knowledge.fauna.value
        val since = today.minusDays(RECENT_DAYS).atStartOfDay(clock.zone).toInstant()
        val visitors = Biodiversity.of(views.sightings.first().map { it.sighting }, fauna, today.year, clock.zone).species
            .filter { it.isNew && it.latest.seenAt.isAfter(since) }
            .sortedByDescending { it.latest.seenAt }
            .map { seen ->
                val plantId = seen.plantIds.firstOrNull { it in titles }
                PulseVisitor(MidSentence.name(seen.title), seen.group, seen.latest.id, plantId, plantId?.let { titles[it] })
            }

        // Solitary native bees visit any garden; honey and stingless bees count where they are kept.
        val groups = listOfNotNull(
            BeeGroup.HONEY.takeIf { rhythm.keepsHoneyBees },
            BeeGroup.STINGLESS.takeIf { rhythm.keepsStinglessBees },
            BeeGroup.SOLITARY,
        )
        val gaps = rhythm.lanes.filter { it.group in groups }.mapNotNull { lane ->
            val ahead = (1..12).map { today.month.plus(it.toLong()) }
            val month: Month? = if (!lane.fed) null else ahead.firstOrNull { it in lane.gaps } ?: return@mapNotNull null
            val plantsFor = rhythm.suggestions[lane.group].orEmpty().filter { month == null || month in it.fills }.take(SUGGESTED).map { it.species.displayName }
            PulseGap(lane.group, month, plantsFor)
        }

        val stingless = natives.hives.first().count { it.status == NativeStatus.ACTIVE }
        val hotDays = if (stingless > 0 && current.heatAlerts) {
            water.weather.first()
                .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(WEEK)) }
                .mapNotNull { day -> day.maxTenthsC?.takeIf { it >= current.heatAlertTenthsC }?.let { PulseHotDay(day.date, it) } }
        } else {
            emptyList()
        }

        val followUps = problems.due.first().map { PulseFollowUp(it.name, it.plantId, it.plantTitle, it.due) }
        val honey = bees.hives.first().count { it.status == HiveStatus.ACTIVE || it.status == HiveStatus.QUEENLESS }

        return GardenPulse(
            today = today,
            season = Seasons.of(today.month, current.hemisphere),
            partOfDay = PartOfDay.of(LocalTime.now(clock).hour),
            inFlower = inFlower,
            visitors = visitors,
            gaps = gaps,
            hotDays = hotDays,
            followUps = followUps,
            honeyHives = honey,
            stinglessHives = stingless,
        )
    }

    private companion object {
        /** How lately an animal new this year must have been seen to be a new visitor, as Pollen Rhythms' visits. */
        const val RECENT_DAYS = 30L
        const val WEEK = 6L
        const val SUGGESTED = 3
    }
}
