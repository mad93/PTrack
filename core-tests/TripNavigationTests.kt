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

    @Test fun parsesAddressesAndNormalizesValidCoordinatePairs() {
        assertEquals(listOf("41.4,2.2", "Carrer de Balmes, 10"),
            TripNavigation.parsePoints(" 41.4, 2.2\n\nCarrer de Balmes, 10 "))
        assertNull(TripNavigation.parsePoints("91, 2.2"))
        assertNull(TripNavigation.parsePoints("41.4, -181"))
        assertNull(TripNavigation.parsePoints("A|B"))
        assertNull(TripNavigation.parsePoints(" \n "))
    }
}
