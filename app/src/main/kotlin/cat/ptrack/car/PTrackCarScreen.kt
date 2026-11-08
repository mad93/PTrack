package cat.ptrack.car

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import cat.ptrack.*
import kotlin.math.max
import kotlin.math.min

/** Renders shared telemetry and delegates OBD connection requests to RecordingService. */
class PTrackCarScreen private constructor(context: CarContext, private val details: Boolean) : Screen(context) {
    constructor(context: CarContext) : this(context, false)
    private val main = Handler(Looper.getMainLooper())
    private var visible = false
    private var queued = false
    private var rendered = 0L
    private var requested = 0L
    private val render = Runnable { queued = false; if (visible) { requested = SystemClock.elapsedRealtime(); invalidate() } }
    private val expire = Runnable { requestRender() }
    private val changed = Runnable { main.post { requestRender() } }
    private val plans by lazy { NavigationPlanStore(carContext) }
    private val planChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> changed.run() }
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                visible = true; VehicleTelemetry.LIVE.subscribe(changed); CarTestFeed.SOURCE.subscribe(changed); requestRender()
                if (details) plans.prefs.registerOnSharedPreferenceChangeListener(planChanged)
            }
            override fun onStop(owner: LifecycleOwner) {
                visible = false; VehicleTelemetry.LIVE.unsubscribe(changed); CarTestFeed.SOURCE.unsubscribe(changed)
                if (details) plans.prefs.unregisterOnSharedPreferenceChangeListener(planChanged)
                main.removeCallbacksAndMessages(null); queued = false
            }
        })
    }
    private fun navigateToNextPoint() {
        val point = plans.read()?.next ?: return
        val labels = Labels(carContext)
        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse(TripNavigation.carDestination(point))))
        } catch (error: RuntimeException) {
            Log.w("PTrackCar", "Navigation handoff declined by Android Auto", error)
            CarToast.makeText(carContext, labels.t("Obre el navegador des de PTrack al mòbil.",
                "Abre el navegador desde PTrack en el móvil.", "Open the navigator from PTrack on the phone."),
                CarToast.LENGTH_LONG).show()
        }
    }
    private fun requestRender() {
        if (!visible || queued) return
        queued = true
        main.postDelayed(render, max(0, 1000 - (SystemClock.elapsedRealtime() - max(rendered, requested))))
    }
    private fun connectObd(): String {
        if (VehicleTelemetry.LIVE.snapshot().obdConnected) return "OBD connectat"
        if (RecordingService.running && RecordingService.state == "demo") return "Atura la simulació al mòbil per connectar l’OBD"
        val address = Store(carContext).use { it.vehicles.active().obdAddress }
        if (address.isNullOrEmpty()) return "Configura l’adaptador OBD a PTrack al mòbil"
        if (Build.VERSION.SDK_INT >= 31 &&
            carContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return "Concedeix a PTrack el permís de Bluetooth al mòbil"
        try {
            val adapter = carContext.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter?.isEnabled != true) return "Activa el Bluetooth al mòbil"
            if (adapter.bondedDevices.none { it.address.equals(address, ignoreCase = true) }) {
                return "Aparella l’adaptador OBD al mòbil"
            }
            val request = Intent(carContext, RecordingService::class.java)
                .setAction(RecordingService.ACTION_CONNECT_OBD)
                .putExtra("background", true)
            if (RecordingService.running) carContext.startService(request)
            else carContext.startForegroundService(request)
            return "Connectant amb l’OBD…"
        } catch (error: RuntimeException) {
            Log.w("PTrackCar", "Could not start OBD connection from Android Auto", error)
            return "Inicia la connexió OBD des de PTrack al mòbil"
        }
    }
    @androidx.annotation.OptIn(markerClass = [androidx.car.app.annotations.ExperimentalCarApi::class])
    override fun onGetTemplate(): Template {
        rendered = SystemClock.elapsedRealtime(); main.removeCallbacks(render); queued = false
        val vehicle = CarTestFeed.snapshot()
        main.removeCallbacks(expire)
        if (visible && vehicle.current(rendered) != null) {
            var deadline = vehicle.receivedAt + VehicleTelemetry.FRESH_MS + 1
            if (vehicle.cable != null && vehicle.cableAt + VehicleTelemetry.FRESH_MS >= rendered) deadline = min(deadline, vehicle.cableAt + VehicleTelemetry.FRESH_MS + 1)
            if (vehicle.energy != null && vehicle.energyAt + VehicleTelemetry.FRESH_MS >= rendered) deadline = min(deadline, vehicle.energyAt + VehicleTelemetry.FRESH_MS + 1)
            main.postDelayed(expire, deadline - rendered)
        }
        Log.i("PTrackCar", "Dashboard requested · " + vehicle.connection(rendered) + if (vehicle.simulation) " · SIMULACIÓ" else "")
        val title = (if (details) "Trajecte" else "PTRACK") + if (vehicle.simulation) " · SIMULACIÓ" else ""
        if (details) {
            val items = ItemList.Builder()
            val plan = plans.read()
            plan?.next?.let { point ->
                val labels = Labels(carContext)
                items.addItem(Row.Builder()
                    .setTitle(labels.t("Navega al pròxim punt", "Navega al siguiente punto", "Navigate to next point"))
                    .addText(plan.label(point))
                    .setOnClickListener(ParkedOnlyOnClickListener.create(::navigateToNextPoint))
                    .build())
            }
            items.addItem(Row.Builder().setTitle("Estat del trajecte").addText(
                (if (vehicle.totals == null) "Sense trajecte" else if (vehicle.totals.active) "En curs" else "Finalitzat") + " · " + vehicle.connection(rendered) + " · " + vehicle.flow(rendered))
                .addText(GaugeRenderer.number(vehicle.instantaneous(rendered), 1) + " kWh/100 km · " + GaugeRenderer.number(vehicle.current(rendered)?.speed ?: Double.NaN, 0) + " km/h · BMS " + GaugeRenderer.number(vehicle.remaining(rendered), 1) + " kWh").build())
            val summary = vehicle.totals?.summary ?: TripStats().summary()
            val currentRange = vehicle.totals == null || !vehicle.totals.active || vehicle.remaining(rendered).isFinite()
            for ((sectionTitle, first, second) in TripSummaryText.sections(summary, currentRange)) items.addItem(Row.Builder().setTitle(sectionTitle).addText(first).addText(second).build())
            val header = Header.Builder().setTitle(title).setStartHeaderAction(Action.BACK).build()
            return ListTemplate.Builder().setHeader(header).setSingleList(items.build()).build()
        }
        val config = carContext.resources.configuration
        val width = config.screenWidthDp; val height = config.screenHeightDp; val api = carContext.carAppApiLevel
        val obdConnected = VehicleTelemetry.LIVE.snapshot().obdConnected
        val obdIcon = if (obdConnected) R.drawable.ic_obd_bluetooth_connected else R.drawable.ic_obd_bluetooth_off
        val obdTint = if (obdConnected) CarColor.BLUE
            else CarColor.createCustom(0xFF60666C.toInt(), 0xFF9AA0A6.toInt())
        val obdAction = Action.Builder()
            .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, obdIcon)).setTint(obdTint).build())
            .setOnClickListener {
                CarToast.makeText(carContext, connectObd(), CarToast.LENGTH_SHORT).show()
            }
            .build()
        val tripAction = Action.Builder().setTitle("Detalls del trajecte")
            .setOnClickListener(ParkedOnlyOnClickListener.create { screenManager.push(PTrackCarScreen(carContext, details = true)) }).build()
        val header = Header.Builder().setTitle(title).setStartHeaderAction(Action.APP_ICON)
            .addEndHeaderAction(obdAction).addEndHeaderAction(tripAction).build()
        if (api < 8) {
            val pane = Pane.Builder()
            for ((lineTitle, lineValue) in CarDashboard.primary(vehicle, rendered)) pane.addRow(Row.Builder().setTitle(lineTitle).addText(lineValue).build())
            return PaneTemplate.Builder(pane.build()).setHeader(header).build()
        }
        val names = arrayOf("Potència", "Bateria", "Intensitat", "Velocitat")
        val singleRow = width >= 600
        val size = if (singleRow || height >= 600) GridSection.ITEM_SIZE_LARGE else GridSection.ITEM_SIZE_MEDIUM
        val upper = GridSection.Builder().setItemSize(size); val lower = GridSection.Builder().setItemSize(size)
        for (i in 0..3) {
            val item = GridItem.Builder().setTitle(names[i]).setImage(GaugeRenderer.icon(i, vehicle, rendered), GridItem.IMAGE_TYPE_LARGE).build()
            (if (singleRow || i < 2) upper else lower).addItem(item)
        }
        Log.i("PTrackCar", "Gauge layout=${if (singleRow) "4" else "2+2"} host api=$api sizeDp=${width}x$height itemSize=$size")
        val dashboard = SectionedItemTemplate.Builder().setHeader(header).addSection(upper.build())
        if (!singleRow) dashboard.addSection(lower.build())
        return dashboard.build()
    }
}
