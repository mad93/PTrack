package cat.ptrack

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.util.JsonWriter
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import java.io.*
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var labels by mutableStateOf<Labels?>(null)
    private val l get() = checkNotNull(labels)
    private lateinit var store: Store
    private lateinit var tripHistory: TripHistory
    private var historyRevision by mutableStateOf(0)
    private var liveState by mutableStateOf<LiveUiState?>(null)
    private var exportId = -1L
    private var exportType: String? = null
    private var backupBusy = false
    private var settingsState by mutableStateOf<SettingsState?>(null)
    private var vehicleEditor by mutableStateOf<VehicleEditorRequest?>(null)
    private var chargingRequest = 0
    private var plannerScreen by mutableStateOf<PlannerScreen?>(null)
    private var savedPlannerItinerary by mutableStateOf<NavigationPlan?>(null)
    private var started = false
    private var resumed = false
    private var importingMap = false
    private var calendarMonth by mutableStateOf(YearMonth.now())
    private var annualCalendar by mutableStateOf(false)
    private var selectedDay by mutableStateOf<LocalDate?>(null)
    private var openedTrip by mutableStateOf<Long?>(null)
    private var allVehicles by mutableStateOf(false)
    private lateinit var shownVehicleId: String
    private var choosingObdProfile = false
    private var page by mutableStateOf(0)
    private var pendingAssociation: String? = null
    private val prefs get() = getSharedPreferences("settings", 0)
    private val syncListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BackupSync.LAST_BACKUP && page == 2) {
            settingsState = settingsState?.copy(backupDate = lastBackupText())
        }
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
                if (page == 1 && !allVehicles) openedTrip = null
                if (page == 2 || page == 1) showPage()
            }
            shownVehicleId = vehicle.id
            if (page == 0) update(vehicle)
            offerObdChoice()
            handler.postDelayed(this, 750)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); store = Store(this)
        tripHistory = TripHistory(store)
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
        labels = Labels(this)
        build()
        if (prefs.getBoolean("auto", false)) observeAutomaticDevices(true)
    }
    override fun onStart() {
        super.onStart()
        started = true
        plannerScreen?.mapView?.onStart()
        BackupSync.prefs(this).registerOnSharedPreferenceChangeListener(syncListener)
        ChargingSync.prefs(this).registerOnSharedPreferenceChangeListener(chargingListener)
        if (page == 2) settings()
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
    private fun build() {
        enableEdgeToEdge()
        showPage()

        val buildInfo = "PTrack ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.BUILD_TYPE}" +
            " · Android Auto " + (if (BuildConfig.CAR_ENABLED) "experimental" else "—") + "\n" +
            "${BuildConfig.SOURCE_BRANCH} @ ${BuildConfig.SOURCE_COMMIT} · ${BuildConfig.SOURCE_STATE}\nBuild UTC: ${BuildConfig.BUILD_TIME_UTC}"

        setContent {
            BackHandler(
                enabled = page == Destination.SETTINGS.id ||
                    (page == Destination.TRIPS.id && (openedTrip != null || selectedDay != null)),
                onBack = ::goBack
            )
            PTrackTheme {
                PTrackScaffold(page, l, ::selectDestination) {
                    if (page == Destination.LIVE.id) {
                        liveState?.let { state ->
                            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                                LiveScreen(state, l, ::connect, ::stopLiveRecording)
                            }
                        }
                    } else if (page == Destination.TRIPS.id) {
                        val revision = historyRevision
                        val vehicle = remember(revision) { store.vehicles.active() }
                        TripsScreen(
                            TripsSelection(calendarMonth, annualCalendar, selectedDay, openedTrip, allVehicles),
                            vehicle, tripHistory, revision, l,
                            onFilter = {
                                allVehicles = it
                                openedTrip = null
                            },
                            onPeriod = { month, annual ->
                                calendarMonth = month
                                annualCalendar = annual
                            },
                            onDay = {
                                selectedDay = it
                                openedTrip = null
                            },
                            onTrip = { openedTrip = it },
                            onExport = ::export
                        )
                    } else if (page == Destination.PLANNER.id) {
                        plannerScreen?.Content {
                            val store = NavigationPlanStore(this@MainActivity)
                            val plan = savedPlannerItinerary
                            SavedPlannerItinerary(
                                plan, l,
                                onEdit = { editNavigationPlan(store, plan?.takeIf { it.next != null }) },
                                onOpen = { maps -> plan?.let { openItinerary(it, maps) } },
                                onCharger = ::openCharger,
                                onAdvance = {
                                    plan?.let { store.save(it.advance()) }
                                    savedPlannerItinerary = store.read()
                                }
                            )
                        }
                    } else {
                        settingsState?.let { state ->
                            SettingsScreen(state, buildInfo, l, ::settingsAction, ::setAutomaticRecording, ::changeLanguage)
                        }
                        vehicleEditor?.let { request ->
                            VehicleEditor(request, l, onDismiss = { vehicleEditor = null },
                                onSave = { finishVehicleEdit(request, it) }, onArchive = ::archiveVehicle)
                        }
                    }
                }
            }
        }
    }

    private fun selectDestination(destination: Destination) {
        page = destination.id
        if (destination != Destination.SETTINGS) {
            selectedDay = null
            openedTrip = null
        }
        if (destination == Destination.TRIPS && !RecordingService.running) {
            io.execute {
                store.recover()
                runOnUiThread {
                    if (page == Destination.TRIPS.id && openedTrip == null && !isDestroyed) showPage()
                }
            }
        }
        showPage()
    }

    private fun showPage() {
        if (page != 3) plannerScreen?.leave()
        when (page) {
            0 -> update()
            1 -> historyRevision++
            3 -> planner()
            else -> settings()
        }
    }
    private fun planner() {
        val plans = NavigationPlanStore(this)
        savedPlannerItinerary = plans.read()
        val screen = plannerScreen ?: PlannerScreen(this, l, importMap = {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), OFFLINE_MAP_IMPORT)
        }, locate = ::locatePlannerOrigin, navigate = { draft, maps ->
            val saved = plans.read()
            val itinerary = saved?.takeIf {
                it.points == draft.points && it.next != null && (it.origin == null || it.origin == draft.origin)
            }?.let { draft.copy(completed = it.completed) } ?: draft
            plans.save(itinerary)
            savedPlannerItinerary = itinerary
            openItinerary(itinerary, maps)
        }, showCharger = ::openCharger).also {
            plannerScreen = it
            if (started) it.mapView.onStart()
            if (resumed) it.mapView.onResume()
        }
        screen.vehicleChanged(store.vehicles.active())
        screen.mapStatus(OfflineValhalla.installed(this), importingMap)
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



    private fun stopLiveRecording() {
        setAutomaticRecording(false)
        if (RecordingService.running) {
            startService(Intent(this, RecordingService::class.java).setAction("stop"))
        }
    }

    private fun update(vehicle: VehicleProfile = store.vehicles.active()) {
        var key = RecordingService.state
        var sample = RecordingService.latest
        if (sample != null && System.currentTimeMillis() - sample.time > 10000) {
            sample = null
            key = "vehicle"
        }
        val readings = RecordingService.advanced
        val detail = buildList {
            if (sample != null) {
                add(l.signal("speed") to (l.n(sample.speed, 1) + " km/h"))
                add(l.signal("voltage") to (l.n(sample.voltage, 1) + " V"))
            }
            readings.toSortedMap().forEach { (signal, value) ->
                add(l.signal(signal) to value)
            }
            val lastSoh = vehicle.soh
            if (lastSoh != null && !readings.containsKey("soh")) {
                val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, l.locale)
                    .format(Date(vehicle.sohAt))
                add(l.t("Darrer SOH", "Último SOH", "Last SOH") to (l.n(lastSoh, 1) + " % · " + date))
            }
        }
        liveState = LiveUiState(vehicle.label, key, sample?.soc, sample?.power(), sample?.current, detail)
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
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PLANNER_LOCATION_PERMISSION) {
            val screen = plannerScreen ?: return
            val request = screen.locationRequest ?: return
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) locatePlannerOrigin()
            else screen.location(request, null)
            return
        }
        if (requestCode == OBD_SETTINGS_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (page == 2) chooseAdapter(startRecording = false)
        } else if (requestCode == OBD_CAR_PERMISSION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            chooseCarTrigger()
        } else if (requestCode == OBD_WAIT_NOTIFICATION && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
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

    private fun chooseVehicle() {
        val active = store.vehicles.active()
        val profiles = store.vehicles.list().filterNot { it.archived }
        AlertDialog.Builder(this).setTitle(l.t("Tria el vehicle", "Elige el vehículo", "Choose vehicle"))
            .setSingleChoiceItems(profiles.map { it.label }.toTypedArray(), profiles.indexOfFirst { it.id == active.id }) { dialog, index ->
                dialog.dismiss()
                if (profiles[index].id != active.id) selectVehicle(profiles[index])
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseVehicleToEdit() {
        val profiles = store.vehicles.list()
        AlertDialog.Builder(this).setTitle(l.t("Edita un perfil", "Edita un perfil", "Edit a profile"))
            .setItems(profiles.map {
                it.label + if (it.archived) l.t(" · Arxivat", " · Archivado", " · Archived") else ""
            }.toTypedArray()) { _, index -> editVehicle(profiles[index]) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun editVehicle(vehicle: VehicleProfile?) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        vehicleEditor = VehicleEditorRequest(vehicle, vehicle != null && vehicle.id != store.vehicles.active().id)
    }

    private fun finishVehicleEdit(request: VehicleEditorRequest, vehicle: VehicleProfile) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        saveVehicle(vehicle)
        if (request.vehicle == null) selectVehicle(vehicle)
        vehicleEditor = null
    }

    private fun archiveVehicle(vehicle: VehicleProfile) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        if (vehicle.id == store.vehicles.active().id) return
        saveVehicle(vehicle.copy(archived = !vehicle.archived))
        vehicleEditor = null
    }

    private fun settings() {
        val active = store.vehicles.active()
        val carAddress = prefs.getString(ObdAutoStart.CAR_ADDRESS, null)
        settingsState = SettingsState(
            vehicle = active.label,
            adapter = active.obdAddress?.let { "${adapterName(it)} · $it" },
            hasRememberedAdapters = rememberedAdapters().isNotEmpty(),
            automaticRecording = prefs.getBoolean("auto", false),
            companionSupported = Build.VERSION.SDK_INT >= 31,
            carTrigger = carAddress?.let { prefs.getString("auto_car_name", it) },
            backupDate = lastBackupText(),
            backupConfigured = BackupSync.destination(this) != null,
            linkedBackups = BackupSync.sources(this).size,
            chargingText = settingsState?.chargingText.orEmpty()
        )
        showChargingStatus()
    }

    private fun settingsAction(action: SettingsAction) {
        when (action) {
            SettingsAction.CHOOSE_VEHICLE -> chooseVehicle()
            SettingsAction.ADD_VEHICLE -> editVehicle(null)
            SettingsAction.EDIT_VEHICLE -> chooseVehicleToEdit()
            SettingsAction.ADD_ADAPTER -> chooseAdapter(startRecording = false)
            SettingsAction.LINK_ADAPTER -> chooseRememberedAdapter()
            SettingsAction.UNLINK_ADAPTER -> if (RecordingService.running) stopBeforeAdapterChange() else bindAdapter(null)
            SettingsAction.SIMULATE -> start(true)
            SettingsAction.ASSOCIATE_OBD -> {
                val address = activeAddress()
                if (address == null) chooseAdapter(startRecording = false) else associate(address, false)
            }
            SettingsAction.ASSOCIATE_CAR -> chooseCarTrigger()
            SettingsAction.REMOVE_CAR -> {
                val address = prefs.getString(ObdAutoStart.CAR_ADDRESS, null) ?: return
                prefs.edit().remove(ObdAutoStart.CAR_ADDRESS).remove("auto_car_name").apply()
                if (address !in store.vehicles.addresses()) ObdAutoStart.observe(this, address, false)
                showPage()
            }
            SettingsAction.WAIT_OBD -> armObd()
            SettingsAction.EXPORT_BACKUP -> openBackup(BACKUP_EXPORT)
            SettingsAction.IMPORT_BACKUP -> openBackup(BACKUP_IMPORT)
            SettingsAction.SET_BACKUP -> openBackup(BACKUP_DESTINATION)
            SettingsAction.LINK_BACKUP -> openBackup(BACKUP_SOURCE)
            SettingsAction.SYNC_BACKUP -> BackupSync.schedule(this)
            SettingsAction.UNLINK_BACKUP -> {
                BackupSync.unlink(this)
                showPage()
            }
            SettingsAction.UPDATE_CHARGING -> {
                if (ChargingSync.request(this)) showChargingStatus()
                else {
                    chargingRequest++
                    settingsState = settingsState?.copy(chargingText = l.t("No s’ha pogut programar l’actualització.",
                        "No se ha podido programar la actualización.", "Could not schedule the update."))
                }
            }
            SettingsAction.LICENSES -> showLicenses()
        }
    }

    private fun changeLanguage(code: String) {
        if (code == l.locale.language) return
        prefs.edit().putString("lang", code).apply()
        plannerScreen?.let { screen ->
            if (resumed) screen.mapView.onPause()
            if (started) screen.mapView.onStop()
            screen.close()
        }
        plannerScreen = null
        labels = Labels(this)
        settingsState = null
        showPage()
    }

    private fun showLicenses() {
        try {
            assets.open("ESCO-LICENSE.txt").use { input ->
                AlertDialog.Builder(this).setTitle("Esco EV OBD Scanner")
                    .setMessage("Car profile: Esa Pelttari, CC BY 4.0\nhttps://github.com/EsaHub/EscoEvObdScanner\nhttps://creativecommons.org/licenses/by/4.0/\n\n" + Streams.bytes(input).toString(Charsets.UTF_8))
                    .setPositiveButton("OK", null).show()
            }
        } catch (_: IOException) { }
    }

    private fun showChargingStatus() {
        val current = settingsState ?: return
        val request = ++chargingRequest
        val state = ChargingSync.state(this)
        settingsState = current.copy(chargingBusy = state == ChargingSync.State.UPDATING || state == ChargingSync.State.QUEUED)
        io.execute {
            val snapshot = runCatching { ChargingCatalog(applicationContext).use { it.snapshot() } }.getOrNull()
            runOnUiThread {
                if (isDestroyed || page != 2 || request != chargingRequest || state != ChargingSync.state(this)) return@runOnUiThread
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
                settingsState = settingsState?.copy(chargingText = listOf(stored, progress)
                    .filter(String::isNotEmpty).joinToString("\n"))
            }
        }
    }
    private fun lastBackupText(): String {
        val timestamp = BackupSync.prefs(this).getLong(BackupSync.LAST_BACKUP, 0)
        val date = if (timestamp == 0L) l.t("encara cap", "todavía ninguna", "none yet")
            else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, l.locale).format(Date(timestamp))
        return l.t("Darrera còpia desada", "Última copia guardada", "Last backup saved") + " · $date"
    }
    private fun goBack() {
        when {
            page == 1 && openedTrip != null -> openedTrip = null
            page == 1 && selectedDay != null -> selectedDay = null
            page == 2 -> { page = 0; showPage() }
        }
    }

    private fun setAutomaticRecording(enabled: Boolean) {
        prefs.edit().putBoolean("auto", enabled).apply()
        settingsState = settingsState?.copy(automaticRecording = enabled)
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
        val paired = pairedDeviceNames()
        if (paired == null) {
            toast(l.status("permission"))
            return
        }
        val devices = paired.entries.filter { it.key !in obd }.sortedBy { it.value }
        if (devices.isEmpty()) {
            toast(l.t("Aparella primer el Bluetooth del cotxe als ajustos d’Android.", "Empareja primero el Bluetooth del coche en los ajustes de Android.", "Pair the car’s Bluetooth in Android settings first."))
            return
        }
        AlertDialog.Builder(this).setTitle(l.t("Tria el Bluetooth del cotxe", "Elige el Bluetooth del coche", "Select the car’s Bluetooth"))
            .setItems(devices.map { "${it.value}\n${it.key}" }.toTypedArray()) { _, which ->
                associate(devices[which].key, true)
            }.show()
    }

    private fun pairedDeviceNames(): Map<String, String>? = try {
        getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices.orEmpty()
            .associate { device -> device.address to (device.name ?: device.address) }
    } catch (_: SecurityException) {
        null
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
            val name = pairedDeviceNames()?.get(address)
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
