package io.github.wkoller.pollenplot.ui.plants

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.garden.PlantStatus
import io.github.wkoller.pollenplot.domain.garden.ZoneBadge
import io.github.wkoller.pollenplot.domain.garden.ZoneKind
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.plants.CompostLikes
import io.github.wkoller.pollenplot.domain.plants.CompostSafe
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.Evidence
import io.github.wkoller.pollenplot.domain.plants.Fertiliser
import io.github.wkoller.pollenplot.domain.plants.GrowthForm
import io.github.wkoller.pollenplot.domain.plants.Soil
import io.github.wkoller.pollenplot.domain.plants.Sun
import io.github.wkoller.pollenplot.domain.plants.Toxicity
import io.github.wkoller.pollenplot.domain.plants.WaterNeed

/** How a badge is coloured. Status is never shown by colour alone: every badge also has an icon and a word. */
enum class BadgeTone { WARNING, WILDLIFE, PLANT, WATER }

@get:StringRes
val EcoRole.label: Int
    get() = when (this) {
        EcoRole.WEED_RISK -> R.string.role_weed_risk
        EcoRole.TOXIC_TO_PETS -> R.string.role_toxic_to_pets
        EcoRole.OVERWATERING_RISK -> R.string.role_overwatering_risk
        EcoRole.POLLINATOR_MAGNET -> R.string.role_pollinator_magnet
        EcoRole.NATIVE_BEE_FAVOURITE -> R.string.role_native_bee_favourite
        EcoRole.BIRD_FEEDER -> R.string.role_bird_feeder
        EcoRole.BUTTERFLY_HOST -> R.string.role_butterfly_host
        EcoRole.FROG_SAFE -> R.string.role_frog_safe
        EcoRole.BENEFICIAL_HAVEN -> R.string.role_beneficial_haven
        EcoRole.LOCAL_NATIVE -> R.string.role_local_native
        EcoRole.EDIBLE -> R.string.role_edible
        EcoRole.HIGH_THIRST -> R.string.role_high_thirst
        EcoRole.DROUGHT_HARDY -> R.string.role_drought_hardy
        EcoRole.GROUND_COVER -> R.string.role_ground_cover
    }

@get:DrawableRes
val EcoRole.icon: Int
    get() = when (this) {
        EcoRole.WEED_RISK -> R.drawable.ic_warning
        EcoRole.TOXIC_TO_PETS -> R.drawable.ic_pets
        EcoRole.OVERWATERING_RISK -> R.drawable.ic_humidity_high
        EcoRole.POLLINATOR_MAGNET -> R.drawable.ic_emoji_nature
        EcoRole.NATIVE_BEE_FAVOURITE -> R.drawable.ic_native_bee
        EcoRole.BIRD_FEEDER -> R.drawable.ic_raven
        EcoRole.BUTTERFLY_HOST -> R.drawable.ic_butterfly
        EcoRole.FROG_SAFE -> R.drawable.ic_frog
        EcoRole.BENEFICIAL_HAVEN -> R.drawable.ic_bug_report
        EcoRole.LOCAL_NATIVE -> R.drawable.ic_eco
        EcoRole.EDIBLE -> R.drawable.ic_restaurant
        EcoRole.HIGH_THIRST -> R.drawable.ic_water_drop
        EcoRole.DROUGHT_HARDY -> R.drawable.ic_humidity_low
        EcoRole.GROUND_COVER -> R.drawable.ic_grass
    }

val EcoRole.tone: BadgeTone
    get() = when (this) {
        EcoRole.WEED_RISK, EcoRole.TOXIC_TO_PETS, EcoRole.OVERWATERING_RISK -> BadgeTone.WARNING
        EcoRole.POLLINATOR_MAGNET, EcoRole.NATIVE_BEE_FAVOURITE, EcoRole.BIRD_FEEDER, EcoRole.BUTTERFLY_HOST,
        EcoRole.FROG_SAFE, EcoRole.BENEFICIAL_HAVEN -> BadgeTone.WILDLIFE
        EcoRole.HIGH_THIRST -> BadgeTone.WATER
        EcoRole.LOCAL_NATIVE, EcoRole.EDIBLE, EcoRole.DROUGHT_HARDY, EcoRole.GROUND_COVER -> BadgeTone.PLANT
    }

