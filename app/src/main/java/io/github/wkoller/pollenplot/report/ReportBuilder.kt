package io.github.wkoller.pollenplot.report

import android.content.Context
import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.BeeRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.HotelRepository
import io.github.wkoller.pollenplot.data.NativeBeeRepository
import io.github.wkoller.pollenplot.data.RhythmsViews
import io.github.wkoller.pollenplot.data.WaterRepository
import io.github.wkoller.pollenplot.data.WaterViews
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.report.PlotZone
import io.github.wkoller.pollenplot.domain.report.Report
import io.github.wkoller.pollenplot.domain.report.ReportCell
import io.github.wkoller.pollenplot.domain.report.ReportFigure
import io.github.wkoller.pollenplot.domain.report.ReportPicture
import io.github.wkoller.pollenplot.domain.report.ReportSection
import io.github.wkoller.pollenplot.domain.report.ReportTable
import io.github.wkoller.pollenplot.domain.rhythms.BeeGroup
import io.github.wkoller.pollenplot.domain.rhythms.ForageLevel
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.label
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.water.label
import io.github.wkoller.pollenplot.ui.wildlife.label
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

/** The parts of the garden a report can cover. */
enum class ReportPart(@StringRes val label: Int) {
    GARDEN(R.string.report_part_garden),
    WILDLIFE(R.string.report_part_wildlife),
    RHYTHMS(R.string.report_part_rhythms),
    HONEY(R.string.report_part_honey),
    NATIVE(R.string.report_part_native),
    HOTELS(R.string.report_part_hotels),
    WATER(R.string.report_part_water),
}

/**
 * Builds a garden report from the records, in the phone's language: each part chosen as its tables, with the records
 * from [from] to [to]. The PDF and the spreadsheet are both written from it.
 */
