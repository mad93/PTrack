package cat.ptrack

import cat.ptrack.ElectricRoutePlanner.Leg
import cat.ptrack.ElectricRoutePlanner.Point
import cat.ptrack.ElectricRoutePlanner.Priority
import cat.ptrack.ElectricRoutePlanner.Stop
import org.junit.Assert.*
import org.junit.Test

class ElectricRoutePlannerTests {
    private val origin = Point(41.0, 1.0)
    private val first = Point(41.0, 2.0)
    private val second = Point(41.0, 3.0)
    private val destination = Point(41.0, 4.0)

    @Test fun addsMultipleChargesAndKeepsBatteryReserve() {
        val stops = listOf(Stop(first, "First", 50.0), Stop(second, "Second", 100.0))
        val points = listOf(origin, first, second, destination)
        val plan = checkNotNull(ElectricRoutePlanner.plan(origin, destination, stops, 50.0, 70.0, 10.0, 100.0, Priority.FASTEST) { a, b ->
            val legs = points.indexOf(b) - points.indexOf(a)
            Leg(90.0 * legs, 28.0 * legs, 10.0 * legs)
        })
        assertEquals(stops, plan.charges.map { it.stop })
        assertTrue(plan.charges.all { it.arrivalKwh >= 5 && it.departureKwh <= 50 })
        assertTrue(plan.arrivalKwh >= 5)
        assertEquals(30.0, plan.estimatedKm, 0.0)
    }

    @Test fun objectivesCanChooseDifferentChargers() {
        val stops = listOf(Stop(first, "Close", 30.0), Stop(second, "Fast", 200.0))
        val legs = mapOf(
            (origin to destination) to Leg(100.0, 55.0, 0.0),
            (origin to first) to Leg(50.0, 20.0, 0.0),
            (first to destination) to Leg(50.0, 22.0, 0.0),
            (origin to second) to Leg(80.0, 20.0, 0.0),
            (second to destination) to Leg(80.0, 22.0, 0.0)
        )
        fun selected(priority: Priority) = checkNotNull(ElectricRoutePlanner.plan(
            origin, destination, stops, 60.0, 60.0, 10.0, 200.0, priority
        ) { a, b -> legs[a to b] }).charges.single().stop
        assertEquals(stops.first(), selected(Priority.FASTEST))
        assertEquals(stops.last(), selected(Priority.LEAST_CHARGING))
    }

    @Test fun rejectsDirectRouteWhenArrivalWouldFallBelowReserve() {
        assertNull(ElectricRoutePlanner.plan(origin, destination, emptyList(), 50.0, 50.0,
            20.0, 100.0, Priority.FASTEST) { _, _ -> Leg(20.0, 16.0, 0.0) })
    }
}
