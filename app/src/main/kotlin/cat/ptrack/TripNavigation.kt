package cat.ptrack

import java.net.URLEncoder

internal data class NavigationPlan(val points: List<String>, val completed: Int = 0) {
    init {
        require(points.isNotEmpty() && completed in 0..points.size)
        require(points.all { it.isNotBlank() && it.length <= 250 })
    }

    val remaining get() = points.drop(completed)
    val next get() = points.getOrNull(completed)

    fun advance() = if (next == null) this else copy(completed = completed + 1)

    fun replaceRemaining(newPoints: List<String>) =
        NavigationPlan(points.take(completed) + newPoints, completed)
}

internal object TripNavigation {
    private val coordinatePair = Regex("""([+-]?\d+(?:\.\d+)?),\s*([+-]?\d+(?:\.\d+)?)""")

    fun waze(point: String): String {
        val location = if (coordinatePair.matches(point)) "ll" else "q"
        return "https://waze.com/ul?$location=${encode(point)}&navigate=yes"
    }

    fun mapsNavigation(point: String) = "google.navigation:q=${encode(point)}&mode=d"

    /** A multi-stop URL is an ordered route preview from the current location. */
    fun mapsDirections(points: List<String>, browser: Boolean): String? {
        val limit = if (browser) 4 else 10 // Includes the destination.
        if (points.size !in 1..limit) return null
        val url = buildString {
            append("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=")
            append(encode(points.last()))
            if (points.size > 1) {
                append("&waypoints=")
                append(encode(points.dropLast(1).joinToString("|")))
            }
        }
        return url.takeIf { it.length <= 2048 }
    }

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
