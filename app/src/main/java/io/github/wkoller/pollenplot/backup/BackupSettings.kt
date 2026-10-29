package io.github.wkoller.pollenplot.backup

import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.ThemeChoice
import io.github.wkoller.pollenplot.domain.bees.stingless.HeatAlerts
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.place.Places
import io.github.wkoller.pollenplot.domain.plants.AuState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The settings that belong to the garden and its gardener, as a backup keeps them. Settings that belong to the phone
 * stay as they are when a backup is restored: which albums to scan and whether to (they depend on the phone's own
 * gallery and permissions), the notifications already shown, and the models downloaded.
 */
@Serializable
data class BackupSettings(
    val theme: String? = null,
    val wallpaperColours: Boolean? = null,
    val gardenState: String? = null,
    val gardenLatE7: Int? = null,
    val gardenLonE7: Int? = null,
    val gardenPlaceName: String? = null,
    val gardenCouncil: String? = null,
    val lastZoneId: String? = null,
    val secondOpinions: Boolean? = null,
    val monthlyMiteChecks: Boolean? = null,
    val hiveReminders: Boolean? = null,
    val inspectEveryDays: Int? = null,
    val heatAlerts: Boolean? = null,
    val heatAlertTenthsC: Int? = null,
    val waterReminders: Boolean? = null,
) {
    fun json(): String = format.encodeToString(serializer(), this)

    /**
     * [current] with these settings in place of its own. The garden's place and state are the backup's, even when it
     * has none. A place outside the world's coordinates is left out, and a place is rounded to about 1 km again, so a
     * changed file cannot make the app send a more exact one.
     */
    fun applyTo(current: Settings): Settings {
        val place = if (gardenLatE7 != null && gardenLonE7 != null && gardenLatE7 in -LAT_E7..LAT_E7 && gardenLonE7 in -LON_E7..LON_E7) {
            GardenPlace.at(gardenLatE7 / 1e7, gardenLonE7 / 1e7, gardenPlaceName, gardenCouncil)
        } else {
            null
        }
        return current.copy(
            theme = ThemeChoice.entries.firstOrNull { it.name == theme } ?: current.theme,
            wallpaperColours = wallpaperColours ?: current.wallpaperColours,
            gardenState = AuState.entries.firstOrNull { it.name == gardenState },
            gardenPlace = place,
            lastZoneId = lastZoneId,
            secondOpinions = secondOpinions ?: current.secondOpinions,
            monthlyMiteChecks = monthlyMiteChecks,
            hiveReminders = hiveReminders ?: current.hiveReminders,
            inspectEveryDays = inspectEveryDays?.takeIf { it in Settings.INSPECT_EVERY_CHOICES } ?: current.inspectEveryDays,
            heatAlerts = heatAlerts ?: current.heatAlerts,
            heatAlertTenthsC = heatAlertTenthsC?.takeIf { it in HeatAlerts.CHOICES } ?: current.heatAlertTenthsC,
            waterReminders = waterReminders ?: current.waterReminders,
        )
    }

    companion object {
        private const val LAT_E7 = 900_000_000
        private const val LON_E7 = 1_800_000_000
        private val format = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        fun of(settings: Settings) = BackupSettings(
            theme = settings.theme.name,
            wallpaperColours = settings.wallpaperColours,
            gardenState = settings.gardenState?.name,
            gardenLatE7 = settings.gardenPlace?.latE7,
            gardenLonE7 = settings.gardenPlace?.lonE7,
            gardenPlaceName = settings.gardenPlace?.name,
            gardenCouncil = settings.gardenPlace?.council,
            lastZoneId = settings.lastZoneId,
            secondOpinions = settings.secondOpinions,
            monthlyMiteChecks = settings.monthlyMiteChecks,
            hiveReminders = settings.hiveReminders,
            inspectEveryDays = settings.inspectEveryDays,
            heatAlerts = settings.heatAlerts,
            heatAlertTenthsC = settings.heatAlertTenthsC,
            waterReminders = settings.waterReminders,
        )

        /** The settings in [text], or null when they cannot be read, in which case the phone's settings stay. */
        fun read(text: String): BackupSettings? = runCatching { format.decodeFromString(serializer(), text) }.getOrNull()
    }
}
