package io.github.wkoller.pollenplot.ui.nav

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.work.WorkManager
import io.github.wkoller.pollenplot.AppContainer
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.wkoller.pollenplot.MainActivity
import io.github.wkoller.pollenplot.scan.AddPhotosWorker
import io.github.wkoller.pollenplot.ui.bees.BeesScreen
import io.github.wkoller.pollenplot.ui.identify.IdentifyModel
import io.github.wkoller.pollenplot.ui.identify.IdentifyScreen
import io.github.wkoller.pollenplot.ui.plants.AddPlantScreen
import io.github.wkoller.pollenplot.ui.plants.PlantDetailScreen
import io.github.wkoller.pollenplot.ui.plot.PlotScreen
import io.github.wkoller.pollenplot.ui.plot.ZoneDetailScreen
import io.github.wkoller.pollenplot.ui.plot.ZoneEditorScreen
import io.github.wkoller.pollenplot.ui.review.ReviewModel
import io.github.wkoller.pollenplot.ui.review.ReviewScreen
import io.github.wkoller.pollenplot.ui.rhythms.RhythmsScreen
import io.github.wkoller.pollenplot.ui.search.SearchScreen
import io.github.wkoller.pollenplot.ui.settings.AboutScreen
import io.github.wkoller.pollenplot.ui.settings.DataSourcesScreen
import io.github.wkoller.pollenplot.ui.settings.GardenLocationModel
import io.github.wkoller.pollenplot.ui.settings.GardenLocationScreen
import io.github.wkoller.pollenplot.ui.settings.LicenceTextScreen
import io.github.wkoller.pollenplot.ui.settings.LicencesScreen
import io.github.wkoller.pollenplot.ui.settings.ModelsScreen
import io.github.wkoller.pollenplot.ui.settings.ScanSettingsScreen
import io.github.wkoller.pollenplot.ui.settings.SettingsScreen
import io.github.wkoller.pollenplot.ui.water.WaterScreen
import io.github.wkoller.pollenplot.ui.weeds.WeedsScreen
import io.github.wkoller.pollenplot.ui.wildlife.WildlifeScreen

/** Windows at least this wide get a navigation rail at the side instead of a bar along the bottom. */
private val WIDE_WINDOW = 600.dp

/** At most this many photos can be added at once, which keeps one batch to a few minutes of work. */
private const val MAX_ADDED_PHOTOS = 30

/**
 * The app's frame: the main tabs in a navigation bar (or a rail on wide screens) around one navigation host. The host
 * always sits at the same place in the layout, so turning the phone keeps the screens the user has open.
 */
