package io.github.wkoller.pollenplot.ui.bees

import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.hotels.HotelMaterial
import io.github.wkoller.pollenplot.domain.hotels.HotelPest
import io.github.wkoller.pollenplot.domain.hotels.Occupant
import io.github.wkoller.pollenplot.domain.hotels.Seal
import io.github.wkoller.pollenplot.domain.hotels.UpkeepKind

@get:StringRes
val HotelMaterial.label: Int
    get() = when (this) {
        HotelMaterial.DRILLED_TIMBER -> R.string.hotel_material_timber
        HotelMaterial.BAMBOO -> R.string.hotel_material_bamboo
        HotelMaterial.PITHY_STEMS -> R.string.hotel_material_stems
        HotelMaterial.CLAY_BLOCK -> R.string.hotel_material_clay
        HotelMaterial.PAPER_TUBES -> R.string.hotel_material_paper
        HotelMaterial.OTHER -> R.string.hotel_material_other
    }

@get:StringRes
val Seal.label: Int
    get() = when (this) {
        Seal.LEAF -> R.string.seal_leaf
        Seal.RESIN -> R.string.seal_resin
        Seal.MEMBRANE -> R.string.seal_membrane
        Seal.CLAY -> R.string.seal_clay
        Seal.GRASS -> R.string.seal_grass
        Seal.OTHER -> R.string.seal_other
    }

@get:StringRes
val HotelPest.label: Int
    get() = when (this) {
        HotelPest.CUCKOO_BEES -> R.string.hotel_pest_cuckoo
        HotelPest.PARASITIC_WASPS -> R.string.hotel_pest_wasps
        HotelPest.MITES -> R.string.hotel_pest_mites
        HotelPest.MOULD -> R.string.hotel_pest_mould
        HotelPest.ANTS -> R.string.hotel_pest_ants
        HotelPest.SPIDERS -> R.string.hotel_pest_spiders
    }

@get:StringRes
val UpkeepKind.label: Int
    get() = when (this) {
        UpkeepKind.CLEANED -> R.string.upkeep_cleaned
        UpkeepKind.NEW_LINERS -> R.string.upkeep_liners
        UpkeepKind.FROZE_NESTS -> R.string.upkeep_froze
        UpkeepKind.REPLACED_NESTS -> R.string.upkeep_replaced
        UpkeepKind.MOVED -> R.string.upkeep_moved
        UpkeepKind.RAIN_COVER -> R.string.upkeep_rain
    }

@get:StringRes
val Occupant.label: Int
    get() = when (this) {
        Occupant.LEAFCUTTER_BEE -> R.string.occupant_leafcutter
        Occupant.RESIN_BEE -> R.string.occupant_resin
        Occupant.MASKED_BEE -> R.string.occupant_masked
        Occupant.WASP_MIMIC_BEE -> R.string.occupant_wasp_mimic
        Occupant.POTTER_WASP -> R.string.occupant_potter
        Occupant.GRASS_CARRYING_WASP -> R.string.occupant_grass
    }
