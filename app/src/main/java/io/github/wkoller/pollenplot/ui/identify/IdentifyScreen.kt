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
) {
    val stage by model.stage.collectAsStateWithLifecycle()
    when (val current = stage) {
        IdentifyStage.Camera -> CameraGate(model, knowledge, onBack)
        IdentifyStage.Analysing -> Analysing(model)
        is IdentifyStage.Results -> Results(model, current.photoId, views, knowledge, settings, onBack, onOpenPlant)
        IdentifyStage.NoneFound -> Problem(
            title = stringResource(R.string.identify_none_title),
            body = stringResource(R.string.identify_none_body),
            onBack = onBack,
            onTryAgain = model::takeAnother,
            onAddByName = onAddByName,
        )
        IdentifyStage.Failed -> Problem(
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
private fun CameraGate(model: IdentifyModel, knowledge: KnowledgeRepository, onBack: () -> Unit) {
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
        Viewfinder(model, knowledge, onBack)
    } else {
        DetailScaffold(title = stringResource(R.string.identify_title), onBack = onBack) { padding ->
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
private fun Viewfinder(model: IdentifyModel, knowledge: KnowledgeRepository, onBack: () -> Unit) {
    val live by model.live.collectAsStateWithLifecycle()
    val capturing by model.capturing.collectAsStateWithLifecycle()
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
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
            FilledTonalIconButton(onClick = onBack, colors = IconButtonDefaults.filledTonalIconButtonColors()) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
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
            LiveSuggestions(live, kb)
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

/** Up to three suggestions for what the camera sees, or a request to point it at a plant. */
@Composable
private fun LiveSuggestions(live: List<Candidate>, kb: KnowledgeBase?) {
    val pill = Modifier.clip(CircleShape)
    if (live.isEmpty()) {
        Surface(color = Color.Black.copy(alpha = 0.6f), contentColor = Color.White, modifier = pill) {
            Text(stringResource(R.string.identify_point), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        live.forEachIndexed { i, candidate ->
            val name = candidate.speciesName?.let { kb?.species(it)?.displayName } ?: candidate.label
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
    photoId: String,
    views: GardenViews,
    knowledge: KnowledgeRepository,
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenPlant: (String) -> Unit,
) {
    // Collected without the lifecycle: under Robolectric, collectAsStateWithLifecycle now and then missed this flow's
    // first value, and an idle database query costs nothing while the app is in the background.
    val result by model.results.collectAsStateWithLifecycle()
    val kb by knowledge.knowledge.collectAsStateWithLifecycle()
    val zones by views.zones.collectAsStateWithLifecycle(initialValue = emptyList())
    val plants by views.plants.collectAsStateWithLifecycle(initialValue = emptyList())
    val state by remember { settings.settings.map { it.gardenState } }.collectAsStateWithLifecycle(initialValue = null)
    // Plants in one photo usually grow together, so a zone chosen for one is offered for the others.
    var photoZone by rememberSaveable(photoId) { mutableStateOf<String?>(null) }
    var choices by rememberSaveable(photoId, stateSaver = ChoicesSaver) { mutableStateOf(emptyMap<String, ReviewChoice>()) }
    var choosingFor by rememberSaveable(photoId) { mutableStateOf<String?>(null) }
    val callbacks = remember(model) {
        ReviewCallbacks(
            onAdd = model::add,
            onAttach = model::attach,
            onReject = model::reject,
            onUndoReject = model::undoReject,
            onChooseAnother = { choosingFor = it.id },
            onOpenPlant = onOpenPlant,
        )
    }

    val searchingFor = choosingFor
    val loadedKb = kb
    if (searchingFor != null && loadedKb != null) {
        BackHandler { choosingFor = null }
        SpeciesSearch(loadedKb, state, onBack = { choosingFor = null }) { name ->
            choices = choices + (searchingFor to ReviewChoice.Picked(name))
            choosingFor = null
        }
        return
    }

    DetailScaffold(title = stringResource(R.string.identify_title), onBack = onBack) { padding ->
        val loaded = result
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { PollenLoadingIndicator() }
            return@DetailScaffold
        }
        val detections = loaded.detections
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MarkedPhoto(loaded)
                    Text(
                        pluralStringResource(R.plurals.identify_found, detections.size, detections.size),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.identify_model_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(detections, key = { _, d -> d.id }) { i, detection ->
                DetectionReviewCard(
                    detection = detection,
                    number = i + 1,
                    total = detections.size,
                    kb = kb,
                    state = state,
                    zones = zones,
                    plants = plants,
                    choice = choices[detection.id],
                    onChoose = { choices = choices + (detection.id to it) },
                    suggestedZoneId = photoZone,
                    onZoneChosen = { photoZone = it },
                    callbacks = callbacks,
                    modifier = Modifier.readable(),
                )
            }
            item {
                Row(Modifier.readable(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = model::takeAnother) { Text(stringResource(R.string.identify_try_again)) }
                    Button(onClick = onBack) { Text(stringResource(R.string.review_done)) }
                }
            }
        }
    }
}

/** Keeps each card's choice as text, so the choices survive the phone being turned. */
private val ChoicesSaver = listSaver<Map<String, ReviewChoice>, String>(
    save = { map -> map.flatMap { (id, choice) -> listOf(id, choice.save()) } },
    restore = { list -> list.chunked(2).mapNotNull { (id, text) -> ReviewChoice.restore(text)?.let { id to it } }.toMap() },
)

/** Keeps content to a readable width on wide screens. */
private fun Modifier.readable() = fillMaxWidth().wrapContentWidth().widthIn(max = 640.dp)

/** The photo, with a numbered marker in the middle of each plant found, matching the numbers on the cards. */
@Composable
private fun MarkedPhoto(result: ResultPhoto) {
    val context = LocalContext.current
    val file = remember(result.path) { File(context.filesDir, result.path) }
    val ratio = if (result.height > 0) result.width.toFloat() / result.height else 4f / 3f
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        AsyncImage(model = file, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        if (result.detections.size > 1) {
            val marker = 32.dp
            result.detections.forEachIndexed { i, detection ->
                val x = maxWidth * ((detection.box.left + detection.box.right) / 2f / BoxBp.FULL) - marker / 2
                val y = maxHeight * ((detection.box.top + detection.box.bottom) / 2f / BoxBp.FULL) - marker / 2
                val number = (i + 1).toString()
                // An outline keeps each number clear of the leaves behind it.
                Box(
                    Modifier.offset(x, y).size(marker).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clearAndSetSemantics { contentDescription = number },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** No plant was found, or the photo could not be identified: what happened and what the user can do next. */
@Composable
private fun Problem(title: String, body: String, onBack: () -> Unit, onTryAgain: () -> Unit, onAddByName: () -> Unit) {
    DetailScaffold(title = stringResource(R.string.identify_title), onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyState(R.drawable.ic_potted_plant, title, body)
            Button(onClick = onTryAgain) { Text(stringResource(R.string.identify_try_again)) }
            OutlinedButton(onClick = onAddByName) { Text(stringResource(R.string.add_plant_by_name)) }
        }
    }
}
