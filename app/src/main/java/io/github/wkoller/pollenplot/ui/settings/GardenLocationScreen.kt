package io.github.wkoller.pollenplot.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.wkoller.pollenplot.R
import io.github.wkoller.pollenplot.data.prefs.Settings
import io.github.wkoller.pollenplot.data.prefs.SettingsStore
import io.github.wkoller.pollenplot.domain.place.GardenPlace
import io.github.wkoller.pollenplot.domain.place.PlaceMatch
import io.github.wkoller.pollenplot.domain.place.Places
import io.github.wkoller.pollenplot.domain.plants.AuState
import io.github.wkoller.pollenplot.place.LocationSource
import io.github.wkoller.pollenplot.place.PlaceFinder
import io.github.wkoller.pollenplot.ui.components.DetailScaffold
import io.github.wkoller.pollenplot.ui.components.PollenLoadingIndicator
import io.github.wkoller.pollenplot.ui.plants.label
import io.github.wkoller.pollenplot.ui.settings.GardenLocationModel.Search
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Finds and saves the garden's place: by searching for a suburb or postcode, or from the phone's location. */
class GardenLocationModel(private val places: PlaceFinder, private val location: LocationSource, private val settings: SettingsStore) : ViewModel() {
    sealed interface Search {
        data object Idle : Search

        data object Searching : Search

        data class Found(val query: String, val places: List<PlaceMatch>) : Search

        data object Failed : Search
    }

    enum class Busy { FINDING, SAVING }

    enum class Problem { NO_LOCATION, OUTSIDE_AUSTRALIA, NO_PERMISSION }

    private val _search = MutableStateFlow<Search>(Search.Idle)
    val search: StateFlow<Search> = _search.asStateFlow()
    private val _busy = MutableStateFlow<Busy?>(null)
    val busy: StateFlow<Busy?> = _busy.asStateFlow()
    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()
    private var searching: Job? = null

    fun search(query: String) {
        val words = query.trim()
        if (words.length < 2) return
        _problem.value = null
        searching?.cancel()
        searching = viewModelScope.launch {
            _search.value = Search.Searching
            _search.value = try {
                Search.Found(words, places.search(words))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Search.Failed
            }
        }
    }

    fun choose(match: PlaceMatch) = save(match.latitude, match.longitude, match.name, match.state)

    fun useThisPhone() {
        _problem.value = null
        viewModelScope.launch {
            _busy.value = Busy.FINDING
            val point = location.current()
            _busy.value = null
            when {
                point == null -> _problem.value = Problem.NO_LOCATION
                !Places.inAustralia(point.first, point.second) -> _problem.value = Problem.OUTSIDE_AUSTRALIA
                else -> save(point.first, point.second, name = null, state = null)
            }
        }
    }

    fun permissionDenied() {
        _problem.value = Problem.NO_PERMISSION
    }

    /** The garden's state alone, without a place: local natives and state rules, but no council list or nearby records. */
    fun chooseState(state: AuState?) {
        viewModelScope.launch { settings.update { it.copy(gardenState = state, gardenPlace = null) } }
    }

    fun remove() {
        viewModelScope.launch { settings.update { it.copy(gardenPlace = null) } }
    }

