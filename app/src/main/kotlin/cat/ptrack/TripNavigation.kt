package cat.ptrack

import java.net.URLEncoder

internal data class NavigationPlan(
    val points: List<String>,
    val completed: Int = 0,
    val origin: String? = null,
    val chargerNames: Map<String, String> = emptyMap()
) {
    init {
        require(points.isNotEmpty() && completed in 0..points.size)
        require(points.all { it.isNotBlank() && it.length <= 250 })
    }

    val remaining get() = points.drop(completed)
    val next get() = points.getOrNull(completed)

    fun advance() = if (next == null) this else copy(completed = completed + 1)

    fun replaceRemaining(newPoints: List<String>): NavigationPlan {
        val updated = points.take(completed) + newPoints
        return copy(points = updated, chargerNames = chargerNames.filterKeys { it in updated })
    }

    fun label(point: String) = chargerNames[point] ?: point
}

internal object TripNavigation {
    data class MapsSection(val stops: IntRange, val url: String)

    private val coordinatePair = Regex("""([+-]?\d+(?:\.\d+)?),\s*([+-]?\d+(?:\.\d+)?)""")

    fun waze(point: String): String {
        val location = if (coordinatePair.matches(point)) "ll" else "q"
        return "https://waze.com/ul?$location=${encode(point)}&navigate=yes"
    }

    fun mapsNavigation(point: String) = "google.navigation:q=${encode(point)}&mode=d"

    fun mapsDirections(points: List<String>, browser: Boolean, origin: String? = null): String? {
        val limit = if (browser) 4 else 10 // Includes the destination.
        if (points.size !in 1..limit) return null
        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1&travelmode=driving")
            if (origin != null) {
                append("&origin=")
                append(encode(origin))
            }
            append("&destination=")
            append(encode(points.last()))
            if (points.size > 1) {
                append("&waypoints=")
                append(encode(points.dropLast(1).joinToString("|")))
            }
        }
        return url.takeIf { it.length <= 2048 }
    }

    /** Each section starts where the previous one ends. Indices refer to the saved itinerary. */
    fun mapsSections(plan: NavigationPlan, browser: Boolean): List<MapsSection>? = buildList {
        var start = plan.completed
        var origin = plan.origin.takeIf { start == 0 }
        val limit = if (browser) 4 else 10
        while (start < plan.points.size) {
            val end = minOf(start + limit, plan.points.size)
            val section = (end downTo start + 1).firstNotNullOfOrNull { stop ->
                mapsDirections(plan.points.subList(start, stop), browser, origin)?.let { url ->
                    MapsSection(start until stop, url)
                }
            } ?: return null
            add(section)
            start = section.stops.last + 1
            origin = plan.points[section.stops.last]
        }
    }

    fun mapsCharger(point: String, name: String) = "geo:$point?z=17&q=${encode(name)}"

    fun mapsChargerWeb(point: String, name: String) =
        "https://www.google.com/maps/search/?api=1&query=${encode("$name $point")}"

    fun carDestination(point: String) = if (coordinatePair.matches(point)) "geo:$point" else "geo:0,0?q=${encode(point)}"

    fun parsePoints(input: String): List<String>? {
        val points = input.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        if (points.isEmpty() || points.any { it.length > 250 || '|' in it }) return null
        return points.map { point ->
            val coordinates = coordinatePair.matchEntire(point) ?: return@map point
            val (latitude, longitude) = coordinates.destructured
            if (latitude.toDouble() !in -90.0..90.0 || longitude.toDouble() !in -180.0..180.0) return null
            "$latitude,$longitude"
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
