package cat.ptrack

import cat.ptrack.ElectricRoutePlanner.Point
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class ChargingWeather {
    private val hoursByPoint = mutableMapOf<Point, Map<Long, Double>>()

    fun load(points: List<Point>, departureMs: Long) {
        val now = System.currentTimeMillis()
        if (departureMs < now - 3_600_000 || departureMs >= now + 16 * 86_400_000L) return
        val missing = points.distinct().filterNot(hoursByPoint::containsKey)
        if (missing.isEmpty()) return
        val days = ((departureMs - now) / 86_400_000 + 3).coerceIn(2, 16)
        val latitudes = missing.joinToString(",") { it.lat.toString() }
        val longitudes = missing.joinToString(",") { it.lon.toString() }
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitudes&longitude=$longitudes" +
            "&hourly=temperature_2m&timeformat=unixtime&forecast_days=$days")
        val forecasts = try {
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 3500
                connection.readTimeout = 4500
                val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                val locations = if (body.startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
                if (locations.length() != missing.size) emptyList()
                else List(locations.length()) { index -> parse(locations.optJSONObject(index)?.optJSONObject("hourly")) }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        }
        missing.forEachIndexed { index, point -> hoursByPoint[point] = forecasts.getOrElse(index) { emptyMap() } }
    }

    fun temperature(point: Point, epochMs: Long): Double? =
        hoursByPoint[point]?.get(Math.floorDiv(epochMs, 3_600_000) * 3600)

    fun hasForecast(points: List<Point>) = points.any { !hoursByPoint[it].isNullOrEmpty() }

    private fun parse(hourly: JSONObject?): Map<Long, Double> {
        val times = hourly?.optJSONArray("time") ?: return emptyMap()
        val values = hourly.optJSONArray("temperature_2m") ?: return emptyMap()
        return buildMap {
            for (i in 0 until times.length()) {
                val time = times.optLong(i, -1)
                val celsius = values.optDouble(i)
                if (time >= 0 && celsius.isFinite()) put(time, celsius)
            }
        }
    }
}
