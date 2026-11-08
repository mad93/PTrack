package cat.ptrack

import kotlin.math.*

internal object ElectricRoutePlanner {
    data class Point(val lat: Double, val lon: Double) {
        init { require(lat in -90.0..90.0 && lon in -180.0..180.0) }
        override fun toString() = "${lat.toBigDecimal().toPlainString()},${lon.toBigDecimal().toPlainString()}"
        fun kmTo(other: Point): Double {
            val lat1 = Math.toRadians(lat)
            val lat2 = Math.toRadians(other.lat)
            val halfLon = Math.toRadians(other.lon - lon) / 2
            val h = sin((lat2 - lat1) / 2).pow(2) + cos(lat1) * cos(lat2) * sin(halfLon).pow(2)
            return 12_742 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }

    data class Stop(val point: Point, val name: String, val powerKw: Double, val id: String = point.toString())
    data class Leg(val minutes: Double, val kwh: Double, val estimatedKm: Double,
        val km: Double = 0.0, val shape: List<Point> = emptyList())
    data class Charge(val stop: Stop, val arrivalKwh: Double, val departureKwh: Double, val minutes: Double,
        val arrivalMinutes: Double = 0.0, val ambientC: Double? = null, val learned: Boolean = false)
    data class ChargeConditions(val ambientC: Double?, val curve: Map<Int, Double>)
    data class Plan(val charges: List<Charge>, val driveMinutes: Double, val chargeMinutes: Double,
        val arrivalKwh: Double, val estimatedKm: Double, val legs: List<Leg> = emptyList()) {
        val totalMinutes get() = driveMinutes + chargeMinutes + charges.size * CONNECTION_MINUTES
    }
    enum class Priority { FASTEST, LEAST_CHARGING }

    /**
     * Stops are ordered by position on a road corridor. Drive edges are real routes,
     * computed once per pair on demand. At each stop test charging in 5% steps.
     */
    fun plan(
        origin: Point, destination: Point, stops: List<Stop>, capacity: Double,
        startPercent: Double, reservePercent: Double, priority: Priority,
        reserveAt: (Point) -> Double = { reservePercent },
        conditions: (Stop, Double) -> ChargeConditions = { _, _ -> ChargeConditions(null, emptyMap()) },
        weatherDepartureMs: Long? = null,
        leg: (Point, Point) -> Leg?
    ): Plan? {
        val points = listOf(origin) + stops.map { it.point } + destination
        val states = List(points.size) { mutableMapOf<Pair<Int, Long>, Plan>() }
        states[0][0 to 0L] = Plan(emptyList(), 0.0, 0.0, capacity * startPercent / 100, 0.0)
        val ranking = when (priority) {
            Priority.FASTEST -> compareBy<Plan> { it.totalMinutes }.thenBy { it.chargeMinutes }
            Priority.LEAST_CHARGING -> compareBy<Plan> { it.chargeMinutes }.thenBy { it.totalMinutes }
        }
        for (from in 0 until points.lastIndex) {
            val departures = states[from].values.flatMap { current ->
                if (from == 0) listOf(current) else {
                    val stop = stops[from - 1]
                    val weather = conditions(stop, current.totalMinutes)
                    (5..100 step 5).mapNotNull { percent ->
                        val target = capacity * percent / 100
                        if (target <= current.arrivalKwh) return@mapNotNull null
                        val estimate = DcChargeProfile.duration(current.arrivalKwh, target, capacity, stop.powerKw, weather.curve)
                        val charge = Charge(stop, current.arrivalKwh, target, estimate.minutes, current.totalMinutes,
                            weather.ambientC, estimate.learned)
                        current.copy(charges = current.charges + charge,
                            chargeMinutes = current.chargeMinutes + estimate.minutes, arrivalKwh = target)
                    }
                }
            }
            if (departures.isEmpty()) continue
            for (to in from + 1..points.lastIndex) {
                val segment = leg(points[from], points[to]) ?: continue
                val reserve = capacity * reserveAt(points[to]) / 100
                for (departure in departures) {
                    val arrival = departure.arrivalKwh - segment.kwh
                    if (arrival < reserve) continue
                    val option = departure.copy(driveMinutes = departure.driveMinutes + segment.minutes,
                        arrivalKwh = arrival, estimatedKm = departure.estimatedKm + segment.estimatedKm,
                        legs = departure.legs + segment)
                    // Arrivals at the same SOC can see different charging conditions in different hours.
                    val hour = if (to == points.lastIndex || weatherDepartureMs == null) 0L
                        else Math.floorDiv(weatherDepartureMs + (option.totalMinutes * 60_000).toLong(), 3_600_000)
                    val bucket = floor(arrival / capacity * 100).toInt() to hour
                    val previous = states[to][bucket]
                    if (previous == null || ranking.compare(option, previous) < 0) states[to][bucket] = option
                }
            }
        }
        return states.last().values.minWithOrNull(ranking)
    }

    private const val CONNECTION_MINUTES = 4
}
