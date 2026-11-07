package cat.ptrack

import kotlin.math.abs
import kotlin.math.max

/** Positive battery power is discharge; negative power is energy entering the pack. */
class TripStats {
    var start = 0L
    var end = 0L
    var count = 0L
    var startSoc = Double.NaN
    var endSoc = Double.NaN
    var km = 0.0
    var consumed = 0.0
    var recovered = 0.0
    var ascent = 0.0
    var descent = 0.0
    var coveredSeconds = 0.0
    var hasAltitude = false
    var maxDischargeKw = 0.0
    var maxIncomingKw = 0.0
    var maxDischargeA = 0.0
    var maxIncomingA = 0.0
    var maxSpeed = 0.0
    var maxMovingIncomingKw = 0.0
    var maxMovingIncomingA = 0.0
    private var firstEnergyTime = Long.MIN_VALUE
    private var lastEnergyTime = Long.MIN_VALUE
    private var firstEnergy = Double.NaN
    private var lastEnergy = Double.NaN
    private var stationaryInput = false
    private var previous: Sample? = null
    private var altitudeAnchor: Double? = null
    private var altitudeTime = 0L

    fun add(s: Sample?) {
        if (s == null || !s.valid()) { previous = null; return }
        if (count > 0 && s.time <= end) return
        if (count == 0L) { start = s.time; startSoc = s.soc }
        end = s.time; endSoc = s.soc; count++
        maxDischargeKw = max(maxDischargeKw, s.power()); maxIncomingKw = max(maxIncomingKw, -s.power())
        maxDischargeA = max(maxDischargeA, s.current); maxIncomingA = max(maxIncomingA, -s.current)
        maxSpeed = max(maxSpeed, s.speed)
        stationaryInput = stationaryInput || s.power() < -0.05 && s.speed <= 1
        if (s.power() < 0 && s.speed >= 5) {
            maxMovingIncomingKw = max(maxMovingIncomingKw, -s.power())
            maxMovingIncomingA = max(maxMovingIncomingA, -s.current)
        }
        previous?.let { before ->
            val seconds = (s.time - before.time) / 1000.0
            // Never interpolate across Bluetooth outages, suspension or an absent vehicle.
            if (seconds <= 10) {
                coveredSeconds += seconds
                km += (before.speed + s.speed) * 0.5 * seconds / 3600
                val a = before.power(); val b = s.power()
                if (a * b < 0) {
                    val fraction = abs(a) / (abs(a) + abs(b))
                    integrate(a * 0.5, seconds * fraction)
                    integrate(b * 0.5, seconds * (1 - fraction))
                } else integrate((a + b) * 0.5, seconds)
            }
        }
        s.altitude?.let { altitude ->
            hasAltitude = true
            if (altitudeAnchor == null || s.time - altitudeTime > 30_000) altitudeAnchor = altitude
            val delta = altitude - checkNotNull(altitudeAnchor)
            if (abs(delta) >= 5) {
                if (delta > 0) ascent += delta else descent -= delta
                altitudeAnchor = altitude
            }
            altitudeTime = s.time
        }
        previous = s
    }
    private fun integrate(kw: Double, seconds: Double) {
        if (kw >= 0) consumed += kw * seconds / 3600 else recovered -= kw * seconds / 3600
    }
    fun averageSpeed() = if (coveredSeconds > 0) km * 3600 / coveredSeconds else Double.NaN
    fun consumption() = if (km >= 0.1) (consumed - recovered) * 100 / km else Double.NaN
    fun energy(time: Long, kwh: Double) {
        if (time < lastEnergyTime) return
        if (kwh.isFinite() && kwh in 0.0..100.0) {
            if (firstEnergyTime == Long.MIN_VALUE) { firstEnergyTime = time; firstEnergy = kwh }
            lastEnergy = kwh
        } else lastEnergy = Double.NaN
        lastEnergyTime = time
    }
    fun summary(): TripSummary {
        val initial = if (count > 0 && abs(firstEnergyTime.toDouble() - start) <= 10_000) firstEnergy else Double.NaN
        val remaining = if (count > 0 && abs(lastEnergyTime.toDouble() - end) <= 10_000) lastEnergy else Double.NaN
        val range = if (count >= 30 && km >= 1 && coveredSeconds >= 60 && coveredSeconds * 1000 >= 0.8 * (end - start) &&
            consumption() > 0 && !stationaryInput && remaining.isFinite()) remaining / consumption() * 100 else Double.NaN
        fun present(value: Double) = if (count > 0) value else Double.NaN
        return TripSummary(count, if (count > 0) end - start else 0, present(km), coveredSeconds,
            present(consumed), present(recovered), averageSpeed(),
            if (coveredSeconds > 0) (consumed - recovered) * 3600 / coveredSeconds else Double.NaN,
            consumption(), present(maxDischargeKw), present(maxIncomingKw), present(maxDischargeA), present(maxIncomingA),
            present(maxSpeed), startSoc, endSoc, initial, remaining, range, present(maxMovingIncomingKw), present(maxMovingIncomingA))
    }
}
