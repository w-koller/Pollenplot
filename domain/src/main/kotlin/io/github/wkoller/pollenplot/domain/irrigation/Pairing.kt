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

    /**
     * The plants that get several times what they need on any of [lines], by id, for the Overwatering risk badge. A
     * line watered by hand is left out, because each plant on it can be given its own amount.
     */
    fun overwatered(lines: List<WateringLine>, plants: List<WaterPlant>): Set<String> =
        lines.filterNot { it.method.byHand }
            .flatMap { line -> check(plants.filter { it.zoneId in line.zoneIds }) }
            .mapTo(mutableSetOf()) { it.plant.id }
}
