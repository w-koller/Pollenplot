package io.github.wkoller.pollenplot.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

data class Settings(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    /** Use the colours of the phone's wallpaper instead of Pollenplot's leaf and pollen colours. */
    val wallpaperColours: Boolean = false,
)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The user's settings, kept with DataStore. Garden records live in the database, not here. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val wallpaperColours = booleanPreferencesKey("wallpaper_colours")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            theme = this[Keys.theme]?.let { runCatching { ThemeChoice.valueOf(it) }.getOrNull() } ?: defaults.theme,
            wallpaperColours = this[Keys.wallpaperColours] ?: defaults.wallpaperColours,
        )
    }

    private fun MutablePreferences.write(settings: Settings) {
        this[Keys.theme] = settings.theme.name
        this[Keys.wallpaperColours] = settings.wallpaperColours
    }
}
