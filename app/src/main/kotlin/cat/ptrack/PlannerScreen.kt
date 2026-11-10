package cat.ptrack

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import cat.ptrack.ElectricRoutePlanner.Plan
import cat.ptrack.ElectricRoutePlanner.Priority
import cat.ptrack.ElectricRoutePlanner.Stop
import cat.ptrack.PlannerPlaces.Place
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.Future

internal class PlannerScreen(
    private val activity: Activity,
    private val labels: Labels,
    private val importMap: () -> Unit,
    private val locate: () -> Unit,
    private val navigate: (NavigationPlan, Boolean) -> Unit,
    private val showCharger: (String, String) -> Unit
) {
    private val context = activity.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val routing = Executors.newSingleThreadExecutor()
    private val searching = Executors.newSingleThreadExecutor()
    private var lookup: Future<*>? = null
    private var lookupRequest: Runnable? = null
    private var lookupId = 0
    private var job: Future<*>? = null
    private var calculation = 0
    private var disposed = false
    private var busy by mutableStateOf(false)
    private var installed by mutableStateOf(false)
    private var importing by mutableStateOf(false)
    private var manualSoc = false
    private var activeVehicle by mutableStateOf<VehicleProfile?>(null)
    private var departureMs by mutableStateOf<Long?>(null)
    private var originText by mutableStateOf("")
    private var destinationText by mutableStateOf("")
    private var originError by mutableStateOf<String?>(null)
    private var destinationError by mutableStateOf<String?>(null)
    private var originPlace: Place? = null
    private var destinationPlace: Place? = null
    private var focused: PlannerEndpoint? = null
    private var focusRequest by mutableStateOf<PlannerEndpoint?>(null)
    private var dismissInput by mutableStateOf(0)
    private var suggestions by mutableStateOf<PlaceSuggestions?>(null)
    private var status by mutableStateOf("")
    private var batterySource by mutableStateOf("")
    private var optionsExpanded by mutableStateOf(false)
    private var soc by mutableStateOf(80)
    private var reserve by mutableStateOf(10)
    private var minimum by mutableStateOf(50)
    private var priority by mutableStateOf(Priority.entries.first())
    var locationRequest by mutableStateOf<CancellationSignal?>(null)
        private set
    private var placeTarget = PlannerEndpoint.ORIGIN
    private var picking by mutableStateOf(false)
    private val map = PlannerMap(activity, ::select, ::selectStop) { point ->
        setPlace(placeTarget, Place(t("Punt al mapa", "Punto en el mapa", "Point on map"), point))
    }
    val mapView get() = map.mapView

    private var plans by mutableStateOf(emptyList<Plan>())
    private var trip by mutableStateOf<ValhallaPlanner.Trip?>(null)
    private var plannedVehicle by mutableStateOf<PlanningEnergy?>(null)
    private var selected by mutableStateOf(0)
    private var stopSelected by mutableStateOf<Int?>(null)
    private var excluded = emptySet<String>()
    private var reducedMargin by mutableStateOf(false)
    private data class SkipPreview(val plan: Plan, val stop: Stop, val arrivalKwh: Double)
    private data class Previous(val plans: List<Plan>, val selected: Int, val excluded: Set<String>, val reducedMargin: Boolean)
    private var proposedSkip by mutableStateOf<SkipPreview?>(null)
    private var previous by mutableStateOf<Previous?>(null)

    @Composable
    fun Content(saved: @Composable () -> Unit) {
        PlannerLayout(
            form = {
                PlannerForm(
                    PlannerFormState(originText, destinationText, originError, destinationError, suggestions,
                        focusRequest, dismissInput, activeVehicle?.label.orEmpty(), batterySource, picking,
                        locationRequest != null, optionsExpanded, soc, reserve, minimum, priority,
                        departureLabel(), canCalculate(), busy, status.takeIf { plans.isEmpty() }.orEmpty()),
                    labels, ::onAction
                )
            },
            map = { modifier ->
                Box(modifier) {
                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                addView(map, FrameLayout.LayoutParams(-1, -1))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { it.removeView(map) },
                        update = {}
                    )
                    if (picking) {
                        Surface(Modifier.align(Alignment.TopCenter).padding(10.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(pickingHint(), Modifier.padding(10.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            },
            results = { Results() },
            saved = saved,
            revealMap = picking
        )
    }

    fun mapStatus(available: Boolean, working: Boolean) {
        installed = available
        importing = working
        if (trip == null && !busy) refreshStartingBattery()
    }

    private fun canCalculate() = installed && !importing && !busy && activeVehicle != null

    private fun onAction(action: PlannerAction) {
        when (action) {
            is PlannerAction.Edit -> edit(action.endpoint, action.text)
            is PlannerAction.Focus -> focus(action.endpoint, action.focused)
            is PlannerAction.Search -> if (place(action.endpoint) == null) {
                suggest(action.endpoint, immediate = true, inCatalonia = action.icgc)
            }
            is PlannerAction.Choose -> setPlace(action.endpoint, action.place)
            is PlannerAction.Battery -> if (soc != action.percent) {
                soc = action.percent
                manualSoc = true
                batterySource = t("Bateria inicial · entrada manual", "Batería inicial · entrada manual", "Starting battery · manual entry")
                invalidateOptions()
            }
            is PlannerAction.Reserve -> if (reserve != action.percent) {
                reserve = action.percent
                invalidateOptions()
            }
            is PlannerAction.Power -> if (minimum != action.kw) {
                minimum = action.kw
                invalidateOptions()
            }
            is PlannerAction.Prefer -> if (priority != action.priority) {
                priority = action.priority
                if (busy || trip != null) invalidateRoutes()
            }
            PlannerAction.AcknowledgeFocus -> focusRequest = null
            PlannerAction.Swap -> swapPlaces()
            PlannerAction.Location -> requestLocation()
            PlannerAction.Pick -> togglePicking()
            PlannerAction.ToggleOptions -> optionsExpanded = !optionsExpanded
            PlannerAction.ChooseDeparture -> chooseDeparture()
            PlannerAction.ResetDeparture -> setDeparture(null)
            PlannerAction.Calculate -> calculate()
        }
    }

    private fun invalidateOptions() {
        if (busy || trip != null || status.isNotEmpty()) invalidateRoutes()
    }

    private fun togglePicking() {
        if (focused == null) placeTarget = if (originPlace == null) PlannerEndpoint.ORIGIN else PlannerEndpoint.DESTINATION
        picking = !picking
        map.setPicking(picking)
        if (picking) {
            clearInput()
        }
    }

    private fun pickingHint(): String =
        if (placeTarget == PlannerEndpoint.ORIGIN)
            t("Toca l’origen al mapa.", "Toca el origen en el mapa.", "Tap the origin on the map.")
        else t("Toca el destí al mapa.", "Toca el destino en el mapa.", "Tap the destination on the map.")

    fun vehicleChanged(vehicle: VehicleProfile) {
        val previous = activeVehicle
        activeVehicle = vehicle
        if (previous?.id == vehicle.id && previous.capacityKwh == vehicle.capacityKwh &&
            previous.maxDcChargeKw == vehicle.maxDcChargeKw) return
        val hadRoute = trip != null || busy
        if (previous?.id != vehicle.id) manualSoc = false
        invalidateRoutes()
        refreshStartingBattery()
        if (hadRoute) status = t("El perfil ha canviat. Torna a calcular la ruta.", "El perfil ha cambiado. Vuelve a calcular la ruta.",
            "The profile changed. Recalculate the route.")
    }

    private fun requestLocation() {
        locationRequest = CancellationSignal()
        locate()
    }

    private fun cancelLocation() {
        val request = locationRequest ?: return
        locationRequest = null
        request.cancel()
    }

    fun leave() {
        cancelLocation()
        clearInput()
    }

    fun location(request: CancellationSignal, place: Place?) {
        if (locationRequest !== request) return
        cancelLocation()
        if (place == null) status = t("Ubicació indisponible. Tria un punt al mapa.",
            "Ubicación no disponible. Elige un punto en el mapa.", "Location unavailable. Pick a point on the map.")
        else setPlace(PlannerEndpoint.ORIGIN, place)
    }

    fun close() {
        disposed = true
        cancelLocation()
        hideSuggestions()
        routing.shutdownNow()
        searching.shutdownNow()
        mapView.onDestroy()
    }

    private fun text(endpoint: PlannerEndpoint) =
        if (endpoint == PlannerEndpoint.ORIGIN) originText else destinationText

    private fun place(endpoint: PlannerEndpoint) =
        if (endpoint == PlannerEndpoint.ORIGIN) originPlace else destinationPlace

    private fun focus(endpoint: PlannerEndpoint, hasFocus: Boolean) {
        if (hasFocus) {
            focused = endpoint
            placeTarget = endpoint
            if (!picking && place(endpoint) == null) suggest(endpoint)
        } else if (focused == endpoint) {
            focused = null
            hideSuggestions()
        }
    }

    private fun edit(endpoint: PlannerEndpoint, value: String) {
        if (endpoint == PlannerEndpoint.ORIGIN) {
            cancelLocation()
            originText = value
            originPlace = null
            originError = null
        } else {
            destinationText = value
            destinationPlace = null
            destinationError = null
        }
        invalidateRoutes()
        if (focused == endpoint) suggest(endpoint)
    }

    private fun setPlace(endpoint: PlannerEndpoint, value: Place) {
        if (endpoint == PlannerEndpoint.ORIGIN) {
            cancelLocation()
            originPlace = value
            originText = value.name
            originError = null
        } else {
            destinationPlace = value
            destinationText = value.name
            destinationError = null
        }
        endpointsChanged()
    }

    private fun swapPlaces() {
        cancelLocation()
        val previousOrigin = originPlace
        originPlace = destinationPlace
        destinationPlace = previousOrigin
        val previousText = originText
        originText = destinationText
        destinationText = previousText
        originError = null
        destinationError = null
        endpointsChanged()
    }

    private fun endpointsChanged() {
        picking = false
        map.setPicking(false)
        clearInput()
        invalidateRoutes(fit = true)
    }

    private fun suggest(endpoint: PlannerEndpoint, immediate: Boolean = false, inCatalonia: Boolean = false) {
        hideSuggestions()
        val query = text(endpoint).trim()
        if (query.length < 3 || PlannerPlaces.coordinates(query) != null) return
        val id = lookupId
        val request = Runnable {
            if (disposed || focused != endpoint) return@Runnable
            suggestions = PlaceSuggestions(endpoint, emptyList(),
                if (inCatalonia) t("Cercant a l’ICGC…", "Buscando en el ICGC…", "Searching ICGC…")
                else t("Cercant…", "Buscando…", "Searching…"))
            lookup = searching.submit {
                val response = runCatching { PlannerPlaces.search(query, labels.locale.language, inCatalonia) }
                handler.post {
                    if (disposed || id != lookupId || focused != endpoint) return@post
                    suggestions = response.fold(
                        { places -> PlaceSuggestions(endpoint, places,
                            t("Cap resultat. Prova d’afegir el municipi.",
                                "Sin resultados. Prueba a añadir el municipio.",
                                "No results. Try adding the town."), offerIcgc = !inCatalonia) },
                        { PlaceSuggestions(endpoint, emptyList(),
                            t("Cerca indisponible. Revisa la connexió o tria al mapa.",
                                "Búsqueda no disponible. Revisa la conexión o elige en el mapa.",
                                "Search unavailable. Check your connection or pick on the map."), offerIcgc = !inCatalonia) }
                    )
                }
            }
        }
        lookupRequest = request
        handler.postDelayed(request, if (immediate) 0L else 400L)
    }

    private fun hideSuggestions() {
        lookupId++
        lookupRequest?.let(handler::removeCallbacks)
        lookup?.cancel(true)
        suggestions = null
    }

    private fun clearInput() {
        focused = null
        focusRequest = null
        hideSuggestions()
        dismissInput++
    }

    private fun calculate() {
        if (!canCalculate()) return
        refreshStartingBattery()
        val from = (originPlace ?: PlannerPlaces.coordinates(originText))?.point
        val to = (destinationPlace ?: PlannerPlaces.coordinates(destinationText))?.point
        if (from == null || to == null) {
            val error = t("Tria un resultat de la cerca o un punt al mapa.",
                "Elige un resultado de la búsqueda o un punto en el mapa.",
                "Choose a search result or a point on the map.")
            if (from == null) {
                originError = error
                focusRequest = PlannerEndpoint.ORIGIN
            } else {
                destinationError = error
                focusRequest = PlannerEndpoint.DESTINATION
            }
            return
        }
        if (from == to) {
            destinationError = t("Tria un destí diferent de l’origen.",
                "Elige un destino distinto del origen.", "Choose an end point different from the start.")
            return
        }
        if (soc < reserve) {
            optionsExpanded = true
            status = t("La bateria de sortida ha de cobrir la reserva.",
                "La batería de salida debe cubrir la reserva.", "The starting battery must cover the reserve.")
            return
        }
        clearInput()
        optionsExpanded = false
        picking = false
        map.setPicking(false)
        val request = ValhallaPlanner.Trip(from, to, soc.toDouble(), reserve.toDouble(), minimum.toDouble(),
            priority, departureMs ?: System.currentTimeMillis())
        runPlanner(refreshVehicle = true, work = { vehicle to alternatives(request) }) { (vehicle, routes) ->
            if (routes.isEmpty()) noRoute() else {
                trip = request
                plannedVehicle = vehicle
                excluded = emptySet()
                reducedMargin = false
                previous = null
                showPlans(routes)
            }
        }
    }

    private fun searchReplacement(stop: Stop) {
        val request = trip ?: return
        val avoid = excluded + stop.id
        proposedSkip = null
        runPlanner(work = { alternatives(request, avoid) }) { routes ->
            if (routes.isEmpty()) noRoute() else {
                rememberSelection()
                excluded = avoid
                reducedMargin = false
                showPlans(routes)
            }
        }
    }

    private fun skip(plan: Plan, stop: Stop) {
        val request = trip ?: return
        proposedSkip = null
        runPlanner(work = { skip(request, plan, stop) }) { replacement ->
            when {
                replacement == null -> status = t("No es pot saltar la parada amb el marge disponible. Busca alternatives.",
                    "No se puede saltar la parada con el margen disponible. Busca alternativas.",
                    "This stop cannot be skipped with the available margin. Find alternatives.")
                replacement.second -> {
                    val index = plan.charges.indexOfFirst { it.stop.id == stop.id }
                    val arrival = replacement.first.charges.getOrNull(index)?.arrivalKwh ?: replacement.first.arrivalKwh
                    proposedSkip = SkipPreview(replacement.first, stop, arrival)
                }
                else -> acceptSkip(replacement.first, stop, false)
            }
        }
    }

    private fun acceptSkip(replacement: Plan, stop: Stop, reducedReserve: Boolean) {
        rememberSelection()
        excluded = excluded + stop.id
        reducedMargin = reducedReserve
        showPlans(listOf(replacement))
    }

    private fun rememberSelection() {
        previous = Previous(plans, selected, excluded, reducedMargin)
    }

    private fun undoChange() {
        val last = previous ?: return
        previous = null
        excluded = last.excluded
        reducedMargin = last.reducedMargin
        showPlans(last.plans, last.selected)
    }

    private fun <T> runPlanner(refreshVehicle: Boolean = false, work: ValhallaPlanner.() -> T, complete: (T) -> Unit) {
        val vehicle = checkNotNull(activeVehicle)
        val version = ++calculation
        val snapshot = plannedVehicle.takeUnless { refreshVehicle }
        busy = true
        status = t("Calculant amb el mapa local…", "Calculando con el mapa local…", "Calculating with the local map…")

        job = routing.submit {
            val result = runCatching {
                val (profile, chargeProfile) = Store(context).use {
                    (snapshot ?: ConsumptionRepository(it, vehicleId = vehicle.id).planningProfile(vehicle.capacityKwh)) to
                        it.dcChargeProfile(vehicle.id)
                }
                ChargingCatalog(context).use { catalog ->
                    OfflineValhalla.withEngine(context) { engine ->
                        ValhallaPlanner(engine, catalog, profile, chargeProfile, vehicle.maxDcChargeKw).work()
                    }
                }
            }
            handler.post {
                if (disposed || version != calculation) return@post
                busy = false
                status = ""

                result.fold(complete, { error ->
                    if (error is ConsumptionRepository.MissingVehicleCapacity) {
                        status = t("Configura la capacitat útil del vehicle en kWh o registra prou viatges per estimar-la.",
                            "Configura la capacidad útil del vehículo en kWh o registra suficientes viajes para estimarla.",
                            "Set the vehicle’s usable capacity in kWh or record enough trips to estimate it.")
                    } else {
                        Log.w("PTrackPlanner", "Could not calculate route", error)
                        status = t("Ha fallat el càlcul amb el mapa local.", "Falló el cálculo con el mapa local.",
                            "Routing with the local map failed.")
                    }
                })
            }
        }
    }

    private fun noRoute() {
        status = t("Cap ruta viable amb aquests punts i opcions.", "No hay rutas viables con estos puntos y opciones.",
            "No feasible routes with these points and options.")
    }

    private fun showPlans(routes: List<Plan>, selection: Int = 0) {
        plans = routes
        selected = selection
        stopSelected = null
        proposedSkip = null
        status = ""
        drawMap(fit = true)
    }

    private fun select(index: Int) {
        if (busy || index !in plans.indices) return
        selected = index
        stopSelected = null
        proposedSkip = null
        status = ""
        drawMap()
    }

    private fun selectStop(index: Int) {
        if (busy || index !in plans.getOrNull(selected)?.charges.orEmpty().indices) return
        stopSelected = if (stopSelected == index) null else index
        proposedSkip = null
        status = ""
    }

    @Composable
    private fun Results() {
        plans.getOrNull(selected)?.let { plan ->
            PlannerPanel {
                Text(t("Ruta proposada", "Ruta propuesta", "Suggested route"),
                    style = MaterialTheme.typography.titleLarge)
                if (status.isNotEmpty()) ResultNote(status)
                RouteHighlights(
                    "${labels.n(plan.legs.sumOf { it.km }, 0)} km",
                    minutes(plan.totalMinutes), percent(plan.arrivalKwh), labels
                )
                if (plans.size > 1) {
                    plans.forEachIndexed { index, option ->
                        val summary = "${labels.n(option.legs.sumOf { it.km }, 0)} km · ${minutes(option.totalMinutes)} · ${percent(option.arrivalKwh)}"
                        FilterChip(selected == index, onClick = { select(index) }, enabled = !busy,
                            label = { Text(t("Ruta", "Ruta", "Route") + " ${index + 1} · $summary") })
                    }
                }
                Text(t("Conducció ${minutes(plan.driveMinutes)} · càrrega ${minutes(plan.chargeMinutes)} · ${plan.charges.size} parades",
                    "Conducción ${minutes(plan.driveMinutes)} · carga ${minutes(plan.chargeMinutes)} · ${plan.charges.size} paradas",
                    "Driving ${minutes(plan.driveMinutes)} · charging ${minutes(plan.chargeMinutes)} · ${plan.charges.size} stops"))
                val vehicle = checkNotNull(plannedVehicle)
                val capacity = labels.n(vehicle.capacityKwh, 1)
                ResultNote(if (vehicle.learnedCapacityKwh == null)
                    t("Capacitat útil de referència · $capacity kWh", "Capacidad útil de referencia · $capacity kWh",
                        "Reference usable capacity · $capacity kWh")
                else t("Capacitat útil estimada dels viatges · $capacity kWh", "Capacidad útil estimada de los viajes · $capacity kWh",
                    "Usable capacity estimated from trips · $capacity kWh"))
                trip?.let { ResultNote(departureLabel(it.departureMs)) }
                if (plan.charges.any { it.ambientC != null }) ResultNote(t(
                    "Previsió horària d’Open-Meteo.", "Previsión horaria de Open-Meteo.", "Hourly forecast by Open-Meteo."))
                if (reducedMargin) ResultNote(t("Marge reduït acceptat per a un tram.",
                    "Margen reducido aceptado para un tramo.", "Reduced margin accepted for one leg."))
                if (previous != null) TextButton(onClick = ::undoChange, enabled = !busy) {
                    Text(t("Desfés el canvi de parada", "Deshaz el cambio de parada", "Undo stop change"))
                }
                if (plan.estimatedKm > 0) ResultNote(t(
                    "Consum de referència en ${labels.n(plan.estimatedKm, 0)} km.",
                    "Consumo de referencia en ${labels.n(plan.estimatedKm, 0)} km.",
                    "Reference consumption used for ${labels.n(plan.estimatedKm, 0)} km."))

                plan.charges.forEachIndexed { index, charge ->
                    val eta = trip?.let { formatTime(it.departureMs + (charge.arrivalMinutes * 60_000).toLong()) }.orEmpty()
                    val ambient = charge.ambientC?.let { "${labels.n(it, 0)} °C" }
                        ?: t("Sense previsió", "Sin previsión", "No forecast")
                    val estimate = if (charge.learned) t("Corba DC apresa", "Curva DC aprendida", "Learned DC curve")
                        else t("Corba de referència", "Curva de referencia", "Reference curve")
                    Surface(
                        onClick = { selectStop(index) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        shape = MaterialTheme.shapes.medium,
                        color = if (stopSelected == index) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${index + 1}. ${charge.stop.name} · ${labels.n(charge.stop.powerKw, 0)} kW",
                                style = MaterialTheme.typography.titleSmall)
                            Text("${percent(charge.arrivalKwh)} → ${percent(charge.departureKwh)} · ${minutes(charge.minutes)}")
                            ResultNote("$eta · $ambient · $estimate")
                        }
                    }
                    if (stopSelected == index && !busy) {
                        TextButton(onClick = { showCharger(charge.stop.point.toString(), charge.stop.name) }) {
                            Text(t("Cerca el carregador a Google Maps", "Busca el cargador en Google Maps", "Find charger in Google Maps"))
                        }
                        PlannerActions(t("Salta la parada", "Salta la parada", "Skip stop"), { skip(plan, charge.stop) },
                            t("Busca alternatives", "Busca alternativas", "Find alternatives"), { searchReplacement(charge.stop) })
                        proposedSkip?.let { preview ->
                            val arrival = percent(preview.arrivalKwh)
                            val reserve = labels.n(checkNotNull(trip).reserve, 1)
                            ResultNote(t("Arribaries amb un $arrival · reserva del $reserve%.",
                                "Llegarías con un $arrival · reserva del $reserve%.",
                                "You would arrive with $arrival · $reserve% reserve."))
                            Button(onClick = { acceptSkip(preview.plan, preview.stop, true) }) {
                                Text(t("Accepta el marge reduït", "Acepta el margen reducido", "Accept reduced margin"))
                            }
                        }
                    }
                }
                Text(t("Obre l’itinerari amb", "Abre el itinerario con", "Open itinerary in"),
                    style = MaterialTheme.typography.titleSmall)
                PlannerActions("Google Maps", { open(plan, true) }, "Waze", { open(plan, false) }, enabled = !busy)
            }
        }

        PlannerPanel {
            Text(t("Mapa de rutes", "Mapa de rutas", "Routing map"), style = MaterialTheme.typography.titleMedium)
            if (!installed) ResultNote(t("Cal importar el graf de carreteres per calcular rutes.",
                "Importa el mapa de carreteras para calcular rutas.", "Import a routing map to calculate routes."))
            OutlinedButton(onClick = importMap, enabled = !importing && !busy) {
                Text(when {
                    importing -> t("Important el mapa…", "Importando el mapa…", "Importing map…")
                    installed -> t("Actualitza el mapa de rutes", "Actualiza el mapa de rutas", "Update routing map")
                    else -> t("Importa un mapa de rutes", "Importa un mapa de rutas", "Import routing map")
                })
            }
        }
    }

    @Composable
    private fun ResultNote(value: String) {
        Text(value, style = MaterialTheme.typography.bodySmall)
    }

    private fun open(plan: Plan, maps: Boolean) {
        val request = trip ?: return
        val stops = plan.charges.map { it.stop }
        navigate(NavigationPlan(stops.map { it.point.toString() } + request.destination.toString(),
            origin = request.origin.toString(), chargerNames = stops.associate { it.point.toString() to it.name }), maps)
    }

    private fun invalidateRoutes(fit: Boolean = false) {
        calculation++
        job?.cancel(true)
        busy = false
        plans = emptyList()
        trip = null
        plannedVehicle = null
        stopSelected = null
        proposedSkip = null
        previous = null
        reducedMargin = false
        status = ""
        drawMap(fit)
    }

    private fun drawMap(fit: Boolean = false) {
        map.update(plans, selected, listOfNotNull(
            trip?.origin ?: originPlace?.point, trip?.destination ?: destinationPlace?.point), fit)
    }

    private fun refreshStartingBattery() {
        if (manualSoc) return
        val vehicle = VehicleTelemetry.LIVE.snapshot()
        val live = vehicle.takeIf { !it.simulation && it.vehicleId == activeVehicle?.id }?.current(SystemClock.elapsedRealtime())
            ?.soc?.takeIf { it in 0.0..100.0 }
        batterySource = if (live == null)
            t("Bateria inicial · 80% per defecte", "Batería inicial · 80% por defecto", "Starting battery · 80% default")
        else t("Bateria inicial · lectura OBD", "Batería inicial · lectura OBD", "Starting battery · OBD reading")
        soc = live?.toInt() ?: 80
    }

    private fun chooseDeparture() {
        val zone = ZoneId.systemDefault()
        val current = departureMs?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: ZonedDateTime.now(zone)
        DatePickerDialog(activity, { _, year, month, day ->
            val date = LocalDate.of(year, month + 1, day)
            TimePickerDialog(activity, { _, hour, minute ->
                setDeparture(date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli())
            }, current.hour, current.minute, android.text.format.DateFormat.is24HourFormat(activity)).show()
        }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
            datePicker.minDate = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        }.show()
    }

    private fun setDeparture(value: Long?) {
        if (departureMs == value) return
        departureMs = value
        invalidateRoutes()
    }

    private fun departureLabel(time: Long? = departureMs): String =
        t("Sortida", "Salida", "Departure") + " · " +
            (time?.let(::formatTime) ?: t("Ara", "Ahora", "Now"))

    private fun formatTime(time: Long): String {
        val date = java.util.Date(time)
        return android.text.format.DateFormat.getDateFormat(activity).format(date) + " " +
            android.text.format.DateFormat.getTimeFormat(activity).format(date)
    }

    private fun percent(kwh: Double) = "${labels.n(kwh / checkNotNull(plannedVehicle).capacityKwh * 100, 1)}%"
    private fun minutes(value: Double) = "${labels.n(value, 0)} min"
    private fun t(ca: String, es: String, en: String) = labels.t(ca, es, en)
}
