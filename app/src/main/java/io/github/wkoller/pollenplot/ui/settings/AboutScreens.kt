package io.github.wkoller.pollenplot.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.wkoller.pollenplot.BuildConfig
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenShapes
import io.github.wkoller.pollenplot.domain.weeds.WeedSources
import io.github.wkoller.pollenplot.ui.plants.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLicences: () -> Unit, onOpenSources: () -> Unit) {
    DetailScaffold(title = stringResource(R.string.about_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(96.dp).clip(PollenShapes.flower()).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_local_florist_filled),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.app_tagline), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.about_body), style = MaterialTheme.typography.bodyLarge)
                }
            }
            item {
                ListItem(
                    onClick = onOpenLicences,
                    supportingContent = { Text(stringResource(R.string.about_licences_body)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_description), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.about_licences))
                }
            }
            item {
                ListItem(
                    onClick = onOpenSources,
                    supportingContent = { Text(stringResource(R.string.about_sources_body)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                ) {
                    Text(stringResource(R.string.about_sources))
                }
            }
        }
    }
}

/** A source of information the app uses, what it gives the app and its licence, and the website it links to. */
private data class SourceItem(@StringRes val name: Int, @StringRes val body: Int, val url: String)

/** About › Data sources: where the weed lists, plant records and place names come from, with their credits. */
@Composable
fun DataSourcesScreen(sources: WeedSources, onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    val items = listOfNotNull(
        sources.councils.firstOrNull { it.id == "brisbane" }?.let { SourceItem(R.string.source_brisbane, R.string.source_brisbane_body, it.site) },
        SourceItem(R.string.source_ala, R.string.source_ala_body, "https://www.ala.org.au/"),
        SourceItem(R.string.source_weeds_australia, R.string.source_weeds_australia_body, "https://weeds.org.au/"),
        SourceItem(R.string.source_open_meteo, R.string.source_open_meteo_body, "https://open-meteo.com/"),
        SourceItem(R.string.source_inaturalist, R.string.source_inaturalist_body, "https://www.inaturalist.org/"),
        SourceItem(R.string.source_gbif, R.string.source_gbif_body, "https://doi.org/10.15468/39omei"),
    )
    DetailScaffold(title = stringResource(R.string.sources_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(stringResource(R.string.sources_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
            items(items) { item ->
                ListItem(
                    onClick = { uri.openUri(item.url) },
                    supportingContent = { Text(stringResource(item.body)) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                ) { Text(stringResource(item.name)) }
            }
            item { SectionHeading(stringResource(R.string.source_states)) }
            item {
                Text(
                    stringResource(R.string.source_states_body),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(sources.states) { state ->
                ListItem(
                    onClick = { uri.openUri(state.url) },
                    supportingContent = { Text(stringResource(state.state.label)) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                ) { Text(state.agency) }
            }
        }
    }
}

/** A piece of software in the app, the licence it comes under, and the file in assets/licences with that licence. */
private data class LicenceItem(@StringRes val name: Int, @StringRes val licence: Int, val file: String)

/** The app's own licence, copied from the repository by the build. */
private const val GPL = "pollenplot-LICENSE.txt"

/** The text of the Apache License 2.0. */
private const val APACHE = "apache-2.0.txt"

private val licences = listOf(
    LicenceItem(R.string.licence_item_app, R.string.licence_gpl, GPL),
    LicenceItem(R.string.licence_item_jetpack, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_kotlin, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_material_symbols, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_aiy_plants, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_litert, R.string.licence_apache, "litert-LICENSE.txt"),
    LicenceItem(R.string.licence_item_litert_notices, R.string.licence_notices, "litert-THIRD_PARTY_NOTICE.txt"),
    LicenceItem(R.string.licence_item_coil, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_java_libraries, R.string.licence_apache, APACHE),
    LicenceItem(R.string.licence_item_onnxruntime, R.string.licence_mit, "onnxruntime-LICENSE.txt"),
    LicenceItem(R.string.licence_item_onnxruntime_notices, R.string.licence_notices, "onnxruntime-THIRD_PARTY_NOTICE.txt"),
    LicenceItem(R.string.licence_item_bge, R.string.licence_mit, "bge-small-en-v1.5-LICENSE.txt"),
    LicenceItem(R.string.licence_item_biocap, R.string.licence_mit, "biocap-LICENSE.txt"),
)

/** About › Licences: the software the app is built with, each with the licence it comes under. */
@Composable
fun LicencesScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    DetailScaffold(title = stringResource(R.string.licences_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    stringResource(R.string.licences_body),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(licences) { item ->
                ListItem(
                    onClick = { onOpen(item.file) },
                    supportingContent = { Text(stringResource(item.licence)) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_description), contentDescription = null) },
                ) {
                    Text(stringResource(item.name))
                }
            }
        }
    }
}

/**
 * The full text of one licence. Licence texts can be long, so they are read away from the main thread and shown a
 * paragraph at a time. Lines are joined within each paragraph, so the text wraps to the screen at any text size.
 */
@Composable
fun LicenceTextScreen(file: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val paragraphs by produceState<List<String>?>(null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("licences/$file").bufferedReader().use { it.readText() }
                    .split(Regex("""\n\s*\n"""))
                    .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.trim() }
                    .filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }
    }
    DetailScaffold(title = stringResource(R.string.licences_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            val loaded = paragraphs
            when {
                loaded == null -> item { Text(stringResource(R.string.licence_loading), modifier = Modifier.padding(16.dp)) }
                loaded.isEmpty() -> item { Text(stringResource(R.string.licence_missing), modifier = Modifier.padding(16.dp)) }
                else -> items(loaded) { paragraph ->
                    Text(paragraph, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        }
    }
}
