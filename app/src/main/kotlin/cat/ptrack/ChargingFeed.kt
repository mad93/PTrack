package cat.ptrack

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2
import java.io.InputStream
import java.io.InterruptedIOException
import java.time.OffsetDateTime
import javax.xml.parsers.SAXParserFactory

/** DATEX II energy infrastructure table. The NAP expresses maxPowerAtSocket in watts. */
internal object ChargingFeed {
    data class Connector(val stationId: String, val pointId: String, val type: String, val mode: String?, val powerKw: Double?)
    data class Site(
        val id: String, val name: String?, val operator: String?, val hours: String?,
        val latitude: Double, val longitude: Double, val connectors: List<Connector>
    )
    data class Publication(val time: Long, val sites: Int, val connectors: Int)

    fun read(input: InputStream, save: (Site) -> Unit): Publication {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val handler = Reader(save)
        factory.newSAXParser().xmlReader.apply {
            contentHandler = handler
            errorHandler = handler
            setProperty("http://xml.org/sax/properties/lexical-handler", handler)
            parse(InputSource(input))
        }
        require(handler.sites > 0 && handler.connectors > 0) { "Empty charging publication" }
        return Publication(requireNotNull(handler.publicationTime), handler.sites, handler.connectors)
    }

    private class Reader(private val save: (Site) -> Unit) : DefaultHandler2() {
        private val path = mutableListOf<String>()
        private val content = StringBuilder()
        private var site: SiteFields? = null
        private var stationId: String? = null
        private var pointId: String? = null
        private var connectorType: String? = null
        private var connectorMode: String? = null
        private var connectorPower: Double? = null
        var publicationTime: Long? = null
        var sites = 0
        var connectors = 0

        override fun startDTD(name: String?, publicId: String?, systemId: String?) {
            throw SAXException("Document types are forbidden in charging publications")
        }

        override fun startElement(uri: String, name: String, qName: String, attributes: Attributes) {
            require(path.size < 128) { "Invalid charging publication depth" }
            val parent = path.lastOrNull()
            path.add(name)
            content.setLength(0)
            when {
                name == "energyInfrastructureSite" && parent == "energyInfrastructureTable" -> {
                    if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                    site = SiteFields(attributes.getValue("id"))
                }
                name == "energyInfrastructureStation" && parent == "energyInfrastructureSite" ->
                    stationId = attributes.getValue("id")
                name == "refillPoint" && parent == "energyInfrastructureStation" ->
                    pointId = attributes.getValue("id")
                name == "connector" && parent == "refillPoint" -> {
                    connectorType = null
                    connectorMode = null
                    connectorPower = null
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (path.lastOrNull() in textFields) {
                require(content.length + length <= 16_384) { "Invalid charging field length" }
                content.append(ch, start, length)
            }
        }

        override fun endElement(uri: String, name: String, qName: String) {
            val parent = path.getOrNull(path.lastIndex - 1)
            val value = content.toString().trim().takeIf(String::isNotEmpty)
            val current = site
            if (name == "publicationTime" && path.size == 2 && value != null) {
                publicationTime = OffsetDateTime.parse(value).toInstant().toEpochMilli()
            }
            if (current != null) when {
                name == "value" && parent == "values" && path.getOrNull(path.lastIndex - 2) == "name" -> {
                    when (path.getOrNull(path.lastIndex - 3)) {
                        "energyInfrastructureSite" -> if (current.name == null) current.name = value
                        "operator" -> if (current.operator == null) current.operator = value
                    }
                }
                name == "label" && parent == "operatingHours" && stationId == null -> current.hours = value
                name == "latitude" && parent == "coordinatesForDisplay" && stationId == null ->
                    current.latitude = value?.toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
                name == "longitude" && parent == "coordinatesForDisplay" && stationId == null ->
                    current.longitude = value?.toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
                name == "connectorType" && parent == "connector" -> connectorType = value
                name == "chargingMode" && parent == "connector" -> connectorMode = value
                name == "maxPowerAtSocket" && parent == "connector" ->
                    connectorPower = value?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }?.div(1000)
                name == "connector" && parent == "refillPoint" -> {
                    val station = stationId
                    val point = pointId
                    val type = connectorType
                    if (!station.isNullOrBlank() && !point.isNullOrBlank() && type != null) {
                        current.connectors.add(Connector(station, point, type, connectorMode, connectorPower))
                    }
                }
                name == "refillPoint" && parent == "energyInfrastructureStation" -> pointId = null
                name == "energyInfrastructureStation" && parent == "energyInfrastructureSite" -> stationId = null
                name == "energyInfrastructureSite" && parent == "energyInfrastructureTable" -> {
                    current.complete()?.let {
                        save(it)
                        sites++
                        connectors += it.connectors.size
                    }
                    site = null
                }
            }
            content.setLength(0)
            path.removeAt(path.lastIndex)
        }
    }

    private class SiteFields(val id: String?) {
        var name: String? = null
        var operator: String? = null
        var hours: String? = null
        var latitude: Double? = null
        var longitude: Double? = null
        val connectors = mutableListOf<Connector>()

        fun complete(): Site? {
            val lat = latitude ?: return null
            val lon = longitude ?: return null
            if (id.isNullOrBlank() || connectors.isEmpty()) return null
            return Site(id, name, operator, hours, lat, lon, connectors)
        }
    }

    private val textFields = setOf("publicationTime", "value", "label", "latitude", "longitude",
        "connectorType", "chargingMode", "maxPowerAtSocket")
}
