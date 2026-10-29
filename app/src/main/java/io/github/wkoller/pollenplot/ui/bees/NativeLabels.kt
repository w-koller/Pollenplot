package io.github.wkoller.pollenplot.ui.bees

import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.bees.stingless.Facing
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeBox
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeOrigin
import io.github.wkoller.pollenplot.domain.bees.stingless.NativePest
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeStatus
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeSwarm
import io.github.wkoller.pollenplot.domain.bees.stingless.StinglessSpecies

@get:StringRes
val StinglessSpecies.label: Int
    get() = when (this) {
        StinglessSpecies.TETRAGONULA_CARBONARIA -> R.string.native_species_carbonaria
        StinglessSpecies.TETRAGONULA_HOCKINGSI -> R.string.native_species_hockingsi
        StinglessSpecies.AUSTROPLEBEIA_AUSTRALIS -> R.string.native_species_austroplebeia
        StinglessSpecies.OTHER -> R.string.native_species_other
        StinglessSpecies.UNKNOWN -> R.string.native_species_unknown
    }

@get:StringRes
val NativeBox.label: Int
    get() = when (this) {
        NativeBox.OATH -> R.string.native_box_oath
        NativeBox.OATH_HONEY_SUPER -> R.string.native_box_oath_super
        NativeBox.ONE_PART -> R.string.native_box_one_part
        NativeBox.LOG -> R.string.native_box_log
        NativeBox.OTHER -> R.string.native_box_other
    }

@get:StringRes
val NativeOrigin.label: Int
    get() = when (this) {
        NativeOrigin.BOUGHT -> R.string.native_origin_bought
        NativeOrigin.SPLIT -> R.string.native_origin_split
        NativeOrigin.BUDDED -> R.string.native_origin_budded
        NativeOrigin.RESCUED -> R.string.native_origin_rescued
        NativeOrigin.OTHER -> R.string.native_origin_other
    }

@get:StringRes
val NativeStatus.label: Int
    get() = when (this) {
        NativeStatus.ACTIVE -> R.string.native_status_active
        NativeStatus.DIED -> R.string.native_status_died
        NativeStatus.GONE -> R.string.native_status_gone
    }

@get:StringRes
val Facing.label: Int
    get() = when (this) {
        Facing.N -> R.string.facing_n
        Facing.NE -> R.string.facing_ne
        Facing.E -> R.string.facing_e
        Facing.SE -> R.string.facing_se
        Facing.S -> R.string.facing_s
        Facing.SW -> R.string.facing_sw
        Facing.W -> R.string.facing_w
        Facing.NW -> R.string.facing_nw
    }

@get:StringRes
val NativeLogKind.label: Int
    get() = when (this) {
        NativeLogKind.ACTIVITY -> R.string.native_log_activity
        NativeLogKind.WEIGHT -> R.string.native_log_weight
        NativeLogKind.SPLIT -> R.string.native_log_split
        NativeLogKind.BUDDING -> R.string.native_log_budding
        NativeLogKind.HONEY -> R.string.native_log_honey
        NativeLogKind.PEST -> R.string.native_log_pest
        NativeLogKind.SWARM -> R.string.native_log_swarm
        NativeLogKind.MOVE -> R.string.native_log_move
        NativeLogKind.HEAT_PROTECTION -> R.string.native_log_heat
        NativeLogKind.COLD_PROTECTION -> R.string.native_log_cold
    }

@get:StringRes
val NativePest.label: Int
    get() = when (this) {
        NativePest.SYRPHID_FLY -> R.string.native_pest_syrphid
        NativePest.PHORID_FLY -> R.string.native_pest_phorid
        NativePest.SMALL_HIVE_BEETLE -> R.string.native_pest_beetle
        NativePest.ANTS -> R.string.native_pest_ants
        NativePest.BEMBIX_WASP -> R.string.native_pest_bembix
        NativePest.OTHER -> R.string.native_pest_other
    }

@get:StringRes
val NativeSwarm.label: Int
    get() = when (this) {
        NativeSwarm.FIGHTING -> R.string.native_swarm_fighting
        NativeSwarm.DEFENDING -> R.string.native_swarm_defending
        NativeSwarm.MATING -> R.string.native_swarm_mating
    }
