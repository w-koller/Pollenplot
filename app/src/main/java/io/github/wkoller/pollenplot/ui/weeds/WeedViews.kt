package io.github.wkoller.pollenplot.ui.weeds

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.domain.weeds.BrisbaneWeeds
import io.github.wkoller.pollenplot.domain.weeds.StateSource
import io.github.wkoller.pollenplot.domain.weeds.WeedLevel
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.domain.weeds.WeedStatus
import io.github.wkoller.pollenplot.ui.UiFormat
import io.github.wkoller.pollenplot.ui.listWords
import io.github.wkoller.pollenplot.ui.plants.label
import java.net.URLEncoder

/** The headline for a weed status, such as "Restricted invasive plant (category 3) in Queensland". */
@Composable
fun weedHeadline(status: WeedStatus, locality: Locality): String? {
    val council = locality.weeds.council
    val stateName = stringResource((council?.state ?: locality.state).label)
    return when (status.level) {
        WeedLevel.PROHIBITED -> stringResource(R.string.weed_level_prohibited, stateName)
        WeedLevel.RESTRICTED -> {
            val categories = status.council?.restricted.orEmpty().map { it.toString() }
            when (categories.size) {
                0 -> stringResource(R.string.weed_level_restricted_plain, stateName)
                1 -> stringResource(R.string.weed_level_restricted, categories.single(), stateName)
                else -> stringResource(R.string.weed_level_restricted_many, UiFormat.joinAnd(categories, listWords()), stateName)
            }
        }
        WeedLevel.NATIONAL -> stringResource(if (status.wons) R.string.weed_level_national else R.string.weed_level_alert)
        WeedLevel.COUNCIL -> stringResource(R.string.weed_level_council, council?.name.orEmpty())
        WeedLevel.LISTED -> stringResource(R.string.weed_level_listed, council?.name.orEmpty())
        WeedLevel.NONE -> null
    }
}

/**
 * What the rules ask of the gardener. Queensland's restricted matter categories each carry their own duty, so they
 * are spelled out for lists from Queensland councils; elsewhere the card says whom to report to.
 */
@Composable
private fun duties(status: WeedStatus, locality: Locality): List<String> {
    val council = locality.weeds.council
    val entry = status.council
    val agency = locality.weeds.state
    val queensland = council?.state == AuState.QLD && entry != null
    val lines = mutableListOf<String>()
    val phone = agency?.phone
    when {
        status.mustReport -> lines += if (queensland && agency != null && phone != null) {
            stringResource(R.string.weed_report_24h, agency.agency, phone)
        } else {
            reportLine(agency, must = true)
        }
        status.askedToReport -> lines += reportLine(agency, must = false)
        entry != null && council != null -> lines += stringResource(R.string.weed_no_report, council.name)
    }
    if (queensland) {
        val categories = entry.restricted
        if (3 in categories) lines += stringResource(R.string.weed_qld_category_3)
        if (4 in categories) lines += stringResource(R.string.weed_qld_category_4)
        if (5 in categories) lines += stringResource(R.string.weed_qld_category_5)
        if (entry.prohibited || categories.isNotEmpty()) lines += stringResource(R.string.weed_qld_obligation)
    }
    return lines
}

@Composable
private fun reportLine(agency: StateSource?, must: Boolean): String {
    val phone = agency?.phone
    val email = agency?.email
    return when {
        agency == null -> stringResource(R.string.weed_please_report_state)
        phone != null -> stringResource(if (must) R.string.weed_must_report_phone else R.string.weed_please_report_phone, agency.agency, phone)
        email != null -> stringResource(if (must) R.string.weed_must_report_email else R.string.weed_please_report_email, agency.agency, email)
        else -> stringResource(if (must) R.string.weed_must_report_page else R.string.weed_please_report_page, agency.agency)
    }
}

