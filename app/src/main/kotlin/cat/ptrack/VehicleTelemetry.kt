package cat.ptrack

import java.util.concurrent.CopyOnWriteArraySet

/** One publication of samples already persisted by RecordingService; freshness uses monotonic time. */
class VehicleTelemetry {
    data class Totals(val tripId: Long, val active: Boolean, val samples: Long, val km: Double,
        val consumed: Double, val recovered: Double, val average: Double, val summary: TripSummary)
    data class Snapshot(val state: String, val sample: Sample?, val receivedAt: Long, val simulation: Boolean,
        val totals: Totals?, val cable: Double?, val cableAt: Long, val energy: Double?, val energyAt: Long,
        val soh: Double? = null, val sohAt: Long = 0, val obdConnected: Boolean = false) {
        fun remaining(now: Long): Double = if (current(now) != null && energy != null && now >= energyAt && now - energyAt <= FRESH_MS) energy else Double.NaN
        fun soh(now: Long): Double = if (soh != null && now >= sohAt && now - sohAt <= FRESH_MS) soh else if (simulation) 96.0 else Double.NaN
        fun current(now: Long): Sample? = if ((state == "connected" || state == "demo") && sample != null &&
            now >= receivedAt && now - receivedAt <= FRESH_MS) sample else null
        fun instantaneous(now: Long): Double = current(now)?.takeIf { it.speed >= 5 }?.let { it.power() * 100 / it.speed } ?: Double.NaN
        fun connection(now: Long): String = when {
            state == "connecting" -> "Connectant"
            current(now) != null -> "Connectat"
            state == "connected" || state == "demo" || state == "vehicle" -> "Dades no disponibles"
            state == "permission" -> "Desconnectat · cal permís Bluetooth"
            else -> "Desconnectat"
        }
        fun flow(now: Long): String {
            val s = current(now) ?: return "Sense dades actuals"
            if (s.power() > 0.05) return "Consum de bateria"
            if (s.power() >= -0.05) return "Potència gairebé nul·la"
            val recentCable = cable == 1.0 && now >= cableAt && now - cableAt <= FRESH_MS
            if (s.speed <= 1 && recentCable) return "Càrrega probable · cable detectat"
            return if (s.speed >= 5) "Regeneració · energia entrant" else "Energia entrant · origen no confirmat"
        }
    }
    private val listeners = CopyOnWriteArraySet<Runnable>()
    private var stats = TripStats()
    private var tripId = -1L
    private var active = false
    private var published = Snapshot("disconnected", null, 0, false, null, null, 0, null, 0)
    @Synchronized fun snapshot(): Snapshot = published
    fun subscribe(listener: Runnable) { listeners.add(listener) }
    fun unsubscribe(listener: Runnable) { listeners.remove(listener) }
    private fun changed() {
        // A presentation failure must never terminate recording.
        for (listener in listeners) try { listener.run() } catch (_: RuntimeException) { }
    }
    fun status(state: String) {
        synchronized(this) {
            val sample = if (state == "connected" || state == "demo") published.sample else null
            if (state == published.state && sample === published.sample) return
            published = published.copy(state = state, sample = sample)
        }
        changed()
    }
    fun obdConnection(connected: Boolean) {
        synchronized(this) {
            if (published.obdConnected == connected) return
            published = published.copy(obdConnected = connected)
        }
        changed()
    }
    fun accept(id: Long, sample: Sample?, simulation: Boolean, elapsed: Long) {
        if (sample == null || !sample.valid()) { status("vehicle"); return }
        synchronized(this) {
            if (id != tripId || !active) { tripId = id; stats = TripStats(); active = true }
            if (stats.count > 0 && sample.time <= stats.end) return
            stats.add(sample)
            published = published.copy(state = if (simulation) "demo" else "connected", sample = sample,
                receivedAt = elapsed, simulation = simulation, totals = totals())
        }
        changed()
    }
    fun cable(value: Double?, elapsed: Long) {
        synchronized(this) { published = published.copy(cable = value?.takeIf { it.isFinite() }, cableAt = elapsed) }
        changed()
    }
    fun finish(id: Long) {
        synchronized(this) {
            if (id != tripId || !active) return
            active = false
            published = published.copy(sample = null, totals = totals(), cable = null, cableAt = 0, energy = null, energyAt = 0)
        }
        changed()
    }
    fun energy(value: Double?, elapsed: Long, wallTime: Long) {
        synchronized(this) {
            val valid = value?.takeIf { it.isFinite() && it in 0.0..100.0 }
            if (active) stats.energy(wallTime, valid ?: Double.NaN)
            published = published.copy(totals = if (active) totals() else published.totals, energy = valid, energyAt = elapsed)
        }
        changed()
    }
    fun soh(value: Double?, elapsed: Long) {
        synchronized(this) {
            val valid = value?.takeIf { it.isFinite() && it in 0.0..100.0 }
            published = published.copy(soh = valid, sohAt = elapsed)
        }
        changed()
    }
    private fun totals() = Totals(tripId, active, stats.count, stats.km, stats.consumed, stats.recovered, stats.consumption(), stats.summary())
    companion object { val LIVE = VehicleTelemetry(); const val FRESH_MS = 10_000L }
}
