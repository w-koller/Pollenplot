package io.github.wkoller.pollenplot.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import kotlinx.serialization.Serializable

@Serializable data object PlotRoute

@Serializable data object RhythmsRoute

@Serializable data object WildlifeRoute

@Serializable data object BeesRoute

@Serializable data object WaterRoute

@Serializable data object SettingsRoute

@Serializable data object AboutRoute

@Serializable data object LicencesRoute

/** One licence file from assets/licences. */
@Serializable data class LicenceTextRoute(val file: String)

@Serializable data class ZoneRoute(val id: String)

@Serializable data class ZoneEditRoute(val id: String)

@Serializable data class PlantRoute(val id: String)

/** Adds a plant, optionally into a zone or starting from a species chosen elsewhere, such as in search. */
@Serializable data class AddPlantRoute(val zoneId: String? = null, val species: String? = null)

@Serializable data object SearchRoute

/** Identify with the camera, or check whether a plant is a weed. */
@Serializable data class IdentifyRoute(val checkingWeed: Boolean = false)

/** Plants found in photos, waiting for review. */
@Serializable data object ReviewRoute

/** Settings › Photo scanning. */
@Serializable data object ScanSettingsRoute

/** Settings › Models. */
@Serializable data object ModelsRoute

/** Settings › Garden location. */
@Serializable data object GardenLocationRoute

/** The weeds in the garden and near it. */
@Serializable data object WeedsRoute

/** About › Data sources. */
@Serializable data object DataSourcesRoute

/**
 * A plant compared with photos recorded in Australia: a suggestion for a photo ([detectionId]), a plant in the garden
 * ([plantId]), or a council entry ([botanical]) from the Weed finder.
 */
@Serializable data class CompareRoute(val species: String, val botanical: String? = null, val detectionId: String? = null, val plantId: String? = null)

/** Finds a weed on the council's list by what it looks like. From a photo, its suggestions come first. */
@Serializable data class WeedFinderRoute(val detectionId: String? = null)

/** The main tabs, in the order they appear in the navigation bar. */
enum class TopLevelTab(
    val route: Any,
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    @DrawableRes val selectedIcon: Int,
) {
    PLOT(PlotRoute, R.string.tab_plot, R.drawable.ic_yard, R.drawable.ic_yard_filled),
    RHYTHMS(RhythmsRoute, R.string.tab_rhythms, R.drawable.ic_local_florist, R.drawable.ic_local_florist_filled),
    WILDLIFE(WildlifeRoute, R.string.tab_wildlife, R.drawable.ic_raven, R.drawable.ic_raven_filled),
    BEES(BeesRoute, R.string.tab_bees, R.drawable.ic_emoji_nature, R.drawable.ic_emoji_nature_filled),
    WATER(WaterRoute, R.string.tab_water, R.drawable.ic_water_drop, R.drawable.ic_water_drop_filled),
}

/** The choices in the Plot's add menu. */
enum class AddAction(@StringRes val label: Int, @DrawableRes val icon: Int) {
    IDENTIFY(R.string.add_identify_camera, R.drawable.ic_photo_camera),
    PHOTOS(R.string.add_photos, R.drawable.ic_add_photo_alternate),
    WEED(R.string.add_check_weed, R.drawable.ic_grass),
    PLANT(R.string.add_plant_by_name, R.drawable.ic_potted_plant),
}
