package io.github.wkoller.pollenplot.ui.wildlife

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.domain.wildlife.Behaviour
import io.github.wkoller.pollenplot.domain.wildlife.FaunaRole
import io.github.wkoller.pollenplot.domain.wildlife.Hazard
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ui.plants.BadgeTone

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

/** The group's name for a heading over many of them, such as "Birds". */
@get:StringRes
val LifeGroup.plural: Int
    get() = when (this) {
        LifeGroup.PLANT -> R.string.group_plural_plant
        LifeGroup.BIRD -> R.string.group_plural_bird
        LifeGroup.INSECT -> R.string.group_plural_insect
        LifeGroup.SPIDER -> R.string.group_plural_spider
        LifeGroup.REPTILE -> R.string.group_plural_reptile
        LifeGroup.FROG -> R.string.group_plural_frog
        LifeGroup.MAMMAL -> R.string.group_plural_mammal
        LifeGroup.FISH -> R.string.group_plural_fish
        LifeGroup.MOLLUSC -> R.string.group_plural_mollusc
        LifeGroup.FUNGUS -> R.string.group_plural_fungus
        LifeGroup.OTHER -> R.string.group_plural_other
    }

@get:DrawableRes
val LifeGroup.icon: Int
    get() = when (this) {
        LifeGroup.PLANT -> R.drawable.ic_local_florist
        LifeGroup.BIRD -> R.drawable.ic_raven
        LifeGroup.INSECT -> R.drawable.ic_bug_report
        LifeGroup.SPIDER -> R.drawable.ic_spider
        LifeGroup.REPTILE -> R.drawable.ic_lizard
        LifeGroup.FROG -> R.drawable.ic_frog
        LifeGroup.MAMMAL -> R.drawable.ic_pets
        LifeGroup.FISH -> R.drawable.ic_fish
        LifeGroup.MOLLUSC -> R.drawable.ic_snail
        LifeGroup.FUNGUS -> R.drawable.ic_mushroom
        LifeGroup.OTHER -> R.drawable.ic_eco
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

@get:StringRes
val FaunaRole.label: Int
    get() = when (this) {
        FaunaRole.POLLINATOR -> R.string.fauna_role_pollinator
        FaunaRole.PEST_CONTROLLER -> R.string.fauna_role_pest_controller
        FaunaRole.PLANT_PEST -> R.string.fauna_role_plant_pest
        FaunaRole.SEED_DISPERSER -> R.string.fauna_role_seed_disperser
        FaunaRole.DECOMPOSER -> R.string.fauna_role_decomposer
    }

@get:DrawableRes
val FaunaRole.icon: Int
    get() = when (this) {
        FaunaRole.POLLINATOR -> R.drawable.ic_emoji_nature
        FaunaRole.PEST_CONTROLLER -> R.drawable.ic_bug_report
        FaunaRole.PLANT_PEST -> R.drawable.ic_warning
        FaunaRole.SEED_DISPERSER -> R.drawable.ic_park
        FaunaRole.DECOMPOSER -> R.drawable.ic_compost
    }

val FaunaRole.tone: BadgeTone
    get() = if (this == FaunaRole.PLANT_PEST) BadgeTone.WARNING else BadgeTone.WILDLIFE
