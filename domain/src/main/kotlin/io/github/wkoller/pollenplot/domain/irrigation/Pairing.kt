package io.github.wkoller.pollenplot.domain.irrigation

import io.github.wkoller.pollenplot.domain.garden.ZoneBadges

/** A plant on a line that shares its run time with a much thirstier plant, and about how many times its need it gets. */
data class PairingNote(val plant: WaterPlant, val thirstiest: WaterPlant, val timesNeeded: Int)

/**
 * Smart pairing: every emitter on a line runs for the same time, so a plant that needs a third as much water as the
 * thirstiest plant on it, or less, gets several times what it needs from drippers of the same size. This is the same
 * ratio as the Plot's Mixed thirst badge.
 */
object Pairing {
    fun check(plants: List<WaterPlant>): List<PairingNote> {
        val counted = plants.filter { it.plantFactorBp != null }
        val thirstiest = counted.maxByOrNull { it.plantFactorBp!! } ?: return emptyList()
        return counted.filter { it.plantFactorBp!! * ZoneBadges.MIXED_THIRST_RATIO <= thirstiest.plantFactorBp!! }
            .map { PairingNote(it, thirstiest, (thirstiest.plantFactorBp!! + it.plantFactorBp!! / 2) / it.plantFactorBp!!) }
            .sortedByDescending { it.timesNeeded }
    }
}