@get:StringRes
val ZoneBadge.label: Int
    get() = when (this) {
        ZoneBadge.WEED_FOUND -> R.string.badge_weed_found
        ZoneBadge.MIXED_THIRST -> R.string.badge_mixed_thirst
        ZoneBadge.POLLINATOR_HOTSPOT -> R.string.badge_pollinator_hotspot
    }

@get:DrawableRes
val ZoneBadge.icon: Int
    get() = when (this) {
        ZoneBadge.WEED_FOUND -> R.drawable.ic_warning
        ZoneBadge.MIXED_THIRST -> R.drawable.ic_humidity_high
        ZoneBadge.POLLINATOR_HOTSPOT -> R.drawable.ic_emoji_nature
    }

val ZoneBadge.tone: BadgeTone
    get() = when (this) {
        ZoneBadge.WEED_FOUND, ZoneBadge.MIXED_THIRST -> BadgeTone.WARNING
        ZoneBadge.POLLINATOR_HOTSPOT -> BadgeTone.WILDLIFE
    }

@get:DrawableRes
val GrowthForm?.icon: Int
    get() = when (this) {
        GrowthForm.TREE, GrowthForm.PALM -> R.drawable.ic_park
        GrowthForm.GROUNDCOVER, GrowthForm.GRASS, GrowthForm.STRAPPY, GrowthForm.LAWN -> R.drawable.ic_grass
        GrowthForm.PERENNIAL, GrowthForm.ANNUAL, GrowthForm.BULB -> R.drawable.ic_local_florist
        GrowthForm.VEGETABLE -> R.drawable.ic_agriculture
        GrowthForm.AQUATIC -> R.drawable.ic_water
        GrowthForm.CLIMBER, GrowthForm.HERB, GrowthForm.FERN -> R.drawable.ic_eco
        GrowthForm.SHRUB, GrowthForm.SUCCULENT, null -> R.drawable.ic_potted_plant
    }

@get:StringRes
val GrowthForm.label: Int
    get() = when (this) {
        GrowthForm.TREE -> R.string.form_tree
        GrowthForm.SHRUB -> R.string.form_shrub
        GrowthForm.GROUNDCOVER -> R.string.form_groundcover
        GrowthForm.GRASS -> R.string.form_grass
        GrowthForm.STRAPPY -> R.string.form_strappy
        GrowthForm.CLIMBER -> R.string.form_climber
        GrowthForm.PERENNIAL -> R.string.form_perennial
        GrowthForm.ANNUAL -> R.string.form_annual
        GrowthForm.SUCCULENT -> R.string.form_succulent
        GrowthForm.FERN -> R.string.form_fern
        GrowthForm.AQUATIC -> R.string.form_aquatic
        GrowthForm.VEGETABLE -> R.string.form_vegetable
        GrowthForm.HERB -> R.string.form_herb
        GrowthForm.PALM -> R.string.form_palm
        GrowthForm.BULB -> R.string.form_bulb
        GrowthForm.LAWN -> R.string.form_lawn
    }

@get:StringRes
val ZoneKind.label: Int
    get() = when (this) {
        ZoneKind.GARDEN_BED -> R.string.kind_garden_bed
        ZoneKind.VEG_PATCH -> R.string.kind_veg_patch
        ZoneKind.ORCHARD -> R.string.kind_orchard
        ZoneKind.LAWN -> R.string.kind_lawn
        ZoneKind.POTS -> R.string.kind_pots
        ZoneKind.POND -> R.string.kind_pond
        ZoneKind.HIVE_CORNER -> R.string.kind_hive_corner
        ZoneKind.OTHER -> R.string.kind_other
    }

@get:DrawableRes
val ZoneKind.icon: Int
    get() = when (this) {
        ZoneKind.GARDEN_BED -> R.drawable.ic_local_florist
        ZoneKind.VEG_PATCH -> R.drawable.ic_agriculture
        ZoneKind.ORCHARD -> R.drawable.ic_park
        ZoneKind.LAWN -> R.drawable.ic_grass
        ZoneKind.POTS -> R.drawable.ic_potted_plant
        ZoneKind.POND -> R.drawable.ic_water
        ZoneKind.HIVE_CORNER -> R.drawable.ic_hive
        ZoneKind.OTHER -> R.drawable.ic_category
    }

