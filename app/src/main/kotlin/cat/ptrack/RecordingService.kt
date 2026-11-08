package cat.ptrack

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.util.Log
import cat.ptrack.ObdSessionTiming.ARMED_WINDOW_MS
import cat.ptrack.ObdSessionTiming.TRIP_GAP_MS
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.sin

class RecordingService : Service() {
    @Volatile private var stopping = false
    @Volatile private var retryRequested = false
    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var location: Location? = null
    private lateinit var store: Store
    private var demo = false
    private var automatic = false
    private var armed = false
    private lateinit var vehicleId: String
    @Volatile private var requestedAddress: String? = null
    private var savedSoh: Double? = null
    private var savedSohAt = 0L
    private var startedAt = 0L
    @Volatile private var lastValid = 0L
    private var dcSession = 0L
    private var lastDcAt = 0L
    private var locations: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var foregroundNotification: Notification
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val idleTimeout = object : Runnable {
        override fun run() {
            val remaining = synchronized(this@RecordingService) {
                idleRemaining(SystemClock.elapsedRealtime()).also { if (it <= 0) stopping = true }
            }
            if (remaining > 0) handler.postDelayed(this, remaining)
            else {
                Log.d(ObdAutoStart.TAG, "OBD session timed out")
                closeSocket()
            }
        }
    }
    private val listener = object : LocationListener { override fun onLocationChanged(fix: Location) { location = fix } }
    override fun onCreate() { super.onCreate(); store = Store(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopping = true; closeSocket(); return START_NOT_STICKY }
        if (intent?.action == "gps") { if (running) enableGps(); return START_NOT_STICKY }
        if (running) {
            if (intent?.action == ACTION_CONNECT_OBD && !demo && !stopping) retryRequested = true
            if (intent?.action == ACTION_AUTO_OBD && !stopping) {
                val address = intent.getStringExtra(EXTRA_OBD)
                val displaced = synchronized(this) {
                    if (address == null || !waitingForObd) null
                    else {
                        requestedAddress = address
                        waitingForObd = false
                        retryRequested = true
                        socket
                    }
                }
                try { displaced?.close() } catch (_: Exception) { }
            }
            return START_NOT_STICKY
        }
        demo = intent?.getBooleanExtra("demo", false) == true
        automatic = intent?.action == ACTION_AUTO_OBD
        armed = intent?.action == ACTION_ARM_OBD
        requestedAddress = intent?.getStringExtra(EXTRA_OBD)
        val labels = Labels(this)
        val channel = NotificationChannel("trip", labels.t("Registre de viatge", "Registro de viaje", "Trip recording"), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val description = if (armed) labels.t("Espera i registre OBD", "Espera y registro OBD", "OBD wait and recording")
            else labels.t("Connexió OBD i registre automàtic", "Conexión OBD y registro automático", "OBD connection and automatic recording")
        val notification = Notification.Builder(this, "trip").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PTrack").setContentText(description)
            .setContentIntent(open).addAction(Notification.Action.Builder(null, labels.t("Atura", "Detener", "Stop"), stop).build()).setOngoing(true).build()
        foregroundNotification = notification
        val foreground = intent == null || !intent.getBooleanExtra("background", false)
        val gps = foreground && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(10, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                if (gps) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
            else startForeground(10, notification)
        } catch (_: RuntimeException) { setState("permission"); stopSelf(); return START_NOT_STICKY }
        if (gps) enableGps()
        startedAt = SystemClock.elapsedRealtime()
        lastValid = 0L
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PTrack:recording").apply {
            setReferenceCounted(false)
            acquire(if (armed) ARMED_WINDOW_MS else 60_000)
        }
        waitingForObd = (automatic || armed) && requestedAddress == null
        running = true; stopping = false; latest = null; advanced.clear()
        if (automatic || armed) {
            Log.d(ObdAutoStart.TAG, if (armed) "OBD wait mode started" else "Automatic OBD session started")
            handler.postDelayed(idleTimeout, idleRemaining(startedAt))
        }
        worker.execute(::run)
        return START_NOT_STICKY
    }
    private fun run() {
        try {
            val prefs = getSharedPreferences("settings", 0)
            if (automatic && !prefs.getBoolean("auto", false)) return
            store.recover()
            val initialVehicle = store.vehicles.active()
            vehicleId = initialVehicle.id
            VehicleTelemetry.LIVE.resetVehicle(vehicleId)
            if (demo) { simulate(); return }
            val profile = Profile(this)
            val extras = profile.signals.values.filter { it.key !in listOf("current", "voltage", "soc", "speed") }
            var extra = 0; var rawIndex = 0; var cycles = 0
            var confirmedAddress: String? = null
            while (!stopping) {
                if (automatic && activeTrip < 0 && !prefs.getBoolean("auto", false)) break
                retryRequested = false
                try {
                    setState("connecting")
                    val adapter = getSystemService(BluetoothManager::class.java).adapter
                    val address = requestedAddress ?: initialVehicle.obdAddress
                    check(adapter != null && adapter.isEnabled && address != null) { "No adapter" }
                    val connecting = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    socket = connecting
                    if (stopping) break
                    val timeout = Runnable { try { connecting.close() } catch (_: Exception) { } }
                    handler.postDelayed(timeout, 12_000)
                    try { connecting.connect() } finally { handler.removeCallbacks(timeout) }
                    if (stopping) break
                    if (automatic || armed) Log.d(ObdAutoStart.TAG, "OBD SPP connected")
                    synchronized(this) {
                        if (!stopping && socket === connecting && connecting.isConnected) {
                            VehicleTelemetry.LIVE.obdConnection(true)
                        }
                    }
                    val elm = ElmProtocol(connecting.inputStream, connecting.outputStream); elm.initialize()
                    var bad = 0
                    var chargeCheckAt = -10_000L
                    var cable = Double.NaN
                    while (!stopping) {
                        val values = mutableMapOf<String, Double>(); val raw = JSONObject()
                        val cycle = SystemClock.elapsedRealtime()
                        for (key in listOf("current", "voltage", "soc", "speed")) {
                            val signal = profile.signals.getValue(key)
                            val reply = elm.read(signal.request, signal.response, signal.did)
                            raw.put(key, reply); values[key] = signal.decode(reply)
                        }
                        val valid = values.values.all { it.isFinite() } && SystemClock.elapsedRealtime() - cycle <= 8000
                        if (stopping) break
                        if (valid) {
                            expire()
                            if (stopping) break
                            if (automatic && activeTrip < 0 && !prefs.getBoolean("auto", false)) {
                                stopping = true
                                break
                            }
                            if (confirmedAddress == null) {
                                val acceptAddress = synchronized(this) {
                                    if (requestedAddress?.let { it != address } == true) false
                                    else { waitingForObd = false; true }
                                }
                                if (!acceptAddress) break
                                val matches = store.vehicles.matches(address)
                                val selected = VehicleProfiles.resolveObd(vehicleId, matches.map { it.id })
                                if (selected == null) {
                                    requestVehicleChoice(address)
                                    stopping = true
                                    break
                                }
                                if (selected != vehicleId) {
                                    if (!store.vehicles.select(selected)) { stopping = true; break }
                                    vehicleId = selected
                                    VehicleTelemetry.LIVE.resetVehicle(vehicleId, connected = true)
                                }
                                if (pendingObdChoice != null) {
                                    pendingObdChoice = null
                                    getSystemService(NotificationManager::class.java).cancel(CHOOSE_VEHICLE_NOTIFICATION)
                                }
                                confirmedAddress = address
                            }
                            val fix = location?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < 10_000_000_000L && it.hasAccuracy() && it.accuracy <= 30 }
                            val altitude = fix?.takeIf { it.hasAltitude() && it.hasVerticalAccuracy() && it.verticalAccuracyMeters <= 10 }
                            val sample = Sample(System.currentTimeMillis(), values.getValue("soc"), values.getValue("voltage"), values.getValue("current"), values.getValue("speed"),
                                fix?.latitude, fix?.longitude, altitude?.altitude, raw.toString())
                            val possibleCharge = sample.speed <= 1 && sample.power() < -.5
                            val checkCharge = possibleCharge && cycle - chargeCheckAt >= 10_000
                            if (!possibleCharge) {
                                chargeCheckAt = -10_000L
                                if (sample.speed > 1) dcSession = 0L
                            } else if (checkCharge) {
                                chargeCheckAt = cycle
                                cable = readSignal(profile, elm, "charging")
                                if (cable == 0.0) dcSession = 0L
                            }
                            if (DcChargeProfile.isExternal(sample.speed, sample.power(), cable)) {
                                if (!keepAlive()) break
                                if (activeTrip >= 0) finishTrip()
                                if (checkCharge) recordDcCharge(profile, elm, sample)
                                latest = sample
                                VehicleTelemetry.LIVE.externalCharge(sample, lastValid, chargeCheckAt)
                            } else if (!accept(sample)) break
                            bad = 0; setState("connected")
                            if (extras.isNotEmpty()) {
                                val signal = extras[extra++ % extras.size]
                                val reply = elm.read(signal.request, signal.response, signal.did)
                                val value = signal.decode(reply)
                                if (activeTrip >= 0) store.reading(activeTrip, signal.key, value, reply)
                                advanced[signal.key] = Labels(this).n(value, 2) + " " + signal.unit
                                if (signal.key == "charging") VehicleTelemetry.LIVE.cable(value, SystemClock.elapsedRealtime())
                                if (signal.key == "energy") VehicleTelemetry.LIVE.energy(value, SystemClock.elapsedRealtime(), System.currentTimeMillis())
                                if (signal.key == "soh") {
                                    VehicleTelemetry.LIVE.soh(value, SystemClock.elapsedRealtime())
                                    val now = System.currentTimeMillis()
                                    if (value.isFinite() && value in 0.0..100.0 && (value != savedSoh || now - savedSohAt >= 60_000)) {
                                        store.vehicles.recordSoh(vehicleId, value, now)
                                        savedSoh = value
                                        savedSohAt = now
                                    }
                                }
                            }
                            if (++cycles % 10 == 0 && profile.rawSignals.isNotEmpty()) {
                                val signal = profile.rawSignals[rawIndex++ % profile.rawSignals.size]
                                val reply = elm.read(signal.request, signal.response, signal.did)
                                if (activeTrip >= 0) store.reading(activeTrip, signal.key, null, reply)
                                advanced[signal.key] = signal.display(reply, Labels(this))
                            }
                        } else {
                            latest = null; advanced.clear(); setState("vehicle")
                            if (activeTrip >= 0) store.reading(activeTrip, "invalid_cycle", null, raw.toString())
                            expire(); if (++bad >= 3) error("No valid vehicle data")
                        }
                        pause(250)
                    }
                } catch (_: SecurityException) { setState("permission"); break }
                catch (_: Exception) { latest = null; advanced.clear(); if (!stopping) setState("missing") }
                finally { closeSocket() }
                if (!stopping) {
                    var remaining = if (armed && activeTrip < 0) 45 else 15
                    while (remaining-- > 0 && !stopping && !retryRequested) {
                        pause(1000)
                        expire()
                    }
                }
            }
        } catch (_: Exception) { setState("missing") }
        finally {
            finishTrip(); latest = null; advanced.clear()
            if (state != "permission" && state != "reportError") setState("disconnected")
            handler.post {
                handler.removeCallbacks(idleTimeout)
                wakeLock?.let { if (it.isHeld) it.release() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                waitingForObd = false; running = false
                stopSelf()
            }
        }
    }
    private fun idleRemaining(now: Long): Long {
        val last = lastValid.takeIf { it != 0L }
        val expiry = if (automatic) ObdSessionTiming.automaticDeadline(startedAt, last)
            else ObdSessionTiming.armedDeadline(startedAt, last)
        return expiry - now
    }
    private fun readSignal(profile: Profile, elm: ElmProtocol, key: String): Double {
        val signal = profile.signals.getValue(key)
        return signal.decode(elm.read(signal.request, signal.response, signal.did))
    }
    private fun recordDcCharge(profile: Profile, elm: ElmProtocol, sample: Sample) {
        val gridVoltage = readSignal(profile, elm, "grid_voltage")
        if (!DcChargeProfile.isDc(gridVoltage)) {
            if (gridVoltage.isFinite()) dcSession = 0L
            return
        }
        val pack = readSignal(profile, elm, "temp_avg")
        val ambient = readSignal(profile, elm, "ambient")
        if (stopping || !sample.valid() || !pack.isFinite() || !ambient.isFinite()) return
        if (dcSession == 0L || lastValid - lastDcAt > TRIP_GAP_MS) dcSession = sample.time
        lastDcAt = lastValid
        store.addDcCharge(DcChargeProfile.Observation(dcSession, sample.time, sample.soc, -sample.power(), pack, ambient), vehicleId)
    }
    private fun keepAlive(): Boolean {
        synchronized(this) {
            val now = SystemClock.elapsedRealtime()
            if ((automatic || armed) && idleRemaining(now) <= 0) stopping = true
            if (stopping) return false
            lastValid = now
        }
        val armedRemaining = if (armed) startedAt + ARMED_WINDOW_MS - lastValid else 0L
        wakeLock?.acquire(max(60_000L, armedRemaining))
        return true
    }
    private fun accept(sample: Sample): Boolean {
        if (!keepAlive()) return false
        if (activeTrip < 0) {
            activeTrip = store.begin(demo, vehicleId)
            if (automatic || armed) Log.d(ObdAutoStart.TAG, "First valid OBD sample, trip started")
        }
        store.add(activeTrip, sample); latest = sample
        VehicleTelemetry.LIVE.accept(activeTrip, sample, demo, lastValid)
        return true
    }
    private fun expire() {
        if (activeTrip < 0 || SystemClock.elapsedRealtime() - lastValid < TRIP_GAP_MS) return
        if (automatic) stopping = true
        finishTrip()
    }
    private fun finishTrip() {
        val id = activeTrip; activeTrip = -1
        if (id < 0) return
        store.finish(id); VehicleTelemetry.LIVE.finish(id)
        BackupSync.schedule(this)
        try { Report(this).generate(store, id) } catch (_: Exception) { setState("reportError") }
    }
    private fun simulate() {
        val begin = SystemClock.elapsedRealtime()
        while (!stopping) {
            val t = (SystemClock.elapsedRealtime() - begin) / 1000.0
            val current = 70 * sin(t / 9) + 20
            accept(Sample(System.currentTimeMillis(), max(0.0, 60 - t / 180), 370.0, current, max(0.0, 45 + 30 * sin(t / 16)),
                null, null, 120 + 15 * sin(t / 20), "{\"simulation\":true}"))
            setState("demo"); pause(1000)
        }
    }
    private fun setState(value: String) { state = value; VehicleTelemetry.LIVE.status(value) }
    private fun requestVehicleChoice(address: String) {
        pendingObdChoice = address
        val labels = Labels(this)
        val open = PendingIntent.getActivity(this, CHOOSE_VEHICLE_NOTIFICATION, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra(EXTRA_OBD, address),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "trip").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PTrack")
            .setContentText(labels.t("Tria el vehicle per a l’OBD compartit", "Elige el vehículo para el OBD compartido",
                "Choose a vehicle for the shared OBD"))
            .setContentIntent(open).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(CHOOSE_VEHICLE_NOTIFICATION, notification)
    }
    private fun pause(ms: Long) { try { Thread.sleep(ms) } catch (_: InterruptedException) { Thread.currentThread().interrupt(); stopping = true } }
    private fun closeSocket() {
        val old = synchronized(this) {
            val current = socket
            socket = null
            VehicleTelemetry.LIVE.obdConnection(false)
            current
        }
        try { old?.close() } catch (_: Exception) { }
    }
    private fun enableGps() {
        if (locations != null || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(10, foregroundNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            locations = getSystemService(LocationManager::class.java)
            locations?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 2f, listener, Looper.getMainLooper())
        } catch (_: RuntimeException) { locations = null }
    }
    override fun onDestroy() {
        handler.removeCallbacks(idleTimeout)
        stopping = true; closeSocket(); locations?.removeUpdates(listener); worker.shutdown(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_CONNECT_OBD = "cat.ptrack.action.CONNECT_OBD"
        const val ACTION_AUTO_OBD = "cat.ptrack.action.AUTO_OBD"
        const val ACTION_ARM_OBD = "cat.ptrack.action.ARM_OBD"
        internal const val EXTRA_OBD = "obdAddress"
        internal const val CHOOSE_VEHICLE_NOTIFICATION = 11
        @Volatile internal var pendingObdChoice: String? = null
        @Volatile internal var waitingForObd = false
        @Volatile var state = "disconnected"
        @Volatile var latest: Sample? = null
        @Volatile var running = false
        @Volatile var activeTrip = -1L
        val advanced: MutableMap<String, String> = ConcurrentHashMap()
    }
}
