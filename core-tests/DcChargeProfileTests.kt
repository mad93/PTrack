package cat.ptrack

import cat.ptrack.ElectricRoutePlanner.ChargeConditions
import cat.ptrack.ElectricRoutePlanner.Leg
import cat.ptrack.ElectricRoutePlanner.Point
import cat.ptrack.ElectricRoutePlanner.Priority
import cat.ptrack.ElectricRoutePlanner.Stop
import org.junit.Assert.*
import org.junit.Test

class DcChargeProfileTests {
    @Test fun requiresCableAndRealGridReading() {
        assertTrue(DcChargeProfile.isExternal(0.0, -20.0, 1.0))
        assertFalse(DcChargeProfile.isExternal(40.0, -20.0, 1.0))
        assertFalse(DcChargeProfile.isExternal(0.0, -20.0, Double.NaN))
        assertFalse(DcChargeProfile.isExternal(0.0, 0.0, 1.0))
        assertFalse(DcChargeProfile.isDc(Double.NaN))
        assertFalse(DcChargeProfile.isDc(230.0))
        assertTrue(DcChargeProfile.isDc(0.0))
    }

    @Test fun learnsFromSeparateSessionsAtComparableTemperatures() {
        fun session(id: Long, pack: Double, ambient: Double, power: Double) =
            (0..2).map { DcChargeProfile.Observation(id, id * 60_000 + it * 10_000, 42.0, power, pack, ambient) }
        val profile = DcChargeProfile(session(1, 8.0, 0.0, 20.0) + session(2, 9.0, 1.0, 30.0) +
            session(3, 28.0, 20.0, 80.0) + session(4, 29.0, 21.0, 90.0))
        assertEquals(mapOf(8 to 25.0), profile.curve(0.0))
        assertEquals(mapOf(8 to 85.0), profile.curve(20.0))
        assertTrue(profile.curve(null).isEmpty())
        assertTrue(profile.curve(-20.0).isEmpty())
        assertTrue(DcChargeProfile(session(1, 8.0, 0.0, 20.0)).curve(0.0).isEmpty())
    }

    @Test fun combinesLearnedAndReferenceBinsAndCapsAtStationPower() {
        val estimate = DcChargeProfile.duration(20.0, 25.0, 50.0, 60.0, mapOf(8 to 100.0, 9 to 30.0))
        assertEquals(7.5, estimate.minutes, .0001)
        assertTrue(estimate.learned)
        val fallback = DcChargeProfile.duration(37.5, 42.5, 50.0, 100.0, emptyMap())
        assertEquals(5.25, fallback.minutes, .0001)
        assertFalse(fallback.learned)
    }

    @Test fun capsOnlyUnlearnedChargingAtTheVehicleMaximum() {
        val mixed = DcChargeProfile.duration(37.5, 42.5, 50.0, 150.0, mapOf(15 to 100.0), 50.0)
        assertEquals(9.0, mixed.minutes, .0001)
        assertTrue(mixed.learned)

        val slowerStation = DcChargeProfile.duration(20.0, 25.0, 50.0, 30.0, emptyMap(), 50.0)
        assertEquals(10.0, slowerStation.minutes, .0001)
        assertFalse(slowerStation.learned)
    }

    @Test fun estimatesLaterWeatherAfterPreviousChargeAndConnectionTime() {
        val points = (0..3).map { Point(41.0, it.toDouble()) }
        val stops = points.subList(1, 3).map { Stop(it, it.toString(), 100.0) }
        fun plan(power: Double) = checkNotNull(ElectricRoutePlanner.plan(points.first(), points.last(),
            stops, 50.0, 70.0, 10.0, Priority.FASTEST, conditions = { _, arrival ->
                ChargeConditions(if (arrival >= 210) 10.0 else 5.0, (0 until 20).associateWith { power })
            }) { from, to ->
                if (points.indexOf(to) - points.indexOf(from) == 1) Leg(90.0, 28.0, 0.0) else null
            })
        assertEquals(5.0, checkNotNull(plan(100.0).charges.last().ambientC), 0.0)
        val cold = plan(30.0)
        assertEquals(10.0, checkNotNull(cold.charges.last().ambientC), 0.0)
        assertTrue(cold.charges.last().arrivalMinutes >= 210)
    }
}
