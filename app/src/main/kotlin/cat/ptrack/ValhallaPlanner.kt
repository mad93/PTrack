package cat.ptrack

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
import java.io.InterruptedIOException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

internal class ValhallaPlanner(
    private val engine: Valhalla,
    private val catalog: ChargingCatalog,
    val vehicle: PlanningEnergy,
    private val dcProfile: DcChargeProfile
) {
    private class SearchLimit : IOException("Routing time limit")
    private val deadline = System.nanoTime() + 75_000_000_000L
    private val roads = mutableMapOf<Pair<Point, Point>, Leg?>()
    private val weather = ChargingWeather()
    private val curves = mutableMapOf<Double, Map<Int, Double>>()

    data class Trip(val origin: Point, val destination: Point, val soc: Double,
        val reserve: Double, val minimumKw: Double, val priority: Priority, val departureMs: Long)

    fun alternatives(trip: Trip, excluded: Set<String> = emptySet()): List<Plan> {
        val corridors = route(trip.origin, trip.destination, alternates = 2)
        val plans = mutableListOf<Plan>()
        for (direct in corridors) {
            try {
                val arrival = vehicle.capacityKwh * trip.soc / 100 - direct.kwh
                val plan = if (arrival >= vehicle.capacityKwh * trip.reserve / 100)
                    Plan(emptyList(), direct.minutes, 0.0, arrival, direct.estimatedKm, listOf(direct))
                else solve(trip, candidates(direct.shape, trip.minimumKw, excluded)) { from, to ->
                    when {
                        from == trip.origin && to == trip.destination -> direct
                        from.kmTo(to) > 800 -> null
                        else -> road(from, to)
                    }
                }
                if (plan != null && plans.none { other ->
                    other.charges.map { it.stop.id } == plan.charges.map { it.stop.id } &&
                        other.legs.map { it.shape } == plan.legs.map { it.shape }
                }) plans.add(plan)
            } catch (error: SearchLimit) {
                if (plans.isEmpty()) throw error
                break
            }
        }
        return when (trip.priority) {
            Priority.FASTEST -> plans.sortedBy { it.totalMinutes }
            Priority.LEAST_CHARGING -> plans.sortedWith(compareBy<Plan> { it.chargeMinutes }.thenBy { it.totalMinutes })
        }
    }

    fun skip(trip: Trip, plan: Plan, removed: Stop): Pair<Plan, Boolean>? {
        val original = plan.charges.map { it.stop }
        val index = original.indexOfFirst { it.id == removed.id }
        require(index >= 0)
        val next = original.getOrNull(index + 1)?.point ?: trip.destination
        val stops = original.filter { it.id != removed.id }
        val edges = (listOf(trip.origin) + stops.map { it.point } + trip.destination).zipWithNext().toSet()
        fun recalculate(reserve: Double) = solve(trip, stops, reserveAt = { point ->
            if (point == next) reserve else trip.reserve
        }) { from, to -> if ((from to to) in edges) road(from, to) else null }
        return recalculate(trip.reserve)?.let { it to false }
            ?: recalculate(trip.reserve.coerceAtMost(5.0))?.let { it to true }
    }

    private fun solve(trip: Trip, stops: List<Stop>, reserveAt: (Point) -> Double = { trip.reserve },
        leg: (Point, Point) -> Leg?): Plan? {
        val points = stops.map { it.point }
        weather.load(points, trip.departureMs)
        return ElectricRoutePlanner.plan(trip.origin, trip.destination, stops, vehicle.capacityKwh, trip.soc,
            trip.reserve, trip.priority, reserveAt, conditions = { stop, minutes ->
                val ambient = weather.temperature(stop.point, trip.departureMs + (minutes * 60_000).toLong())
                val curve = ambient?.let { curves.getOrPut(it) { dcProfile.curve(it) } }.orEmpty()
                ElectricRoutePlanner.ChargeConditions(ambient, curve)
            }, weatherDepartureMs = trip.departureMs.takeIf { dcProfile.hasData && weather.hasForecast(points) },
            leg = leg)
    }

    private fun road(from: Point, to: Point): Leg? {
        if (from == to) return null
        val key = from to to
        if (!roads.containsKey(key)) roads[key] = route(from, to).firstOrNull()
        return roads[key]
    }

    private fun candidates(shape: List<Point>, minimumKw: Double, excluded: Set<String>): List<Stop> {
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
            if (stop.id in excluded) continue
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

    private fun route(from: Point, to: Point, alternates: Int = 0): List<Leg> {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
        if (System.nanoTime() >= deadline) throw SearchLimit()
        val response = try {
            JSONObject(engine.routeRaw(request(from, to, alternates)))
        } catch (error: ValhallaException.Internal) {
            // The Android wrapper exposes the engine code only through ErrorResponse.toString().
            if (noRoad.containsMatchIn(error.message.orEmpty())) return emptyList()
            throw error
        }
        return buildList {
            add(readLeg(response.getJSONObject("trip")))
            val alternatives = response.optJSONArray("alternates") ?: return@buildList
            for (index in 0 until alternatives.length()) {
                add(readLeg(alternatives.getJSONObject(index).getJSONObject("trip")))
            }
        }
    }

    private fun readLeg(trip: JSONObject): Leg {
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
            val segment = vehicle.segment(km, speed, elevationChange(heights, interval, offset, km))
            if (segment.usesReference) estimated += km
            energy += segment.kwh
            offset += km
        }
        require(shape.isNotEmpty() && offset > 0 && energy.isFinite())
        val summary = trip.getJSONObject("summary")
        return Leg(summary.getDouble("time") / 60, energy, estimated, summary.getDouble("length"), shape)
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

        fun request(from: Point, to: Point, alternates: Int = 0): String {
            val locations = JSONArray(listOf(from, to).map { JSONObject().put("lat", it.lat).put("lon", it.lon) })
            return JSONObject().put("locations", locations).put("costing", "auto").put("units", "kilometers")
                .put("directions_type", "maneuvers").put("elevation_interval", 30)
                .apply { if (alternates > 0) put("alternates", alternates) }.toString()
        }
    }
}
