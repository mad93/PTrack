package cat.ptrack

import cat.ptrack.ConsumptionModel.*
import java.util.Collections
import java.util.TreeSet
import kotlin.math.*

/** Read-only analysis. Positive power is battery discharge. */
class ConsumptionLearner(private val binWidth: Int) {
    data class Key(val speedBin: Int, val terrain: Terrain)
    class Bucket {
        var km = 0.0
        var consumedKwh = 0.0
        var recoveredKwh = 0.0
        var intervals = 0L
        var trips = 0
        fun estimate(): Estimate {
            val confidence = when {
                km < 5 || intervals < 30 || trips < 3 -> Confidence.INSUFFICIENT
                km >= 50 && intervals >= 200 && trips >= 10 -> Confidence.HIGH
                km >= 20 && trips >= 5 -> Confidence.MEDIUM
                else -> Confidence.LOW
            }
            val observed = if (km > 0) 100 * (consumedKwh - recoveredKwh) / km else null
            return Estimate(observed, if (confidence == Confidence.INSUFFICIENT) null else observed,
                intervals, km, trips, confidence)
        }
        fun merge(b: Bucket) {
            km += b.km; consumedKwh += b.consumedKwh; recoveredKwh += b.recoveredKwh; intervals += b.intervals
        }
    }
    private val accumulated = mutableMapOf<Key, Bucket>()
    init { require(binWidth in 1..250) { "binWidth: 1..250 km/h" } }
    fun bin(speed: Double) = floor(speed / binWidth).toInt()
    fun buckets(): Map<Key, Bucket> = Collections.unmodifiableMap(accumulated)
    fun addTrip(samples: List<Sample?>) {
        val trip = mutableMapOf<Key, Bucket>()
        val run = mutableListOf<Sample>()
        var previous: Sample? = null
        for (s in samples) {
            if (!valid(s)) { accumulate(run, trip); run.clear(); previous = null; continue }
            checkNotNull(s)
            if (previous != null && !interval(previous, s)) { accumulate(run, trip); run.clear() }
            run.add(s); previous = s
        }
        accumulate(run, trip)
        trip.forEach { (key, value) -> accumulated.getOrPut(key) { Bucket() }.apply { merge(value); trips++ } }
    }
    private fun valid(s: Sample?): Boolean = s != null && s.time > 0 && finite(s.soc, 0.0, 100.0) &&
        finite(s.voltage, 100.0, 1000.0) && finite(s.current, -1000.0, 1000.0) && finite(s.speed, 5.0, 250.0) &&
        s.latitude != null && s.longitude != null && finite(s.latitude, -90.0, 90.0) && finite(s.longitude, -180.0, 180.0)
    private fun finite(x: Double, min: Double, max: Double) = x.isFinite() && x in min..max
    private fun interval(a: Sample, b: Sample): Boolean {
        val dt = (b.time - a.time) / 1000.0
        if (dt <= 0 || dt > 10 || abs(b.speed - a.speed) / 3.6 / dt > 8 || abs(b.soc - a.soc) > 2 + dt * 0.05) return false
        val km = (a.speed + b.speed) * 0.5 * dt / 3600
        return gpsKm(a, b) <= km * 1.5 + 0.06
    }
    private fun gpsKm(a: Sample, b: Sample): Double {
        val lat = Math.toRadians(checkNotNull(b.latitude) - checkNotNull(a.latitude))
        val lon = Math.toRadians(checkNotNull(b.longitude) - checkNotNull(a.longitude))
        val h = sin(lat / 2) * sin(lat / 2) + cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(lon / 2) * sin(lon / 2)
        return 6371 * 2 * asin(sqrt(min(1.0, h)))
    }
    private fun accumulate(run: List<Sample>, trip: MutableMap<Key, Bucket>) {
        if (run.size < 2) return
        var km = 0.0; var gps = 0.0; var altitude = true
        for ((i, s) in run.withIndex()) {
            altitude = altitude && s.altitude != null && finite(s.altitude, -500.0, 9000.0)
            if (i > 0) {
                val a = run[i - 1]
                km += (a.speed + s.speed) * 0.5 * (s.time - a.time) / 3600000.0
                gps += gpsKm(a, s)
                if (a.altitude != null && s.altitude != null && abs(s.altitude - a.altitude) > 20) altitude = false
            }
        }
        val first = run.first(); val last = run.last()
        if (km < 1 || last.time - first.time < 60_000 || gps < km * 0.5 || gps > km * 1.5 + 0.06) return
        var terrain = Terrain.UNKNOWN
        if (altitude) {
            val delta = checkNotNull(last.altitude) - checkNotNull(first.altitude)
            val margin = km * 1000 * 0.02
            terrain = when {
                delta > margin + 20 -> Terrain.UPHILL
                delta < -margin - 20 -> Terrain.DOWNHILL
                abs(delta) + 20 <= margin -> Terrain.LEVEL
                else -> Terrain.UNKNOWN
            }
        }
        for (i in 1 until run.size) integrate(run[i - 1], run[i], terrain, trip)
    }
    private fun integrate(a: Sample, b: Sample, terrain: Terrain, trip: MutableMap<Key, Bucket>) {
        // Split at speed boundaries and zero power, using the same linear signals as TripStats.
        val cuts = TreeSet(listOf(0.0, 1.0))
        if (a.speed != b.speed) for (edge in binWidth..250 step binWidth) {
            val f = (edge - a.speed) / (b.speed - a.speed)
            if (f > 0 && f < 1) cuts.add(f)
        }
        if (a.power() * b.power() < 0) cuts.add(-a.power() / (b.power() - a.power()))
        val points = cuts.toList(); val seen = mutableSetOf<Key>()
        for (i in 1 until points.size) {
            val lo = points[i - 1]; val hi = points[i]; val mid = (lo + hi) / 2
            val hours = (b.time - a.time) / 3600000.0 * (hi - lo)
            val speed = a.speed + (b.speed - a.speed) * mid
            val power = a.power() + (b.power() - a.power()) * mid
            val key = Key(bin(speed), terrain)
            val value = trip.getOrPut(key) { Bucket() }
            value.km += speed * hours
            if (power >= 0) value.consumedKwh += power * hours else value.recoveredKwh -= power * hours
            if (seen.add(key)) value.intervals++
        }
    }
}
