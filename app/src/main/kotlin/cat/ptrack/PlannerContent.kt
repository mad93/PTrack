package cat.ptrack

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import cat.ptrack.ElectricRoutePlanner.Priority
import cat.ptrack.PlannerPlaces.Place
import kotlin.math.roundToInt

internal enum class PlannerEndpoint { ORIGIN, DESTINATION }

internal data class PlaceSuggestions(
    val endpoint: PlannerEndpoint,
    val matches: List<Place>,
    val message: String,
    val offerIcgc: Boolean = false
)

internal data class PlannerFormState(
    val origin: String,
    val destination: String,
    val originError: String?,
    val destinationError: String?,
    val suggestions: PlaceSuggestions?,
    val focusRequest: PlannerEndpoint?,
    val dismissInput: Int,
    val vehicle: String,
    val batterySource: String,
    val picking: Boolean,
    val locating: Boolean,
    val expanded: Boolean,
    val soc: Int,
    val reserve: Int,
    val minimum: Int,
    val priority: Priority,
    val departure: String,
    val canCalculate: Boolean,
    val busy: Boolean,
    val status: String
)

internal sealed interface PlannerAction {
    data class Edit(val endpoint: PlannerEndpoint, val text: String) : PlannerAction
    data class Focus(val endpoint: PlannerEndpoint, val focused: Boolean) : PlannerAction
    data class Search(val endpoint: PlannerEndpoint, val icgc: Boolean = false) : PlannerAction
    data class Choose(val endpoint: PlannerEndpoint, val place: Place) : PlannerAction
    data class Battery(val percent: Int) : PlannerAction
    data class Reserve(val percent: Int) : PlannerAction
    data class Power(val kw: Int) : PlannerAction
    data class Prefer(val priority: Priority) : PlannerAction
    data object AcknowledgeFocus : PlannerAction
    data object Swap : PlannerAction
    data object Location : PlannerAction
    data object Pick : PlannerAction
    data object ToggleOptions : PlannerAction
    data object ChooseDeparture : PlannerAction
    data object ResetDeparture : PlannerAction
    data object Calculate : PlannerAction
}

@Composable
internal fun PlannerLayout(
    form: @Composable () -> Unit,
    map: @Composable (Modifier) -> Unit,
    results: @Composable () -> Unit,
    saved: @Composable () -> Unit,
    revealMap: Boolean = false
) {
    val widthClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val currentForm = rememberUpdatedState(form)
    val currentMap = rememberUpdatedState(map)
    val currentResults = rememberUpdatedState(results)
    val currentSaved = rememberUpdatedState(saved)
    val formSlot = remember { movableContentOf { currentForm.value() } }
    val mapSlot = remember { movableContentOf { modifier: Modifier -> currentMap.value(modifier) } }
    val resultsSlot = remember { movableContentOf { currentResults.value(); currentSaved.value() } }
    val scroll = rememberScrollState()
    val mapPosition = remember { BringIntoViewRequester() }
    LaunchedEffect(revealMap) {
        if (revealMap) mapPosition.bringIntoView()
    }

    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp)) {
        if (widthClass.isWidthAtLeastBreakpoint(840) && maxWidth >= 700.dp && maxHeight >= 280.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                mapSlot(Modifier.weight(1f).fillMaxHeight().bringIntoViewRequester(mapPosition))
                Column(
                    Modifier.weight(0.9f).fillMaxHeight().verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    formSlot()
                    resultsSlot()
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                formSlot()
                mapSlot(Modifier.fillMaxWidth().height(340.dp).bringIntoViewRequester(mapPosition))
                resultsSlot()
            }
        }
    }
}

