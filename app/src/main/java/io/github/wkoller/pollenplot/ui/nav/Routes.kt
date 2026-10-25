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

/** A part of the app that is not built yet, named by [AddAction]. */
@Serializable data class NotReadyRoute(val action: String)

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
enum class AddAction(@StringRes val label: Int, @DrawableRes val icon: Int, @StringRes val notReady: Int) {
    IDENTIFY(R.string.add_identify_camera, R.drawable.ic_photo_camera, R.string.not_ready_identify),
    PHOTOS(R.string.add_photos, R.drawable.ic_add_photo_alternate, R.string.not_ready_photos),
    WEED(R.string.add_check_weed, R.drawable.ic_grass, R.string.not_ready_weed),
    PLANT(R.string.add_plant_by_name, R.drawable.ic_potted_plant, R.string.not_ready_plant),
}
