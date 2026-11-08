package cat.ptrack

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.util.JsonWriter
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var l: Labels
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var status: TextView? = null
    private lateinit var charge: TextView
    private lateinit var advanced: TextView
    private lateinit var power: Gauge
    private lateinit var current: Gauge
    private var exportId = -1L
    private var exportType: String? = null
    private var backupBusy = false
    private var backupDate: TextView? = null
    private var chargingStatus: TextView? = null
    private var chargingRefresh: Button? = null
    private var plannerScreen: PlannerScreen? = null
    private var started = false
    private var resumed = false
    private var importingMap = false
    private var calendarMonth = YearMonth.now()
    private var annualCalendar = false
    private var selectedDay: LocalDate? = null
    private var openedTrip: Long? = null
    private var allVehicles = false
    private var vehicleTitle: TextView? = null
    private lateinit var shownVehicleId: String
    private var choosingObdProfile = false
    private var page = 0
    private var pendingAssociation: String? = null
    private val prefs get() = getSharedPreferences("settings", 0)
    private val syncListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BackupSync.LAST_BACKUP && page == 2) backupDate?.text = lastBackupText()
        if (key == BackupSync.HISTORY_CHANGED && page == 1 && openedTrip == null) showPage()
        if (key == BackupSync.HISTORY_CHANGED && page == 2) showPage()
    }
    private val chargingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (page == 2 && key == ChargingSync.REVISION) showChargingStatus()
    }
    private val refresh = object : Runnable {
        override fun run() {
            val vehicle = store.vehicles.active()
            plannerScreen?.vehicleChanged(vehicle)
            if (vehicle.id != shownVehicleId) {
                if (!RecordingService.running && VehicleTelemetry.LIVE.snapshot().vehicleId != vehicle.id) {
                    VehicleTelemetry.LIVE.resetVehicle(vehicle.id)
                    RecordingService.state = if (vehicle.obdAddress == null) "unconfigured" else "disconnected"
                }
                if (page == 2 || (page == 1 && openedTrip == null)) showPage()
            }
            shownVehicleId = vehicle.id
            if (page == 0) update(vehicle)
            offerObdChoice()
            handler.postDelayed(this, 750)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); store = Store(this)
        shownVehicleId = store.vehicles.active().id
        if (state != null) {
            exportId = state.getLong("exportId", -1); exportType = state.getString("exportType"); page = state.getInt("page", 0)
            calendarMonth = state.getString("calendarMonth")?.let(YearMonth::parse) ?: calendarMonth
            annualCalendar = state.getBoolean("annualCalendar")
            allVehicles = state.getBoolean("allVehicles")
            selectedDay = state.getString("selectedDay")?.let(LocalDate::parse)
            openedTrip = state.getLong("openedTrip", -1).takeIf { it >= 0 }
            pendingAssociation = state.getString("pendingAssociation")
        }
        receiveObdChoice(intent)
        l = Labels(this); build()
        if (prefs.getBoolean("auto", false)) observeAutomaticDevices(true)
    }
    override fun onStart() {
        super.onStart()
        started = true
        plannerScreen?.mapView?.onStart()
        BackupSync.prefs(this).registerOnSharedPreferenceChangeListener(syncListener)
        ChargingSync.prefs(this).registerOnSharedPreferenceChangeListener(chargingListener)
        if (page == 2) showChargingStatus()
        if (page == 2) backupDate?.text = lastBackupText()
        if (page == 1 && openedTrip == null) showPage()
    }
    override fun onStop() {
        BackupSync.prefs(this).unregisterOnSharedPreferenceChangeListener(syncListener)
        ChargingSync.prefs(this).unregisterOnSharedPreferenceChangeListener(chargingListener)
        plannerScreen?.mapView?.onStop()
        started = false
        super.onStop()
    }
    override fun onResume() {
        super.onResume(); handler.post(refresh)
        resumed = true
        plannerScreen?.mapView?.onResume()
        BackupSync.schedule(this)
        ChargingSync.schedule(this)
        if (backupBusy) return
        if (RecordingService.running) startService(Intent(this, RecordingService::class.java).setAction("gps"))
        else if (activeAddress() == null) RecordingService.state = "unconfigured"
        else if (Build.VERSION.SDK_INT < 31 && prefs.getBoolean("auto", false) && RecordingService.pendingObdChoice == null) {
            try {
                startForegroundService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_AUTO_OBD))
            } catch (_: RuntimeException) {
                RecordingService.state = "permission"
            }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putLong("exportId", exportId); state.putString("exportType", exportType); state.putInt("page", page)
        state.putString("calendarMonth", calendarMonth.toString())
        state.putBoolean("annualCalendar", annualCalendar)
        state.putBoolean("allVehicles", allVehicles)
        state.putString("selectedDay", selectedDay?.toString())
        openedTrip?.let { state.putLong("openedTrip", it) }
        state.putString("pendingAssociation", pendingAssociation)
        super.onSaveInstanceState(state)
    }
    override fun onPause() {
        resumed = false
        plannerScreen?.mapView?.onPause()
        handler.removeCallbacks(refresh)
        super.onPause()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        plannerScreen?.mapView?.onLowMemory()
    }
    override fun onDestroy() { plannerScreen?.close(); io.shutdown(); super.onDestroy() }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Int, color: Int) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setOnClickListener { action() }
    }
    @Suppress("DEPRECATION")
    private fun build() {
        l = Labels(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF0A211C.toInt()) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(dp(14) + safe.left, dp(12) + safe.top, dp(14) + safe.right, safe.bottom)
            insets
        }
        window.statusBarColor = 0xFF0A211C.toInt(); window.navigationBarColor = 0xFF0A211C.toInt(); setContentView(root)
        ViewCompat.requestApplyInsets(root)
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(text("PTrack", 30, 0xFFBCF46A.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_settings)
            contentDescription = l.t("Configuració", "Ajustes", "Settings")
            setOnClickListener { page = 2; showPage() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header)
        val tabs = LinearLayout(this)
        val tabsByPage = listOf(0 to l.t("En directe", "En directo", "Live"),
            1 to l.t("Viatges", "Viajes", "Trips"), 3 to l.t("Planificador", "Planificador", "Planner"))
        for ((target, name) in tabsByPage) tabs.addView(button(name) {
            page = target
            selectedDay = null; openedTrip = null
            if (page == 1 && !RecordingService.running) io.execute {
                store.recover()
                runOnUiThread { if (page == 1 && openedTrip == null && !isDestroyed) showPage() }
            }
            showPage()
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(tabs)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body); root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val buildInfo = text("PTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}" +
            " · Android Auto " + (if (BuildConfig.CAR_ENABLED) "experimental" else "—") + "\n" +
            "${BuildConfig.SOURCE_BRANCH} @ ${BuildConfig.SOURCE_COMMIT} · ${BuildConfig.SOURCE_STATE}\nBuild UTC: ${BuildConfig.BUILD_TIME_UTC}", 11, 0xFF9DB5AA.toInt())
        buildInfo.setTextIsSelectable(true); root.addView(buildInfo); showPage()
    }
    private fun showPage() {
        if (page != 3) plannerScreen?.cancelLocation()
        body.removeAllViews()
        chargingStatus = null
        chargingRefresh = null
        vehicleTitle = null
        when (page) {
            0 -> live()
            1 -> openedTrip?.let(::trip) ?: history()
            3 -> planner()
            else -> settings()
        }
    }
    private fun planner() {
        val plans = NavigationPlanStore(this)
        val plan = plans.read()
        val point = plan?.next
        val screen = plannerScreen ?: PlannerScreen(this, l, importMap = {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), OFFLINE_MAP_IMPORT)
        }, locate = ::locatePlannerOrigin, navigate = { draft, maps ->
            val saved = plans.read()
            val itinerary = saved?.takeIf {
                it.points == draft.points && it.next != null && (it.origin == null || it.origin == draft.origin)
            }?.let { draft.copy(completed = it.completed) } ?: draft
            plans.save(itinerary)
            showPage()
            openItinerary(itinerary, maps)
        }, showCharger = ::openCharger).also {
            plannerScreen = it
            if (started) it.mapView.onStart()
            if (resumed) it.mapView.onResume()
        }
        body.addView(screen.view)
        screen.vehicleChanged(store.vehicles.active())
        screen.mapStatus(OfflineValhalla.installed(this), importingMap)
        body.addView(text(l.t("Itinerari desat", "Itinerario guardado", "Saved itinerary"), 20, Color.WHITE))
        if (plan == null || point == null) {
            val message = if (plan == null) l.t("Afegeix les parades i el destí del viatge.",
                "Añade las paradas y el destino del viaje.", "Add the stops and destination for your trip.")
            else l.t("Itinerari completat.", "Itinerario completado.", "Itinerary complete.")
            body.addView(text(message, 16, Color.WHITE))
            body.addView(button(l.t("Nou itinerari", "Nuevo itinerario", "New itinerary")) { editNavigationPlan(plans, null) })
            return
        }
        val heading = if (plan.remaining.size == 1) l.t("Destí", "Destino", "Destination")
            else l.t("Pròxima parada", "Próxima parada", "Next stop")
        body.addView(text("$heading · ${plan.completed + 1}/${plan.points.size}\n${plan.label(point)}", 18, Color.WHITE))
        body.addView(button(l.t("Obre amb Waze", "Abrir con Waze", "Open in Waze")) {
            openItinerary(plan, maps = false)
        })
        body.addView(button(l.t("Obre amb Google Maps", "Abrir con Google Maps", "Open in Google Maps")) {
            openItinerary(plan, maps = true)
        })
        chargerButton(plan, point)
        body.addView(button(l.t("Marca el punt com a completat", "Marcar el punto como completado", "Mark point complete")) {
            plans.save(plan.advance())
            showPage()
        })
        body.addView(button(l.t("Edita els punts pendents", "Editar los puntos pendientes", "Edit remaining points")) {
            editNavigationPlan(plans, plan)
        })
        plan.remaining.drop(1).forEachIndexed { index, stop ->
            body.addView(text("${plan.completed + index + 2}. ${plan.label(stop)}", 15, Color.WHITE))
            chargerButton(plan, stop)
        }
    }

    private fun chargerButton(plan: NavigationPlan, point: String) {
        val name = plan.chargerNames[point] ?: return
        body.addView(button(l.t("Cerca el carregador a Google Maps", "Busca el cargador en Google Maps",
            "Find charger in Google Maps")) { openCharger(point, name) })
    }

    private fun openCharger(point: String, name: String) {
        openNavigation("com.google.android.apps.maps", TripNavigation.mapsCharger(point, name),
            TripNavigation.mapsChargerWeb(point, name))
    }

    private fun openItinerary(plan: NavigationPlan, maps: Boolean) {
        if (!maps) {
            chooseNavigationLeg(plan, maps = false)
            return
        }
        val mapsPackage = "com.google.android.apps.maps"
        val directions = Uri.parse("https://www.google.com/maps/dir/?api=1")
        val browser = Intent(Intent.ACTION_VIEW, directions).setPackage(mapsPackage).resolveActivity(packageManager) == null
        val sections = TripNavigation.mapsSections(plan, browser)
        if (sections.isNullOrEmpty()) {
            chooseNavigationLeg(plan, maps = true)
            return
        }
        fun open(section: TripNavigation.MapsSection) {
            val webUrl = section.url.takeIf { section.stops.count() <= 4 }
            openNavigation(mapsPackage, section.url, webUrl)
        }
        if (sections.size == 1) {
            open(sections.single())
            return
        }
        val labels = sections.mapIndexed { index, section ->
            "${index + 1}. ${plan.label(plan.points[section.stops.last])}"
        }
        AlertDialog.Builder(this)
            .setTitle(l.t("Tria una part de la ruta a Google Maps", "Elige una parte de la ruta en Google Maps",
                "Choose a route section in Google Maps"))
            .setItems(labels.toTypedArray()) { _, index -> open(sections[index]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseNavigationLeg(plan: NavigationPlan, maps: Boolean) {
        val remaining = plan.remaining
        if (remaining.size == 1) {
            openNextNavigation(remaining.single(), maps)
            return
        }
        val labels = remaining.mapIndexed { index, point -> "${plan.completed + index + 1}. ${plan.label(point)}" }
        val navigator = if (maps) "Google Maps" else "Waze"
        AlertDialog.Builder(this)
            .setTitle(l.t("Tria un tram · $navigator", "Elige un tramo · $navigator", "Choose a leg · $navigator"))
            .setItems(labels.toTypedArray()) { _, index -> openNextNavigation(remaining[index], maps) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun importRoutingMap(uri: Uri) {
        importingMap = true
        if (page == 3) showPage()
        io.execute {
            val error = runCatching {
                val context = applicationContext
                val input = contentResolver.openInputStream(uri) ?: throw IOException("Map file unavailable")
                input.use { OfflineValhalla.install(context, it) }
            }.exceptionOrNull()
            runOnUiThread {
                importingMap = false
                if (isDestroyed) return@runOnUiThread
                if (page == 3) showPage()
                if (error == null) toast(l.t("Mapa de rutes importat.", "Mapa de rutas importado.", "Routing map imported."))
                else {
                    Log.w("PTrackPlanner", "Offline map import failed", error)
                    toast(l.t("No s’ha pogut importar el mapa. Comprova el paquet i l’espai disponible.",
                        "No se ha podido importar el mapa. Comprueba el paquete y el espacio disponible.",
                        "Could not import the map. Check the package and available space."))
                }
            }
        }
    }

    private fun locatePlannerOrigin() {
        val screen = plannerScreen ?: return
        val request = screen.locationRequest ?: return
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        if (checkSelfPermission(fine) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(coarse) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(fine, coarse), PLANNER_LOCATION_PERMISSION)
            return
        }
        val manager = getSystemService(LocationManager::class.java)
        val enabled = manager.getProviders(true)
        val providers = buildList {
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }.filter { it in enabled }.iterator()
        val executor = ContextCompat.getMainExecutor(this)
        fun show(location: Location?) {
            val place = location?.let {
                PlannerPlaces.Place(l.t("La meva ubicació", "Mi ubicación", "My location"),
                    ElectricRoutePlanner.Point(it.latitude, it.longitude))
            }
            screen.location(request, place)
        }
        fun tryNextProvider() {
            if (request.isCanceled) return
            while (providers.hasNext()) {
                try {
                    LocationManagerCompat.getCurrentLocation(manager, providers.next(), request, executor) { fix ->
                        if (fix == null) tryNextProvider() else show(fix)
                    }
                    return
                } catch (_: SecurityException) {
                    // A provider can require a permission that the user did not grant.
                } catch (_: IllegalArgumentException) {
                    // The provider can become unavailable between lookup and request.
                }
            }
            show(null)
        }
        tryNextProvider()
    }

    private fun openNextNavigation(point: String, maps: Boolean) {
        if (maps) openNavigation("com.google.android.apps.maps", TripNavigation.mapsNavigation(point),
            TripNavigation.mapsDirections(listOf(point), browser = true))
        else {
            val url = TripNavigation.waze(point)
            openNavigation("com.waze", url, url)
        }
    }
    private fun editNavigationPlan(plans: NavigationPlanStore, plan: NavigationPlan?) {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            maxLines = 8
            hint = l.t("Una adreça o latitud,longitud per línia. El destí va al final.",
                "Una dirección o latitud,longitud por línea. El destino va al final.",
                "One address or latitude,longitude per line. Put the destination last.")
            setText(plan?.remaining?.joinToString("\n"))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(l.t("Parades i destí", "Paradas y destino", "Stops and destination"))
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(l.t("Desa", "Guardar", "Save"), null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val points = TripNavigation.parsePoints(input.text.toString())
            if (points == null) {
                input.error = l.t("Introdueix com a mínim un punt vàlid.", "Introduce al menos un punto válido.",
                    "Enter at least one valid point.")
                return@setOnClickListener
            }
            plans.save(plan?.replaceRemaining(points) ?: NavigationPlan(points))
            dialog.dismiss()
            showPage()
        }
    }

    private fun openNavigation(packageName: String, appUrl: String, webUrl: String?) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)).setPackage(packageName))
            return
        } catch (_: ActivityNotFoundException) {
            if (webUrl != null) try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                return
            } catch (_: ActivityNotFoundException) { }
        }
        Toast.makeText(this, l.t("No s’ha pogut obrir el navegador per a aquest itinerari.",
            "No se ha podido abrir el navegador para este itinerario.",
            "Could not open the navigator for this itinerary."), Toast.LENGTH_LONG).show()
    }

    private fun live() {
        vehicleTitle = text("", 17, Color.WHITE).also(body::addView)
        status = text("", 15, 0xFFBCF46A.toInt()).also(body::addView)
        val gauges = LinearLayout(this)
        power = Gauge(this, l.t("Potència", "Potencia", "Power"), "kW", -80.0, 120.0)
        current = Gauge(this, l.signal("current"), "A", -200.0, 300.0)
        gauges.addView(power, LinearLayout.LayoutParams(0, dp(210), 1f)); gauges.addView(current, LinearLayout.LayoutParams(0, dp(210), 1f)); body.addView(gauges)
        charge = text("— %", 38, Color.WHITE).apply { gravity = Gravity.CENTER }; body.addView(charge)
        body.addView(text(l.t("+ consum   /   − energia cap a la bateria", "+ consumo   /   − energía hacia la batería", "+ discharge   /   − energy into battery"), 12, 0xFF9DB5AA.toInt()).apply { gravity = Gravity.CENTER })
        body.addView(button(l.t("Connecta OBD", "Conectar OBD", "Connect OBD"), ::connect))
        body.addView(button(l.t("Atura i desa el viatge", "Detener y guardar viaje", "Stop and save trip")) {
            setAutomaticRecording(false)
            if (RecordingService.running) startService(Intent(this, RecordingService::class.java).setAction("stop"))
        })
        body.addView(text(l.t("TELEMETRIA DEL VEHICLE", "TELEMETRÍA DEL VEHÍCULO", "VEHICLE TELEMETRY"), 12, 0xFFBCF46A.toInt()))
        advanced = text("", 14, 0xFFDBE8E0.toInt()); body.addView(advanced); update()
    }
    private fun update(vehicle: VehicleProfile = store.vehicles.active()) {
        val statusView = status ?: return
        if (!::power.isInitialized) return
        vehicleTitle?.text = vehicle.label
        var key = RecordingService.state; var s = RecordingService.latest
        if (s != null && System.currentTimeMillis() - s.time > 10000) { s = null; key = "vehicle" }
        statusView.text = l.status(key); power.value(s?.power()); current.value(s?.current); charge.text = if (s == null) "— %" else l.n(s.soc, 1) + " %"
        val detail = StringBuilder()
        if (s != null) {
            detail.append(l.signal("speed")).append("     ").append(l.n(s.speed, 1)).append(" km/h\n")
            detail.append(l.signal("voltage")).append("     ").append(l.n(s.voltage, 1)).append(" V\n")
        }
        RecordingService.advanced.toSortedMap().forEach { (key, value) -> detail.append(l.signal(key)).append("     ").append(value).append('\n') }
        val lastSoh = vehicle.soh
        if (lastSoh != null && !RecordingService.advanced.containsKey("soh")) {
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, l.locale).format(Date(vehicle.sohAt))
            detail.append(l.t("Darrer SOH", "Último SOH", "Last SOH")).append("     ")
                .append(l.n(lastSoh, 1)).append(" % · ").append(date).append('\n')
        }
        if (detail.isEmpty()) detail.append(l.t("Les dades apareixeran quan el vehicle respongui.", "Los datos aparecerán cuando responda el vehículo.", "Data appears when the vehicle responds."))
        advanced.text = detail.toString()
    }
    private fun permissions(): Boolean {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.BLUETOOTH_CONNECT)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { missing.add(Manifest.permission.ACCESS_FINE_LOCATION); missing.add(Manifest.permission.ACCESS_COARSE_LOCATION) }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS)
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 30); return false }
        return true
    }
    private fun connect() {
        if (RecordingService.running) { toast(l.t("El servei ja està actiu", "El servicio ya está activo", "Service is already active")); return }
        if (!permissions()) return
        if (activeAddress() == null) { chooseAdapter(); return }
        start(false)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == PLANNER_LOCATION_PERMISSION) {
            val screen = plannerScreen ?: return
            val request = screen.locationRequest ?: return
            if (results.any { it == PackageManager.PERMISSION_GRANTED }) locatePlannerOrigin()
            else screen.location(request, null)
            return
        }
        if (requestCode == OBD_SETTINGS_PERMISSION && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (page == 2) chooseAdapter(startRecording = false)
        } else if (requestCode == OBD_CAR_PERMISSION && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            chooseCarTrigger()
        } else if (requestCode == OBD_WAIT_NOTIFICATION && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            armObd()
        } else if (requestCode == 30) {
            if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (activeAddress() == null) chooseAdapter() else start(false)
            } else toast(l.status("permission"))
        }
    }
    private fun start(demo: Boolean, address: String? = null) {
        if (RecordingService.running) return
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { permissions(); return }
        try {
            val request = Intent(this, RecordingService::class.java).putExtra("demo", demo)
            if (address != null) request.putExtra(RecordingService.EXTRA_OBD, address)
            startForegroundService(request); page = 0; showPage()
        }
        catch (_: RuntimeException) { toast(l.status("permission")) }
    }
    private fun chooseAdapter(startRecording: Boolean = true) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        val target = store.vehicles.active().id
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (startRecording) permissions()
            else requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), OBD_SETTINGS_PERMISSION)
            return
        }
        val adapter = getSystemService(BluetoothManager::class.java).adapter
        if (adapter == null) { toast(l.t("Bluetooth no disponible", "Bluetooth no disponible", "Bluetooth unavailable")); return }
        if (!adapter.isEnabled) { startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)); return }
        val devices = adapter.bondedDevices.sortedBy { it.name ?: it.address }
        if (devices.isEmpty()) {
            AlertDialog.Builder(this).setMessage(l.t("Aparella primer l’ELM327 als ajustos Bluetooth d’Android.", "Empareja primero el ELM327 en los ajustes Bluetooth de Android.", "Pair the ELM327 in Android Bluetooth settings first."))
                .setPositiveButton("OK") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }.show()
            return
        }
        val names = devices.map { "${it.name}\n${it.address}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle(l.t("Tria l’ELM327 Bluetooth clàssic", "Elige el ELM327 Bluetooth clásico", "Select classic Bluetooth ELM327"))
            .setItems(names) { _, which ->
                if (RecordingService.running) { stopBeforeAdapterChange(); return@setItems }
                if (store.vehicles.active().id != target) return@setItems
                val device = devices[which]
                prefs.edit().putStringSet("obd_saved", rememberedAdapters() + device.address)
                    .putString("obd_name_${device.address}", device.name)
                    .apply()
                if (!startRecording) bindAdapter(device.address)
                else if (store.vehicles.matches(device.address).isNotEmpty()) start(false, device.address)
                else AlertDialog.Builder(this)
                    .setTitle(l.t("OBD sense perfil vinculat", "OBD sin perfil vinculado", "OBD without a linked profile"))
                    .setItems(arrayOf(l.t("Connecta només ara", "Conecta solo ahora", "Connect this time"),
                        l.t("Vincula al perfil actiu i connecta", "Vincula al perfil activo y conecta", "Link to active profile and connect"))) unbound@{ _, option ->
                        if (RecordingService.running) { stopBeforeAdapterChange(); return@unbound }
                        if (store.vehicles.active().id != target) return@unbound
                        if (option == 1) bindAdapter(device.address)
                        start(false, device.address)
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }.show()
    }
    private fun rememberedAdapters(): Set<String> {
        val stored = prefs.getStringSet("obd_saved", emptySet()).orEmpty()
        return stored + store.vehicles.addresses()
    }
    private fun activeAddress() = store.vehicles.active().obdAddress
    private fun adapterName(address: String) = prefs.getString("obd_name_$address", null) ?: address
    private fun stopBeforeAdapterChange() {
        AlertDialog.Builder(this).setMessage(l.t("Atura i desa el registre abans de canviar el perfil o l’OBD.",
            "Detén y guarda el registro antes de cambiar el perfil o el OBD.",
            "Stop and save recording before changing the profile or OBD."))
            .setPositiveButton(l.t("Atura i desa", "Detén y guarda", "Stop and save")) { _, _ ->
                if (RecordingService.running) startService(Intent(this, RecordingService::class.java).setAction("stop"))
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun chooseRememberedAdapter() {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        val target = store.vehicles.active().id
        val addresses = rememberedAdapters().sortedBy(::adapterName)
        if (addresses.isEmpty()) return
        val title = l.t("Vincula un OBD recordat", "Vincula un OBD recordado", "Link a saved OBD adapter")
        AlertDialog.Builder(this).setTitle(title)
            .setItems(addresses.map { "${adapterName(it)}\n$it" }.toTypedArray()) { _, which ->
                if (RecordingService.running) { stopBeforeAdapterChange(); return@setItems }
                if (store.vehicles.active().id == target) bindAdapter(addresses[which])
            }.show()
    }

    private fun bindAdapter(address: String?) {
        val vehicle = store.vehicles.active()
        saveVehicle(vehicle.copy(obdAddress = address))
    }

    private fun saveVehicle(vehicle: VehicleProfile) {
        val before = store.vehicles.addresses()
        store.vehicles.save(vehicle)
        ObdAutoStart.updateObservations(this, before, store.vehicles.addresses())
        BackupSync.schedule(this)
        showPage()
    }

    private fun selectVehicle(vehicle: VehicleProfile): Boolean {
        if (RecordingService.running) { stopBeforeAdapterChange(); return false }
        if (!store.vehicles.select(vehicle.id)) return false
        VehicleTelemetry.LIVE.resetVehicle(vehicle.id)
        RecordingService.state = if (vehicle.obdAddress == null) "unconfigured" else "disconnected"
        shownVehicleId = vehicle.id
        plannerScreen?.vehicleChanged(vehicle)
        showPage()
        return true
    }

    private fun receiveObdChoice(intent: Intent) {
        intent.getStringExtra(RecordingService.EXTRA_OBD)?.takeIf(VehicleProfiles::validAddress)?.let {
            RecordingService.pendingObdChoice = it
        }
        intent.removeExtra(RecordingService.EXTRA_OBD)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiveObdChoice(intent)
    }

    private fun offerObdChoice() {
        if (RecordingService.running || backupBusy || choosingObdProfile || isFinishing) return
        val address = RecordingService.pendingObdChoice ?: return
        val vehicles = store.vehicles.matches(address)
        if (vehicles.isEmpty()) { clearObdChoice(); return }
        choosingObdProfile = true
        AlertDialog.Builder(this).setTitle(l.t("Tria el vehicle per a l’OBD compartit", "Elige el vehículo para el OBD compartido",
            "Choose a vehicle for the shared OBD"))
            .setItems(vehicles.map { it.label }.toTypedArray()) { _, index ->
                clearObdChoice()
                if (selectVehicle(vehicles[index])) start(false, address)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> clearObdChoice() }
            .setOnCancelListener { clearObdChoice() }
            .show().setOnDismissListener { choosingObdProfile = false }
    }
    private fun clearObdChoice() {
        RecordingService.pendingObdChoice = null
        getSystemService(NotificationManager::class.java).cancel(RecordingService.CHOOSE_VEHICLE_NOTIFICATION)
    }

    private fun vehicleSettings() {
        val active = store.vehicles.active()
        body.addView(text(l.t("Perfils de vehicle", "Perfiles de vehículo", "Vehicle profiles"), 20, Color.WHITE))
        body.addView(text(l.t("Vehicle actiu · ${active.label}", "Vehículo activo · ${active.label}", "Active vehicle · ${active.label}"),
            17, Color.WHITE))
        body.addView(button(l.t("Tria el vehicle", "Elige el vehículo", "Choose vehicle")) {
            val profiles = store.vehicles.list().filterNot { it.archived }
            AlertDialog.Builder(this).setTitle(l.t("Tria el vehicle", "Elige el vehículo", "Choose vehicle"))
                .setSingleChoiceItems(profiles.map { it.label }.toTypedArray(), profiles.indexOfFirst { it.id == active.id }) { dialog, index ->
                    dialog.dismiss()
                    if (profiles[index].id != active.id) selectVehicle(profiles[index])
                }.setNegativeButton(android.R.string.cancel, null).show()
        })
        body.addView(button(l.t("Afegeix un perfil", "Añade un perfil", "Add a profile")) { editVehicle(null) })
        body.addView(button(l.t("Edita un perfil", "Edita un perfil", "Edit a profile")) {
            val profiles = store.vehicles.list()
            AlertDialog.Builder(this).setTitle(l.t("Edita un perfil", "Edita un perfil", "Edit a profile"))
                .setItems(profiles.map {
                    it.label + if (it.archived) l.t(" · Arxivat", " · Archivado", " · Archived") else ""
                }.toTypedArray()) { _, index -> editVehicle(profiles[index]) }
                .setNegativeButton(android.R.string.cancel, null).show()
        })
    }

    private fun editVehicle(vehicle: VehicleProfile?) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        fun field(label: String, value: String?) = EditText(this).apply field@{
            id = View.generateViewId()
            fields.addView(TextView(this@MainActivity).apply {
                text = label
                labelFor = this@field.id
                setPadding(0, dp(8), 0, 0)
            })
            setSingleLine()
            setText(value.orEmpty())
            fields.addView(this)
        }
        val name = field(l.t("Nom del perfil", "Nombre del perfil", "Profile name"), vehicle?.name)
        val model = field(l.t("Model (opcional)", "Modelo (opcional)", "Model (optional)"), vehicle?.model)
        val capacity = field(l.t("Capacitat útil de referència (kWh)", "Capacidad útil de referencia (kWh)", "Reference usable capacity (kWh)"),
            vehicle?.capacityKwh?.toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val power = field(l.t("Potència màxima de càrrega DC (kW)", "Potencia máxima de carga DC (kW)", "Maximum DC charging power (kW)"),
            vehicle?.maxDcChargeKw?.toString()).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        fields.addView(TextView(this).apply {
            text = l.t("Opcional. Limita la potència estimada als trams de bateria sense corba de càrrega apresa.",
                "Opcional. Limita la potencia estimada en los tramos de batería sin curva de carga aprendida.",
                "Optional. Caps estimated power for battery levels without a learned charging curve.")
        })
        val builder = AlertDialog.Builder(this).setTitle(l.t("Perfil de vehicle", "Perfil de vehículo", "Vehicle profile"))
            .setView(ScrollView(this).apply { addView(fields) }).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(l.t("Desa", "Guardar", "Save"), null)
        if (vehicle != null && vehicle.id != store.vehicles.active().id) {
            builder.setNeutralButton(if (vehicle.archived) l.t("Recupera", "Recuperar", "Restore")
                else l.t("Arxiva", "Archivar", "Archive")) { _, _ ->
                if (RecordingService.running) stopBeforeAdapterChange()
                else saveVehicle(vehicle.copy(archived = !vehicle.archived))
            }
        }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (RecordingService.running) { stopBeforeAdapterChange(); return@setOnClickListener }
            val label = name.text.toString().trim()
            val modelName = model.text.toString().trim().ifEmpty { null }
            val enteredCapacity = capacity.text.toString().trim().replace(',', '.')
            val kwh = enteredCapacity.toDoubleOrNull()
            val enteredPower = power.text.toString().trim().replace(',', '.')
            val kw = enteredPower.toDoubleOrNull()
            if (label.isEmpty() || label.length > 80) {
                name.error = l.t("Escriu un nom d’1 a 80 caràcters", "Escribe un nombre de 1 a 80 caracteres", "Enter a name of 1–80 characters")
                return@setOnClickListener
            }
            if (modelName != null && modelName.length > 80) {
                model.error = l.t("Màxim 80 caràcters", "Máximo 80 caracteres", "Maximum 80 characters")
                return@setOnClickListener
            }
            if (enteredCapacity.isNotEmpty() && (kwh == null || !kwh.isFinite() || kwh <= 0)) {
                capacity.error = l.t("Introdueix una capacitat positiva", "Introduce una capacidad positiva", "Enter a positive capacity")
                return@setOnClickListener
            }
            if (enteredPower.isNotEmpty() && (kw == null || !kw.isFinite() || kw <= 0)) {
                power.error = l.t("Introdueix una potència positiva", "Introduce una potencia positiva", "Enter a positive power")
                return@setOnClickListener
            }
            val saved = vehicle?.copy(name = label, model = modelName, capacityKwh = kwh, maxDcChargeKw = kw)
                ?: VehicleProfile(VehicleProfiles.newId(), label, modelName, kwh, null, maxDcChargeKw = kw)
            saveVehicle(saved)
            if (vehicle == null) selectVehicle(saved)
            dialog.dismiss()
        }
    }

    private fun settings() {
        vehicleSettings()
        body.addView(text(l.t("Idioma de l’aplicació i dels informes", "Idioma de la aplicación y de los informes", "App and report language"), 17, Color.WHITE))
        val codes = arrayOf("ca", "es", "en"); val names = arrayOf("Català", "Español", "English")
        val currentLanguage = codes.indexOf(l.locale.language).coerceAtLeast(0)
        body.addView(Spinner(this, Spinner.MODE_DROPDOWN).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, names)
            setSelection(currentLanguage)
            minimumHeight = dp(48)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position != currentLanguage) {
                        prefs.edit().putString("lang", codes[position]).apply()
                        plannerScreen?.let { screen ->
                            if (resumed) screen.mapView.onPause()
                            if (started) screen.mapView.onStop()
                            screen.close()
                        }
                        plannerScreen = null
                        build()
                    }
                }
            }
        })
        body.addView(text(l.t("Punts de recàrrega", "Puntos de recarga", "Charging points"), 20, Color.WHITE))
        body.addView(text(l.t("Font: NAP de la DGT. Actualització aproximadament setmanal quan hi ha connexió.",
            "Fuente: NAP de la DGT. Actualización aproximadamente semanal cuando hay conexión.",
            "Source: DGT National Access Point. Updates roughly weekly when connected."), 13, 0xFFB3C8BD.toInt()))
        chargingStatus = text("", 15, Color.WHITE).also(body::addView)
        chargingRefresh = button(l.t("Actualitza ara", "Actualizar ahora", "Update now")) {
            if (ChargingSync.request(this)) showChargingStatus()
            else chargingStatus?.text = l.t("No s’ha pogut programar l’actualització.",
                "No se ha podido programar la actualización.", "Could not schedule the update.")
        }.also(body::addView)
        showChargingStatus()
        val address = activeAddress()
        val activeAdapter = address?.let { "${adapterName(it)} · $it" }
        val obdTitle = l.t("Adaptador OBD actiu", "Adaptador OBD activo", "Active OBD adapter")
        val obdValue = activeAdapter ?: l.t("Cap adaptador seleccionat", "Ningún adaptador seleccionado", "No adapter selected")
        body.addView(text("$obdTitle · $obdValue", 17, Color.WHITE))
        body.addView(button(l.t("Afegeix un OBD aparellat", "Añadir un OBD emparejado", "Add a paired OBD adapter")) { chooseAdapter(false) })
        if (rememberedAdapters().isNotEmpty()) {
            body.addView(button(l.t("Vincula un OBD recordat", "Vincula un OBD recordado", "Link a saved OBD adapter"), ::chooseRememberedAdapter))
        }
        if (address != null) body.addView(button(l.t("Desvincula l’OBD d’aquest perfil", "Desvincula el OBD de este perfil",
            "Unlink OBD from this profile")) {
            if (RecordingService.running) stopBeforeAdapterChange() else bindAdapter(null)
        })
        body.addView(button(l.t("Prova amb dades simulades", "Probar con datos simulados", "Try simulated data")) { start(true) })
        val automatic = Switch(this).apply {
            setTextColor(Color.WHITE); text = l.t("Registre automàtic en connectar", "Registro automático al conectar", "Record automatically on connection")
            isChecked = prefs.getBoolean("auto", false)
            setOnCheckedChangeListener { _, on -> setAutomaticRecording(on) }
        }
        body.addView(automatic)
        if (Build.VERSION.SDK_INT >= 31) {
            body.addView(button(l.t("Associa l’OBD per a l’inici automàtic", "Asociar OBD para inicio automático", "Associate OBD for automatic start")) {
                val address = activeAddress()
                if (address == null) chooseAdapter(startRecording = false) else associate(address, false)
            })
            body.addView(button(l.t("Associa el Bluetooth del cotxe", "Asociar el Bluetooth del coche", "Associate the car’s Bluetooth"), ::chooseCarTrigger))
            prefs.getString(ObdAutoStart.CAR_ADDRESS, null)?.let { address ->
                val name = prefs.getString("auto_car_name", address)
                body.addView(text(l.t("Disparador del cotxe · $name", "Disparador del coche · $name", "Car trigger · $name"), 15, Color.WHITE))
                body.addView(button(l.t("Treu el disparador del cotxe", "Quitar el disparador del coche", "Remove car trigger")) {
                    prefs.edit().remove(ObdAutoStart.CAR_ADDRESS).remove("auto_car_name").apply()
                    if (address !in store.vehicles.addresses()) ObdAutoStart.observe(this, address, false)
                    showPage()
                })
            }
        }
        body.addView(button(l.t("Espera l’OBD durant 30 minuts", "Esperar al OBD durante 30 minutos", "Wait for OBD for 30 minutes"), ::armObd))
        body.addView(text(l.t("Amb el registre automàtic activat, una connexió Bluetooth de l’OBD o del cotxe pot iniciar el registre. Si l’ELM327 espera que el mòbil s’hi connecti i el cotxe no dona cap senyal, activa l’espera abans de sortir. L’espera manté una notificació i fa reintents amb la pantalla apagada.",
            "Con el registro automático activado, una conexión Bluetooth del OBD o del coche puede iniciar el registro. Si el ELM327 espera a que se conecte el móvil y el coche no da ninguna señal, activa la espera antes de salir. La espera mantiene una notificación y reintenta con la pantalla apagada.",
            "With automatic recording enabled, an OBD or car Bluetooth connection can start recording. If the ELM327 waits for the phone to connect and the car supplies no signal, turn on wait mode before leaving. It shows a notification and retries with the screen off."), 13, 0xFFB3C8BD.toInt()))
        body.addView(text(l.t("Android Auto: integració experimental provada al DHU. 'Fonts desconegudes' no habilita aquesta app al cotxe. La via oficial de prova al vehicle és instal·lar-la des de Google Play amb Compartició interna d'aplicacions o una prova interna. L'acceptació de PTrack al vehicle i de la seva categoria continua pendent.",
            "Android Auto: integración experimental probada en DHU. 'Fuentes desconocidas' no habilita esta app en el coche. La vía oficial de prueba en el vehículo es instalarla desde Google Play con Compartir aplicaciones de forma interna o una prueba interna. La aceptación de PTrack en el vehículo y de su categoría sigue pendiente.",
            "Android Auto: experimental integration tested with DHU. 'Unknown sources' does not enable this app in the car. The official vehicle testing route is installation through Google Play using Internal App Sharing or an internal test track. PTrack's acceptance in the vehicle and category eligibility remain unverified."), 13, 0xFFB3C8BD.toInt()))
        body.addView(text(l.t("Còpies de seguretat", "Copias de seguridad", "Backups"), 20, Color.WHITE))
        body.addView(text(l.t(
            "Tria Google Drive al selector per desar o recuperar la còpia. Inclou els viatges i les lectures. Importar afegeix els viatges de la còpia i conserva els que ja tens.",
            "Elige Google Drive en el selector para guardar o recuperar la copia. Incluye los viajes y las lecturas. Importar añade los viajes de la copia y conserva los que ya tienes.",
            "Choose Google Drive in the picker to save or restore a backup. It includes trips and readings. Import adds trips from the backup and keeps your existing ones."), 13, 0xFFB3C8BD.toInt()))
        body.addView(button(l.t("Exporta una còpia", "Exportar una copia", "Export backup")) { openBackup(BACKUP_EXPORT) })
        body.addView(button(l.t("Importa una còpia", "Importar una copia", "Import backup")) { openBackup(BACKUP_IMPORT) })
        backupDate = text(lastBackupText(), 15, Color.WHITE).also(body::addView)
        val hasDestination = BackupSync.destination(this) != null
        val sourceCount = BackupSync.sources(this).size
        val syncState = if (hasDestination) l.t("Còpia automàtica activada", "Copia automática activada", "Automatic backup enabled")
            else l.t("Còpia automàtica pendent de configurar", "Copia automática sin configurar", "Automatic backup not set up")
        body.addView(text("$syncState · " + l.t("Fitxers vinculats: $sourceCount", "Archivos vinculados: $sourceCount", "Linked files: $sourceCount"), 15, Color.WHITE))
        body.addView(text(l.t(
            "Crea un fitxer de còpia diferent per a cada dispositiu. Per sincronitzar-los, vincula el fitxer de l’altre dispositiu a cadascun.",
            "Crea un archivo de copia diferente para cada dispositivo. Para sincronizarlos, vincula el archivo del otro dispositivo en cada uno.",
            "Create a different backup file for each device. To sync them, link the other device’s file on each one."), 13, 0xFFB3C8BD.toInt()))
        body.addView(button(l.t("Configura la còpia automàtica", "Configurar la copia automática", "Set up automatic backup")) { openBackup(BACKUP_DESTINATION) })
        body.addView(button(l.t("Vincula la còpia d’un altre dispositiu", "Vincular la copia de otro dispositivo", "Link another device’s backup")) { openBackup(BACKUP_SOURCE) })
        if (hasDestination || sourceCount > 0) {
            body.addView(button(l.t("Sincronitza ara", "Sincronizar ahora", "Sync now")) { BackupSync.schedule(this) })
            body.addView(button(l.t("Desvincula la sincronització", "Desvincular la sincronización", "Unlink sync")) {
                BackupSync.unlink(this); showPage()
            })
        }
        body.addView(button(l.t("Llicències i atribució", "Licencias y atribución", "Licenses and attribution")) {
            try { assets.open("ESCO-LICENSE.txt").use { input -> AlertDialog.Builder(this).setTitle("Esco EV OBD Scanner")
                .setMessage("Car profile: Esa Pelttari, CC BY 4.0\nhttps://github.com/EsaHub/EscoEvObdScanner\nhttps://creativecommons.org/licenses/by/4.0/\n\n" + Streams.bytes(input).toString(Charsets.UTF_8))
                .setPositiveButton("OK", null).show() } } catch (_: IOException) { }
        })
    }
    private fun showChargingStatus() {
        val view = chargingStatus ?: return
        val state = ChargingSync.state(this)
        chargingRefresh?.isEnabled = state != ChargingSync.State.UPDATING && state != ChargingSync.State.QUEUED
        io.execute {
            val snapshot = runCatching { ChargingCatalog(applicationContext).use { it.snapshot() } }.getOrNull()
            runOnUiThread {
                if (isDestroyed || view !== chargingStatus || state != ChargingSync.state(this)) return@runOnUiThread
                val stored = if (snapshot == null) {
                    l.t("Encara no hi ha cap catàleg desat.", "Todavía no hay ningún catálogo guardado.", "No catalog saved yet.")
                } else {
                    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, l.locale)
                        .format(Date(snapshot.checkedAt))
                    val count = snapshot.sites
                    l.t("Darrera actualització · $date · $count emplaçaments",
                        "Última actualización · $date · $count ubicaciones",
                        "Last update · $date · $count sites")
                }
                val progress = when (state) {
                    ChargingSync.State.IDLE -> ""
                    ChargingSync.State.QUEUED -> l.t("Actualització en cua.", "Actualización en cola.", "Update queued.")
                    ChargingSync.State.UPDATING -> l.t("Actualitzant el catàleg…", "Actualizando el catálogo…", "Updating catalog…")
                    ChargingSync.State.FAILED -> l.t("Ha fallat l’última actualització.", "Falló la última actualización.", "The last update failed.")
                }
                view.text = listOf(stored, progress).filter(String::isNotEmpty).joinToString("\n")
            }
        }
    }
    private fun lastBackupText(): String {
        val timestamp = BackupSync.prefs(this).getLong(BackupSync.LAST_BACKUP, 0)
        val date = if (timestamp == 0L) l.t("encara cap", "todavía ninguna", "none yet")
            else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, l.locale).format(Date(timestamp))
        return l.t("Darrera còpia desada", "Última copia guardada", "Last backup saved") + " · $date"
    }
    private fun history() {
        if (!allVehicles) body.addView(text(store.vehicles.active().label, 17, Color.WHITE))
        body.addView(Switch(this).apply {
            text = l.t("Tots els vehicles", "Todos los vehículos", "All vehicles")
            setTextColor(Color.WHITE)
            isChecked = allVehicles
            setOnCheckedChangeListener { _, checked -> allVehicles = checked; showPage() }
        })
        val day = selectedDay
        val zone = ZoneId.systemDefault()
        if (day == null) {
            val month = if (annualCalendar) calendarMonth.withMonth(1) else calendarMonth
            val first = month.atDay(1)
            val end = (if (annualCalendar) month.plusYears(1) else month.plusMonths(1)).atDay(1)
            val counts = mutableMapOf<LocalDate, Int>()
            tripsBetween(first, end, zone).use { rows ->
                while (rows.moveToNext()) {
                    val date = Instant.ofEpochMilli(rows.getLong(1)).atZone(zone).toLocalDate()
                    counts[date] = (counts[date] ?: 0) + 1
                }
            }
            body.addView(TripCalendar(this, l).apply {
                render(calendarMonth, annualCalendar, counts, { target, annual ->
                    calendarMonth = target; annualCalendar = annual; showPage()
                }, { selectedDay = it; showPage() })
            })
            return
        }
        val date = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(l.locale))
        body.addView(text(date, 20, Color.WHITE))
        body.addView(button(l.t("Torna al calendari", "Volver al calendario", "Back to calendar")) {
            selectedDay = null; showPage()
        })
        var any = false
        tripsBetween(day, day.plusDays(1), zone).use { c -> while (c.moveToNext()) {
            any = true; val id = c.getLong(0); val demo = c.getInt(3) == 1; val state = c.getString(4)
            var name = DateFormat.getTimeInstance(DateFormat.SHORT, l.locale).format(Date(c.getLong(1))) + " · #$id"
            if (allVehicles) name += " · ${c.getString(5)}"
            if (demo) name += " · " + l.t("SIMULACIÓ", "SIMULACIÓN", "SIMULATION")
            if (state == "recording") name += " · " + l.t("en curs", "en curso", "recording")
            if (state == "interrupted") name += " · " + l.t("interromput", "interrumpido", "interrupted")
            body.addView(button(name) { trip(id) })
        } }
        if (!any) body.addView(text(l.t("Cap viatge aquest dia.", "Ningún viaje este día.", "No trips on this day."), 16, 0xFFB3C8BD.toInt()))
    }
    private fun tripsBetween(first: LocalDate, end: LocalDate, zone: ZoneId): Cursor = store.tripsBetween(
        first.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli(),
        if (allVehicles) null else store.vehicles.active().id
    )
    private fun trip(id: Long) {
        openedTrip = id
        body.removeAllViews(); body.addView(text(l.t("Carregant viatge…", "Cargando viaje…", "Loading trip…"), 17, Color.WHITE))
        io.execute {
            val stats = TripStats(); store.samples(id).forEach(stats::add)
            val info = TripSummaryText.describe(store.summary(id)) + "\n" + l.t("Pujada / baixada GPS", "Ascenso / descenso GPS", "GPS ascent / descent") + ": " +
                if (stats.hasAltitude) l.n(stats.ascent, 0) + " / " + l.n(stats.descent, 0) + " m" else "—"
            runOnUiThread {
                if (isFinishing || isDestroyed || page != 1 || openedTrip != id) return@runOnUiThread
                body.removeAllViews()
                val tripTitle = l.t("Viatge", "Viaje", "Trip") + " #$id · ${store.tripVehicle(id)}"
                body.addView(text(if (store.isDemo(id)) "$tripTitle · " + l.t("SIMULACIÓ", "SIMULACIÓN", "SIMULATION") else tripTitle, 20, Color.WHITE))
                body.addView(text(info, 18, 0xFFDBE8E0.toInt()))
                body.addView(button(l.t("Exporta informe PDF", "Exportar informe PDF", "Export PDF report")) { export(id, "pdf") })
                body.addView(button(l.t("Exporta registre complet JSON", "Exportar registro completo JSON", "Export full JSON log")) { export(id, "json") })
                body.addView(button(l.t("Torna als viatges", "Volver a viajes", "Back to trips"), ::historyReset))
            }
        }
    }
    private fun historyReset() { openedTrip = null; showPage() }
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        when {
            page == 1 && openedTrip != null -> historyReset()
            page == 1 && selectedDay != null -> { selectedDay = null; showPage() }
            page == 2 -> { page = 0; showPage() }
            else -> super.onBackPressed()
        }
    }
    private fun setAutomaticRecording(enabled: Boolean) {
        prefs.edit().putBoolean("auto", enabled).apply()
        observeAutomaticDevices(enabled)
    }

    private fun observeAutomaticDevices(enabled: Boolean) {
        val addresses = store.vehicles.addresses() + listOfNotNull(prefs.getString(ObdAutoStart.CAR_ADDRESS, null))
        addresses.forEach { ObdAutoStart.observe(this, it, enabled) }
    }

    private fun chooseCarTrigger() {
        if (activeAddress() == null) {
            chooseAdapter(startRecording = false)
            return
        }
        if (!ObdAutoStart.hasBluetoothPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), OBD_CAR_PERMISSION)
            return
        }
        val obd = store.vehicles.addresses()
        val devices = getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .filter { it.address !in obd }.sortedBy { it.name ?: it.address }
        if (devices.isEmpty()) {
            toast(l.t("Aparella primer el Bluetooth del cotxe als ajustos d’Android.", "Empareja primero el Bluetooth del coche en los ajustes de Android.", "Pair the car’s Bluetooth in Android settings first."))
            return
        }
        AlertDialog.Builder(this).setTitle(l.t("Tria el Bluetooth del cotxe", "Elige el Bluetooth del coche", "Select the car’s Bluetooth"))
            .setItems(devices.map { "${it.name ?: it.address}\n${it.address}" }.toTypedArray()) { _, which ->
                associate(devices[which].address, true)
            }.show()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    private fun associate(address: String, car: Boolean) {
        val manager = getSystemService(CompanionDeviceManager::class.java)
        if (manager == null || !packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            toast(l.t("Associació no disponible", "Asociación no disponible", "Association unavailable"))
            return
        }
        if (manager.associations.any { it.equals(address, ignoreCase = true) }) {
            completeAssociation(address, car)
            return
        }
        val request = AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build()).setSingleDevice(true).build()
        manager.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(sender: IntentSender) = showAssociation(sender)
            override fun onAssociationPending(intentSender: IntentSender) = showAssociation(intentSender)

            private fun showAssociation(sender: IntentSender) {
                pendingAssociation = address
                try {
                    startIntentSenderForResult(sender, if (car) ASSOCIATE_CAR else ASSOCIATE_OBD, null, 0, 0, 0)
                } catch (_: IntentSender.SendIntentException) {
                    pendingAssociation = null
                    onFailure(null)
                }
            }
            override fun onFailure(error: CharSequence?) {
                toast(l.t("No s’ha pogut associar el dispositiu", "No se ha podido asociar el dispositivo", "Device association failed"))
            }
        }, handler)
    }

    private fun completeAssociation(address: String, car: Boolean) {
        val obd = store.vehicles.addresses()
        if (if (car) address in obd else address !in obd) return
        if (!ObdAutoStart.observe(this, address, true)) {
            toast(l.t("No s’ha pogut activar la detecció", "No se ha podido activar la detección", "Presence detection could not be enabled"))
            return
        }
        val previousCar = prefs.getString(ObdAutoStart.CAR_ADDRESS, null)
        val edit = prefs.edit().putBoolean("auto", true)
        if (car) {
            val name = getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices
                ?.firstOrNull { it.address == address }?.name
            edit.putString(ObdAutoStart.CAR_ADDRESS, address).putString("auto_car_name", name ?: address)
        }
        edit.apply()
        if (car && previousCar != null && previousCar != address && previousCar !in obd) {
            ObdAutoStart.observe(this, previousCar, false)
        }
        observeAutomaticDevices(true)
        toast(l.t("Inici automàtic activat", "Inicio automático activado", "Automatic start enabled"))
        showPage()
    }

    private fun armObd() {
        if (RecordingService.running) {
            toast(l.t("El servei ja està actiu", "El servicio ya está activo", "Service is already active"))
            return
        }
        if (activeAddress() == null || !ObdAutoStart.hasBluetoothPermission(this)) {
            chooseAdapter(startRecording = false)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), OBD_WAIT_NOTIFICATION)
            return
        }
        try {
            startForegroundService(Intent(this, RecordingService::class.java)
                .setAction(RecordingService.ACTION_ARM_OBD).putExtra("background", true))
            page = 0
            showPage()
        } catch (_: RuntimeException) {
            toast(l.status("permission"))
        }
    }
    private fun export(id: Long, type: String) {
        if (id == RecordingService.activeTrip) { toast(l.t("Atura el viatge abans d’exportar", "Detén el viaje antes de exportar", "Stop the trip before exporting")); return }
        exportId = id; exportType = type
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).setType(if (type == "pdf") "application/pdf" else "application/json")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "ptrack-$id.$type")
        startActivityForResult(intent, 42)
    }
    private fun openBackup(request: Int) {
        if (backupBusy) return
        if (RecordingService.running && (request == BACKUP_EXPORT || request == BACKUP_IMPORT)) {
            showBackupRecordingMessage(); return
        }
        val intent = if (request == BACKUP_EXPORT || request == BACKUP_DESTINATION) {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            val prefix = if (request == BACKUP_DESTINATION) "PTrack-auto" else "PTrack"
            Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/gzip")
                .putExtra(Intent.EXTRA_TITLE, "$prefix-$stamp.ptrack.json.gz")
        } else Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*")
        try {
            startActivityForResult(intent.addCategory(Intent.CATEGORY_OPENABLE), request)
        } catch (_: ActivityNotFoundException) {
            toast(l.t("El selector de fitxers no està disponible", "El selector de archivos no está disponible", "The file picker is unavailable"))
        }
    }
    private fun showBackupRecordingMessage() = toast(l.t(
        "Atura el registre abans de continuar amb la còpia",
        "Detén el registro antes de continuar con la copia",
        "Stop recording before continuing with the backup"))

    private fun transferBackup(uri: Uri, exporting: Boolean) {
        if (backupBusy) return
        backupBusy = true
        toast(l.t("S’està processant la còpia…", "Procesando la copia…", "Processing backup…"))
        io.execute {
            val message = try {
                val temporary = File.createTempFile("ptrack-backup-", ".gz", cacheDir)
                try {
                    if (exporting) {
                        TripBackup.write(store, temporary)
                        val output = contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Backup destination unavailable")
                        output.use { out -> temporary.inputStream().use { Streams.copy(it, out) } }
                        BackupSync.prefs(this).edit().putLong(BackupSync.LAST_BACKUP, System.currentTimeMillis()).apply()
                        l.t("Còpia exportada", "Copia exportada", "Backup exported")
                    } else {
                        val input = contentResolver.openInputStream(uri) ?: throw IOException("Backup source unavailable")
                        input.use { source -> temporary.outputStream().use { Streams.copy(source, it) } }
                        val addresses = store.vehicles.addresses()
                        val count = TripBackup.read(store, temporary)
                        ObdAutoStart.updateObservations(this, addresses, store.vehicles.addresses())
                        BackupSync.schedule(this)
                        if (count == 0) l.t("Còpia importada · cap viatge nou", "Copia importada · ningún viaje nuevo", "Backup imported · no new trips")
                        else l.t("Viatges importats: $count", "Viajes importados: $count", "Trips imported: $count")
                    }
                } finally { temporary.delete() }
            } catch (_: TripBackup.RecordingInProgress) {
                null
            } catch (error: Exception) {
                Log.w("PTrackBackup", "Backup transfer failed", error)
                if (exporting) l.t("No s’ha pogut exportar la còpia", "No se ha podido exportar la copia", "Could not export backup")
                else l.t("No s’ha pogut importar la còpia", "No se ha podido importar la copia", "Could not import backup")
            }
            runOnUiThread {
                backupBusy = false
                if (message == null) showBackupRecordingMessage() else toast(message)
                if (!exporting && page == 1 && !isDestroyed) showPage()
            }
        }
    }
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request == ASSOCIATE_OBD || request == ASSOCIATE_CAR) {
            val address = pendingAssociation
            pendingAssociation = null
            if (result == RESULT_OK && address != null) completeAssociation(address, request == ASSOCIATE_CAR)
            return
        }
        val uri: Uri = data?.data ?: return
        if (result == RESULT_OK && request == OFFLINE_MAP_IMPORT) {
            importRoutingMap(uri)
            return
        }
        if (result == RESULT_OK && (request == BACKUP_DESTINATION || request == BACKUP_SOURCE)) {
            try {
                BackupSync.link(this, uri, request == BACKUP_DESTINATION)
                if (page == 2) showPage()
            } catch (error: RuntimeException) {
                Log.w("PTrackBackup", "Could not link a backup document", error)
                toast(l.t("El fitxer no permet l’accés automàtic", "El archivo no permite el acceso automático", "This file does not allow automatic access"))
            }
            return
        }
        if (result == RESULT_OK && (request == BACKUP_EXPORT || request == BACKUP_IMPORT)) {
            transferBackup(uri, request == BACKUP_EXPORT)
            return
        }
        if (request != 42 || result != RESULT_OK) return
        val id = exportId; val type = exportType
        if (id < 0 || type == null) { toast(l.t("Torna a iniciar l’exportació", "Vuelve a iniciar la exportación", "Start the export again")); return }
        io.execute {
            try { contentResolver.openOutputStream(uri, "wt").use { out ->
                if (out == null) throw IOException()
                if (type == "pdf") { val file = Report(this).generate(store, id); FileInputStream(file).use { Streams.copy(it, out) } }
                else exportJson(out, id)
                runOnUiThread { toast(l.t("Exportació completada", "Exportación completada", "Export complete")) }
            } } catch (_: Exception) { runOnUiThread { toast(l.t("No s’ha pogut exportar", "No se ha podido exportar", "Export failed")) } }
        }
    }
    private fun exportJson(out: OutputStream, id: Long) {
        val writer = JsonWriter(OutputStreamWriter(out, Charsets.UTF_8)); writer.setIndent("  ")
        writer.beginObject().name("schema").value(2).name("tripId").value(id).name("simulation").value(store.isDemo(id))
            .name("vehicle").value(store.tripVehicle(id)).name("profile").value("Stellantis e-CMP v4; Esa Pelttari CC-BY-4.0")
        for (table in listOf("trips", "samples", "readings", "trip_summaries")) {
            writer.name(table).beginArray(); val where = if (table == "trips") "id" else "trip"
            store.readableDatabase.rawQuery("SELECT * FROM $table WHERE $where=?", arrayOf(id.toString())).use { c ->
                while (c.moveToNext()) {
                    writer.beginObject()
                    for (i in 0 until c.columnCount) {
                        writer.name(c.getColumnName(i))
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> writer.nullValue()
                            Cursor.FIELD_TYPE_INTEGER -> writer.value(c.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> writer.value(c.getDouble(i))
                            Cursor.FIELD_TYPE_BLOB -> writer.value(android.util.Base64.encodeToString(c.getBlob(i), android.util.Base64.NO_WRAP))
                            Cursor.FIELD_TYPE_STRING -> writer.value(c.getString(i))
                            else -> throw IOException("Unsupported database value")
                        }
                    }
                    writer.endObject()
                }
            }
            writer.endArray()
        }
        writer.endObject(); writer.flush()
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }

    private companion object {
        const val ASSOCIATE_OBD = 43
        const val BACKUP_EXPORT = 44
        const val BACKUP_IMPORT = 45
        const val OBD_SETTINGS_PERMISSION = 46
        const val BACKUP_DESTINATION = 47
        const val BACKUP_SOURCE = 48
        const val OFFLINE_MAP_IMPORT = 49
        const val PLANNER_LOCATION_PERMISSION = 50
        const val ASSOCIATE_CAR = 51
        const val OBD_CAR_PERMISSION = 52
        const val OBD_WAIT_NOTIFICATION = 53
    }
}