@Composable
fun AppRoot(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
    open: String? = null,
    onOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val currentTab = TopLevelTab.entries.firstOrNull { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true }
    val showTabs = currentTab != null
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= WIDE_WINDOW

    fun openTab(tab: TopLevelTab) = navController.navigate(tab.route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    // Photos chosen in the system's photo picker need no permission. The app keeps read access while it looks at
    // them in the background, then shows them in the Review queue as they are done.
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_ADDED_PHOTOS)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        AddPhotosWorker.enqueue(context, uris)
        navController.navigate(ReviewRoute) { launchSingleTop = true }
    }
    val addPhotos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    // A notification can ask for a screen, such as the Review queue after a scan. It opens once the navigation host
    // has its first screen, because there is nowhere to navigate from before then.
    val ready = entry != null
    LaunchedEffect(open, ready) {
        if (ready && open == MainActivity.OPEN_REVIEW) {
            navController.navigate(ReviewRoute) { launchSingleTop = true }
            onOpened()
        }
    }

    Row(Modifier.fillMaxSize()) {
        if (wide && showTabs) {
            WideNavigationRail {
                TopLevelTab.entries.forEach { tab ->
                    WideNavigationRailItem(
                        selected = tab == currentTab,
                        onClick = { openTab(tab) },
                        icon = { Icon(painterResource(if (tab == currentTab) tab.selectedIcon else tab.icon), contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                        railExpanded = false,
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!wide && showTabs) {
                    ShortNavigationBar {
                        TopLevelTab.entries.forEach { tab ->
                            ShortNavigationBarItem(
                                selected = tab == currentTab,
                                onClick = { openTab(tab) },
                                icon = { Icon(painterResource(if (tab == currentTab) tab.selectedIcon else tab.icon), contentDescription = null) },
                                label = { Text(stringResource(tab.label)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            val railInsets = if (wide && showTabs) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier
            NavHost(
                navController = navController,
                startDestination = PlotRoute,
                modifier = Modifier.padding(padding).consumeWindowInsets(padding).then(railInsets),
            ) {
                val openSettings = { navController.navigate(SettingsRoute) }
                val back: () -> Unit = { navController.popBackStack() }
                composable<PlotRoute> {
                    PlotScreen(
                        garden = container.garden,
                        views = container.views,
                        onOpenSettings = openSettings,
                        onAdd = { action ->
                            when (action) {
                                AddAction.IDENTIFY -> navController.navigate(IdentifyRoute())
                                AddAction.PHOTOS -> addPhotos()
                                AddAction.WEED -> navController.navigate(IdentifyRoute(checkingWeed = true))
                                AddAction.PLANT -> navController.navigate(AddPlantRoute())
                            }
                        },
                        onOpenZone = { navController.navigate(ZoneRoute(it)) },
                        onEditZone = { navController.navigate(ZoneEditRoute(it)) },
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onSearch = { navController.navigate(SearchRoute) },
                        onReview = { navController.navigate(ReviewRoute) },
                        onWeeds = { navController.navigate(WeedsRoute) },
                    )
                }
                composable<ZoneRoute> { entry ->
                    val id = entry.toRoute<ZoneRoute>().id
                    ZoneDetailScreen(
                        views = container.views,
                        zoneId = id,
                        onBack = back,
                        onEdit = { navController.navigate(ZoneEditRoute(id)) },
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onAddPlant = { navController.navigate(AddPlantRoute(zoneId = id)) },
                    )
                }
                composable<ZoneEditRoute> { entry ->
                    ZoneEditorScreen(
                        garden = container.garden,
                        zoneId = entry.toRoute<ZoneEditRoute>().id,
                        onBack = back,
                        onRemoved = { navController.popBackStack(PlotRoute, inclusive = false) },
                    )
                }
                composable<PlantRoute> { entry ->
                    PlantDetailScreen(
                        views = container.views,
                        garden = container.garden,
                        knowledge = container.knowledge,
                        plantId = entry.toRoute<PlantRoute>().id,
                        onBack = back,
                    )
                }
                composable<AddPlantRoute> { entry ->
                    val route = entry.toRoute<AddPlantRoute>()
                    AddPlantScreen(
                        knowledge = container.knowledge,
                        garden = container.garden,
                        views = container.views,
                        initialZoneId = route.zoneId,
                        initialSpecies = route.species,
                        onBack = back,
                        onAdded = { plantId ->
                            navController.navigate(PlantRoute(plantId)) { popUpTo<AddPlantRoute> { inclusive = true } }
                        },
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(
                        garden = container.garden,
                        views = container.views,
                        knowledge = container.knowledge,
                        meaning = container.meaning,
                        onBack = back,
                        onOpenZone = { navController.navigate(ZoneRoute(it)) },
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onAddSpecies = { navController.navigate(AddPlantRoute(species = it)) },
                    )
                }
                composable<IdentifyRoute> { entry ->
                    val model = viewModel {
                        IdentifyModel(container.analysis, { container.identifier }, container.garden, container.knowledge, container.settingsStore, createSavedStateHandle())
                    }
                    IdentifyScreen(
                        model = model,
                        views = container.views,
                        knowledge = container.knowledge,
                        settings = container.settingsStore,
                        onBack = back,
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onAddByName = { navController.navigate(AddPlantRoute()) { popUpTo<IdentifyRoute> { inclusive = true } } },
                        checkingWeed = entry.toRoute<IdentifyRoute>().checkingWeed,
                    )
                }
                composable<ReviewRoute> {
                    val model = viewModel { ReviewModel(container.garden, container.settingsStore, WorkManager.getInstance(container.appContext)) }
                    ReviewScreen(
                        model = model,
                        views = container.views,
                        knowledge = container.knowledge,
                        settings = container.settingsStore,
                        onBack = back,
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onAddPhotos = addPhotos,
                        onIdentify = { navController.navigate(IdentifyRoute()) },
                    )
                }
                composable<RhythmsRoute> { RhythmsScreen(onOpenSettings = openSettings) }
                composable<WildlifeRoute> { WildlifeScreen(onOpenSettings = openSettings) }
                composable<BeesRoute> { BeesScreen(onOpenSettings = openSettings) }
                composable<WaterRoute> { WaterScreen(onOpenSettings = openSettings) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        store = container.settingsStore,
                        onBack = back,
                        onOpenLocation = { navController.navigate(GardenLocationRoute) },
                        onOpenScanning = { navController.navigate(ScanSettingsRoute) },
                        onOpenModels = { navController.navigate(ModelsRoute) },
                        onOpenAbout = { navController.navigate(AboutRoute) },
                    )
                }
                composable<ModelsRoute> { ModelsScreen(container.meaning, container.accurate, onBack = back) }
                composable<GardenLocationRoute> {
                    val model = viewModel { GardenLocationModel(container.places, container.location, container.settingsStore) }
                    GardenLocationScreen(model, container.settingsStore, onBack = back)
                }
                composable<WeedsRoute> {
                    WeedsScreen(
                        views = container.views,
                        knowledge = container.knowledge,
                        settings = container.settingsStore,
                        onBack = back,
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onCheckWeed = { navController.navigate(IdentifyRoute(checkingWeed = true)) },
                        onSetLocation = { navController.navigate(GardenLocationRoute) },
                    )
                }
                composable<ScanSettingsRoute> {
                    ScanSettingsScreen(container.settingsStore, container.gallery, container.notifications, container.clock, onBack = back)
                }
                composable<AboutRoute> {
                    AboutScreen(
                        onBack = back,
                        onOpenLicences = { navController.navigate(LicencesRoute) },
                        onOpenSources = { navController.navigate(DataSourcesRoute) },
                    )
                }
                composable<DataSourcesRoute> { DataSourcesScreen(container.weeds.sources, onBack = back) }
                composable<LicencesRoute> { LicencesScreen(onBack = back, onOpen = { navController.navigate(LicenceTextRoute(it)) }) }
                composable<LicenceTextRoute> { LicenceTextScreen(file = it.toRoute<LicenceTextRoute>().file, onBack = back) }
            }
        }
    }
}
