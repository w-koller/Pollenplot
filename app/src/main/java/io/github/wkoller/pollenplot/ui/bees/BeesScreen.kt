package io.github.wkoller.pollenplot.ui.bees

import io.github.wkoller.pollenplot.ui.components.readableSides
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.ui.nav.AddAction
import io.github.wkoller.pollenplot.ui.components.AddMenu
import androidx.compose.foundation.layout.PaddingValues
import io.github.wkoller.pollenplot.data.BeeViews
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.guides.GuideLibrary
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.guide.Guide
import io.github.wkoller.pollenplot.domain.guide.GuideSection
import io.github.wkoller.pollenplot.domain.wildlife.NativeBeesSeen
import io.github.wkoller.pollenplot.notify.Notifications
import io.github.wkoller.pollenplot.ui.components.ConnectedChoice
import io.github.wkoller.pollenplot.ui.components.TabScaffold
import io.github.wkoller.pollenplot.ui.components.rememberNotificationAccess
import kotlinx.coroutines.launch

/**
 * The three bee sections. European honey bees, Australian native bees and bee hotels each keep their own records,
 * screens and guide, so they are chosen here and never shown mixed together.
 */
enum class BeeSection(@StringRes val label: Int) {
    HONEY(R.string.bees_section_honey),
    NATIVE(R.string.bees_section_native),
    HOTELS(R.string.bees_section_hotels),
}

@Composable
fun BeesScreen(
    beeViews: BeeViews,
    views: GardenViews,
    settings: SettingsStore,
    notifications: Notifications,
    guides: GuideLibrary,
    onOpenSettings: () -> Unit,
    onAddHive: () -> Unit,
    onOpenHive: (String) -> Unit,
    onAddNativeHive: () -> Unit,
    onOpenNativeHive: (String) -> Unit,
    onOpenAnimal: (String) -> Unit,
    onAddHotel: () -> Unit,
    onOpenHotel: (String) -> Unit,
    onOpenGuide: (String) -> Unit,
    initialSection: BeeSection = BeeSection.HONEY,
    /** Opens the report with the bees' parts chosen. */
    onReport: (() -> Unit)? = null,
) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    val hives by beeViews.hives.collectAsStateWithLifecycle(initialValue = null)
    val nativeHives by beeViews.nativeHives.collectAsStateWithLifecycle(initialValue = null)
    val hotels by beeViews.hotels.collectAsStateWithLifecycle(initialValue = null)
    val sightings by views.sightings.collectAsStateWithLifecycle(initialValue = emptyList())
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val access = rememberNotificationAccess(notifications)
    val honeyGuide by produceState(emptyList<Guide>()) { value = guides.section(GuideSection.HONEY) }
    val nativeGuide by produceState(emptyList<Guide>()) { value = guides.section(GuideSection.NATIVE) }
    val hotelGuide by produceState(emptyList<Guide>()) { value = guides.section(GuideSection.HOTELS) }
    TabScaffold(
        title = stringResource(R.string.bees_title),
        subtitle = stringResource(R.string.bees_subtitle),
        onOpenSettings = onOpenSettings,
        actions = {
            onReport?.let { report -> IconButton(onClick = report) { Icon(painterResource(R.drawable.ic_description), contentDescription = stringResource(R.string.report_open)) } }
        },
        // The same add button as the Plot's, with a hive of either kind and a hotel in it whichever section shows.
        floatingActionButton = {
            if (hives != null) {
                AddMenu(AddAction.BEES) { action ->
                    when (action) {
                        AddAction.HONEY_HIVE -> onAddHive()
                        AddAction.NATIVE_HIVE -> onAddNativeHive()
                        AddAction.HOTEL -> onAddHotel()
                        else -> Unit
                    }
                }
            }
        },
    ) { padding ->
        // The list ends clear of the add button.
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = readableSides(), end = readableSides(), top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp)) {
            item {
                ConnectedChoice(
                    options = BeeSection.entries,
                    selected = section,
                    label = { stringResource(it.label) },
                    onChoose = { section = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            val loaded = hives
            val natives = nativeHives
            val chosen = current
            if (section == BeeSection.HONEY && loaded != null && chosen != null) {
                honeySection(
                    hives = loaded,
                    today = beeViews.today(),
                    settings = chosen,
                    onSettings = { change -> scope.launch { settings.update(change) } },
                    notifications = access,
                    guide = honeyGuide,
                    onAddHive = onAddHive,
                    onOpenHive = onOpenHive,
                    onOpenGuide = onOpenGuide,
                    onOpenLink = { uri.openUri(it) },
                )
            } else if (section == BeeSection.NATIVE && natives != null && chosen != null) {
                val today = beeViews.today()
                nativeSection(
                    hives = natives,
                    seen = NativeBeesSeen.of(sightings.map { it.sighting to it.animal }, today.year, beeViews.zone()),
                    settings = chosen,
                    onSettings = { change -> scope.launch { settings.update(change) } },
                    notifications = access,
                    guide = nativeGuide,
                    onAddHive = onAddNativeHive,
                    onOpenHive = onOpenNativeHive,
                    onOpenAnimal = onOpenAnimal,
                    onOpenGuide = onOpenGuide,
                )
            } else if (section == BeeSection.HOTELS && hotels != null) {
                hotelSection(
                    hotels = hotels.orEmpty(),
                    state = chosen?.gardenState,
                    guide = hotelGuide,
                    onAddHotel = onAddHotel,
                    onOpenHotel = onOpenHotel,
                    onOpenGuide = onOpenGuide,
                )
            }
        }
    }
}