@Composable
internal fun PlannerForm(state: PlannerFormState, labels: Labels, onAction: (PlannerAction) -> Unit) {
    val focusManager = LocalFocusManager.current
    val originFocus = remember { FocusRequester() }
    val destinationFocus = remember { FocusRequester() }
    LaunchedEffect(state.dismissInput) { if (state.dismissInput > 0) focusManager.clearFocus(force = true) }
    LaunchedEffect(state.focusRequest) {
        state.focusRequest?.let {
            (if (it == PlannerEndpoint.ORIGIN) originFocus else destinationFocus).requestFocus()
            onAction(PlannerAction.AcknowledgeFocus)
        }
    }

    PlannerPanel {
        Text(Destination.PLANNER.label(labels), style = MaterialTheme.typography.headlineSmall)
        if (state.vehicle.isNotEmpty()) Text(state.vehicle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EndpointField(PlannerEndpoint.ORIGIN, state.origin, state.originError, state.suggestions, originFocus, labels, onAction)
        EndpointField(PlannerEndpoint.DESTINATION, state.destination, state.destinationError, state.suggestions,
            destinationFocus, labels, onAction)

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onAction(PlannerAction.Swap) }) {
                Text(labels.t("Intercanvia", "Intercambia", "Swap endpoints"))
            }
            TextButton(enabled = !state.locating, onClick = { onAction(PlannerAction.Location) }) {
                Text(if (state.locating) labels.t("Localitzant…", "Localizando…", "Locating…")
                    else labels.t("La meva ubicació", "Mi ubicación", "My location"))
            }
            FilterChip(selected = state.picking, onClick = { onAction(PlannerAction.Pick) },
                label = { Text(labels.t("Tria al mapa", "Elige en el mapa", "Pick on map")) })
        }

        Text(state.batterySource, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onAction(PlannerAction.ToggleOptions) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.expanded) labels.t("Amaga les opcions", "Oculta las opciones", "Hide options")
                else labels.t("Opcions de viatge", "Opciones de viaje", "Trip options"))
        }
        if (state.expanded) {
            PlannerOptions(state, labels, onAction)
        }
        Button(onClick = { onAction(PlannerAction.Calculate) }, enabled = state.canCalculate,
            modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) labels.t("Calculant…", "Calculando…", "Calculating…")
                else labels.t("Calcula les rutes", "Calcula las rutas", "Find routes"))
        }
        if (state.status.isNotEmpty()) Text(state.status, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EndpointField(
    endpoint: PlannerEndpoint,
    value: String,
    error: String?,
    suggestions: PlaceSuggestions?,
    focusRequester: FocusRequester,
    labels: Labels,
    onAction: (PlannerAction) -> Unit
) {
    val title = if (endpoint == PlannerEndpoint.ORIGIN) labels.t("Origen", "Origen", "Origin")
        else labels.t("Destí", "Destino", "Destination")
    Column(Modifier.onFocusChanged { onAction(PlannerAction.Focus(endpoint, it.hasFocus)) }.focusGroup()) {
        OutlinedTextField(
            value = value,
            onValueChange = { onAction(PlannerAction.Edit(endpoint, it)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            label = { Text(title) },
            placeholder = { Text(labels.t("Carrer, poble o masia", "Calle, pueblo o masía", "Street, town or farm")) },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAction(PlannerAction.Search(endpoint)) })
        )
        if (suggestions?.endpoint == endpoint) {
            val colors = MaterialTheme.colorScheme
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHigh) {
                Column {
                    if (suggestions.matches.isEmpty()) {
                        Text(suggestions.message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                    suggestions.matches.forEachIndexed { index, place ->
                        if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                        Text(
                            place.name,
                            Modifier.fillMaxWidth().clickable { onAction(PlannerAction.Choose(endpoint, place)) }.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (suggestions.offerIcgc) {
                        TextButton(onClick = { onAction(PlannerAction.Search(endpoint, icgc = true)) }) {
                            Text(labels.t("Cerca a Catalunya (ICGC)", "Busca en Cataluña (ICGC)", "Search Catalonia (ICGC)"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerOptions(state: PlannerFormState, labels: Labels, onAction: (PlannerAction) -> Unit) {
    PlannerSlider(labels.t("Bateria de sortida", "Batería de salida", "Starting battery"),
        state.soc, "%", 100) { onAction(PlannerAction.Battery(it)) }
    PlannerSlider(labels.t("Bateria mínima d’arribada", "Batería mínima de llegada", "Minimum arrival battery"),
        state.reserve, "%", 99) { onAction(PlannerAction.Reserve(it)) }
    PlannerSlider(labels.t("Potència mínima CCS", "Potencia mínima CCS", "Minimum CCS power"),
        state.minimum, " kW", 1000) { onAction(PlannerAction.Power(it)) }
    Text(labels.t("Prioritat de viatge", "Prioridad de viaje", "Trip priority"),
        style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Priority.entries.forEach { priority ->
            val label = if (priority.ordinal == 0) labels.t("Arribar abans", "Llegar antes", "Arrive sooner")
                else labels.t("Carregar menys temps", "Cargar menos tiempo", "Spend less time charging")
            FilterChip(state.priority == priority, onClick = { onAction(PlannerAction.Prefer(priority)) },
                label = { Text(label) })
        }
    }
    PlannerActions(
        state.departure, { onAction(PlannerAction.ChooseDeparture) },
        labels.t("Ara", "Ahora", "Now"), { onAction(PlannerAction.ResetDeparture) }
    )
}

@Composable
private fun PlannerSlider(title: String, value: Int, unit: String, max: Int, onChange: (Int) -> Unit) {
    Text("$title · $value$unit", style = MaterialTheme.typography.labelLarge)
    Slider(value.toFloat(), { onChange(it.roundToInt()) },
        modifier = Modifier.semantics { contentDescription = title },
        valueRange = 0f..max.toFloat())
}

@Composable
internal fun PlannerPanel(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

@Composable
internal fun PlannerActions(
    first: String, onFirst: () -> Unit, second: String, onSecond: () -> Unit, enabled: Boolean = true
) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
        OutlinedButton(onClick = onFirst, enabled = enabled, modifier = Modifier.weight(1f)) { Text(first) }
        OutlinedButton(onClick = onSecond, enabled = enabled, modifier = Modifier.weight(1f)) { Text(second) }
    }
}

@Composable
internal fun RouteHighlights(distance: String, duration: String, arrival: String, labels: Labels) {
    val metrics = listOf(
        labels.t("Arribada", "Llegada", "Arrival") to arrival,
        labels.t("Temps total", "Tiempo total", "Total time") to duration,
        labels.t("Distància", "Distancia", "Distance") to distance
    )
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 3) {
        metrics.forEach { (name, value) ->
            Surface(Modifier.weight(1f), color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(8.dp)) {
                    Text(name, style = MaterialTheme.typography.labelMedium)
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
internal fun SavedPlannerItinerary(
    plan: NavigationPlan?,
    labels: Labels,
    onEdit: () -> Unit,
    onOpen: (Boolean) -> Unit,
    onCharger: (String, String) -> Unit,
    onAdvance: () -> Unit
) {
    PlannerPanel {
        Text(labels.t("Itinerari desat", "Itinerario guardado", "Saved itinerary"),
            style = MaterialTheme.typography.titleLarge)
        val point = plan?.next
        if (plan == null || point == null) {
            Text(if (plan == null) labels.t("Afegeix les parades i el destí del viatge.",
                "Añade las paradas y el destino del viaje.", "Add the stops and destination for your trip.")
            else labels.t("Itinerari completat.", "Itinerario completado.", "Itinerary complete."))
            OutlinedButton(onClick = onEdit) { Text(labels.t("Nou itinerari", "Nuevo itinerario", "New itinerary")) }
        } else {
            val heading = if (plan.remaining.size == 1) labels.t("Destí", "Destino", "Destination")
                else labels.t("Pròxima parada", "Próxima parada", "Next stop")
            Text("$heading · ${plan.completed + 1}/${plan.points.size}", style = MaterialTheme.typography.labelLarge)
            Text(plan.label(point), style = MaterialTheme.typography.titleMedium)
            PlannerActions("Google Maps", { onOpen(true) }, "Waze", { onOpen(false) })
            SavedCharger(plan, point, labels, onCharger)
            TextButton(onClick = onAdvance) {
                Text(labels.t("Marca el punt com a completat", "Marcar el punto como completado", "Mark point complete"))
            }
            TextButton(onClick = onEdit) {
                Text(labels.t("Edita els punts pendents", "Editar los puntos pendientes", "Edit remaining points"))
            }
            plan.remaining.drop(1).forEachIndexed { index, stop ->
                Text("${plan.completed + index + 2}. ${plan.label(stop)}")
                SavedCharger(plan, stop, labels, onCharger)
            }
        }
    }
}

@Composable
private fun SavedCharger(
    plan: NavigationPlan, point: String, labels: Labels, onCharger: (String, String) -> Unit
) {
    val name = plan.chargerNames[point] ?: return
    TextButton(onClick = { onCharger(point, name) }) {
        Text(labels.t("Cerca el carregador a Google Maps", "Busca el cargador en Google Maps",
            "Find charger in Google Maps"))
    }
}
