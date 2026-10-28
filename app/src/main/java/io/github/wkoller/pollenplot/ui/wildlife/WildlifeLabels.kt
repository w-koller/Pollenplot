package io.github.wkoller.pollenplot.ui.wildlife

import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.Hazard
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup

/** The groups the camera offers, in the order they are shown. */
val CAMERA_GROUPS = listOf(
    LifeGroup.PLANT, LifeGroup.BIRD, LifeGroup.INSECT, LifeGroup.SPIDER, LifeGroup.REPTILE, LifeGroup.FROG,
    LifeGroup.MAMMAL, LifeGroup.FISH, LifeGroup.MOLLUSC, LifeGroup.FUNGUS,
)

@get:StringRes
val LifeGroup.label: Int
    get() = when (this) {
        LifeGroup.PLANT -> R.string.group_plant
        LifeGroup.BIRD -> R.string.group_bird
        LifeGroup.INSECT -> R.string.group_insect
        LifeGroup.SPIDER -> R.string.group_spider
        LifeGroup.REPTILE -> R.string.group_reptile
        LifeGroup.FROG -> R.string.group_frog
        LifeGroup.MAMMAL -> R.string.group_mammal
        LifeGroup.FISH -> R.string.group_fish
        LifeGroup.MOLLUSC -> R.string.group_mollusc
        LifeGroup.FUNGUS -> R.string.group_fungus
        LifeGroup.OTHER -> R.string.group_other
    }

/** The group as a noun in a sentence, such as "bird" in "Please put the bird in the middle of the square". */
@get:StringRes
val LifeGroup.noun: Int
    get() = when (this) {
        LifeGroup.BIRD -> R.string.group_noun_bird
        LifeGroup.INSECT -> R.string.group_noun_insect
        LifeGroup.SPIDER -> R.string.group_noun_spider
        LifeGroup.REPTILE -> R.string.group_noun_reptile
        LifeGroup.FROG -> R.string.group_noun_frog
        LifeGroup.MAMMAL -> R.string.group_noun_mammal
        LifeGroup.FISH -> R.string.group_noun_fish
        LifeGroup.MOLLUSC -> R.string.group_noun_mollusc
        LifeGroup.FUNGUS -> R.string.group_noun_fungus
        LifeGroup.PLANT, LifeGroup.OTHER -> R.string.group_noun_other
    }

@get:StringRes
val Behaviour?.label: Int
    get() = when (this) {
        null -> R.string.behaviour_unknown
        Behaviour.FEEDING -> R.string.behaviour_feeding
        Behaviour.POLLINATING -> R.string.behaviour_pollinating
        Behaviour.NESTING -> R.string.behaviour_nesting
        Behaviour.BASKING -> R.string.behaviour_basking
        Behaviour.CALLING -> R.string.behaviour_calling
        Behaviour.MATING -> R.string.behaviour_mating
        Behaviour.RESTING -> R.string.behaviour_resting
        Behaviour.PASSING -> R.string.behaviour_passing
    }

@get:StringRes
val Hazard.label: Int
    get() = when (this) {
        Hazard.VENOMOUS -> R.string.hazard_venomous
        Hazard.STINGS -> R.string.hazard_stings
        Hazard.BITES -> R.string.hazard_bites
        Hazard.SWOOPS -> R.string.hazard_swoops
        Hazard.POISONOUS -> R.string.hazard_poisonous
    }
