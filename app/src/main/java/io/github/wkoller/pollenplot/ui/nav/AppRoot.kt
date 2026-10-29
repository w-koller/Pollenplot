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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import io.github.wkoller.pollenplot.MainActivity
import io.github.wkoller.pollenplot.ai.AskSubject
import io.github.wkoller.pollenplot.domain.assistant.RecordKind
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.PackState
import io.github.wkoller.pollenplot.scan.AddPhotosWorker
import io.github.wkoller.pollenplot.domain.bees.stingless.NativeLogKind
import io.github.wkoller.pollenplot.ui.assistant.AskModel
import io.github.wkoller.pollenplot.ui.assistant.AskScreen
import io.github.wkoller.pollenplot.ui.bees.BeesScreen
import io.github.wkoller.pollenplot.ui.bees.HiveEditorScreen
import io.github.wkoller.pollenplot.ui.bees.HotelEditorScreen
import io.github.wkoller.pollenplot.ui.bees.HotelLookScreen
import io.github.wkoller.pollenplot.ui.bees.HotelScreen
import io.github.wkoller.pollenplot.ui.bees.HotelUpkeepScreen
import io.github.wkoller.pollenplot.ui.bees.HiveScreen
import io.github.wkoller.pollenplot.ui.bees.HoneyRecordKind
import io.github.wkoller.pollenplot.ui.bees.HoneyRecordScreen
import io.github.wkoller.pollenplot.ui.bees.NativeHiveEditorScreen
import io.github.wkoller.pollenplot.ui.bees.NativeHiveScreen
import io.github.wkoller.pollenplot.ui.bees.NativeLogScreen
import io.github.wkoller.pollenplot.ui.guides.GuideScreen
import io.github.wkoller.pollenplot.ui.identify.IdentifyModel
import io.github.wkoller.pollenplot.ui.identify.IdentifyScreen
import io.github.wkoller.pollenplot.ui.plants.AddPlantScreen
import io.github.wkoller.pollenplot.ui.plants.label
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
import io.github.wkoller.pollenplot.ui.settings.BackupModel
import io.github.wkoller.pollenplot.ui.settings.BackupScreen
import io.github.wkoller.pollenplot.ui.settings.ExportScreen
import io.github.wkoller.pollenplot.ui.settings.SettingsScreen
import io.github.wkoller.pollenplot.ui.water.LineEditorScreen
import io.github.wkoller.pollenplot.ui.water.WaterScreen
import io.github.wkoller.pollenplot.ui.weeds.CompareScreen
import io.github.wkoller.pollenplot.ui.weeds.WeedFinderScreen
import io.github.wkoller.pollenplot.ui.weeds.WeedsScreen
import io.github.wkoller.pollenplot.ui.wildlife.AnimalScreen
import io.github.wkoller.pollenplot.ui.wildlife.SightingScreen
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
        if (!ready) return@LaunchedEffect
        when {
            open == MainActivity.OPEN_REVIEW -> navController.navigate(ReviewRoute) { launchSingleTop = true }
            open == MainActivity.OPEN_WEEDS -> navController.navigate(WeedsRoute) { launchSingleTop = true }
            // A tab opens as if it were chosen in the navigation bar.
            open == MainActivity.OPEN_BEES -> openTab(TopLevelTab.BEES)
            open == MainActivity.OPEN_WATER -> openTab(TopLevelTab.WATER)
            open != null && open.startsWith(MainActivity.OPEN_GUIDE) -> navController.navigate(GuideRoute(open.removePrefix(MainActivity.OPEN_GUIDE)))
            else -> return@LaunchedEffect
        }
        onOpened()
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
                                AddAction.SIGHTING -> navController.navigate(AddSightingRoute())
                            }
                        },
                        onOpenZone = { navController.navigate(ZoneRoute(it)) },
                        onEditZone = { navController.navigate(ZoneEditRoute(it)) },
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onSearch = { navController.navigate(SearchRoute) },
                        onReview = { navController.navigate(ReviewRoute) },
                        onWeeds = { navController.navigate(WeedsRoute) },
                        today = container.today,
                        onOpenWater = { openTab(TopLevelTab.WATER) },
                        onOpenBees = { openTab(TopLevelTab.BEES) },
                        onOpenGuide = { navController.navigate(GuideRoute(it)) },
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
                        onOpenSighting = { navController.navigate(SightingRoute(it)) },
                        onAddSighting = { navController.navigate(AddSightingRoute(zoneId = id)) },
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
                    val id = entry.toRoute<PlantRoute>().id
                    PlantDetailScreen(
                        views = container.views,
                        garden = container.garden,
                        knowledge = container.knowledge,
                        plantId = id,
                        onBack = back,
                        onCompare = { navController.navigate(CompareRoute(it, plantId = id)) },
                        onOpenAnimal = { navController.navigate(AnimalRoute(it)) },
                        onOpenSighting = { navController.navigate(SightingRoute(it)) },
                        onAsk = { navController.navigate(AskRoute(plantId = id)) },
                        assistant = container.assistant,
                        onOpenModels = { navController.navigate(ModelsRoute) },
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
                        onAskGarden = { navController.navigate(AskRoute()) },
                        onOpenSighting = { navController.navigate(SightingRoute(it)) },
                        onOpenHive = { navController.navigate(HiveRoute(it)) },
                        onOpenNativeHive = { navController.navigate(NativeHiveRoute(it)) },
                        onOpenHotel = { navController.navigate(HotelRoute(it)) },
                    )
                }
                composable<IdentifyRoute> { entry ->
                    val model = viewModel {
                        IdentifyModel(container.analysis, { container.identifier }, container.garden, container.knowledge, container.settingsStore, createSavedStateHandle(), container::recordsNearby)
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
                        accurateReady = container.accurate.state.collectAsStateWithLifecycle().value == PackState.Ready,
                        onCompare = { detection, species -> navController.navigate(CompareRoute(species, detectionId = detection)) },
                        onOpenFinder = { navController.navigate(WeedFinderRoute(it)) },
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
                        onCompare = { detection, species -> navController.navigate(CompareRoute(species, detectionId = detection)) },
                    )
                }
                composable<RhythmsRoute> {
                    RhythmsScreen(
                        rhythms = container.rhythms,
                        onOpenSettings = openSettings,
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onAddSpecies = { navController.navigate(AddPlantRoute(species = it)) },
                    )
                }
                composable<WildlifeRoute> {
                    WildlifeScreen(
                        views = container.views,
                        knowledge = container.knowledge,
                        settings = container.settingsStore,
                        clock = container.clock,
                        onOpenSettings = openSettings,
                        onAdd = { action ->
                            when (action) {
                                // Birds are what most people photograph first; the camera's chips change it.
                                AddAction.IDENTIFY -> navController.navigate(IdentifyRoute(group = LifeGroup.BIRD.name))
                                AddAction.SIGHTING -> navController.navigate(AddSightingRoute())
                                AddAction.PHOTOS -> addPhotos()
                                AddAction.WEED -> navController.navigate(IdentifyRoute(checkingWeed = true))
                                AddAction.PLANT -> navController.navigate(AddPlantRoute())
                            }
                        },
                        onOpenAnimal = { navController.navigate(AnimalRoute(it)) },
                        onOpenSighting = { navController.navigate(SightingRoute(it)) },
                        onSetLocation = { navController.navigate(GardenLocationRoute) },
                    )
                }
                composable<AnimalRoute> { entry ->
                    val key = entry.toRoute<AnimalRoute>().key
                    AnimalScreen(
                        views = container.views,
                        knowledge = container.knowledge,
                        key = key,
                        onBack = back,
                        onOpenSighting = { navController.navigate(SightingRoute(it)) },
                        onOpenPlant = { navController.navigate(PlantRoute(it)) },
                        onAsk = { navController.navigate(AskRoute(animalKey = key)) },
                    )
                }
                composable<AskRoute> { entry ->
                    val route = entry.toRoute<AskRoute>()
                    val subject = when {
                        route.plantId != null -> AskSubject.Plant(route.plantId)
                        route.animalKey != null -> AskSubject.Animal(route.animalKey)
                        else -> AskSubject.Garden
                    }
                    val model = viewModel { AskModel(container.assistant, subject) }
                    AskScreen(
                        model = model,
                        assistant = container.assistant,
                        views = container.views,
                        knowledge = container.knowledge,
                        onBack = back,
                        onOpenModels = { navController.navigate(ModelsRoute) },
                        onOpenRecord = { record ->
                            when (record.kind) {
                                RecordKind.PLANT -> navController.navigate(PlantRoute(record.id))
                                RecordKind.ZONE -> navController.navigate(ZoneRoute(record.id))
                                RecordKind.SIGHTING -> navController.navigate(SightingRoute(record.id))
                            }
                        },
                    )
                }
                composable<SightingRoute> { entry ->
                    SightingScreen(
                        views = container.views,
                        knowledge = container.knowledge,
                        garden = container.garden,
                        clock = container.clock,
                        sightingId = entry.toRoute<SightingRoute>().id,
                        startZoneId = null,
                        onBack = back,
                        onOpenAnimal = { navController.navigate(AnimalRoute(it)) },
                    )
                }
                composable<AddSightingRoute> { entry ->
                    SightingScreen(
                        views = container.views,
                        knowledge = container.knowledge,
                        garden = container.garden,
                        clock = container.clock,
                        sightingId = null,
                        startZoneId = entry.toRoute<AddSightingRoute>().zoneId,
                        onBack = back,
                        onOpenAnimal = { navController.navigate(AnimalRoute(it)) },
                    )
                }
                composable<BeesRoute> {
                    BeesScreen(
                        beeViews = container.beeViews,
                        views = container.views,
                        settings = container.settingsStore,
                        notifications = container.notifications,
                        guides = container.guides,
                        onOpenSettings = openSettings,
                        onAddHive = { navController.navigate(HiveEditRoute()) },
                        onOpenHive = { navController.navigate(HiveRoute(it)) },
                        onAddNativeHive = { navController.navigate(NativeHiveEditRoute()) },
                        onOpenNativeHive = { navController.navigate(NativeHiveRoute(it)) },
                        onOpenAnimal = { navController.navigate(AnimalRoute(it)) },
                        onAddHotel = { navController.navigate(HotelEditRoute()) },
                        onOpenHotel = { navController.navigate(HotelRoute(it)) },
                        onOpenGuide = { navController.navigate(GuideRoute(it)) },
                    )
                }
                composable<HotelRoute> { entry ->
                    val id = entry.toRoute<HotelRoute>().id
                    HotelScreen(
                        views = container.beeViews,
                        hotels = container.hotels,
                        hotelId = id,
                        onBack = back,
                        onEdit = { navController.navigate(HotelEditRoute(id)) },
                        onLook = { navController.navigate(HotelLookRoute(id)) },
                        onUpkeep = { navController.navigate(HotelUpkeepRoute(id)) },
                    )
                }
                composable<HotelEditRoute> { entry ->
                    HotelEditorScreen(
                        hotels = container.hotels,
                        views = container.views,
                        hotelId = entry.toRoute<HotelEditRoute>().id,
                        today = container.beeViews.today(),
                        onBack = back,
                        onDeleted = { navController.popBackStack(BeesRoute, inclusive = false) },
                        onCreated = { id -> navController.navigate(HotelRoute(id)) { popUpTo<HotelEditRoute> { inclusive = true } } },
                    )
                }
                composable<HotelLookRoute> { entry ->
                    HotelLookScreen(entry.toRoute<HotelLookRoute>().hotelId, container.hotels, container.beeViews.today(), onBack = back)
                }
                composable<HotelUpkeepRoute> { entry ->
                    HotelUpkeepScreen(entry.toRoute<HotelUpkeepRoute>().hotelId, container.hotels, container.beeViews.today(), onBack = back)
                }
                composable<NativeHiveRoute> { entry ->
                    val id = entry.toRoute<NativeHiveRoute>().id
                    NativeHiveScreen(
                        views = container.beeViews,
                        natives = container.nativeBees,
                        hiveId = id,
                        onBack = back,
                        onEdit = { navController.navigate(NativeHiveEditRoute(id)) },
                        onRecord = { kind -> navController.navigate(NativeLogRoute(id, kind.name)) },
                    )
                }
                composable<NativeHiveEditRoute> { entry ->
                    NativeHiveEditorScreen(
                        natives = container.nativeBees,
                        views = container.views,
                        hiveId = entry.toRoute<NativeHiveEditRoute>().id,
                        today = container.beeViews.today(),
                        onBack = back,
                        onDeleted = { navController.popBackStack(BeesRoute, inclusive = false) },
                        onCreated = { id -> navController.navigate(NativeHiveRoute(id)) { popUpTo<NativeHiveEditRoute> { inclusive = true } } },
                    )
                }
                composable<NativeLogRoute> { entry ->
                    val route = entry.toRoute<NativeLogRoute>()
                    NativeLogScreen(NativeLogKind.valueOf(route.kind), route.hiveId, container.nativeBees, container.beeViews.today(), onBack = back)
                }
                composable<GuideRoute> { entry -> GuideScreen(container.guides, entry.toRoute<GuideRoute>().id, onBack = back) }
                composable<HiveRoute> { entry ->
                    val id = entry.toRoute<HiveRoute>().id
                    HiveScreen(
                        views = container.beeViews,
                        bees = container.bees,
                        hiveId = id,
                        onBack = back,
                        onEdit = { navController.navigate(HiveEditRoute(id)) },
                        onRecord = { kind -> navController.navigate(HoneyRecordRoute(id, kind.name)) },
                    )
                }
                composable<HiveEditRoute> { entry ->
                    HiveEditorScreen(
                        bees = container.bees,
                        views = container.views,
                        hiveId = entry.toRoute<HiveEditRoute>().id,
                        today = container.beeViews.today(),
                        onBack = back,
                        onDeleted = { navController.popBackStack(BeesRoute, inclusive = false) },
                        onCreated = { id -> navController.navigate(HiveRoute(id)) { popUpTo<HiveEditRoute> { inclusive = true } } },
                    )
                }
                composable<HoneyRecordRoute> { entry ->
                    val route = entry.toRoute<HoneyRecordRoute>()
                    HoneyRecordScreen(HoneyRecordKind.valueOf(route.kind), route.hiveId, container.bees, container.settingsStore, container.beeViews.today(), onBack = back)
                }
                composable<WaterRoute> {
                    WaterScreen(
                        views = container.waterViews,
                        water = container.water,
                        settings = container.settingsStore,
                        notifications = container.notifications,
                        onOpenSettings = openSettings,
                        onAddLine = { navController.navigate(LineEditRoute()) },
                        onEditLine = { navController.navigate(LineEditRoute(it)) },
                        onOpenGuide = { navController.navigate(GuideRoute(it)) },
                    )
                }
                composable<LineEditRoute> { entry -> LineEditorScreen(container.water, container.views, entry.toRoute<LineEditRoute>().id, onBack = back) }
                composable<SettingsRoute> {
                    SettingsScreen(
                        store = container.settingsStore,
                        onBack = back,
                        onOpenLocation = { navController.navigate(GardenLocationRoute) },
                        onOpenScanning = { navController.navigate(ScanSettingsRoute) },
                        onOpenModels = { navController.navigate(ModelsRoute) },
                        onOpenExport = { navController.navigate(ExportRoute) },
                        onOpenBackup = { navController.navigate(BackupRoute) },
                        onOpenAbout = { navController.navigate(AboutRoute) },
                    )
                }
                composable<ExportRoute> {
                    val current by container.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
                    val stateName = current?.gardenState?.let { stringResource(it.label) }
                    ExportScreen(
                        today = container.beeViews.today(),
                        report = { parts, from, to, pdf, out -> container.writeReport(parts, from, to, pdf, out) },
                        jsonLd = { container.exportJsonLd() },
                        darwinCore = { container.exportDarwinCore(stateName) },
                        onBack = back,
                    )
                }
                composable<BackupRoute> {
                    val model = viewModel { BackupModel(container.backups, context.contentResolver) }
                    val current by container.settingsStore.settings.collectAsStateWithLifecycle(initialValue = null)
                    BackupScreen(model, current?.lastBackupAt, container.clock.zone, onBack = back)
                }
                composable<ModelsRoute> { ModelsScreen(container.meaning, container.accurate, container.gemma, onBack = back) }
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
                        onCompare = { navController.navigate(CompareRoute(it)) },
                        onOpenFinder = { navController.navigate(WeedFinderRoute()) },
                    )
                }
                composable<CompareRoute> { entry ->
                    val route = entry.toRoute<CompareRoute>()
                    CompareScreen(
                        species = route.species,
                        botanical = route.botanical,
                        detectionId = route.detectionId,
                        plantId = route.plantId,
                        views = container.views,
                        garden = container.garden,
                        knowledge = container.knowledge,
                        photos = container.weedPhotos,
                        onBack = back,
                        onOpenFinder = { navController.navigate(WeedFinderRoute(route.detectionId)) },
                    )
                }
                composable<WeedFinderRoute> { entry ->
                    val detectionId = entry.toRoute<WeedFinderRoute>().detectionId
                    WeedFinderScreen(
                        views = container.views,
                        garden = container.garden,
                        detectionId = detectionId,
                        onBack = back,
                        onOpen = { species, botanical -> navController.navigate(CompareRoute(species, botanical = botanical, detectionId = detectionId)) },
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
