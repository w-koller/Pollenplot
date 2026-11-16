package io.github.wkoller.pollenplot.ui.plants

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.ZoneView
import io.github.wkoller.pollenplot.ui.components.ChipGroup

/**
 * How many plants are being added or kept: a [total], or with [byZone] a count for each zone. A zone not yet counted
 * has one.
 */
data class PlantCount(val total: Int = 1, val byZone: Boolean = false, val counts: Map<String, Int> = emptyMap()) {
    /** The count for each of [zoneIds] when they are counted zone by zone, or null when a total is given. */
    fun countsFor(zoneIds: List<String>, each: Boolean): Map<String, Int>? =
        if (zoneIds.size > 1 && (each || byZone)) zoneIds.associateWith { counts[it] ?: 1 } else null

    /** How many there are in all across [zoneIds]. */
    fun totalFor(zoneIds: List<String>, each: Boolean): Int = countsFor(zoneIds, each)?.values?.sum() ?: total

    companion object {
        /** Keeps the count as text, so it survives the phone being turned. */
        val Saver = Saver<PlantCount, ArrayList<String>>(
            save = { count -> arrayListOf(count.total.toString(), count.byZone.toString()).apply { count.counts.forEach { (zone, n) -> add("$zone=$n") } } },
            restore = { list ->
                PlantCount(
                    total = list[0].toInt(),
                    byZone = list[1].toBoolean(),
                    counts = list.drop(2).associate { it.substringBeforeLast('=') to it.substringAfterLast('=').toInt() },
                )
            },
        )
    }
}

/**
 * Asks how many plants there are. For one zone it is one number. For several zones the user counts each zone or gives a
 * total, unless the plants are a plant in each zone ([each]), when each zone is counted.
 */
@Composable
fun PlantCounts(zones: List<ZoneView>, zoneIds: List<String>, each: Boolean, count: PlantCount, onChange: (PlantCount) -> Unit) {
    if (zoneIds.size < 2) {
        QuantityStepper(count.total) { onChange(count.copy(total = it)) }
        return
    }
    val names = zones.associate { it.zone.id to it.zone.name }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (each) {
            Text(stringResource(R.string.count_each_zone_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        } else {
            ChipGroup(
                title = stringResource(R.string.count_how),
                options = listOf(true, false),
                selected = count.byZone,
                label = { stringResource(if (it) R.string.count_by_zone else R.string.count_total) },
                onChoose = { onChange(count.copy(byZone = it)) },
            )
        }
        if (each || count.byZone) {
            zoneIds.forEach { zone ->
                QuantityStepper(count.counts[zone] ?: 1, title = names[zone].orEmpty()) { onChange(count.copy(counts = count.counts + (zone to it))) }
            }
        } else {
            QuantityStepper(count.total, title = stringResource(R.string.count_total_title)) { onChange(count.copy(total = it)) }
            Text(stringResource(R.string.count_total_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
