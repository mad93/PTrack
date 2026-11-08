package cat.ptrack

import android.net.Uri
import cat.ptrack.ElectricRoutePlanner.Point
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object PlannerPlaces {
    data class Place(val name: String, val point: Point)

    fun coordinates(value: String): Place? = runCatching {
        val (lat, lon) = value.trim().split(',').also { require(it.size == 2) }
        val point = Point(lat.trim().toDouble(), lon.trim().toDouble())
        Place(point.toString(), point)
    }.getOrNull()

    fun search(query: String, language: String, inCatalonia: Boolean = false): List<Place> {
        val uri = if (inCatalonia) {
            Uri.parse("https://eines.icgc.cat/geocodificador/autocompletar").buildUpon()
                .appendQueryParameter("text", query).appendQueryParameter("size", "5").build()
        } else {
            Uri.parse("https://photon.komoot.io/api/").buildUpon()
                .appendQueryParameter("q", query).appendQueryParameter("limit", "5").build()
        }
        val connection = URL(uri.toString()).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("User-Agent", "PTrack/0.2")
            connection.setRequestProperty("Accept-Language", language)
            val features = connection.inputStream.bufferedReader().use {
                JSONObject(it.readText()).getJSONArray("features")
            }
            return (0 until features.length()).mapNotNull { index ->
                val item = features.getJSONObject(index)
                val properties = item.getJSONObject("properties")
                val coordinates = item.getJSONObject("geometry").getJSONArray("coordinates")
                val label = if (inCatalonia) properties.labelPart("etiqueta") else osmLabel(properties)
                if (label.isBlank()) null else Place(label, Point(coordinates.getDouble(1), coordinates.getDouble(0)))
            }.distinct()
        } finally {
            connection.disconnect()
        }
    }

    private fun osmLabel(properties: JSONObject): String {
        val street = listOf(properties.labelPart("street"), properties.labelPart("housenumber"))
            .filter { it.isNotBlank() }.joinToString(" ")
        val name = properties.labelPart("name").ifBlank { street }
        if (name.isBlank()) return ""
        val municipality = properties.labelPart("city").ifBlank { properties.labelPart("locality") }
        val region = properties.labelPart("county").ifBlank { properties.labelPart("state") }
        return listOf(name, street, municipality, region, properties.labelPart("country"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
    }

    private fun JSONObject.labelPart(key: String) = if (isNull(key)) "" else optString(key)
}
