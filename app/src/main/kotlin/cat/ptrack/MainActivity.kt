package cat.ptrack

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.companion.*
import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.util.JsonWriter
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.*
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
    private var planning = false
    private var importingMap = false
    private var calendarMonth = YearMonth.now()
    private var annualCalendar = false
    private var selectedDay: LocalDate? = null
    private var openedTrip: Long? = null
    private var page = 0
    private val prefs get() = getSharedPreferences("settings", 0)
    private val syncListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BackupSync.LAST_BACKUP && page == 2) backupDate?.text = lastBackupText()
        if (key == BackupSync.HISTORY_CHANGED && page == 1 && openedTrip == null) showPage()
    }
    private val chargingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (page == 2 && key == ChargingSync.REVISION) showChargingStatus()
    }
    private val refresh = object : Runnable {
        override fun run() { if (page == 0) update(); handler.postDelayed(this, 750) }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); store = Store(this)
        if (state != null) {
            exportId = state.getLong("exportId", -1); exportType = state.getString("exportType"); page = state.getInt("page", 0)
            calendarMonth = state.getString("calendarMonth")?.let(YearMonth::parse) ?: calendarMonth
            annualCalendar = state.getBoolean("annualCalendar")
            selectedDay = state.getString("selectedDay")?.let(LocalDate::parse)
            openedTrip = state.getLong("openedTrip", -1).takeIf { it >= 0 }
        }
        l = Labels(this); build()
    }
    override fun onStart() {
        super.onStart()
        BackupSync.prefs(this).registerOnSharedPreferenceChangeListener(syncListener)
        ChargingSync.prefs(this).registerOnSharedPreferenceChangeListener(chargingListener)
        if (page == 2) showChargingStatus()
        if (page == 2) backupDate?.text = lastBackupText()
        if (page == 1 && openedTrip == null) showPage()
    }
    override fun onStop() {
        BackupSync.prefs(this).unregisterOnSharedPreferenceChangeListener(syncListener)
        ChargingSync.prefs(this).unregisterOnSharedPreferenceChangeListener(chargingListener)
        super.onStop()
    }
    override fun onResume() {
        super.onResume(); handler.post(refresh)
        BackupSync.schedule(this)
        ChargingSync.schedule(this)
        if (backupBusy) return
        if (RecordingService.running) startService(Intent(this, RecordingService::class.java).setAction("gps"))
        else if (prefs.getString("address", "").isNullOrEmpty()) RecordingService.state = "unconfigured"
        else if (prefs.getBoolean("auto", false) && (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)) {
            try { startForegroundService(Intent(this, RecordingService::class.java)) } catch (_: RuntimeException) { RecordingService.state = "permission" }
        }
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putLong("exportId", exportId); state.putString("exportType", exportType); state.putInt("page", page)
        state.putString("calendarMonth", calendarMonth.toString())
        state.putBoolean("annualCalendar", annualCalendar)
        state.putString("selectedDay", selectedDay?.toString())
        openedTrip?.let { state.putLong("openedTrip", it) }
        super.onSaveInstanceState(state)
    }
    override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
    override fun onDestroy() { io.shutdown(); super.onDestroy() }
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
        body.removeAllViews()
        chargingStatus = null
        chargingRefresh = null
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
        val mapInstalled = OfflineValhalla.installed(this)
        val mapStatus = if (mapInstalled) l.t("Mapa local disponible", "Mapa local disponible", "Local map available")
            else l.t("Importa un paquet ZIP de Valhalla per calcular rutes sense connexió.",
                "Importa un paquete ZIP de Valhalla para calcular rutas sin conexión.",
                "Import a Valhalla ZIP package to calculate routes offline.")
        body.addView(text(mapStatus, 15, Color.WHITE))
        body.addView(button(if (importingMap) l.t("Important el mapa…", "Importando el mapa…", "Importing map…")
            else l.t("Importa un mapa de rutes", "Importa un mapa de rutas", "Import a routing map")) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*"), OFFLINE_MAP_IMPORT)
        }.apply { isEnabled = !importingMap && !planning })
        body.addView(button(if (planning) l.t("Calculant la ruta…", "Calculando la ruta…", "Calculating route…")
            else l.t("Calcula una ruta elèctrica", "Calcula una ruta eléctrica", "Calculate an EV route")) {
            electricRouteForm(plans)
        }.apply { isEnabled = mapInstalled && !importingMap && !planning })
        body.addView(text(l.t("Navegació per trams", "Navegación por tramos", "Leg-by-leg navigation"), 20, Color.WHITE))
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
        body.addView(text("$heading · ${plan.completed + 1}/${plan.points.size}\n$point", 18, Color.WHITE))
        body.addView(button(l.t("Obre amb Waze", "Abrir con Waze", "Open in Waze")) {
            val url = TripNavigation.waze(point)
            openNavigation("com.waze", url, url)
        })
        body.addView(button(l.t("Obre amb Google Maps", "Abrir con Google Maps", "Open in Google Maps")) {
            openNavigation("com.google.android.apps.maps", TripNavigation.mapsNavigation(point),
                TripNavigation.mapsDirections(listOf(point), browser = true))
        })
        val route = TripNavigation.mapsDirections(plan.remaining, browser = false)
        if (plan.remaining.size > 1 && route != null) {
            body.addView(button(l.t("Previsualitza les parades a Google Maps", "Previsualizar las paradas en Google Maps",
                "Preview stops in Google Maps")) {
                openNavigation("com.google.android.apps.maps", route, TripNavigation.mapsDirections(plan.remaining, browser = true))
            })
        }
        body.addView(button(l.t("Marca el punt com a completat", "Marcar el punto como completado", "Mark point complete")) {
            plans.save(plan.advance())
            showPage()
        })
        body.addView(button(l.t("Edita els punts pendents", "Editar los puntos pendientes", "Edit remaining points")) {
            editNavigationPlan(plans, plan)
        })
        plan.remaining.drop(1).forEachIndexed { index, stop ->
            body.addView(text("${plan.completed + index + 2}. $stop", 15, Color.WHITE))
        }
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

    private fun electricRouteForm(plans: NavigationPlanStore) {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), 0, dp(18), 0) }
        fun field(label: String, value: String = ""): EditText {
            form.addView(text(label, 14, Color.WHITE))
            return EditText(this).apply {
                setSingleLine()
                setText(value)
                contentDescription = label
                form.addView(this)
            }
        }
        val origin = field(l.t("Origen (latitud,longitud)", "Origen (latitud,longitud)", "Origin (latitude,longitude)"))
        val destination = field(l.t("Destí (latitud,longitud)", "Destino (latitud,longitud)", "Destination (latitude,longitude)"))
        val vehicle = VehicleTelemetry.LIVE.snapshot()
        val liveSoc = if (vehicle.simulation) null else vehicle.current(SystemClock.elapsedRealtime())?.soc
        val start = field(l.t("Bateria inicial (%)", "Batería inicial (%)", "Starting battery (%)"),
            liveSoc?.takeIf { it in 0.0..100.0 }?.toString().orEmpty())
        val capacity = field(l.t("Capacitat útil (kWh)", "Capacidad útil (kWh)", "Usable capacity (kWh)"),
            prefs.getString("planner_capacity", "").orEmpty())
        val reserve = field(l.t("Reserva mínima (%)", "Reserva mínima (%)", "Minimum reserve (%)"), "10")
        val fallback = field(l.t("Consum de referència (kWh/100 km)", "Consumo de referencia (kWh/100 km)", "Fallback consumption (kWh/100 km)"),
            prefs.getString("planner_consumption", "").orEmpty())
        val minimum = field(l.t("Potència mínima del punt CCS (kW)", "Potencia mínima del punto CCS (kW)", "Minimum CCS charger power (kW)"), "50")
        val power = field(l.t("Potència màxima DC del cotxe (kW)", "Potencia máxima DC del coche (kW)", "Vehicle maximum DC power (kW)"),
            prefs.getString("planner_power", "").orEmpty())
        val modes = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf(
                l.t("Arribar abans", "Llegar antes", "Arrive sooner"),
                l.t("Carregar menys temps", "Cargar menos tiempo", "Spend less time charging")))
        }
        form.addView(modes)
        val dialog = AlertDialog.Builder(this).setTitle(l.t("Ruta elèctrica", "Ruta eléctrica", "EV route"))
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(l.t("Calcula", "Calcula", "Calculate"), null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun point(input: EditText): ElectricRoutePlanner.Point {
                val parts = input.text.toString().split(',')
                require(parts.size == 2)
                return ElectricRoutePlanner.Point(parts[0].trim().toDouble(), parts[1].trim().toDouble())
            }
            fun number(input: EditText, range: ClosedFloatingPointRange<Double>): Double =
                input.text.toString().trim().replace(',', '.').toDouble().also { require(it in range) }
            val from: ElectricRoutePlanner.Point
            val to: ElectricRoutePlanner.Point
            val soc: Double
            val kwh: Double
            val floor: Double
            val consumption: Double
            val minKw: Double
            val maxKw: Double
            try {
                from = point(origin); to = point(destination); require(from != to)
                soc = number(start, 0.0..100.0); kwh = number(capacity, 1.0..500.0)
                floor = number(reserve, 0.0..99.0); require(soc >= floor)
                consumption = number(fallback, 1.0..100.0)
                minKw = number(minimum, 0.0..1000.0); maxKw = number(power, 1.0..1000.0)
            } catch (_: Exception) {
                toast(l.t("Revisa les coordenades i els valors de bateria i càrrega.",
                    "Revisa las coordenadas y los valores de batería y carga.",
                    "Check the coordinates, battery and charging values."))
                return@setOnClickListener
            }
            val priority = ElectricRoutePlanner.Priority.entries[modes.selectedItemPosition]
            prefs.edit().putString("planner_capacity", kwh.toString())
                .putString("planner_consumption", consumption.toString()).putString("planner_power", maxKw.toString()).apply()
            dialog.dismiss()
            planning = true
            showPage()
            io.execute {
                val result = runCatching {
                    Store(applicationContext).use { history ->
                        ChargingCatalog(applicationContext).use { catalog ->
                            val profile = ConsumptionRepository(history).planningProfile()
                            OfflineValhalla.withEngine(applicationContext) { engine ->
                                ValhallaPlanner(engine, catalog, profile, consumption)
                                    .plan(from, to, kwh, soc, floor, minKw, maxKw, priority)
                            }
                        }
                    }
                }
                runOnUiThread {
                    planning = false
                    if (isDestroyed || page != 3) return@runOnUiThread
                    showPage()
                    val route = result.getOrNull()
                    if (route == null) {
                        result.exceptionOrNull()?.let { Log.w("PTrackPlanner", "Could not calculate route", it) }
                        val message = if (result.isFailure) l.t("Ha fallat el càlcul amb el mapa local.",
                            "Falló el cálculo con el mapa local.", "Routing with the local map failed.")
                        else l.t("Cap ruta viable. Revisa la cobertura del mapa, el catàleg i els filtres.",
                            "Ninguna ruta viable. Revisa la cobertura del mapa, el catálogo y los filtros.",
                            "No feasible route. Check map coverage, the charging catalog and filters.")
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    } else showElectricRoute(plans, route, to, kwh)
                }
            }
        }
    }

    private fun showElectricRoute(plans: NavigationPlanStore, route: ElectricRoutePlanner.Plan,
        destination: ElectricRoutePlanner.Point, capacity: Double) {
        fun percent(kwh: Double) = l.n(kwh / capacity * 100, 0) + "%"
        val summary = buildString {
            append(l.t("Durada prevista", "Duración prevista", "Estimated duration"))
            append(" · ${l.n(route.totalMinutes, 0)} min\n")
            append(l.t("Bateria al destí", "Batería al llegar", "Battery at destination"))
            append(" · ${percent(route.arrivalKwh)}\n")
            append(l.t("Distància amb consum de referència", "Distancia con consumo de referencia", "Distance using fallback consumption"))
            append(" · ${l.n(route.estimatedKm, 1)} km\n")
            for (charge in route.charges) {
                append("\n${charge.stop.name} · ${l.n(charge.stop.powerKw, 0)} kW\n")
                append("${percent(charge.arrivalKwh)} → ${percent(charge.departureKwh)} · ${l.n(charge.minutes, 0)} min\n")
            }
        }
        AlertDialog.Builder(this).setTitle(l.t("Itinerari estimat", "Itinerario estimado", "Estimated itinerary"))
            .setMessage(summary).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(l.t("Activa l’itinerari", "Activa el itinerario", "Activate itinerary")) { _, _ ->
                plans.save(NavigationPlan(route.charges.map { it.stop.point.toString() } + destination.toString()))
                showPage()
            }.show()
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
        status = text("", 15, 0xFFBCF46A.toInt()).also(body::addView)
        val gauges = LinearLayout(this)
        power = Gauge(this, l.t("Potència", "Potencia", "Power"), "kW", -80.0, 120.0)
        current = Gauge(this, l.signal("current"), "A", -200.0, 300.0)
        gauges.addView(power, LinearLayout.LayoutParams(0, dp(210), 1f)); gauges.addView(current, LinearLayout.LayoutParams(0, dp(210), 1f)); body.addView(gauges)
        charge = text("— %", 38, Color.WHITE).apply { gravity = Gravity.CENTER }; body.addView(charge)
        body.addView(text(l.t("+ consum   /   − energia cap a la bateria", "+ consumo   /   − energía hacia la batería", "+ discharge   /   − energy into battery"), 12, 0xFF9DB5AA.toInt()).apply { gravity = Gravity.CENTER })
        body.addView(button(l.t("Connecta OBD", "Conectar OBD", "Connect OBD"), ::connect))
        body.addView(button(l.t("Atura i desa el viatge", "Detener y guardar viaje", "Stop and save trip")) {
            prefs.edit().putBoolean("auto", false).apply()
            if (RecordingService.running) startService(Intent(this, RecordingService::class.java).setAction("stop"))
        })
        body.addView(text(l.t("TELEMETRIA DEL VEHICLE", "TELEMETRÍA DEL VEHÍCULO", "VEHICLE TELEMETRY"), 12, 0xFFBCF46A.toInt()))
        advanced = text("", 14, 0xFFDBE8E0.toInt()); body.addView(advanced); update()
    }
    private fun update() {
        val statusView = status ?: return
        if (!::power.isInitialized) return
        var key = RecordingService.state; var s = RecordingService.latest
        if (s != null && System.currentTimeMillis() - s.time > 10000) { s = null; key = "vehicle" }
        statusView.text = l.status(key); power.value(s?.power()); current.value(s?.current); charge.text = if (s == null) "— %" else l.n(s.soc, 1) + " %"
        val detail = StringBuilder()
        if (s != null) {
            detail.append(l.signal("speed")).append("     ").append(l.n(s.speed, 1)).append(" km/h\n")
            detail.append(l.signal("voltage")).append("     ").append(l.n(s.voltage, 1)).append(" V\n")
        }
        RecordingService.advanced.toSortedMap().forEach { (key, value) -> detail.append(l.signal(key)).append("     ").append(value).append('\n') }
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
        if (prefs.getString("address", "").isNullOrEmpty()) { chooseAdapter(); return }
        start(false)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == OBD_SETTINGS_PERMISSION && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (page == 2) chooseAdapter(startRecording = false)
        } else if (requestCode == 30) {
            if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                if (prefs.getString("address", "").isNullOrEmpty()) chooseAdapter() else start(false)
            } else toast(l.status("permission"))
        }
    }
    private fun start(demo: Boolean) {
        if (RecordingService.running) return
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { permissions(); return }
        prefs.edit().putBoolean("auto", !demo).apply()
        try { startForegroundService(Intent(this, RecordingService::class.java).putExtra("demo", demo)); page = 0; showPage() }
        catch (_: RuntimeException) { toast(l.status("permission")) }
    }
    private fun chooseAdapter(startRecording: Boolean = true) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
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
                val device = devices[which]
                prefs.edit().putStringSet("obd_saved", rememberedAdapters() + device.address)
                    .putString("obd_name_${device.address}", device.name)
                    .putString("address", device.address).apply()
                if (startRecording) start(false) else showPage()
            }.show()
    }
    private fun rememberedAdapters(): Set<String> {
        val stored = prefs.getStringSet("obd_saved", emptySet()).orEmpty()
        val active = prefs.getString("address", null)
        return if (active.isNullOrEmpty()) stored else stored + active
    }
    private fun adapterName(address: String) = prefs.getString("obd_name_$address", null) ?: address
    private fun stopBeforeAdapterChange() = toast(l.t("Atura abans el registre", "Detén antes el registro", "Stop recording first"))
    private fun manageAdapters(forget: Boolean) {
        if (RecordingService.running) { stopBeforeAdapterChange(); return }
        val addresses = rememberedAdapters().sortedBy(::adapterName)
        if (addresses.isEmpty()) return
        val title = if (forget) l.t("Oblida un adaptador OBD", "Olvidar un adaptador OBD", "Forget an OBD adapter")
            else l.t("Tria l’adaptador actiu", "Elige el adaptador activo", "Choose active adapter")
        AlertDialog.Builder(this).setTitle(title)
            .setItems(addresses.map { "${adapterName(it)}\n$it" }.toTypedArray()) { _, which ->
                if (RecordingService.running) { stopBeforeAdapterChange(); return@setItems }
                val address = addresses[which]
                val edit = prefs.edit()
                if (forget) {
                    edit.putStringSet("obd_saved", rememberedAdapters() - address).remove("obd_name_$address")
                    if (prefs.getString("address", null) == address) edit.remove("address").putBoolean("auto", false)
                } else edit.putStringSet("obd_saved", rememberedAdapters()).putString("address", address)
                edit.apply()
                showPage()
            }.show()
    }
    private fun settings() {
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
        val activeAdapter = prefs.getString("address", null)?.let(::adapterName)
        val obdTitle = l.t("Adaptador OBD actiu", "Adaptador OBD activo", "Active OBD adapter")
        val obdValue = activeAdapter ?: l.t("Cap adaptador seleccionat", "Ningún adaptador seleccionado", "No adapter selected")
        body.addView(text("$obdTitle · $obdValue", 17, Color.WHITE))
        body.addView(button(l.t("Afegeix un OBD aparellat", "Añadir un OBD emparejado", "Add a paired OBD adapter")) { chooseAdapter(false) })
        if (rememberedAdapters().isNotEmpty()) {
            body.addView(button(l.t("Tria un OBD recordat", "Elegir un OBD recordado", "Choose a saved OBD adapter")) { manageAdapters(false) })
            body.addView(button(l.t("Oblida un OBD recordat", "Olvidar un OBD recordado", "Forget a saved OBD adapter")) { manageAdapters(true) })
        }
        body.addView(button(l.t("Prova amb dades simulades", "Probar con datos simulados", "Try simulated data")) { start(true) })
        body.addView(button(l.t("Associa l’OBD per a l’inici automàtic", "Asociar OBD para inicio automático", "Associate OBD for automatic start"), ::associate))
        val automatic = Switch(this).apply {
            setTextColor(Color.WHITE); text = l.t("Registre automàtic en connectar", "Registro automático al conectar", "Record automatically on connection")
            isChecked = prefs.getBoolean("auto", false)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("auto", on).apply() }
        }
        body.addView(automatic)
        body.addView(text(l.t("Connexió: ELM327 Bluetooth clàssic (SPP). Activa el servei una vegada des del mòbil. Es reconnecta mentre és actiu i quan Android anuncia una connexió Bluetooth de l’adaptador seleccionat. Aparellar no implica una connexió activa. Algunes marques limiten l’execució en segon pla. No hi ha inici després de reiniciar el mòbil.",
            "Conexión: ELM327 Bluetooth clásico (SPP). Activa el servicio una vez desde el móvil. Se reconecta mientras está activo y cuando Android anuncia una conexión Bluetooth del adaptador elegido. Emparejar no implica conexión activa. Algunas marcas limitan la ejecución en segundo plano. No se inicia tras reiniciar el móvil.",
            "Connection: classic Bluetooth ELM327 (SPP). Start the service once on the phone. It retries while active and responds when Android announces a connection from the selected adapter. Pairing does not mean an active connection. Some manufacturers restrict background execution. No startup after reboot."), 13, 0xFFB3C8BD.toInt()))
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
            if (demo) name += " · " + l.t("SIMULACIÓ", "SIMULACIÓN", "SIMULATION")
            if (state == "recording") name += " · " + l.t("en curs", "en curso", "recording")
            if (state == "interrupted") name += " · " + l.t("interromput", "interrumpido", "interrupted")
            body.addView(button(name) { trip(id) })
        } }
        if (!any) body.addView(text(l.t("Cap viatge aquest dia.", "Ningún viaje este día.", "No trips on this day."), 16, 0xFFB3C8BD.toInt()))
    }
    private fun tripsBetween(first: LocalDate, end: LocalDate, zone: ZoneId): Cursor = store.tripsBetween(
        first.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli()
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
                val tripTitle = l.t("Viatge", "Viaje", "Trip") + " #$id"
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
    @Suppress("DEPRECATION")
    private fun associate() {
        val address = prefs.getString("address", "") ?: ""
        if (address.isEmpty()) { chooseAdapter(startRecording = false); return }
        val manager = getSystemService(CompanionDeviceManager::class.java)
        if (manager == null) { toast(l.t("Associació no disponible", "Asociación no disponible", "Association unavailable")); return }
        if (manager.associations.contains(address)) { observePresence(manager, address); return }
        val request = AssociationRequest.Builder().addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(address).build()).setSingleDevice(true).build()
        manager.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onDeviceFound(sender: IntentSender) {
                try { startIntentSenderForResult(sender, 43, null, 0, 0, 0) }
                catch (_: IntentSender.SendIntentException) { toast(l.t("No s’ha pogut associar", "No se ha podido asociar", "Association failed")) }
            }
            override fun onFailure(error: CharSequence?) { toast(l.t("No s’ha pogut associar l’OBD", "No se ha podido asociar el OBD", "OBD association failed")) }
        }, handler)
    }
    @Suppress("DEPRECATION")
    private fun observePresence(manager: CompanionDeviceManager, address: String) {
        try {
            if (Build.VERSION.SDK_INT >= 31) manager.startObservingDevicePresence(address)
            prefs.edit().putBoolean("auto", true).apply(); toast(l.t("Inici automàtic activat", "Inicio automático activado", "Automatic start enabled"))
        } catch (_: Exception) { toast(l.t("No s’ha pogut activar la detecció", "No se ha podido activar la detección", "Presence detection could not be enabled")) }
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
                        val count = TripBackup.read(store, temporary)
                        if (count > 0) BackupSync.schedule(this)
                        if (count == 0) l.t("La còpia no conté viatges nous", "La copia no contiene viajes nuevos", "The backup contains no new trips")
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
        if (request == 43 && result == RESULT_OK) {
            observePresence(getSystemService(CompanionDeviceManager::class.java), prefs.getString("address", "") ?: ""); return
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
            .name("vehicle").value("Peugeot e-2008 2020").name("profile").value("Stellantis e-CMP v4; Esa Pelttari CC-BY-4.0")
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
        const val BACKUP_EXPORT = 44
        const val BACKUP_IMPORT = 45
        const val OBD_SETTINGS_PERMISSION = 46
        const val BACKUP_DESTINATION = 47
        const val BACKUP_SOURCE = 48
        const val OFFLINE_MAP_IMPORT = 49
    }
}
