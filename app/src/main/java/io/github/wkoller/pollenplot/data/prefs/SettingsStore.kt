package io.github.wkoller.pollenplot.data.prefs

import java.time.DayOfWeek
import io.github.wkoller.pollenplot.domain.calendar.ReminderSchedule
import io.github.wkoller.pollenplot.domain.calendar.ReminderMode
import io.github.wkoller.pollenplot.domain.garden.ManyZones
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.wkoller.pollenplot.domain.bees.stingless.HeatAlerts
import io.github.wkoller.pollenplot.domain.irrigation.WateringWindow
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.season.Hemisphere
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** What the Plot is drawn on: the theme's own background, or a colour like the ground of the yard. */
enum class PlotBackground { THEME, GRASS, SOIL, SAND, PAVING }

/** Which side of the Plot, as it is drawn, faces north, for the north arrow. */
enum class NorthSide { TOP, RIGHT, BOTTOM, LEFT }

/**
 * A layer over the Plot's zones, one at a time as on a map: their sun or shade, how much of each is in flower now, or
 * how much will be in flower in a month ahead.
 */
enum class PlotLayer { SUN, BLOOM_NOW, BLOOM_LATER }

/** How no layer is written, which is not the same as never having chosen one. */
private const val NO_LAYER = "NONE"

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Use the colours of the phone's wallpaper instead of Pollenplot's leaf and pollen colours. */
    val wallpaperColours: Boolean = false,
    /** The garden's state or territory, which decides what counts as a local native and whose weed rules apply. */
    val gardenState: AuState? = null,
    /** Where the garden is, rounded to about 1 km, for the council's weed list and plants recorded nearby. */
    val gardenPlace: GardenPlace? = null,
    /** Look for plants in new photos in the phone's gallery. */
    val scanLibrary: Boolean = false,
    /** The albums to scan, by their MediaStore bucket IDs. Null means the camera's own album. */
    val scanAlbums: Set<String>? = null,
    /** How many days before scanning was turned on the first scan looks back. */
    val scanBackDays: Int = DEFAULT_SCAN_BACK_DAYS,
    /** When scanning was turned on, in epoch milliseconds. Photos from before then, less [scanBackDays], are left alone. */
    val scanSince: Long? = null,
    /** The zone chosen for the last plant added, which is offered for the next one. */
    val lastZoneId: String? = null,
    /** Let Gemma 4, once it is on the phone, give a second opinion on unsure finds while the phone charges. */
    val secondOpinions: Boolean = true,
    /** Mite checks every month, or null until the beekeeper chooses, when they follow what the garden's state asks. */
    val monthlyMiteChecks: Boolean? = null,
    /** Send a notification on the morning a hive check falls due. */
    val hiveReminders: Boolean = true,
    /** How many days apart hive inspections come in spring and summer. */
    val inspectEveryDays: Int = DEFAULT_INSPECT_EVERY_DAYS,
    /** The hive checks still due that a notification has told about, as `HiveReminders.key` gives them. */
    val hiveTasksTold: Set<String> = emptySet(),
    /** Warn before a day forecast to reach [heatAlertTenthsC], for stingless bee hives. */
    val heatAlerts: Boolean = true,
    /** The forecast top temperature that brings a warning, in tenths of a degree Celsius. */
    val heatAlertTenthsC: Int = HeatAlerts.DEFAULT_TENTHS_C,
    /** The hot days already warned about, as ISO dates. */
    val heatToldDays: Set<String> = emptySet(),
    /** Send a notification on a morning when watering is due. */
    val waterReminders: Boolean = true,
    /** The day a notification last told what watering was due, as an ISO date. */
    val waterToldOn: String? = null,
    /** Send a notification on a morning when a pot is due a flush or a look at repotting. */
    val potReminders: Boolean = true,
    /** The pot jobs still due that a notification has told about, as `PotDue.key` gives them. */
    val potTasksTold: Set<String> = emptySet(),
    /** Send a notification on a morning when a zone is due a feed. */
    val feedReminders: Boolean = true,
    /** The feeds still due that a notification has told about, as `FeedDue.key` gives them, and hot days as "heat:" dates. */
    val feedTasksTold: Set<String> = emptySet(),
    /** Send a notification on a morning when a treated problem is due a second look. */
    val problemReminders: Boolean = true,
    /** The follow-ups still due that a notification has told about, as `FollowUpDue.key` gives them. */
    val problemTasksTold: Set<String> = emptySet(),
    /** Send a notification on a morning when a plant falls due a prune. */
    val pruneReminders: Boolean = true,
    /** The prunes still due that a notification has told about, as `PruneDue.key` gives them. */
    val pruneTasksTold: Set<String> = emptySet(),
    /** A notification in the morning when a compost bin is due a turn or a look whether it is ready. */
    val compostReminders: Boolean = true,
    /** The compost jobs a notification has already told about. */
    val compostTasksTold: Set<String> = emptySet(),
    /** When reminders that can wait come: each day, at weekends, on chosen days, or as a weekly or monthly to-do list. */
    val reminderMode: ReminderMode = ReminderMode.EACH_DAY,
    /** The days chosen for [ReminderMode.CHOSEN_DAYS]. */
    val reminderDays: Set<DayOfWeek> = emptySet(),
    /** The first day of the last period a to-do list was sent for, so each is sent once. */
    val listToldFor: String? = null,
    /** The user's own reminder to give a conditioner before a hot day. */
    val conditionerBeforeHeat: Boolean = false,
    /** When a backup file was last saved on this phone, or the newest backup restored here, in epoch milliseconds. */
    val lastBackupAt: Long? = null,
    /** The hours watering is planned for, to suit the local water rules. */
    val wateringWindow: WateringWindow = WateringWindow.DEFAULT,
    /** The first-launch setup was finished or skipped, or the garden already had records when it would have opened. */
    val setupDone: Boolean = false,
    /** Cats or dogs live at home, so plants toxic to pets are flagged first and left out of suggestions. */
    val petsAtHome: Boolean = false,
    /** Whether the plant library fetches photos online: null until the user has been asked. */
    val libraryPhotos: Boolean? = null,
    val plotBackground: PlotBackground = PlotBackground.THEME,
    /** Whether the Plot's grid lines show while the layout is only looked at. They always show while it is edited. */
    val gridInView: Boolean = true,
    /** The layer over the Plot's zones, with its key, or null for none. */
    val plotLayer: PlotLayer? = null,
    /** How many months ahead the "In flower later" layer looks, from one to [BLOOM_LATER_MOST]. */
    val bloomLaterMonths: Int = 1,
    val plotNorth: NorthSide = NorthSide.TOP,
    /** What adding a plant to several zones makes: one plant in them all, one in each, or a question each time. */
    val manyZones: ManyZones = ManyZones.ASK,
) {
    /** When reminders that can wait are told. */
    val reminderSchedule: ReminderSchedule get() = ReminderSchedule(reminderMode, reminderDays)

    /** The garden's hemisphere, from where it is, or the Southern Hemisphere until a place is chosen. */
    val hemisphere: Hemisphere get() = gardenPlace?.let { Hemisphere.ofLatitude(it.latE7) } ?: Hemisphere.SOUTHERN

    companion object {
        const val DEFAULT_SCAN_BACK_DAYS = 30
        const val DEFAULT_INSPECT_EVERY_DAYS = 14

        /** The inspection intervals a beekeeper can choose, from a week to a fortnight. */
        val INSPECT_EVERY_CHOICES = listOf(7, 10, 14)

        /** The furthest ahead the "In flower later" layer looks, in months. */
        const val BLOOM_LATER_MOST = 6
    }
}

