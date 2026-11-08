package cat.ptrack

import org.junit.Assert.*
import org.junit.Test

class TripNavigationTests {
    @Test fun buildsNavigatorSpecificLinks() {
        assertEquals("https://waze.com/ul?ll=41.4%2C2.2&navigate=yes", TripNavigation.waze("41.4,2.2"))
        assertEquals("https://waze.com/ul?q=A%20%26%20B&navigate=yes", TripNavigation.waze("A & B"))
        assertEquals("google.navigation:q=A%20%26%20B&mode=d", TripNavigation.mapsNavigation("A & B"))
        assertEquals("geo:41.4,2.2", TripNavigation.carDestination("41.4,2.2"))
        assertEquals("geo:0,0?q=A%20%26%20B", TripNavigation.carDestination("A & B"))
    }

    @Test fun preservesMapsStopOrderAndRejectsLinksOutsidePlatformLimits() {
        val route = TripNavigation.mapsDirections(listOf("41.4,2.2", "42.2,3.0", "Destí"), browser = true)
        assertEquals("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=Dest%C3%AD&waypoints=41.4%2C2.2%7C42.2%2C3.0", route)
        assertNotNull(TripNavigation.mapsDirections(List(4) { "Point $it" }, browser = true))
        assertNull(TripNavigation.mapsDirections(List(5) { "Point $it" }, browser = true))
        assertNotNull(TripNavigation.mapsDirections(List(10) { "Point $it" }, browser = false))
        assertNull(TripNavigation.mapsDirections(List(11) { "Point $it" }, browser = false))
        assertNull(TripNavigation.mapsDirections(List(4) { "à".repeat(250) }, browser = false))
    }

    @Test fun advancesOnlyOnConfirmationAndEditsOnlyPendingPoints() {
        val plan = NavigationPlan(listOf("First", "Second", "Destination"))
        TripNavigation.waze(plan.next!!)
        assertEquals("First", plan.next)
        val afterStop = plan.advance().replaceRemaining(listOf("Other stop", "Destination"))
        assertEquals(listOf("First", "Other stop", "Destination"), afterStop.points)
        assertEquals("Other stop", afterStop.next)
        assertEquals("Destination", afterStop.advance().next)
        assertNull(afterStop.advance().advance().next)
    }

    @Test fun splitsMapsExportWithoutDroppingStopsAndReusesThePreviousEndpoint() {
        val plan = NavigationPlan(List(12) { "Stop ${it + 1}" }, origin = "Start")
        val app = TripNavigation.mapsSections(plan, browser = false)!!
        assertEquals(listOf(0..9, 10..11), app.map { it.stops })
        assertTrue(app.first().url.contains("&origin=Start&destination=Stop%2010&waypoints=Stop%201%7CStop%202"))
        assertTrue(app.last().url.endsWith("&origin=Stop%2010&destination=Stop%2012&waypoints=Stop%2011"))

        val web = TripNavigation.mapsSections(plan.advance(), browser = true)!!
        assertEquals(listOf(1..4, 5..8, 9..11), web.map { it.stops })
        assertFalse(web.first().url.contains("&origin="))
        assertTrue(web[1].url.contains("&origin=Stop%205&"))
        assertTrue(web.last().url.contains("&origin=Stop%209&destination=Stop%2012"))
    }

    @Test fun splitsMapsExportWhenEncodedWaypointsExceedTheUrlLimit() {
        val plan = NavigationPlan(List(4) { "à".repeat(200) + it })
        val sections = TripNavigation.mapsSections(plan, browser = true)
        assertNull(sections) // Even two of these addresses cannot fit as origin and destination.
        val shortEnough = plan.copy(points = List(4) { "à".repeat(130) + it })
        val split = TripNavigation.mapsSections(shortEnough, browser = true)!!
        assertEquals(listOf(0..1, 2..2, 3..3), split.map { it.stops })
        assertTrue(split.all { it.url.length <= 2048 })
    }

    @Test fun keepsChargerNamesSeparateFromRoutingCoordinates() {
        val charger = "41.4,2.2"
        val plan = NavigationPlan(listOf(charger, "Destination"), chargerNames = mapOf(charger to "Xarxa A & B"))
        assertEquals("Xarxa A & B", plan.label(charger))
        assertEquals("geo:41.4,2.2?z=17&q=Xarxa%20A%20%26%20B", TripNavigation.mapsCharger(charger, plan.label(charger)))
        assertTrue(TripNavigation.mapsChargerWeb(charger, plan.label(charger)).endsWith("Xarxa%20A%20%26%20B%2041.4%2C2.2"))
        assertTrue(TripNavigation.mapsSections(plan, browser = true)!!.single().url.endsWith("&waypoints=41.4%2C2.2"))
        assertEquals("Xarxa A & B", plan.replaceRemaining(listOf(charger, "New destination")).label(charger))
        assertTrue(plan.replaceRemaining(listOf("New destination")).chargerNames.isEmpty())
    }

    @Test fun parsesAddressesAndNormalizesValidCoordinatePairs() {
        assertEquals(listOf("41.4,2.2", "Carrer de Balmes, 10"),
            TripNavigation.parsePoints(" 41.4, 2.2\n\nCarrer de Balmes, 10 "))
        assertNull(TripNavigation.parsePoints("91, 2.2"))
        assertNull(TripNavigation.parsePoints("41.4, -181"))
        assertNull(TripNavigation.parsePoints("A|B"))
        assertNull(TripNavigation.parsePoints(" \n "))
    }
}
