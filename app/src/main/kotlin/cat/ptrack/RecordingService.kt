package cat.ptrack

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
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
    private var lastValid = 0L
    private var locations: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var foregroundNotification: Notification
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val listener = object : LocationListener { override fun onLocationChanged(fix: Location) { location = fix } }
    override fun onCreate() { super.onCreate(); store = Store(this) }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { stopping = true; closeSocket(); return START_NOT_STICKY }
        if (intent?.action == "gps") { if (running) enableGps(); return START_NOT_STICKY }
        if (running) {
            if (intent?.action == ACTION_CONNECT_OBD && !demo && !stopping) retryRequested = true
            return START_NOT_STICKY
        }
        demo = intent?.getBooleanExtra("demo", false) == true
        val labels = Labels(this)
        val channel = NotificationChannel("trip", labels.t("Registre de viatge", "Registro de viaje", "Trip recording"), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, "trip").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PTrack").setContentText(labels.t("Connexió OBD i registre automàtic", "Conexión OBD y registro automático", "OBD connection and automatic recording"))
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
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PTrack:recording").apply {
            setReferenceCounted(false); acquire(60_000)
        }
        running = true; stopping = false; latest = null; advanced.clear()
        worker.execute(::run)
        return START_NOT_STICKY
    }
    private fun run() {
        try {
            store.recover()
            if (demo) { simulate(); return }
            val profile = Profile(this)
            val extras = profile.signals.values.filter { it.key !in listOf("current", "voltage", "soc", "speed") }
            var extra = 0; var rawIndex = 0; var cycles = 0
            while (!stopping) {
                retryRequested = false
                try {
                    setState("connecting")
                    val adapter = getSystemService(BluetoothManager::class.java).adapter
                    val address = getSharedPreferences("settings", 0).getString("address", "") ?: ""
                    check(adapter != null && adapter.isEnabled && address.isNotEmpty()) { "No adapter" }
                    val connecting = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                    socket = connecting
                    if (stopping) break
                    val timeout = Runnable { try { connecting.close() } catch (_: Exception) { } }
                    handler.postDelayed(timeout, 12_000)
                    try { connecting.connect() } finally { handler.removeCallbacks(timeout) }
                    synchronized(this) {
                        if (!stopping && socket === connecting && connecting.isConnected) {
                            VehicleTelemetry.LIVE.obdConnection(true)
                        }
                    }
                    val elm = ElmProtocol(connecting.inputStream, connecting.outputStream); elm.initialize()
                    var bad = 0
                    while (!stopping) {
                        val values = mutableMapOf<String, Double>(); val raw = JSONObject()
                        val cycle = SystemClock.elapsedRealtime()
                        for (key in listOf("current", "voltage", "soc", "speed")) {
                            val signal = profile.signals.getValue(key)
                            val reply = elm.read(signal.request, signal.response, signal.did)
                            raw.put(key, reply); values[key] = signal.decode(reply)
                        }
                        val valid = values.values.all { it.isFinite() } && SystemClock.elapsedRealtime() - cycle <= 8000
                        if (valid) {
                            val fix = location?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < 10_000_000_000L && it.hasAccuracy() && it.accuracy <= 30 }
                            val altitude = fix?.takeIf { it.hasAltitude() && it.hasVerticalAccuracy() && it.verticalAccuracyMeters <= 10 }
                            val sample = Sample(System.currentTimeMillis(), values.getValue("soc"), values.getValue("voltage"), values.getValue("current"), values.getValue("speed"),
                                fix?.latitude, fix?.longitude, altitude?.altitude, raw.toString())
                            accept(sample); bad = 0; setState("connected")
                            if (extras.isNotEmpty()) {
                                val signal = extras[extra++ % extras.size]
                                val reply = elm.read(signal.request, signal.response, signal.did)
                                val value = signal.decode(reply); store.reading(activeTrip, signal.key, value, reply)
                                advanced[signal.key] = Labels(this).n(value, 2) + " " + signal.unit
                                if (signal.key == "charging") VehicleTelemetry.LIVE.cable(value, SystemClock.elapsedRealtime())
                                if (signal.key == "energy") VehicleTelemetry.LIVE.energy(value, SystemClock.elapsedRealtime(), System.currentTimeMillis())
                                if (signal.key == "soh") VehicleTelemetry.LIVE.soh(value, SystemClock.elapsedRealtime())
                            }
                            if (++cycles % 10 == 0 && profile.rawSignals.isNotEmpty()) {
                                val signal = profile.rawSignals[rawIndex++ % profile.rawSignals.size]
                                val reply = elm.read(signal.request, signal.response, signal.did)
                                store.reading(activeTrip, signal.key, null, reply)
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
                    var i = 0
                    while (i < 15 && !stopping && !retryRequested) { pause(1000); expire(); i++ }
                }
            }
        } catch (_: Exception) { setState("missing") }
        finally {
            finishTrip(); latest = null; advanced.clear(); running = false
            wakeLock?.let { if (it.isHeld) it.release() }
            if (state != "permission" && state != "reportError") setState("disconnected")
            handler.post { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
    }
    private fun accept(sample: Sample) {
        wakeLock?.acquire(60_000)
        if (activeTrip < 0) activeTrip = store.begin(demo)
        store.add(activeTrip, sample); latest = sample; lastValid = SystemClock.elapsedRealtime()
        VehicleTelemetry.LIVE.accept(activeTrip, sample, demo, lastValid)
    }
    private fun expire() { if (activeTrip >= 0 && SystemClock.elapsedRealtime() - lastValid >= 45_000) finishTrip() }
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
        stopping = true; closeSocket(); locations?.removeUpdates(listener); worker.shutdown(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val ACTION_CONNECT_OBD = "cat.ptrack.action.CONNECT_OBD"
        @Volatile var state = "disconnected"
        @Volatile var latest: Sample? = null
        @Volatile var running = false
        @Volatile var activeTrip = -1L
        val advanced: MutableMap<String, String> = ConcurrentHashMap()
    }
}