/** A plant's weed card: its status where the garden is, what the rules ask, how to control it and where to read more. */
@Composable
fun WeedCard(status: WeedStatus, locality: Locality, scientificName: String, modifier: Modifier = Modifier, onCompare: (() -> Unit)? = null) {
    val headline = weedHeadline(status, locality) ?: return
    val uri = LocalUriHandler.current
    val weeds = locality.weeds
    // Weeds the law restricts stand out in the error colours; the card says why in words as well.
    val serious = status.level == WeedLevel.PROHIBITED || status.level == WeedLevel.RESTRICTED || status.mustReport
    val container = if (serious) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (serious) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
                Text(stringResource(R.string.weed_card_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(headline, style = MaterialTheme.typography.titleSmall)
            if (status.level != WeedLevel.NATIONAL) {
                if (status.wons) Text(stringResource(R.string.weed_also_national), style = MaterialTheme.typography.bodyLarge)
                else if (status.alert) Text(stringResource(R.string.weed_also_alert), style = MaterialTheme.typography.bodyLarge)
            }
            duties(status, locality).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            val nearby = weeds.nearby
            val records = status.nearbyRecords
            if (nearby != null && records != null) {
                Text(
                    if (records > 0) {
                        pluralStringResource(R.plurals.weed_nearby_records, records, records, nearby.radiusKm, nearby.sinceYear)
                    } else {
                        stringResource(R.string.weed_nearby_none, nearby.radiusKm, nearby.sinceYear)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            // The council writes each method as a label, such as "Foliar spray"; in a sentence only the first keeps its capital.
            val control = status.council?.control.orEmpty().mapIndexed { i, method -> if (i == 0) method else method.replaceFirstChar(Char::lowercaseChar) }
            if (control.isNotEmpty()) {
                Text(stringResource(R.string.weed_control), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                Text(UiFormat.joinAnd(control, listWords()) + ". " + stringResource(R.string.weed_control_label), style = MaterialTheme.typography.bodyLarge)
            }
            Text(stringResource(R.string.weed_disposal), style = MaterialTheme.typography.bodyLarge)
            Column {
                onCompare?.let { compare -> LinkButton(R.drawable.ic_photo_library, stringResource(R.string.weed_compare), content) { compare() } }
                val council = weeds.council
                val entry = status.council
                if (council != null && entry != null && council.format == WeedSources.BRISBANE_OPEN_DATA) {
                    LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.weed_link_council, council.name), content) {
                        uri.openUri(BrisbaneWeeds.profileUrl(council.site, entry))
                    }
                }
                weeds.weedsAustralia?.let { links ->
                    LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.weed_link_weeds_australia), content) {
                        uri.openUri(links.search.replace("{name}", URLEncoder.encode(scientificName, "UTF-8")))
                    }
                }
                weeds.state?.let { agency ->
                    LinkButton(R.drawable.ic_open_in_new, agency.agency, content) { uri.openUri(agency.url) }
                    if (status.mustReport || status.askedToReport) {
                        agency.phone?.let { phone -> LinkButton(R.drawable.ic_call, phone, content) { uri.openUri("tel:" + phone.replace(" ", "")) } }
                        agency.email?.let { email -> LinkButton(R.drawable.ic_mail, email, content) { uri.openUri("mailto:$email") } }
                    }
                }
            }
        }
    }
}

/** A one-line weed warning, for a suggestion on a review card. */
@Composable
fun WeedNotice(status: WeedStatus, locality: Locality, modifier: Modifier = Modifier) {
    val headline = weedHeadline(status, locality) ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_warning), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        Text(
            if (status.mustReport) headline + ". " + stringResource(R.string.weed_notice_report) else headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** A link to a web page, a phone number or an email address, with an icon saying which. */
@Composable
internal fun LinkButton(@DrawableRes icon: Int, label: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    val colors = if (color == Color.Unspecified) ButtonDefaults.textButtonColors() else ButtonDefaults.textButtonColors(contentColor = color)
    TextButton(onClick = onClick, colors = colors) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
