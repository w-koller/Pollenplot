package io.github.wkoller.pollenplot.ui.identify

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.semantics.contentDescription
import io.github.wkoller.pollenplot.domain.wildlife.LifeGroup
import io.github.wkoller.pollenplot.ml.AiyModel
import io.github.wkoller.pollenplot.ui.wildlife.CAMERA_GROUPS
import io.github.wkoller.pollenplot.ui.wildlife.label
import io.github.wkoller.pollenplot.ui.wildlife.noun
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.GardenViews
import io.github.wkoller.pollenplot.data.kb.KnowledgeRepository
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.id.BoxBp
import io.github.wkoller.pollenplot.domain.id.Candidate
import io.github.wkoller.pollenplot.domain.plants.KnowledgeBase
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.EmptyState
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.review.PhotoReviewScreen
import io.github.wkoller.pollenplot.ui.review.confidenceLabel
import io.github.wkoller.pollenplot.ui.review.readable
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Identify with the camera: a viewfinder with live suggestions, then the plants found in the photo, each ready to add
 * to the garden. Plants that are not reviewed here wait in the Review queue.
 */
@Composable
fun IdentifyScreen(
    model: IdentifyModel,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onAddByName: () -> Unit,
    checkingWeed: Boolean = false,
    onCompare: (detectionId: String, speciesName: String) -> Unit = { _, _ -> },
    onOpenFinder: ((detectionId: String?) -> Unit)? = null,
    /** Whether the accurate pack is on the phone, which names the groups the quick models do not cover. */
    accurateReady: Boolean = false,
) {
    val stage by model.stage.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    // Checking a weed is the same identification of plants; each suggestion's weed status shows on its card.
    LaunchedEffect(checkingWeed) { if (checkingWeed) model.choose(LifeGroup.PLANT) }
    val screenTitle = stringResource(if (checkingWeed) R.string.identify_weed_title else R.string.identify_title)
    val hint = when {
        checkingWeed -> stringResource(R.string.identify_weed_hint)
        group == LifeGroup.PLANT -> stringResource(R.string.identify_point)
        AiyModel.of(group) == null && !accurateReady -> stringResource(R.string.identify_needs_pack)
        else -> stringResource(R.string.identify_point_animal, stringResource(group.noun))
    }
    when (val current = stage) {
        IdentifyStage.Camera -> CameraGate(model, knowledge, screenTitle, hint, onBack, chooseGroup = !checkingWeed)
        IdentifyStage.Analysing -> Analysing(model)
        is IdentifyStage.Results -> Results(model, views, knowledge, settings, screenTitle, onBack, onOpenPlant, onCompare, onOpenFinder.takeIf { checkingWeed })
        IdentifyStage.NoneFound -> Problem(
            screenTitle = screenTitle,
            title = if (group == LifeGroup.PLANT) stringResource(R.string.identify_none_title) else stringResource(R.string.identify_none_animal, stringResource(group.noun)),
            body = when {
                group == LifeGroup.PLANT -> stringResource(R.string.identify_none_body)
                AiyModel.of(group) == null && !accurateReady -> stringResource(R.string.identify_needs_pack)
                else -> stringResource(R.string.identify_none_animal_body)
            },
            onBack = onBack,
            onTryAgain = model::takeAnother,
            onAddByName = onAddByName,
        )
        IdentifyStage.Failed -> Problem(
            screenTitle = screenTitle,
            title = stringResource(R.string.identify_failed_title),
            body = stringResource(R.string.identify_failed),
            onBack = onBack,
            onTryAgain = model::takeAnother,
            onAddByName = onAddByName,
        )
    }
}

private fun hasCamera(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/** Asks for the camera the first time, explains why if the user says no, and shows the viewfinder once allowed. */
@Composable
private fun CameraGate(model: IdentifyModel, knowledge: KnowledgeRepository, title: String, hint: String, onBack: () -> Unit, chooseGroup: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(hasCamera(context)) }
    var asked by rememberSaveable { mutableStateOf(false) }
    var canAskAgain by remember { mutableStateOf(true) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        // After the user says no twice, Android stops showing the request, so the app offers its settings instead.
        canAskAgain = allowed || activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == true
    }
    // The user may allow the camera in the phone's settings and come back.
    LifecycleResumeEffect(Unit) {
        granted = hasCamera(context)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (granted) {
        Viewfinder(model, knowledge, hint, onBack, chooseGroup)
    } else {
        DetailScaffold(title = title, onBack = onBack) { padding ->
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(R.drawable.ic_photo_camera, stringResource(R.string.identify_permission_title), stringResource(R.string.identify_permission_body))
                if (canAskAgain) {
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.identify_permission_allow)) }
                } else {
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }) { Text(stringResource(R.string.identify_permission_settings)) }
                }
            }
        }
    }
}

