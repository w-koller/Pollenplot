package io.github.wkoller.pollenplot.data.prefs

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
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.plants.AuState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

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
) {
    companion object {
        const val DEFAULT_SCAN_BACK_DAYS = 30
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
    }

    private fun <T> MutablePreferences.setOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value != null) this[key] = value else remove(key)
    }

    private companion object {
        const val FILE = "settings"
    }
}
