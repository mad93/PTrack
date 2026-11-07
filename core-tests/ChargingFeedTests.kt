package cat.ptrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.xml.sax.SAXException
import java.time.Instant

class ChargingFeedTests {
    @Test fun importsSiteAndKeepsConnectorsAndPowerSeparate() {
        val saved = mutableListOf<ChargingFeed.Site>()
        val result = ChargingFeed.read(xml.byteInputStream(), saved::add)
        assertEquals(Instant.parse("2026-11-07T09:16:37Z").toEpochMilli(), result.time)
        assertEquals(1, result.sites)
        assertEquals(2, result.connectors)
        val site = saved.single()
        assertEquals("Electrolinera", site.name)
        assertEquals("Operador", site.operator)
        assertEquals(41.4, site.latitude, 0.0)
        assertEquals(2.2, site.longitude, 0.0)
        assertEquals(
            listOf(
                ChargingFeed.Connector("station", "point", "iec62196T2COMBO", "mode4DC", 150.0),
                ChargingFeed.Connector("station", "point", "iec62196T2", null, null)
            ),
            site.connectors
        )
    }

    @Test fun rejectsIncompleteOrEmptyPublications() {
        assertThrows(SAXException::class.java) {
            ChargingFeed.read(xml.substringBefore("</d:payload>").byteInputStream()) {}
        }
        assertThrows(IllegalArgumentException::class.java) {
            ChargingFeed.read(xml.replace("41.4", "NaN").byteInputStream()) {}
        }
    }

    @Test fun rejectsDocumentTypes() {
        val input = "<!DOCTYPE payload [<!ENTITY unwanted 'text'>]>" + xml
        assertThrows(SAXException::class.java) { ChargingFeed.read(input.byteInputStream()) {} }
    }

    private val xml = """
        <d:payload xmlns:d="http://datex2.eu/schema/3/d2Payload"
            xmlns:e="http://datex2.eu/schema/3/energyInfrastructure"
            xmlns:c="http://datex2.eu/schema/3/common"
            xmlns:f="http://datex2.eu/schema/3/facilities"
            xmlns:l="http://datex2.eu/schema/3/locationReferencing">
          <c:publicationTime>2026-11-07T10:16:37+01:00</c:publicationTime>
          <e:energyInfrastructureTable>
            <e:energyInfrastructureSite id="site">
              <f:name><c:values><c:value>Electrolinera</c:value></c:values></f:name>
              <f:operator><f:name><c:values><c:value>Operador</c:value></c:values></f:name></f:operator>
              <f:locationReference><l:coordinatesForDisplay>
                <l:latitude>41.4</l:latitude><l:longitude>2.2</l:longitude>
              </l:coordinatesForDisplay></f:locationReference>
              <e:energyInfrastructureStation id="station">
                <e:refillPoint id="point">
                  <e:connector>
                    <e:connectorType>iec62196T2COMBO</e:connectorType>
                    <e:chargingMode>mode4DC</e:chargingMode>
                    <e:maxPowerAtSocket>150000</e:maxPowerAtSocket>
                  </e:connector>
                  <e:connector><e:connectorType>iec62196T2</e:connectorType></e:connector>
                </e:refillPoint>
              </e:energyInfrastructureStation>
            </e:energyInfrastructureSite>
          </e:energyInfrastructureTable>
        </d:payload>
    """.trimIndent()
}
