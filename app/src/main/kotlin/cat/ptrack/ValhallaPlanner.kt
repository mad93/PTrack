package cat.ptrack

import cat.ptrack.ConsumptionModel.Terrain
import cat.ptrack.ElectricRoutePlanner.Leg
import cat.ptrack.ElectricRoutePlanner.Plan
import cat.ptrack.ElectricRoutePlanner.Point
import cat.ptrack.ElectricRoutePlanner.Priority
import cat.ptrack.ElectricRoutePlanner.Stop
import com.valhalla.valhalla.Valhalla
import com.valhalla.valhalla.ValhallaException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class ValhallaPlanner(
    private val engine: Valhalla,
    private val catalog: ChargingCatalog,
    private val profile: ConsumptionModel,
    private val fallbackConsumption: Double
) {
    private data class Road(val shape: List<Point>, val leg: Leg)
    private val deadline = System.nanoTime() + 75_000_000_000L

    fun plan(origin: Point, destination: Point, capacity: Double, soc: Double, reserve: Double,
        minimumKw: Double, maxPower: Double, priority: Priority): Plan? {
        val direct = road(origin, destination) ?: return null
        val arrival = capacity * soc / 100 - direct.leg.kwh
        if (arrival >= capacity * reserve / 100) {
            return Plan(emptyList(), direct.leg.minutes, 0.0, arrival, direct.leg.estimatedKm)
        }
        val stops = candidates(direct.shape, minimumKw)
        return ElectricRoutePlanner.plan(origin, destination, stops, capacity, soc, reserve, maxPower, priority) { from, to ->
            if (from == origin && to == destination) direct.leg
            else if (from == to || from.kmTo(to) > 800) null
            else road(from, to)?.leg
        }
    }

    private fun candidates(shape: List<Point>, minimumKw: Double): List<Stop> {
        val samples = buildList {
            for ((a, b) in shape.zipWithNext()) {
                val parts = ceil(a.kmTo(b) / 5).toInt().coerceAtLeast(1)
                for (i in 0 until parts) {
                    val fraction = i.toDouble() / parts
                    add(Point(a.lat + (b.lat - a.lat) * fraction, a.lon + (b.lon - a.lon) * fraction))
                }
            }
            add(shape.last())
        }
        val shortlist = catalog.fastChargers(samples.minOf { it.lat } - .15, samples.maxOf { it.lat } + .15,
            samples.minOf { it.lon } - .2, samples.maxOf { it.lon } + .2, minimumKw)
        data class Candidate(val stop: Stop, val km: Double, val offset: Double)
        val best = mutableMapOf<Int, Candidate>()
        val offsets = DoubleArray(samples.size)
        for (i in 1 until samples.size) offsets[i] = offsets[i - 1] + samples[i - 1].kmTo(samples[i])
        val spacing = (offsets.last() / 10).coerceAtLeast(5.0)
        for (stop in shortlist) {
            var nearest = 0
            var distance = Double.POSITIVE_INFINITY
            for ((i, sample) in samples.withIndex()) {
                if (abs(stop.point.lat - sample.lat) > .12 || abs(stop.point.lon - sample.lon) > .2) continue
                val km = sample.kmTo(stop.point)
                if (km < distance) { distance = km; nearest = i }
            }
            if (distance > 10) continue
            val offset = offsets[nearest]
            val group = (offset / spacing).toInt()
            val candidate = Candidate(stop, distance, offset)
            val previous = best[group]
            if (previous == null || candidate.km < previous.km ||
                (candidate.km == previous.km && stop.powerKw > previous.stop.powerKw)) best[group] = candidate
        }
        return best.values.sortedBy { it.offset }.map { it.stop }
    }

    private fun road(from: Point, to: Point): Road? {
        if (Thread.currentThread().isInterrupted || System.nanoTime() >= deadline) throw IOException("Routing time limit")
        val response = try {
            JSONObject(engine.routeRaw(request(from, to)))
        } catch (error: ValhallaException.Internal) {
            // The Android wrapper exposes the engine code only through ErrorResponse.toString().
            if (noRoad.containsMatchIn(error.message.orEmpty())) return null
            throw error
        }
        val trip = response.getJSONObject("trip")
        val leg = trip.getJSONArray("legs").getJSONObject(0)
        val shape = decode(leg.getString("shape"))
        val maneuvers = leg.getJSONArray("maneuvers")
        val heights = leg.optJSONArray("elevation")
        val interval = leg.optDouble("elevation_interval", 30.0)
        var energy = 0.0
        var offset = 0.0
        var estimated = 0.0
        for (i in 0 until maneuvers.length()) {
            val step = maneuvers.getJSONObject(i)
            val km = step.getDouble("length")
            if (km <= 0) continue
            val speed = km * 3600 / step.getDouble("time")
            val ascent = elevationChange(heights, interval, offset, km)
            val terrain = when {
                ascent == null -> Terrain.UNKNOWN
                ascent / km > 20 -> Terrain.UPHILL
                ascent / km < -20 -> Terrain.DOWNHILL
                else -> Terrain.LEVEL
            }
            val learned = profile.estimate(speed.coerceIn(5.0, 250.0), terrain).kwhPer100Km
            if (learned == null) estimated += km
            energy += if (learned != null) learned.coerceAtLeast(0.0) * km / 100 * 1.10
                else fallbackConsumption * km / 100 * 1.15 + (ascent ?: 0.0).coerceAtLeast(0.0) * .008
            offset += km
        }
        require(shape.isNotEmpty() && offset > 0 && energy.isFinite())
        return Road(shape, Leg(trip.getJSONObject("summary").getDouble("time") / 60, energy, estimated))
    }

    private fun elevationChange(heights: JSONArray?, interval: Double, startKm: Double, km: Double): Double? {
        if (heights == null || heights.length() < 2 || !interval.isFinite() || interval <= 0) return null
        val last = heights.length() - 1
        val start = heights.optDouble((startKm * 1000 / interval).roundToInt().coerceIn(0, last))
        val end = heights.optDouble(((startKm + km) * 1000 / interval).roundToInt().coerceIn(0, last))
        return (end - start).takeIf { start in -500.0..9000.0 && end in -500.0..9000.0 }
    }

    private fun decode(encoded: String): List<Point> {
        var cursor = 0
        fun delta(): Int {
            var value = 0
            var shift = 0
            do {
                require(cursor < encoded.length && shift <= 25)
                val chunk = encoded[cursor++].code - 63
                require(chunk in 0..63)
                value = value or ((chunk and 31) shl shift)
                shift += 5
            } while (chunk >= 32)
            return if (value and 1 == 0) value shr 1 else (value shr 1).inv()
        }
        return buildList {
            var lat = 0
            var lon = 0
            while (cursor < encoded.length) {
                lat += delta()
                lon += delta()
                add(Point(lat / 1e6, lon / 1e6))
            }
        }
    }

    companion object {
        private val noRoad = Regex("""^ErrorResponse\(code=(170|171|441|442),""")

        fun request(from: Point, to: Point): String {
            val locations = JSONArray(listOf(from, to).map { JSONObject().put("lat", it.lat).put("lon", it.lon) })
            return JSONObject().put("locations", locations).put("costing", "auto").put("units", "kilometers")
                .put("directions_type", "maneuvers").put("elevation_interval", 30).toString()
        }
    }
}
