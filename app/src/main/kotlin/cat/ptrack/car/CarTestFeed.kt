package cat.ptrack.car

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import cat.ptrack.Sample
import cat.ptrack.VehicleTelemetry
import kotlin.math.max
import kotlin.math.sin

/** Memory fixture for testing car layout; never touches LIVE, Bluetooth or SQLite. */
object CarTestFeed {
    val SOURCE = VehicleTelemetry()
    private val main = Handler(Looper.getMainLooper())
    private var enabled = false
    private var scenario = "drive"
    private var trip = 0L
    private var tick = 0L
    private var until = 0L
    private val update = object : Runnable {
        override fun run() {
            if (SystemClock.elapsedRealtime() > until) { command("real"); return }
            val current = when (scenario) { "regen" -> -40.0; "charge" -> -20.0; "standstill" -> 2.0; else -> 30 + 20 * sin(tick++ / 4.0) }
            val speed = when (scenario) { "charge", "standstill" -> 0.0; "regen" -> 40.0; else -> 60.0 }
            SOURCE.accept(trip, Sample(System.currentTimeMillis(), max(0.0, 70 - tick * .002), 400.0, current, speed, null, null, null, "{\"simulation\":true}"), true, SystemClock.elapsedRealtime())
            SOURCE.cable(if (scenario == "charge") 1.0 else null, SystemClock.elapsedRealtime())
            SOURCE.energy(max(0.0, 31.8 - tick * .001), SystemClock.elapsedRealtime(), System.currentTimeMillis())
            SOURCE.soh(96.0, SystemClock.elapsedRealtime())
            main.postDelayed(this, 1000)
        }
    }
    fun snapshot(): VehicleTelemetry.Snapshot = (if (enabled) SOURCE else VehicleTelemetry.LIVE).snapshot()
    fun command(value: String) {
        require(value in setOf("drive", "regen", "charge", "standstill", "freeze", "disconnect", "reconnect", "finish", "real")) { "Unknown scenario" }
        main.removeCallbacks(update)
        if (value == "real") { enabled = false; SOURCE.status("disconnected"); return }
        if (!enabled) {
            enabled = true; trip++; tick = 0
            val end = System.currentTimeMillis()
            for (i in 60 downTo 0) SOURCE.accept(trip, Sample(end - i * 1000, 70.0, 400.0, 30.0, 60.0, null, null, null, "{\"simulation\":true}"), true, SystemClock.elapsedRealtime())
            SOURCE.energy(32.0, SystemClock.elapsedRealtime(), end - 60_000)
            SOURCE.energy(31.8, SystemClock.elapsedRealtime(), end)
        }
        if (value == "finish") { SOURCE.finish(trip); return }
        if (value == "freeze") return
        if (value == "disconnect") { SOURCE.status("missing"); return }
        scenario = if (value == "reconnect") "drive" else value
        until = SystemClock.elapsedRealtime() + 600_000
        main.postDelayed(update, 10)
    }
}
