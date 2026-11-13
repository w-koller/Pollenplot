package io.github.wkoller.pollenplot.ui.feeding

import io.github.wkoller.pollenplot.ui.components.readable
import io.github.wkoller.pollenplot.ui.components.ChipGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.FeedRepository
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.LastFed
import io.github.wkoller.pollenplot.domain.feeding.Npk
import io.github.wkoller.pollenplot.domain.feeding.PhosphorusGuard
import io.github.wkoller.pollenplot.domain.feeding.Product
import io.github.wkoller.pollenplot.domain.feeding.ProductKind
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.bees.DateRow
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PastDateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate

@get:StringRes
val ProductKind.label: Int
    get() = when (this) {
        ProductKind.CONDITIONER -> R.string.feed_kind_conditioner
        ProductKind.LIQUID -> R.string.feed_kind_liquid
        ProductKind.SLOW_RELEASE -> R.string.feed_kind_slow_release
        ProductKind.ORGANIC -> R.string.feed_kind_organic
        ProductKind.COMPOST -> R.string.feed_kind_compost
    }

/** A label's percentage, such as "1.4", from basis points. */
fun percent(bp: Int): String = BigDecimal.valueOf(bp.toLong(), 2).stripTrailingZeros().toPlainString()

/** "Fed 12 days ago · PowerFeed All Purpose", the line a zone shows under its next watering. */
@Composable
fun LastFedLine(fed: LastFed, modifier: Modifier = Modifier) {
    val ago = if (fed.days <= 0) stringResource(R.string.fed_today) else androidx.compose.ui.res.pluralStringResource(R.plurals.fed_days_ago, fed.days.toInt(), fed.days.toInt())
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_compost), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(
            fed.product?.let { stringResource(R.string.zone_fed_with, ago, it.name) } ?: stringResource(R.string.zone_fed, ago),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Records a feed: the product, the zones it went on, the day, and the rate as the user measured it. Pollenplot never
 * suggests an amount. A product with more phosphorus than suits the zones' Proteaceae asks before it is saved.
 */
@Composable
fun FeedScreen(zoneId: String?, feeds: FeedRepository, views: GardenViews, today: LocalDate, onAddProduct: () -> Unit, onBack: () -> Unit) {
    val products by feeds.products.collectAsStateWithLifecycle(initialValue = emptyList())
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var productKey by rememberSaveable { mutableStateOf<String?>(null) }
    var chosenZones by rememberSaveable { mutableStateOf(listOfNotNull(zoneId)) }
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var rate by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var warning by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val date = LocalDate.ofEpochDay(day)
    val product = products.firstOrNull { it.key == productKey }

    fun save() {
        val key = productKey ?: return
        scope.launch {
            feeds.addFeed(key, date, chosenZones.toSet(), rate, notes)
            // The database finishes on its own thread; going back has to happen on the main one.
            withContext(Dispatchers.Main) { onBack() }
        }
    }

    DetailScaffold(title = stringResource(R.string.feed_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.feed_product), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Column(Modifier.selectableGroup()) {
                products.forEach { item -> ProductRow(item, selected = item.key == productKey) { productKey = item.key } }
            }
            TextButton(onClick = onAddProduct) { Text(stringResource(R.string.feed_own_product)) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feed_zones), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    zones.forEach { view ->
                        val on = view.zone.id in chosenZones
                        FilterChip(
                            selected = on,
                            onClick = { chosenZones = if (on) chosenZones - view.zone.id else chosenZones + view.zone.id },
                            label = { Text(view.zone.name) },
                            leadingIcon = if (on) {
                                { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
            DateRow(date) { pickingDate = true }
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it },
                label = { Text(stringResource(R.string.feed_rate)) },
                supportingText = { Text(stringResource(R.string.feed_rate_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.feed_no_amounts), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.honey_notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            when {
                product == null -> Text(stringResource(R.string.feed_choose_product), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                chosenZones.isEmpty() -> Text(stringResource(R.string.feed_choose_zone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = {
                    val plants = zones.filter { it.zone.id in chosenZones }.flatMap { it.plants }.map { it.title to it.care?.care?.fertiliser }
                    val sensitive = PhosphorusGuard.sensitive(product?.npk, plants)
                    if (sensitive.isEmpty()) save() else warning = sensitive
                },
                enabled = product != null && chosenZones.isNotEmpty(),
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }
    warning?.let { plants ->
        AlertDialog(
            onDismissRequest = { warning = null },
            title = { Text(stringResource(R.string.feed_phosphorus_title)) },
            text = { Text(stringResource(R.string.feed_phosphorus_body, product?.title.orEmpty(), product?.npk?.let { percent(it.pBp) }.orEmpty(), UiFormat.joinAnd(plants))) },
            confirmButton = {
                TextButton(onClick = {
                    warning = null
                    save()
                }) { Text(stringResource(R.string.feed_phosphorus_save)) }
            },
            dismissButton = { TextButton(onClick = { warning = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (pickingDate) {
        PastDateDialog(date, today, onDismiss = { pickingDate = false }) {
            day = it.toEpochDay()
            pickingDate = false
        }
    }
}

@Composable
private fun ProductRow(product: Product, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.title, style = MaterialTheme.typography.bodyLarge)
            val npk = product.npk
            Text(
                listOfNotNull(
                    stringResource(if (product.own) R.string.feed_your_product else product.kind.label),
                    npk?.let { stringResource(R.string.feed_npk, percent(it.nBp), percent(it.pBp), percent(it.kBp)) }
                        ?: stringResource(R.string.feed_npk_unknown).takeIf { !product.own },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A product the user adds: its name, maker, kind and the analysis from its label, if it has one. */
@Composable
fun OwnProductScreen(feeds: FeedRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var maker by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(ProductKind.LIQUID) }
    var n by rememberSaveable { mutableStateOf("") }
    var p by rememberSaveable { mutableStateOf("") }
    var k by rememberSaveable { mutableStateOf("") }
    val values = listOf(n, p, k).map { text -> text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() >= 0 && it <= BigDecimal(100) } }
    val anyTyped = listOf(n, p, k).any { it.isNotBlank() }
    val npkError = anyTyped && values.any { it == null }
    val npk = if (anyTyped && !npkError) values.map { it!!.movePointRight(2).toInt() }.let { Npk(it[0], it[1], it[2]) } else null

    DetailScaffold(title = stringResource(R.string.product_title), onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp).readable(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.product_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = maker, onValueChange = { maker = it }, label = { Text(stringResource(R.string.product_maker)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ChipGroup(stringResource(R.string.product_kind), ProductKind.entries, kind, { stringResource(it.label) }) { kind = it }
            Text(stringResource(R.string.product_npk_body), style = MaterialTheme.typography.bodyMedium)
            listOf(
                Triple(R.string.product_n, n) { v: String -> n = v },
                Triple(R.string.product_p, p) { v: String -> p = v },
                Triple(R.string.product_k, k) { v: String -> k = v },
            ).forEach { (label, value, change) ->
                OutlinedTextField(
                    value = value,
                    onValueChange = change,
                    label = { Text(stringResource(label)) },
                    singleLine = true,
                    isError = npkError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (npkError) Text(stringResource(R.string.product_npk_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    scope.launch {
                        feeds.addProduct(name, maker, kind, npk)
                        withContext(Dispatchers.Main) { onBack() }
                    }
                },
                enabled = name.isNotBlank() && !npkError,
            ) { Text(stringResource(R.string.honey_save)) }
        }
    }
}
