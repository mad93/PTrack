package cat.ptrack

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.widget.doAfterTextChanged
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
    private val navigate: (NavigationPlan, Boolean) -> Unit
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
    private var busy = false
    private var installed = false
    private var importing = false
    private var updating = false
    private var manualSoc = false
    private var activeVehicle: VehicleProfile? = null

    private val page = column()
    val view: View get() = page
    private val options = column()
    private var departureMs: Long? = null
    private val departure = button(departureLabel(), ::chooseDeparture)
    private val suggestions = ListPopupWindow(activity).apply {
        inputMethodMode = ListPopupWindow.INPUT_METHOD_NEEDED
        isModal = false
    }
    private val results = column()
    private val status = text("")
    private val availability = text("")
    private val source = text("")
    private val vehicleName = text("")
    private val origin = EditText(activity)
    private val destination = EditText(activity)
    private var originPlace: Place? = null
    private var destinationPlace: Place? = null
    private var placeTarget = origin
    private var picking = false
    private val map = PlannerMap(activity, ::select, ::selectStop) { point ->
        setPlace(placeTarget, Place(t("Punt al mapa", "Punto en el mapa", "Point on map"), point))
    }
    val mapView get() = map.mapView
    private val calculate = button(t("Calcula les rutes", "Calcula las rutas", "Find routes")) { calculate() }
    private val mapImport = button(t("Importa un mapa de rutes", "Importa un mapa de rutas", "Import routing map"), importMap)
    private val picker = button(t("Tria al mapa", "Elige en el mapa", "Pick on map")) {
        if (!origin.hasFocus() && !destination.hasFocus()) placeTarget = if (originPlace == null) origin else destination
        picking = !picking
        map.setPicking(picking)
        if (picking) {
            hideSuggestions()
            hideKeyboard()
            status.text = if (placeTarget === origin)
                t("Toca l’origen al mapa.", "Toca el origen en el mapa.", "Tap the origin on the map.")
            else t("Toca el destí al mapa.", "Toca el destino en el mapa.", "Tap the destination on the map.")
        } else status.text = ""
    }
    private var plans = emptyList<Plan>()
    private var trip: ValhallaPlanner.Trip? = null
    private var plannedVehicle: PlanningEnergy? = null
    private var selected = 0
    private var stopSelected: Int? = null
    private var excluded = emptySet<String>()
    private var reducedMargin = false
    private data class SkipPreview(val plan: Plan, val stop: Stop, val arrivalKwh: Double)
    private data class Previous(val plans: List<Plan>, val selected: Int, val excluded: Set<String>, val reducedMargin: Boolean)
    private var proposedSkip: SkipPreview? = null
    private var previous: Previous? = null

    private val soc = slider(t("Bateria de sortida", "Batería de salida", "Starting battery"), "%", 100, 80) {
        manualSoc = true
        source.text = t("Bateria inicial · entrada manual", "Batería inicial · entrada manual", "Starting battery · manual entry")
    }
    private val reserve = slider(t("Bateria mínima d’arribada", "Batería mínima de llegada", "Minimum arrival battery"), "%", 99, 10)
    private val minimum = slider(t("Potència mínima dels carregadors CCS", "Potencia mínima de los cargadores CCS",
        "Minimum CCS charger power"), " kW", 1000, 50)
    private val priority = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, listOf(
            t("Arribar abans", "Llegar antes", "Arrive sooner"),
            t("Carregar menys temps", "Cargar menos tiempo", "Spend less time charging")))
    }

    init {
        page.isFocusableInTouchMode = true
        setupPlace(origin, t("Origen", "Origen", "Origin"))
        setupPlace(destination, t("Destí", "Destino", "Destination"))
        page.addView(row(button(t("La meva ubicació", "Mi ubicación", "My location"), locate), picker))
        page.addView(vehicleName)
        page.addView(source)
        refreshStartingBattery()
        page.addView(button(t("Opcions de viatge", "Opciones de viaje", "Trip options")) { toggle(options) })
        options.addView(text(t("Prioritat de viatge", "Prioridad del viaje", "Trip priority")))
        options.addView(priority)
        options.addView(row(departure, button(t("Ara", "Ahora", "Now")) { setDeparture(null) }))
        options.visibility = View.GONE
        page.addView(options)
        page.addView(calculate)
        page.addView(map, LinearLayout.LayoutParams(-1, dp(340)))
        page.addView(status)
        page.addView(results)
        page.addView(availability)
        page.addView(mapImport)
        priority.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (trip?.priority?.ordinal != position && trip != null) invalidateRoutes()
            }
        }
    }

    fun mapStatus(available: Boolean, working: Boolean) {
        installed = available
        importing = working
        if (trip == null && !busy) refreshStartingBattery()
        availability.visibility = if (available) View.GONE else View.VISIBLE
        availability.text = t("Cal importar el graf de carreteres per calcular rutes.",
            "Importa el mapa de carreteras para calcular rutas.", "Import a routing map to calculate routes.")
        mapImport.text = if (working) t("Important el mapa…", "Importando el mapa…", "Importing map…")
        else if (available) t("Actualitza el mapa de rutes", "Actualiza el mapa de rutas", "Update routing map")
        else t("Importa un mapa de rutes", "Importa un mapa de rutas", "Import routing map")
        controls()
    }

    fun vehicleChanged(vehicle: VehicleProfile) {
        val previous = activeVehicle
        activeVehicle = vehicle
        vehicleName.text = vehicle.label
        if (previous?.id == vehicle.id && previous.capacityKwh == vehicle.capacityKwh &&
            previous.maxDcChargeKw == vehicle.maxDcChargeKw) return
        val hadRoute = trip != null || busy
        if (previous?.id != vehicle.id) manualSoc = false
        invalidateRoutes()
        refreshStartingBattery()
        if (hadRoute) status.text = t("El perfil ha canviat. Torna a calcular la ruta.", "El perfil ha cambiado. Vuelve a calcular la ruta.",
            "The profile changed. Recalculate the route.")
    }

    fun location(place: Place?) {
        if (place == null) status.text = t("Ubicació indisponible. Tria un punt al mapa.",
            "Ubicación no disponible. Elige un punto en el mapa.", "Location unavailable. Pick a point on the map.")
        else setPlace(origin, place)
    }

    fun close() {
        disposed = true
        hideSuggestions()
        routing.shutdownNow()
        searching.shutdownNow()
        mapView.onDestroy()
    }

    private fun setupPlace(field: EditText, hint: String) {
        field.hint = "$hint · " + t("carrer, poble o masia", "calle, pueblo o masía", "street, town or farm")
        field.contentDescription = hint
        field.setSingleLine()
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        field.imeOptions = EditorInfo.IME_ACTION_SEARCH
        field.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) false
            else {
                if (place(field) == null) suggest(field, immediate = true)
                true
            }
        }
        field.setOnFocusChangeListener { _, focused ->
            if (focused) {
                placeTarget = field
                if (!picking && place(field) == null) suggest(field)
            } else if (placeTarget === field) hideSuggestions()
        }
        field.doAfterTextChanged {
            if (!updating) {
                field.error = null
                if (field === origin) originPlace = null else destinationPlace = null
                invalidateRoutes()
                if (field.hasFocus()) suggest(field)
            }
        }
        page.addView(field)
    }

    private fun place(field: EditText): Place? = if (field === origin) originPlace else destinationPlace

    private fun setPlace(field: EditText, value: Place) {
        if (field === origin) originPlace = value else destinationPlace = value
        updating = true
        field.setText(value.name)
        field.error = null
        updating = false
        picking = false
        map.setPicking(false)
        hideSuggestions()
        hideKeyboard()
        page.requestFocus()
        invalidateRoutes()
        drawMap(fit = true)
    }

    private fun suggest(field: EditText, immediate: Boolean = false, inCatalonia: Boolean = false) {
        hideSuggestions()
        val query = field.text.toString().trim()
        if (query.length < 3 || PlannerPlaces.coordinates(query) != null) return
        val id = lookupId
        val request = Runnable {
            if (!field.hasFocus() || !field.hasWindowFocus() || !field.isShown) return@Runnable
            showSuggestions(field, emptyList(),
                if (inCatalonia) t("Cercant a l’ICGC…", "Buscando en el ICGC…", "Searching ICGC…")
                else t("Cercant…", "Buscando…", "Searching…"))
            lookup = searching.submit {
                val response = runCatching { PlannerPlaces.search(query, labels.locale.language, inCatalonia) }
                handler.post {
                    if (disposed || id != lookupId || !field.hasFocus() || !field.hasWindowFocus() || !field.isShown) return@post
                    response.fold({ places ->
                        showSuggestions(field, places, t(
                            "Cap resultat. Prova d’afegir el municipi.",
                            "Sin resultados. Prueba a añadir el municipio.",
                            "No results. Try adding the town."), offerIcgc = !inCatalonia)
                    }, {
                        showSuggestions(field, emptyList(), t(
                            "Cerca indisponible. Revisa la connexió o tria al mapa.",
                            "Búsqueda no disponible. Revisa la conexión o elige en el mapa.",
                            "Search unavailable. Check your connection or pick on the map."), offerIcgc = !inCatalonia)
                    })
                }
            }
        }
        lookupRequest = request
        handler.postDelayed(request, if (immediate) 0L else 400L)
    }

    private fun showSuggestions(field: EditText, places: List<Place>, message: String, offerIcgc: Boolean = false) {
        val matches = places.map { it.name }.ifEmpty { listOf(message) }
        val rows = if (offerIcgc) matches + t("Cerca a Catalunya (ICGC)", "Buscar en Cataluña (ICGC)",
            "Search Catalonia (ICGC)") else matches
        val adapter = object : ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1, rows) {
            override fun areAllItemsEnabled() = places.isNotEmpty()
            override fun isEnabled(position: Int) = position in places.indices || (offerIcgc && position == matches.size)
        }
        suggestions.anchorView = field
        suggestions.width = field.width
        suggestions.setAdapter(adapter)
        suggestions.show()
        // The list may already exist from the loading state.
        suggestions.listView?.setOnItemClickListener { _, _, index, _ ->
            if (offerIcgc && index == matches.size) suggest(field, immediate = true, inCatalonia = true)
            else places.getOrNull(index)?.let { setPlace(field, it) }
        }
    }

    private fun hideSuggestions() {
        lookupId++
        lookupRequest?.let(handler::removeCallbacks)
        lookup?.cancel(true)
        suggestions.dismiss()
    }

    private fun calculate() {
        refreshStartingBattery()
        val from = (originPlace ?: PlannerPlaces.coordinates(origin.text.toString()))?.point
        val to = (destinationPlace ?: PlannerPlaces.coordinates(destination.text.toString()))?.point
        if (from == null || to == null) {
            val field = if (from == null) origin else destination
            field.requestFocus()
            field.error = t("Tria un resultat de la cerca o un punt al mapa.",
                "Elige un resultado de la búsqueda o un punto en el mapa.",
                "Choose a search result or a point on the map.")
            return
        }
        if (from == to) {
            destination.error = t("Tria un destí diferent de l’origen.",
                "Elige un destino distinto del origen.", "Choose an end point different from the start.")
            return
        }
        val start = soc.progress.toDouble()
        val floor = reserve.progress.toDouble()
        val minKw = minimum.progress.toDouble()
        if (start < floor) {
            options.visibility = View.VISIBLE
            status.text = t("La bateria de sortida ha de cobrir la reserva.",
                "La batería de salida debe cubrir la reserva.", "The starting battery must cover the reserve.")
            return
        }
        hideSuggestions()
        hideKeyboard()
        page.requestFocus()
        options.visibility = View.GONE
        picking = false
        map.setPicking(false)
        val request = ValhallaPlanner.Trip(from, to, start, floor, minKw,
            Priority.entries[priority.selectedItemPosition], departureMs ?: System.currentTimeMillis())
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
                replacement == null -> status.text = t("No es pot saltar la parada amb el marge disponible. Busca alternatives.",
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
        status.text = t("Calculant amb el mapa local…", "Calculando con el mapa local…", "Calculating with the local map…")
        controls()
        renderResults()
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
                status.text = ""
                controls()
                result.fold(complete, { error ->
                    if (error is ConsumptionRepository.MissingVehicleCapacity) {
                        status.text = t("Configura la capacitat útil del vehicle en kWh o registra prou viatges per estimar-la.",
                            "Configura la capacidad útil del vehículo en kWh o registra suficientes viajes para estimarla.",
                            "Set the vehicle’s usable capacity in kWh or record enough trips to estimate it.")
                    } else {
                        Log.w("PTrackPlanner", "Could not calculate route", error)
                        status.text = t("Ha fallat el càlcul amb el mapa local.", "Falló el cálculo con el mapa local.",
                            "Routing with the local map failed.")
                    }
                })
                renderResults()
            }
        }
    }

    private fun noRoute() {
        status.text = t("Cap ruta viable amb aquests punts i opcions.", "No hay rutas viables con estos puntos y opciones.",
            "No feasible routes with these points and options.")
    }

    private fun showPlans(routes: List<Plan>, selection: Int = 0) {
        plans = routes
        selected = selection
        stopSelected = null
        proposedSkip = null
        status.text = ""
        drawMap(fit = true)
        renderResults()
    }

    private fun select(index: Int) {
        if (busy || index !in plans.indices) return
        selected = index
        stopSelected = null
        proposedSkip = null
        status.text = ""
        drawMap()
        renderResults()
    }

    private fun selectStop(index: Int) {
        if (busy || index !in plans.getOrNull(selected)?.charges.orEmpty().indices) return
        stopSelected = if (stopSelected == index) null else index
        proposedSkip = null
        status.text = ""
        renderResults()
    }

    private fun renderResults() {
        results.removeAllViews()
        val plan = plans.getOrNull(selected) ?: return
        plans.forEachIndexed { index, option ->
            val summary = "${labels.n(option.legs.sumOf { it.km }, 0)} km · ${minutes(option.totalMinutes)} · ${percent(option.arrivalKwh)}"
            results.addView(button("${if (selected == index) "●" else "○"} ${t("Ruta", "Ruta", "Route")} ${index + 1} · $summary") {
                select(index)
            }.apply { isEnabled = !busy })
        }
        results.addView(text(t("Conducció ${minutes(plan.driveMinutes)} · càrrega ${minutes(plan.chargeMinutes)} · ${plan.charges.size} parades",
            "Conducción ${minutes(plan.driveMinutes)} · carga ${minutes(plan.chargeMinutes)} · ${plan.charges.size} paradas",
            "Driving ${minutes(plan.driveMinutes)} · charging ${minutes(plan.chargeMinutes)} · ${plan.charges.size} stops")))
        val vehicle = checkNotNull(plannedVehicle)
        val capacity = labels.n(vehicle.capacityKwh, 1)
        results.addView(text(if (vehicle.learnedCapacityKwh == null)
            t("Capacitat útil de referència · $capacity kWh", "Capacidad útil de referencia · $capacity kWh",
                "Reference usable capacity · $capacity kWh")
        else t("Capacitat útil estimada dels viatges · $capacity kWh", "Capacidad útil estimada de los viajes · $capacity kWh",
            "Usable capacity estimated from trips · $capacity kWh")))
        trip?.let { results.addView(text(departureLabel(it.departureMs))) }
        if (plan.charges.any { it.ambientC != null }) results.addView(text(t(
            "Previsió horària d’Open-Meteo.", "Previsión horaria de Open-Meteo.", "Hourly forecast by Open-Meteo.")))
        if (reducedMargin) results.addView(text(t("Marge reduït acceptat per a un tram.", "Margen reducido aceptado para un tramo.",
            "Reduced margin accepted for one leg.")))
        if (previous != null) results.addView(button(t("Desfés el canvi de parada", "Deshaz el cambio de parada",
            "Undo stop change"), ::undoChange).apply { isEnabled = !busy })
        if (plan.estimatedKm > 0) results.addView(text(t(
            "Consum de referència en ${labels.n(plan.estimatedKm, 0)} km.",
            "Consumo de referencia en ${labels.n(plan.estimatedKm, 0)} km.",
            "Reference consumption used for ${labels.n(plan.estimatedKm, 0)} km.")))
        plan.charges.forEachIndexed { index, charge ->
            val eta = trip?.let { formatTime(it.departureMs + (charge.arrivalMinutes * 60_000).toLong()) }.orEmpty()
            val ambient = charge.ambientC?.let { "${labels.n(it, 0)} °C" }
                ?: t("Sense previsió", "Sin previsión", "No forecast")
            val estimate = if (charge.learned) t("Corba DC apresa", "Curva DC aprendida", "Learned DC curve")
                else t("Corba de referència", "Curva de referencia", "Reference curve")
            results.addView(button("${index + 1}. ${charge.stop.name} · ${labels.n(charge.stop.powerKw, 0)} kW\n" +
                "${percent(charge.arrivalKwh)} → ${percent(charge.departureKwh)} · ${minutes(charge.minutes)}\n" +
                "$eta · $ambient · $estimate") {
                selectStop(index)
            }.apply { isEnabled = !busy })
            if (stopSelected == index && !busy) {
                results.addView(row(button(t("Salta la parada", "Salta la parada", "Skip stop")) { skip(plan, charge.stop) },
                    button(t("Busca alternatives", "Busca alternativas", "Find alternatives")) { searchReplacement(charge.stop) }))
                proposedSkip?.let { preview ->
                    val arrival = percent(preview.arrivalKwh)
                    val reserve = labels.n(checkNotNull(trip).reserve, 1)
                    results.addView(text(t("Arribaries amb un $arrival · reserva del $reserve%.",
                        "Llegarías con un $arrival · reserva del $reserve%.",
                        "You would arrive with $arrival · $reserve% reserve.")))
                    results.addView(button(t("Accepta el marge reduït", "Acepta el margen reducido", "Accept reduced margin")) {
                        acceptSkip(preview.plan, preview.stop, true)
                    })
                }
            }
        }
        results.addView(text(t("Obre el pròxim punt amb", "Abre el siguiente punto con", "Open the next point with")))
        results.addView(row(button("Google Maps") { open(plan, true) }.apply { isEnabled = !busy },
            button("Waze") { open(plan, false) }.apply { isEnabled = !busy }))
    }

    private fun open(plan: Plan, maps: Boolean) {
        val request = trip ?: return
        navigate(NavigationPlan(plan.charges.map { it.stop.point.toString() } + request.destination.toString()), maps)
    }

    private fun invalidateRoutes() {
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
        status.text = ""
        controls()
        drawMap()
        renderResults()
    }

    private fun drawMap(fit: Boolean = false) {
        map.update(plans, selected, listOfNotNull(
            trip?.origin ?: originPlace?.point, trip?.destination ?: destinationPlace?.point), fit)
    }

    private fun controls() {
        calculate.text = if (busy) t("Calculant…", "Calculando…", "Calculating…")
        else t("Calcula les rutes", "Calcula las rutas", "Find routes")
        calculate.isEnabled = installed && !importing && !busy
        mapImport.isEnabled = !importing && !busy
    }

    private fun refreshStartingBattery() {
        if (manualSoc) return
        val vehicle = VehicleTelemetry.LIVE.snapshot()
        val live = vehicle.takeIf { !it.simulation && it.vehicleId == activeVehicle?.id }?.current(SystemClock.elapsedRealtime())
            ?.soc?.takeIf { it in 0.0..100.0 }
        source.text = if (live == null)
            t("Bateria inicial · 80% per defecte", "Batería inicial · 80% por defecto", "Starting battery · 80% default")
        else t("Bateria inicial · lectura OBD", "Batería inicial · lectura OBD", "Starting battery · OBD reading")
        soc.progress = live?.toInt() ?: 80
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
        departure.text = departureLabel()
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
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun column() = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private fun text(value: String) = TextView(activity).apply {
        text = value
        textSize = 15f
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(6), dp(8), dp(6))
    }
    private fun slider(name: String, unit: String, maximum: Int, initial: Int, onChange: () -> Unit = {}): SeekBar {
        val label = text("")
        options.addView(label)
        return SeekBar(activity).apply {
            max = maximum
            progress = initial
            keyProgressIncrement = 1
            contentDescription = name
            fun showValue(value: Int) {
                label.text = "$name · $value$unit"
                if (Build.VERSION.SDK_INT >= 30) stateDescription = "$value$unit"
            }
            showValue(initial)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    showValue(progress)
                    if (fromUser) {
                        onChange()
                        if (busy || trip != null || status.text.isNotEmpty()) invalidateRoutes()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
            options.addView(this, LinearLayout.LayoutParams(-1, dp(48)))
        }
    }

    private fun button(name: String, action: () -> Unit) = Button(activity).apply {
        text = name
        isAllCaps = false
        setOnClickListener { action() }
    }
    private fun row(vararg buttons: Button) = LinearLayout(activity).apply {
        for (button in buttons) addView(button, LinearLayout.LayoutParams(0, -2, 1f))
    }
    private fun toggle(panel: View) { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
    private fun hideKeyboard() = activity.getSystemService(InputMethodManager::class.java)
        .hideSoftInputFromWindow(page.windowToken, 0)
}