    private fun save(latitude: Double, longitude: Double, name: String?, state: AuState?) {
        viewModelScope.launch {
            _busy.value = Busy.SAVING
            val place = GardenPlace.at(latitude, longitude, name)
            // Without a connection the place is saved anyway, and the next weed update looks up its council area.
            val area = try {
                places.area(place)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            settings.update { it.copy(gardenPlace = place.copy(council = area?.council), gardenState = area?.state ?: state ?: it.gardenState) }
            _busy.value = null
            _search.value = Search.Idle
        }
    }
}

/** Settings › Garden location. */
@Composable
fun GardenLocationScreen(model: GardenLocationModel, settings: SettingsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    val current by settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val search by model.search.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val problem by model.problem.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var choosingState by rememberSaveable { mutableStateOf(false) }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.useThisPhone() else model.permissionDenied()
    }
    val usePhone = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            model.useThisPhone()
        } else {
            askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    DetailScaffold(title = stringResource(R.string.location_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(stringResource(R.string.location_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
            item { SectionHeading(stringResource(R.string.location_current)) }
            item { CurrentPlace(current, onRemove = model::remove) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeading(stringResource(R.string.location_search_heading)) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.location_search)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { model.search(query) }),
                    trailingIcon = {
                        IconButton(onClick = { model.search(query) }) {
                            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.location_search_action))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            when (val shown = search) {
                Search.Idle -> Unit
                Search.Searching -> item { Working(stringResource(R.string.location_searching)) }
                Search.Failed -> item { Notice(stringResource(R.string.location_search_failed)) }
                is Search.Found -> if (shown.places.isEmpty()) {
                    item { Notice(stringResource(R.string.location_no_results, shown.query)) }
                } else {
                    items(shown.places) { match -> PlaceRow(match, enabled = busy == null) { model.choose(match) } }
                }
            }
            item {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (busy) {
                        GardenLocationModel.Busy.FINDING -> Working(stringResource(R.string.location_finding))
                        GardenLocationModel.Busy.SAVING -> Working(stringResource(R.string.location_saving))
                        null -> OutlinedButton(onClick = usePhone) {
                            Icon(painterResource(R.drawable.ic_my_location), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.location_use_phone), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    problem?.let { found ->
                        Text(
                            stringResource(
                                when (found) {
                                    GardenLocationModel.Problem.NO_LOCATION -> R.string.location_phone_failed
                                    GardenLocationModel.Problem.OUTSIDE_AUSTRALIA -> R.string.location_outside
                                    GardenLocationModel.Problem.NO_PERMISSION -> R.string.location_permission_denied
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                }
            }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item { SectionHeading(stringResource(R.string.location_other)) }
            item {
                ListItem(
                    onClick = { choosingState = true },
                    leadingContent = { Icon(painterResource(R.drawable.ic_map), contentDescription = null) },
                    supportingContent = { Text(stringResource(current.gardenState.label) + ". " + stringResource(R.string.location_state_only_body)) },
                ) { Text(stringResource(R.string.location_state_only)) }
            }
        }
    }
    if (choosingState) {
        StateDialog(current = current.gardenState, onDismiss = { choosingState = false }) { state ->
            choosingState = false
            model.chooseState(state)
        }
    }
}

/** The garden's place as the user would describe it: its name, or else its council area, or else the point. */
@Composable
fun placeTitle(place: GardenPlace): String = place.name
    ?: place.council?.let { stringResource(R.string.location_near, Places.councilName(it)) }
    ?: stringResource(R.string.location_point, place.latText, place.lonText)

@Composable
private fun CurrentPlace(settings: Settings, onRemove: () -> Unit) {
    val place = settings.gardenPlace
    if (place == null) {
        Text(stringResource(R.string.location_none), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        return
    }
    ListItem(
        leadingContent = { Icon(painterResource(R.drawable.ic_location_on), contentDescription = null) },
        supportingContent = {
            Text(
                listOfNotNull(
                    place.council?.let { stringResource(R.string.location_council, Places.councilName(it)) },
                    settings.gardenState?.let { stringResource(it.label) },
                    stringResource(R.string.location_rounded),
                ).joinToString(" · ") + if (place.council == null) "\n" + stringResource(R.string.location_council_unknown) else "",
            )
        },
        trailingContent = { TextButton(onClick = onRemove) { Text(stringResource(R.string.location_remove)) } },
    ) { Text(placeTitle(place)) }
}

@Composable
private fun PlaceRow(match: PlaceMatch, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        enabled = enabled,
        leadingContent = { Icon(painterResource(R.drawable.ic_location_on), contentDescription = null) },
        supportingContent = {
            Text(listOfNotNull(match.district, match.state?.let { stringResource(it.label) }, match.postcodes.joinToString(", ").takeIf { it.isNotEmpty() }).joinToString(" · "))
        },
    ) { Text(match.name) }
}

@Composable
private fun Working(text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PollenLoadingIndicator(Modifier.size(32.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
    )
}