@get:StringRes
val Sun.label: Int
    get() = when (this) {
        Sun.FULL_SUN -> R.string.sun_full_sun
        Sun.PART_SHADE -> R.string.sun_part_shade
        Sun.FULL_SHADE -> R.string.sun_full_shade
    }

@get:DrawableRes
val Sun.icon: Int
    get() = when (this) {
        Sun.FULL_SUN -> R.drawable.ic_sunny
        Sun.PART_SHADE -> R.drawable.ic_partly_cloudy_day
        Sun.FULL_SHADE -> R.drawable.ic_cloud
    }

@get:StringRes
val Soil.label: Int
    get() = when (this) {
        Soil.SAND -> R.string.soil_sand
        Soil.LOAM -> R.string.soil_loam
        Soil.CLAY -> R.string.soil_clay
        Soil.WET -> R.string.soil_wet
        Soil.POTTING_MIX -> R.string.soil_potting_mix
    }

@get:StringRes
val WaterNeed.label: Int
    get() = when (this) {
        WaterNeed.VERY_LOW -> R.string.water_very_low
        WaterNeed.LOW -> R.string.water_low
        WaterNeed.MODERATE -> R.string.water_moderate
        WaterNeed.HIGH -> R.string.water_high
    }

@get:StringRes
val Fertiliser.label: Int
    get() = when (this) {
        Fertiliser.NONE -> R.string.fert_none
        Fertiliser.LOW_PHOSPHORUS -> R.string.fert_low_phosphorus
        Fertiliser.GENERAL -> R.string.fert_general
        Fertiliser.CITRUS -> R.string.fert_citrus
        Fertiliser.HEAVY_FEEDER -> R.string.fert_heavy_feeder
    }

@get:StringRes
val CompostLikes.label: Int
    get() = when (this) {
        CompostLikes.YES -> R.string.compost_likes_yes
        CompostLikes.SOME -> R.string.compost_likes_some
        CompostLikes.NO -> R.string.compost_likes_no
    }

@get:StringRes
val CompostSafe.label: Int
    get() = when (this) {
        CompostSafe.YES -> R.string.compost_safe_yes
        CompostSafe.CAUTION -> R.string.compost_safe_caution
        CompostSafe.NO -> R.string.compost_safe_no
    }

@get:StringRes
val Toxicity.label: Int
    get() = when (this) {
        Toxicity.NONE -> R.string.toxic_none
        Toxicity.MILD -> R.string.toxic_mild
        Toxicity.SEVERE -> R.string.toxic_severe
    }

@get:StringRes
val Evidence.label: Int
    get() = when (this) {
        Evidence.TRADITIONAL -> R.string.evidence_traditional
        Evidence.SOME_EVIDENCE -> R.string.evidence_some_evidence
    }

@get:StringRes
val PlantStatus.label: Int
    get() = when (this) {
        PlantStatus.GROWING -> R.string.status_growing
        PlantStatus.REMOVED -> R.string.status_removed
        PlantStatus.DIED -> R.string.status_died
    }

@get:StringRes
val AuState?.label: Int
    get() = when (this) {
        null -> R.string.state_not_set
        AuState.QLD -> R.string.state_qld
        AuState.NSW -> R.string.state_nsw
        AuState.VIC -> R.string.state_vic
        AuState.TAS -> R.string.state_tas
        AuState.SA -> R.string.state_sa
        AuState.WA -> R.string.state_wa
        AuState.NT -> R.string.state_nt
        AuState.ACT -> R.string.state_act
    }

@StringRes
fun scoreLabel(score: Int): Int = when (score.coerceIn(0, 3)) {
    0 -> R.string.score_0
    1 -> R.string.score_1
    2 -> R.string.score_2
    else -> R.string.score_3
}

@StringRes
fun toleranceLabel(score: Int): Int = when (score.coerceIn(0, 3)) {
    0 -> R.string.tolerance_0
    1 -> R.string.tolerance_1
    2 -> R.string.tolerance_2
    else -> R.string.tolerance_3
}