/**
 * The user's settings, kept with DataStore. Garden records live in the database, not here. The app makes one store
 * when it starts; a second store for the same file in one process would be refused by DataStore.
 */
class SettingsStore(context: Context) {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE) }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val wallpaperColours = booleanPreferencesKey("wallpaper_colours")
        val gardenState = stringPreferencesKey("garden_state")
        val gardenLatE7 = intPreferencesKey("garden_lat_e7")
        val gardenLonE7 = intPreferencesKey("garden_lon_e7")
        val gardenPlaceName = stringPreferencesKey("garden_place_name")
        val gardenCouncil = stringPreferencesKey("garden_council")
        val scanLibrary = booleanPreferencesKey("scan_library")
        val scanAlbums = stringSetPreferencesKey("scan_albums")
        val scanBackDays = intPreferencesKey("scan_back_days")
        val scanSince = longPreferencesKey("scan_since")
        val lastZoneId = stringPreferencesKey("last_zone_id")
        val secondOpinions = booleanPreferencesKey("second_opinions")
        val monthlyMiteChecks = booleanPreferencesKey("monthly_mite_checks")
        val hiveReminders = booleanPreferencesKey("hive_reminders")
        val inspectEveryDays = intPreferencesKey("inspect_every_days")
        val hiveTasksTold = stringSetPreferencesKey("hive_tasks_told")
        val heatAlerts = booleanPreferencesKey("heat_alerts")
        val heatAlertTenthsC = intPreferencesKey("heat_alert_tenths_c")
        val heatToldDays = stringSetPreferencesKey("heat_told_days")
        val waterReminders = booleanPreferencesKey("water_reminders")
        val waterToldOn = stringPreferencesKey("water_told_on")
        val potReminders = booleanPreferencesKey("pot_reminders")
        val potTasksTold = stringSetPreferencesKey("pot_tasks_told")
        val feedReminders = booleanPreferencesKey("feed_reminders")
        val feedTasksTold = stringSetPreferencesKey("feed_tasks_told")
        val conditionerBeforeHeat = booleanPreferencesKey("conditioner_before_heat")
        val problemReminders = booleanPreferencesKey("problem_reminders")
        val problemTasksTold = stringSetPreferencesKey("problem_tasks_told")
        val pruneReminders = booleanPreferencesKey("prune_reminders")
        val pruneTasksTold = stringSetPreferencesKey("prune_tasks_told")
        val compostReminders = booleanPreferencesKey("compost_reminders")
        val compostTasksTold = stringSetPreferencesKey("compost_tasks_told")
        val reminderMode = stringPreferencesKey("reminder_mode")
        val reminderDays = stringSetPreferencesKey("reminder_days")
        val listToldFor = stringPreferencesKey("list_told_for")
        val lastBackupAt = longPreferencesKey("last_backup_at")
        val wateringFromMinute = intPreferencesKey("watering_from_minute")
        val wateringToMinute = intPreferencesKey("watering_to_minute")
        val setupDone = booleanPreferencesKey("setup_done")
        val petsAtHome = booleanPreferencesKey("pets_at_home")
        val libraryPhotos = booleanPreferencesKey("library_photos")
        val plotBackground = stringPreferencesKey("plot_background")
        val gridInView = booleanPreferencesKey("grid_in_view")
        /** The sun layer's old switch, read for a garden set up before there was more than one layer. */
        val sunOverlay = booleanPreferencesKey("sun_overlay")
        val plotLayer = stringPreferencesKey("plot_layer")
        val bloomLaterMonths = intPreferencesKey("bloom_later_months")
        val plotNorth = stringPreferencesKey("plot_north")
        val manyZones = stringPreferencesKey("many_zones")
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            theme = this[Keys.theme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: defaults.theme,
            wallpaperColours = this[Keys.wallpaperColours] ?: defaults.wallpaperColours,
            gardenState = this[Keys.gardenState]?.let { name -> AuState.entries.firstOrNull { it.name == name } },
            gardenPlace = this[Keys.gardenLatE7]?.let { lat ->
                this[Keys.gardenLonE7]?.let { lon -> GardenPlace(lat, lon, this[Keys.gardenPlaceName], this[Keys.gardenCouncil]) }
            },
            scanLibrary = this[Keys.scanLibrary] ?: defaults.scanLibrary,
            scanAlbums = this[Keys.scanAlbums],
            scanBackDays = this[Keys.scanBackDays] ?: defaults.scanBackDays,
            scanSince = this[Keys.scanSince],
            lastZoneId = this[Keys.lastZoneId],
            secondOpinions = this[Keys.secondOpinions] ?: defaults.secondOpinions,
            monthlyMiteChecks = this[Keys.monthlyMiteChecks],
            hiveReminders = this[Keys.hiveReminders] ?: defaults.hiveReminders,
            inspectEveryDays = this[Keys.inspectEveryDays]?.takeIf { it in Settings.INSPECT_EVERY_CHOICES } ?: defaults.inspectEveryDays,
            hiveTasksTold = this[Keys.hiveTasksTold] ?: defaults.hiveTasksTold,
            heatAlerts = this[Keys.heatAlerts] ?: defaults.heatAlerts,
            heatAlertTenthsC = this[Keys.heatAlertTenthsC]?.takeIf { it in HeatAlerts.CHOICES } ?: defaults.heatAlertTenthsC,
            heatToldDays = this[Keys.heatToldDays] ?: defaults.heatToldDays,
            waterReminders = this[Keys.waterReminders] ?: defaults.waterReminders,
            waterToldOn = this[Keys.waterToldOn],
            potReminders = this[Keys.potReminders] ?: defaults.potReminders,
            potTasksTold = this[Keys.potTasksTold] ?: defaults.potTasksTold,
            feedReminders = this[Keys.feedReminders] ?: defaults.feedReminders,
            feedTasksTold = this[Keys.feedTasksTold] ?: defaults.feedTasksTold,
            conditionerBeforeHeat = this[Keys.conditionerBeforeHeat] ?: defaults.conditionerBeforeHeat,
            problemReminders = this[Keys.problemReminders] ?: defaults.problemReminders,
            problemTasksTold = this[Keys.problemTasksTold] ?: defaults.problemTasksTold,
            pruneReminders = this[Keys.pruneReminders] ?: defaults.pruneReminders,
            pruneTasksTold = this[Keys.pruneTasksTold] ?: defaults.pruneTasksTold,
            compostReminders = this[Keys.compostReminders] ?: defaults.compostReminders,
            compostTasksTold = this[Keys.compostTasksTold] ?: defaults.compostTasksTold,
            reminderMode = this[Keys.reminderMode]?.let { name -> ReminderMode.entries.firstOrNull { it.name == name } } ?: defaults.reminderMode,
            reminderDays = this[Keys.reminderDays]?.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }?.toSet() ?: defaults.reminderDays,
            listToldFor = this[Keys.listToldFor],
            lastBackupAt = this[Keys.lastBackupAt],
            wateringWindow = this[Keys.wateringFromMinute]?.let { from ->
                this[Keys.wateringToMinute]?.let { to -> runCatching { WateringWindow(from, to) }.getOrNull() }
            } ?: defaults.wateringWindow,
            setupDone = this[Keys.setupDone] ?: defaults.setupDone,
            petsAtHome = this[Keys.petsAtHome] ?: defaults.petsAtHome,
            libraryPhotos = this[Keys.libraryPhotos],
            plotBackground = this[Keys.plotBackground]?.let { runCatching { PlotBackground.valueOf(it) }.getOrNull() } ?: defaults.plotBackground,
            gridInView = this[Keys.gridInView] ?: defaults.gridInView,
            plotLayer = this[Keys.plotLayer]?.let { name -> PlotLayer.entries.firstOrNull { it.name == name } }
                ?: PlotLayer.SUN.takeIf { this[Keys.sunOverlay] == true && this[Keys.plotLayer] == null },
            bloomLaterMonths = (this[Keys.bloomLaterMonths] ?: defaults.bloomLaterMonths).coerceIn(1, Settings.BLOOM_LATER_MOST),
            plotNorth = this[Keys.plotNorth]?.let { runCatching { NorthSide.valueOf(it) }.getOrNull() } ?: defaults.plotNorth,
            manyZones = this[Keys.manyZones]?.let { runCatching { ManyZones.valueOf(it) }.getOrNull() } ?: defaults.manyZones,
        )
    }

    private fun MutablePreferences.write(settings: Settings) {
        this[Keys.theme] = settings.theme.name
        this[Keys.wallpaperColours] = settings.wallpaperColours
        setOrRemove(Keys.gardenState, settings.gardenState?.name)
        setOrRemove(Keys.gardenLatE7, settings.gardenPlace?.latE7)
        setOrRemove(Keys.gardenLonE7, settings.gardenPlace?.lonE7)
        setOrRemove(Keys.gardenPlaceName, settings.gardenPlace?.name)
        setOrRemove(Keys.gardenCouncil, settings.gardenPlace?.council)
        this[Keys.scanLibrary] = settings.scanLibrary
        setOrRemove(Keys.scanAlbums, settings.scanAlbums)
        this[Keys.scanBackDays] = settings.scanBackDays
        setOrRemove(Keys.scanSince, settings.scanSince)
        setOrRemove(Keys.lastZoneId, settings.lastZoneId)
        this[Keys.secondOpinions] = settings.secondOpinions
        setOrRemove(Keys.monthlyMiteChecks, settings.monthlyMiteChecks)
        this[Keys.hiveReminders] = settings.hiveReminders
        this[Keys.inspectEveryDays] = settings.inspectEveryDays
        this[Keys.hiveTasksTold] = settings.hiveTasksTold
        this[Keys.heatAlerts] = settings.heatAlerts
        this[Keys.heatAlertTenthsC] = settings.heatAlertTenthsC
        this[Keys.heatToldDays] = settings.heatToldDays
        this[Keys.waterReminders] = settings.waterReminders
        setOrRemove(Keys.waterToldOn, settings.waterToldOn)
        this[Keys.potReminders] = settings.potReminders
        this[Keys.potTasksTold] = settings.potTasksTold
        this[Keys.feedReminders] = settings.feedReminders
        this[Keys.feedTasksTold] = settings.feedTasksTold
        this[Keys.conditionerBeforeHeat] = settings.conditionerBeforeHeat
        this[Keys.problemReminders] = settings.problemReminders
        this[Keys.problemTasksTold] = settings.problemTasksTold
        this[Keys.pruneReminders] = settings.pruneReminders
        this[Keys.pruneTasksTold] = settings.pruneTasksTold
        this[Keys.compostReminders] = settings.compostReminders
        this[Keys.compostTasksTold] = settings.compostTasksTold
        this[Keys.reminderMode] = settings.reminderMode.name
        this[Keys.reminderDays] = settings.reminderDays.map { it.name }.toSet()
        settings.listToldFor?.let { this[Keys.listToldFor] = it } ?: remove(Keys.listToldFor)
        setOrRemove(Keys.lastBackupAt, settings.lastBackupAt)
        this[Keys.wateringFromMinute] = settings.wateringWindow.fromMinute
        this[Keys.wateringToMinute] = settings.wateringWindow.toMinute
        this[Keys.setupDone] = settings.setupDone
        this[Keys.petsAtHome] = settings.petsAtHome
        settings.libraryPhotos?.let { this[Keys.libraryPhotos] = it } ?: remove(Keys.libraryPhotos)
        this[Keys.plotBackground] = settings.plotBackground.name
        this[Keys.gridInView] = settings.gridInView
        // "None" is kept as a word, so a garden that turned the old sun switch off and on does not come back to it.
        this[Keys.plotLayer] = settings.plotLayer?.name ?: NO_LAYER
        remove(Keys.sunOverlay)
        this[Keys.bloomLaterMonths] = settings.bloomLaterMonths
        this[Keys.plotNorth] = settings.plotNorth.name
        this[Keys.manyZones] = settings.manyZones.name
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value else remove(key)
    }

    private companion object {
        const val FILE = "settings"
    }
}