class ReportBuilder(
    private val context: Context,
    private val views: GardenViews,
    private val bees: BeeRepository,
    private val natives: NativeBeeRepository,
    private val hotels: HotelRepository,
    private val water: WaterRepository,
    private val waterViews: WaterViews,
    private val rhythms: RhythmsViews,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun text(value: String?) = ReportCell.Text(value.orEmpty())

    private fun number(value: Number?) = value?.let { ReportCell.Number(BigDecimal(it.toString())) } ?: ReportCell.Text("")

    private fun day(date: LocalDate?) = date?.let { ReportCell.Day(it) } ?: ReportCell.Text("")

    private fun yesNo(value: Boolean) = text(s(if (value) R.string.report_yes else R.string.report_no))

    private fun table(@StringRes title: Int, columns: List<Int>, rows: List<List<ReportCell>>) = ReportTable(s(title), columns.map { s(it) }, rows)

    /** The report of [parts] for the records from [from] to [to], with the plants' cut-outs when [pictures] is set. */
    suspend fun build(parts: Set<ReportPart>, from: LocalDate, to: LocalDate, pictures: Boolean = true): Report {
        val today = LocalDate.now(clock)
        val place = settings.current().gardenPlace
        val lines = listOfNotNull(
            s(R.string.report_covers, UiFormat.longDate(from), UiFormat.longDate(to)),
            s(R.string.report_prepared, UiFormat.longDate(today)),
            place?.name?.let { s(R.string.report_place, it) },
        )
        fun inPeriod(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)
        val sections = ReportPart.entries.filter { it in parts }.map { part ->
            when (part) {
                ReportPart.GARDEN -> garden(pictures)
                ReportPart.WILDLIFE -> wildlife(::inPeriod)
                ReportPart.RHYTHMS -> rhythms()
                ReportPart.HONEY -> honey(::inPeriod)
                ReportPart.NATIVE -> native(::inPeriod)
                ReportPart.HOTELS -> hotels(::inPeriod)
                ReportPart.WATER -> water(::inPeriod)
            }
        }
        // A part with nothing recorded says so, rather than showing a heading with nothing under it.
        val said = sections.map { section -> if (section.isEmpty) section.copy(paragraphs = listOf(s(R.string.report_none))) else section }
        return Report(s(R.string.report_title), lines, said)
    }

    private suspend fun garden(pictures: Boolean): ReportSection {
        val zones = views.zones.first()
        val plants = views.plants.first()
        val plot = ReportFigure.Plot(
            s(R.string.report_plot),
            zones.map { view -> view.zone.rect.let { PlotZone(view.zone.name, it.xCm, it.yCm, it.widthCm, it.heightCm) } },
        )
        val cutOuts = if (!pictures) {
            emptyList()
        } else {
            plants.filter { it.isGrowing }.sortedBy { it.title.lowercase() }.mapNotNull { view ->
                view.plant.thumbnail?.let { File(context.filesDir, it) }?.takeIf { it.isFile }?.let { ReportPicture(it.path, view.title) }
            }
        }
        val figures = listOfNotNull(
            plot.takeIf { it.zones.isNotEmpty() },
            cutOuts.takeIf { it.isNotEmpty() }?.let { ReportFigure.Pictures(s(R.string.report_pictures), it) },
        )
        val zoneTable = table(
            R.string.report_zones,
            listOf(R.string.col_zone, R.string.col_kind, R.string.col_sun, R.string.col_soil, R.string.col_area, R.string.col_plants),
            zones.map { view ->
                val zone = view.zone
                listOf(
                    text(zone.name), text(s(zone.kind.label)), text(zone.sun?.let { s(it.label) }), text(zone.soil?.let { s(it.label) }),
                    ReportCell.Number(BigDecimal(zone.areaCm2).movePointLeft(4).setScale(1, java.math.RoundingMode.HALF_UP)),
                    number(view.plants.sumOf { it.plant.quantity }),
                )
            },
        )
        val plantTable = table(
            R.string.report_plants,
            listOf(R.string.col_plant, R.string.col_scientific, R.string.col_zone, R.string.col_quantity, R.string.col_water, R.string.col_flowering, R.string.col_roles, R.string.col_weed),
            plants.sortedBy { it.title.lowercase() }.map { view ->
                val care = view.care?.care
                listOf(
                    text(view.title), text(view.species?.name ?: view.plant.speciesName), text(view.zoneName), number(view.plant.quantity),
                    text(care?.water?.let { s(it.label) }), text(care?.flowering?.let { UiFormat.months(it) }),
                    // Without the official lists on the phone, the national Weeds of National Significance flag still counts.
                    text(view.roles.joinToString(", ") { s(it.label) }), yesNo(view.weed.isWeed || view.species?.wons == true),
                )
            },
        )
        return ReportSection(s(R.string.report_part_garden), emptyList(), listOf(zoneTable, plantTable), figures)
    }

    private suspend fun wildlife(inPeriod: (LocalDate) -> Boolean): ReportSection {
        val zone = clock.zone
        val seen = views.sightings.first().filter { inPeriod(it.sighting.seenAt.atZone(zone).toLocalDate()) }.sortedBy { it.sighting.seenAt }
        val sightings = table(
            R.string.report_sightings,
            listOf(R.string.col_day, R.string.col_animal, R.string.col_scientific, R.string.col_group, R.string.col_count, R.string.col_zone, R.string.col_on_plant),
            seen.map { view ->
                val sighting = view.sighting
                listOf(
                    day(sighting.seenAt.atZone(zone).toLocalDate()), text(view.animal?.displayName ?: sighting.label), text(view.animal?.name ?: sighting.speciesName),
                    text(s(sighting.group.label)), number(sighting.count), text(view.zoneName), text(view.plantTitle),
                )
            },
        )
        return ReportSection(s(R.string.report_part_wildlife), emptyList(), listOf(sightings))
    }

    private suspend fun rhythms(): ReportSection {
        val state = rhythms.state.first()
        val locale = Locale.getDefault()
        val months = Month.entries.map { it.getDisplayName(TextStyle.SHORT, locale) }
        val calendar = ReportTable(
            s(R.string.report_calendar),
            listOf(s(R.string.col_bees)) + months,
            state.lanes.map { lane ->
                listOf(text(s(groupLabel(lane.group)))) + Month.entries.map { text(s(levelLabel(lane.level(it)))) }
            },
        )
        return ReportSection(s(R.string.report_part_rhythms), listOf(s(R.string.rhythms_how)), listOf(calendar))
    }

    private suspend fun honey(inPeriod: (LocalDate) -> Boolean): ReportSection {
        val hives = bees.hives.first()
        val names = hives.associate { it.id to it.name }
        val zones = views.zones.first().associate { it.zone.id to it.zone.name }
        val hiveTable = table(
            R.string.report_hives,
            listOf(R.string.col_hive, R.string.col_kind, R.string.col_zone, R.string.col_queen, R.string.col_status),
            hives.map { listOf(text(it.name), text(s(it.type.label)), text(it.zoneId?.let(zones::get)), number(it.queenYear), text(s(it.status.label))) },
        )
        val inspections = table(
            R.string.report_inspections,
            listOf(R.string.col_day, R.string.col_hive, R.string.col_queen_seen, R.string.col_brood, R.string.col_signs, R.string.col_actions),
            bees.inspections.first().filter { inPeriod(it.date) }.sortedBy { it.date }.map { inspection ->
                listOf(
                    day(inspection.date), text(names[inspection.hiveId]), yesNo(inspection.queenSeen || inspection.eggsSeen),
                    text(inspection.brood?.let { s(it.label) }), text(inspection.signs.sortedBy { it.ordinal }.joinToString(", ") { s(it.label) }), text(inspection.actions),
                )
            },
        )
        val checks = table(
            R.string.report_checks,
            listOf(R.string.col_day, R.string.col_hive, R.string.col_method, R.string.col_sample, R.string.col_mites, R.string.col_per_100),
            bees.checks.first().filter { inPeriod(it.date) }.sortedBy { it.date }.map { check ->
                listOf(
                    day(check.date), text(names[check.hiveId]), text(s(check.method.label)), number(check.sample), number(check.mites),
                    check.mitesPer100BeesTenths?.let { ReportCell.Number(BigDecimal(it).movePointLeft(1)) } ?: text(null),
                )
            },
        )
        val treatments = table(
            R.string.report_treatments,
            listOf(R.string.col_started, R.string.col_ended, R.string.col_hive, R.string.col_product, R.string.col_ingredient),
            bees.treatments.first().filter { inPeriod(it.started) || (it.ended?.let(inPeriod) ?: false) }.sortedBy { it.started }.map {
                listOf(day(it.started), day(it.ended), text(names[it.hiveId]), text(it.product), text(it.activeIngredient))
            },
        )
        val harvests = table(
            R.string.report_harvests,
            listOf(R.string.col_day, R.string.col_hive, R.string.col_frames, R.string.col_honey_kg),
            bees.harvests.first().filter { inPeriod(it.date) }.sortedBy { it.date }.map {
                listOf(day(it.date), text(names[it.hiveId]), number(it.frames), ReportCell.Number(BigDecimal(it.honeyGrams).movePointLeft(3)))
            },
        )
        return ReportSection(s(R.string.report_part_honey), emptyList(), listOf(hiveTable, inspections, checks, treatments, harvests))
    }

    private suspend fun native(inPeriod: (LocalDate) -> Boolean): ReportSection {
        val hives = natives.hives.first()
        val names = hives.associate { it.id to it.name }
        val zones = views.zones.first().associate { it.zone.id to it.zone.name }
        val hiveTable = table(
            R.string.report_native_hives,
            listOf(R.string.col_hive, R.string.col_bees, R.string.col_box, R.string.col_zone, R.string.col_status),
            hives.map { listOf(text(it.name), text(s(it.species.label)), text(s(it.box.label)), text(it.zoneId?.let(zones::get)), text(s(it.status.label))) },
        )
        val logs = table(
            R.string.report_native_logs,
            listOf(R.string.col_day, R.string.col_hive, R.string.col_record, R.string.col_measure, R.string.col_notes),
            natives.logs.first().filter { inPeriod(it.date) }.sortedBy { it.date }.map { log ->
                val measure = log.beesPerMinute ?: log.honeyMl ?: log.weightGrams?.let { BigDecimal(it).movePointLeft(3) }
                listOf(day(log.date), text(names[log.hiveId]), text(s(log.kind.label)), number(measure), text(log.notes))
            },
        )
        return ReportSection(s(R.string.report_part_native), emptyList(), listOf(hiveTable, logs))
    }

    private suspend fun hotels(inPeriod: (LocalDate) -> Boolean): ReportSection {
        val all = hotels.hotels.first()
        val names = all.associate { it.id to it.name }
        val zones = views.zones.first().associate { it.zone.id to it.zone.name }
        val hotelTable = table(
            R.string.report_hotels,
            listOf(R.string.col_hotel, R.string.col_zone, R.string.col_holes),
            all.map { listOf(text(it.name), text(it.zoneId?.let(zones::get)), number(it.holes)) },
        )
        val looks = table(
            R.string.report_hotel_looks,
            listOf(R.string.col_day, R.string.col_hotel, R.string.col_sealed, R.string.col_busy, R.string.col_emerged, R.string.col_pests),
            hotels.observations.first().filter { inPeriod(it.date) }.sortedBy { it.date }.map { look ->
                listOf(
                    day(look.date), text(names[look.hotelId]), number(look.sealedHoles), number(look.busy), number(look.emerged),
                    text(look.pests.sortedBy { it.ordinal }.joinToString(", ") { s(it.label) }),
                )
            },
        )
        return ReportSection(s(R.string.report_part_hotels), emptyList(), listOf(hotelTable, looks))
    }

    private suspend fun water(inPeriod: (LocalDate) -> Boolean): ReportSection {
        val state = waterViews.state.first()
        val lines = table(
            R.string.report_lines,
            listOf(R.string.col_line, R.string.col_method, R.string.col_zones, R.string.col_flow, R.string.col_next),
            state.lines.map { view ->
                listOf(
                    text(view.line.name), text(s(view.line.method.label)), text(view.zoneNames.joinToString(", ")),
                    view.line.flowMlPerHour?.let { ReportCell.Number(BigDecimal(it).movePointLeft(3)) } ?: text(null), day(view.plan.dueOn),
                )
            },
        )
        val lineNames = state.lines.associate { it.line.id to it.line.name }
        val zoneNames = views.zones.first().associate { it.zone.id to it.zone.name }
        val waterings = table(
            R.string.report_waterings,
            listOf(R.string.col_day, R.string.col_where),
            water.waterings.first().filter { inPeriod(it.date) }.sortedBy { it.date }.map {
                listOf(day(it.date), text(it.lineId?.let(lineNames::get) ?: it.zoneId?.let(zoneNames::get)))
            },
        )
        return ReportSection(s(R.string.report_part_water), emptyList(), listOf(lines, waterings))
    }

    private fun groupLabel(group: BeeGroup) = when (group) {
        BeeGroup.HONEY -> R.string.bee_group_honey
        BeeGroup.STINGLESS -> R.string.bee_group_stingless
        BeeGroup.SOLITARY -> R.string.bee_group_solitary
    }

    private fun levelLabel(level: ForageLevel) = when (level) {
        ForageLevel.PLENTY -> R.string.forage_plenty
        ForageLevel.SOME -> R.string.forage_some
        ForageLevel.LITTLE -> R.string.forage_little
        ForageLevel.NONE -> R.string.forage_none
    }
}
