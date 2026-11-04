package io.github.wkoller.pollenplot.ui.weeds

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.Locality
import io.github.wkoller.pollenplot.data.WishRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.garden.WishStatus
import io.github.wkoller.pollenplot.domain.garden.Wishlist
import io.github.wkoller.pollenplot.domain.plants.CareResolver
import io.github.wkoller.pollenplot.domain.plants.EcoRole
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.domain.weeds.WeedAlternatives
import io.github.wkoller.pollenplot.ui.components.Badge
import io.github.wkoller.pollenplot.ui.components.RoleIcons
import io.github.wkoller.pollenplot.ui.components.largeText
import io.github.wkoller.pollenplot.ui.plants.BadgeTone
import kotlinx.coroutines.launch

/** At most this many alternatives are shown for one weed. */
private const val MAX_ALTERNATIVES = 5

/**
 * What the screens need to show native plants to grow instead of a weed: the published pairs, the wishlist to add them
 * to, and whether pets live at home.
 */
class WeedHelp(private val load: () -> WeedAlternatives, val wishes: WishRepository, val settings: SettingsStore) {
    /** The pairs, or none when the file cannot be read, so a damaged file never stops a screen from showing. */
    val alternatives: WeedAlternatives by lazy {
        runCatching(load).onFailure { Log.w("WeedHelp", "The weed alternatives could not be read", it) }.getOrDefault(WeedAlternatives.EMPTY)
    }
}

/**
 * Native plants to grow instead of the weed known by [weedNames], from sources written for the garden's state, each with
 * its badges and a button that adds it to the wishlist. With pets at home, a plant toxic to them says so first. Nothing
 * shows when no source names an alternative.
 */
@Composable
fun NativeAlternatives(help: WeedHelp, weedNames: List<String>, kb: KnowledgeBase?, locality: Locality, modifier: Modifier = Modifier) {
    if (kb == null) return
    val suggested = remember(help, weedNames, locality.state) { help.alternatives.forWeed(weedNames, locality.state) }
        .mapNotNull { alt -> kb.species(alt.speciesName)?.let { it to alt.source } }
        .take(MAX_ALTERNATIVES)
    if (suggested.isEmpty()) return
    val wishes by help.wishes.wishes.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by help.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val pets = settings?.petsAtHome == true
    val wanted = wishes.filter { it.status == WishStatus.WANTED }.mapNotNull { it.speciesName }.toSet()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val stacked = largeText()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.alternatives_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.alternatives_body), style = MaterialTheme.typography.bodyMedium)
        suggested.forEach { (species, _) ->
            val roles = Wishlist.badgeOrder(locality.roles(species, CareResolver.resolve(kb, species).care), pets)
            val action = @Composable {
                if (species.name in wanted) {
                    Badge(R.drawable.ic_check_circle, stringResource(R.string.alternatives_on_wishlist), BadgeTone.PLANT)
                } else {
                    OutlinedButton(onClick = { scope.launch { help.wishes.add(species.name) } }) {
                        Text(stringResource(R.string.alternatives_add))
                    }
                }
            }
            val about = @Composable {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(species.displayName, style = MaterialTheme.typography.bodyLarge)
                    if (species.displayName != species.name) {
                        Text(species.name, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (pets && EcoRole.TOXIC_TO_PETS in roles) {
                        Badge(R.drawable.ic_pets, stringResource(R.string.alternatives_toxic_pets), BadgeTone.WARNING)
                    }
                    RoleIcons(roles.filterNot { pets && it == EcoRole.TOXIC_TO_PETS }, max = 4, size = 20.dp)
                }
            }
            // With large text the button goes under the plant's name rather than squeezing it.
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    about()
                    action()
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { about() }
                    action()
                }
            }
        }
        suggested.map { it.second }.distinctBy { it.key }.forEach { source ->
            LinkButton(R.drawable.ic_open_in_new, stringResource(R.string.alternatives_source, source.title)) { uri.openUri(source.url) }
        }
    }
}
