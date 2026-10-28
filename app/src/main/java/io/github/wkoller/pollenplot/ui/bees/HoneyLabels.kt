package io.github.wkoller.pollenplot.ui.bees

import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.bees.honey.BroodPattern
import io.github.wkoller.pollenplot.domain.bees.honey.ColonyEventKind
import io.github.wkoller.pollenplot.domain.bees.honey.FeedKind
import io.github.wkoller.pollenplot.domain.bees.honey.HiveSign
import io.github.wkoller.pollenplot.domain.bees.honey.HiveStatus
import io.github.wkoller.pollenplot.domain.bees.honey.HiveTaskKind
import io.github.wkoller.pollenplot.domain.bees.honey.HiveType
import io.github.wkoller.pollenplot.domain.bees.honey.QueenCells
import io.github.wkoller.pollenplot.domain.bees.honey.QueenColour
import io.github.wkoller.pollenplot.domain.bees.honey.Temperament
import io.github.wkoller.pollenplot.domain.bees.honey.VarroaMethod

@get:StringRes
val HiveType.label: Int
    get() = when (this) {
        HiveType.LANGSTROTH_8 -> R.string.hive_type_langstroth_8
        HiveType.LANGSTROTH_10 -> R.string.hive_type_langstroth_10
        HiveType.FLOW -> R.string.hive_type_flow
        HiveType.WARRE -> R.string.hive_type_warre
        HiveType.TOP_BAR -> R.string.hive_type_top_bar
        HiveType.NUCLEUS -> R.string.hive_type_nucleus
        HiveType.OTHER -> R.string.hive_type_other
    }

@get:StringRes
val HiveStatus.label: Int
    get() = when (this) {
        HiveStatus.ACTIVE -> R.string.hive_status_active
        HiveStatus.QUEENLESS -> R.string.hive_status_queenless
        HiveStatus.DEAD -> R.string.hive_status_dead
        HiveStatus.GONE -> R.string.hive_status_gone
    }

@get:StringRes
val QueenColour.label: Int
    get() = when (this) {
        QueenColour.WHITE -> R.string.queen_white
        QueenColour.YELLOW -> R.string.queen_yellow
        QueenColour.RED -> R.string.queen_red
        QueenColour.GREEN -> R.string.queen_green
        QueenColour.BLUE -> R.string.queen_blue
    }

@get:StringRes
val HiveTaskKind.label: Int
    get() = when (this) {
        HiveTaskKind.INSPECT -> R.string.honey_task_inspect
        HiveTaskKind.BROOD_CHECK -> R.string.honey_task_brood
        HiveTaskKind.MITE_CHECK -> R.string.honey_task_mites
    }

@get:StringRes
val BroodPattern?.label: Int
    get() = when (this) {
        null -> R.string.brood_not_looked
        BroodPattern.SOLID -> R.string.brood_solid
        BroodPattern.PATCHY -> R.string.brood_patchy
        BroodPattern.SPOTTY -> R.string.brood_spotty
    }

@get:StringRes
val Temperament?.label: Int
    get() = when (this) {
        null -> R.string.temperament_unknown
        Temperament.CALM -> R.string.temperament_calm
        Temperament.NERVOUS -> R.string.temperament_nervous
        Temperament.DEFENSIVE -> R.string.temperament_defensive
    }

@get:StringRes
val QueenCells.label: Int
    get() = when (this) {
        QueenCells.NONE -> R.string.queen_cells_none
        QueenCells.SWARM -> R.string.queen_cells_swarm
        QueenCells.SUPERSEDURE -> R.string.queen_cells_supersedure
        QueenCells.EMERGENCY -> R.string.queen_cells_emergency
    }

@get:StringRes
val HiveSign.label: Int
    get() = when (this) {
        HiveSign.CHALKBROOD -> R.string.sign_chalkbrood
        HiveSign.SACBROOD -> R.string.sign_sacbrood
        HiveSign.SUNKEN_CAPPINGS -> R.string.sign_sunken_cappings
        HiveSign.ROPY_BROOD -> R.string.sign_ropy_brood
        HiveSign.TWISTED_LARVAE -> R.string.sign_twisted_larvae
        HiveSign.DEFORMED_WINGS -> R.string.sign_deformed_wings
        HiveSign.SMALL_HIVE_BEETLE -> R.string.sign_small_hive_beetle
        HiveSign.WAX_MOTH -> R.string.sign_wax_moth
        HiveSign.ANTS -> R.string.sign_ants
    }

@get:StringRes
val VarroaMethod.label: Int
    get() = when (this) {
        VarroaMethod.ALCOHOL_WASH -> R.string.varroa_alcohol_wash
        VarroaMethod.SOAPY_WATER_WASH -> R.string.varroa_soapy_water_wash
        VarroaMethod.SUGAR_SHAKE -> R.string.varroa_sugar_shake
        VarroaMethod.STICKY_MAT -> R.string.varroa_sticky_mat
        VarroaMethod.DRONE_UNCAPPING -> R.string.varroa_drone_uncapping
    }

@get:StringRes
val FeedKind.label: Int
    get() = when (this) {
        FeedKind.SYRUP_1_TO_1 -> R.string.feed_syrup_1_to_1
        FeedKind.SYRUP_2_TO_1 -> R.string.feed_syrup_2_to_1
        FeedKind.POLLEN_SUBSTITUTE -> R.string.feed_pollen_substitute
        FeedKind.FONDANT -> R.string.feed_fondant
    }

@get:StringRes
val ColonyEventKind.label: Int
    get() = when (this) {
        ColonyEventKind.SWARMED -> R.string.event_swarmed
        ColonyEventKind.SPLIT -> R.string.event_split
        ColonyEventKind.COMBINED -> R.string.event_combined
        ColonyEventKind.REQUEENED -> R.string.event_requeened
        ColonyEventKind.MOVED -> R.string.event_moved
        ColonyEventKind.DIED -> R.string.event_died
    }