/** The camera, full screen, with the live suggestions and the button that takes the photo. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Viewfinder(model: IdentifyModel, knowledge: KnowledgeRepository, hint: String, onBack: () -> Unit, chooseGroup: Boolean) {
    val live by model.live.collectAsStateWithLifecycle()
    val capturing by model.capturing.collectAsStateWithLifecycle()
    val group by model.group.collectAsStateWithLifecycle()
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val fauna by knowledge.fauna.collectAsStateWithLifecycle()
    val nameOf: (Candidate) -> String = { candidate ->
        val name = candidate.speciesName ?: candidate.label
        (if (group == LifeGroup.PLANT) kb?.species(name)?.displayName else fauna?.animal(name)?.displayName) ?: candidate.label
    }
    val imageCapture = remember { identifyImageCapture() }
    LightSystemBarIcons()

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(imageCapture, onFrame = model::glance, modifier = Modifier.fillMaxSize())
        // Marks the middle of the view, which the live suggestions look at.
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth(0.7f).aspectRatio(1f)
                .border(3.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(36.dp)),
        )
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onBack, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                // The quick models cannot tell a plant from an animal, so the user says what the photo is of.
                if (chooseGroup) GroupChoice(group, model::choose)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LiveSuggestions(live, nameOf, hint)
            MediumExtendedFloatingActionButton(
                text = { Text(stringResource(if (capturing) R.string.identify_capturing else R.string.identify_capture)) },
                icon = {
                    if (capturing) {
                        PollenLoadingIndicator(Modifier.size(28.dp))
                    } else {
                        Icon(painterResource(R.drawable.ic_photo_camera), contentDescription = null)
                    }
                },
                onClick = { model.capture { imageCapture.takeUprightBitmap() } },
            )
        }
    }
}

/** What the photo is of, as a row of chips over the camera that scrolls sideways. */
@Composable
private fun GroupChoice(selected: LifeGroup, onChoose: (LifeGroup) -> Unit) {
    val description = stringResource(R.string.identify_what)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CAMERA_GROUPS.forEach { group ->
            FilterChip(
                selected = group == selected,
                onClick = { onChoose(group) },
                label = { Text(stringResource(group.label)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color.Black.copy(alpha = 0.55f),
                    labelColor = Color.White,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
    }
}

/** Up to three suggestions for what the camera sees, or a request to point it at what is being photographed. */
@Composable
private fun LiveSuggestions(live: List<Candidate>, nameOf: (Candidate) -> String, hint: String) {
    val pill = Modifier.clip(CircleShape)
    if (live.isEmpty()) {
        Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, modifier = pill) {
            Text(hint, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        live.forEachIndexed { i, candidate ->
            val name = nameOf(candidate)
            val label = stringResource(R.string.identify_live_label, name, stringResource(confidenceLabel(candidate.scoreBp)))
            Surface(
                color = if (i == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (i == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = pill,
            ) {
                Text(
                    label,
                    style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    fontStyle = if (candidate.speciesName == null) FontStyle.Italic else null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Light status and navigation bar icons while the camera fills the screen, put back as they were afterwards. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val status = controller.isAppearanceLightStatusBars
        val navigation = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = status
            controller.isAppearanceLightNavigationBars = navigation
        }
    }
}

/** The photo just taken, dimmed, while the plants in it are found. */
@Composable
private fun Analysing(model: IdentifyModel) {
    val captured by model.captured.collectAsStateWithLifecycle()
    LightSystemBarIcons()
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        captured?.let { photo ->
            val image = remember(photo) { photo.asImageBitmap() }
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            PollenLoadingIndicator(Modifier.size(96.dp), contained = true)
            Text(
                stringResource(R.string.identify_analysing),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The photo with each plant found marked by its number, then a card for each plant. */
@Composable
private fun Results(
    model: IdentifyModel,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    title: String,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
    onCompare: (detectionId: String, speciesName: String) -> Unit,
    onOpenFinder: ((detectionId: String?) -> Unit)?,
) {
    val result by model.results.collectAsStateWithLifecycle()
    PhotoReviewScreen(
        onCompare = onCompare,
        title = title,
        reviews = result?.let(::listOf),
        actions = model.actions,
        views = views,
        knowledge = knowledge,
        settings = settings,
        onBack = onBack,
        onOpenPlant = onOpenPlant,
        photoHeading = { review ->
            // A photo of an animal has one animal in it, in the middle of the square.
            val animal = review.detections.firstOrNull()?.group?.takeIf { it.isWildlife }
            Text(
                if (animal != null) {
                    stringResource(R.string.identify_found_animal, stringResource(animal.noun))
                } else {
                    pluralStringResource(R.plurals.identify_found, review.detections.size, review.detections.size)
                },
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (animal != null) R.string.identify_animal_note else R.string.identify_model_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        bottom = {
            // Checking a weed: when no suggestion is right, the council's list can be searched by what the plant looks like.
            if (onOpenFinder != null) {
                item {
                    Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.identify_weed_more), style = MaterialTheme.typography.bodyLarge)
                        OutlinedButton(onClick = { onOpenFinder(result?.detections?.firstOrNull()?.id) }) { Text(stringResource(R.string.finder_title)) }
                    }
                }
            }
            item {
                Row(Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = model::takeAnother) { Text(stringResource(R.string.identify_try_again)) }
                    Button(onClick = onBack) { Text(stringResource(R.string.review_done)) }
                }
            }
        },
    )
}

/** No plant was found, or the photo could not be identified: what happened and what the user can do next. */
@Composable
private fun Problem(screenTitle: String, title: String, body: String, onBack: () -> Unit, onTryAgain: () -> Unit, onAddByName: () -> Unit) {
    DetailScaffold(title = screenTitle, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyState(R.drawable.ic_potted_plant, title, body)
            Button(onClick = onTryAgain) { Text(stringResource(R.string.identify_try_again)) }
            OutlinedButton(onClick = onAddByName) { Text(stringResource(R.string.add_plant_by_name)) }
        }
    }
}
