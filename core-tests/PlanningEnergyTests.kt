package cat.ptrack

import cat.ptrack.ConsumptionModel.Confidence
import cat.ptrack.ConsumptionModel.Estimate
import cat.ptrack.ConsumptionModel.Terrain
import org.junit.Assert.*
import org.junit.Test

class PlanningEnergyTests {
    @Test fun capacityRequiresEnoughDischargeAndConsistentTrips() {
        val trip = TripStats().summary().copy(count = 600, durationMs = 600_000,
            coveredSeconds = 590.0, startSoc = 80.0, endSoc = 60.0, startEnergy = 35.0, endEnergy = 26.2,
            consumed = 9.2, incoming = 0.4)
        assertEquals(44.0, checkNotNull(PlanningEnergy.capacityFrom(trip, false)), .001)
        assertNull(PlanningEnergy.capacityFrom(trip, true))
        assertNull(PlanningEnergy.capacityFrom(trip.copy(endSoc = 75.0), false))
        assertNull(PlanningEnergy.capacityFrom(trip.copy(endEnergy = 12.0), false))
        val withoutBms = trip.copy(startEnergy = Double.NaN, endEnergy = Double.NaN)
        assertEquals(44.0, checkNotNull(PlanningEnergy.capacityFrom(withoutBms, false)), .001)
        assertNull(PlanningEnergy.capacityFrom(withoutBms.copy(coveredSeconds = 500.0), false))
        assertNull(PlanningEnergy.learnedCapacity(listOf(43.0, 44.0)))
        assertNull(PlanningEnergy.learnedCapacity(listOf(27.0, 45.0, 65.0)))
        val learned = PlanningEnergy.learnedCapacity(listOf(43.0, 44.0, 45.0, 66.0))
        assertEquals(44.0, PlanningEnergy(learned, emptyMap(), 10).capacityKwh, .001)
        assertEquals(46.0, PlanningEnergy(null, emptyMap(), 10).capacityKwh, .001)
    }

    @Test fun roadSpeedAndElevationShapeTheReferenceAndLearnedDataCalibratesIt() {
        val initial = PlanningEnergy(null, emptyMap(), 10)
        val flat = initial.segment(10.0, 90.0, 0.0)
        assertTrue(flat.usesReference)
        assertTrue(initial.segment(10.0, 125.0, 0.0).kwh > flat.kwh)
        assertTrue(initial.segment(10.0, 90.0, 300.0).kwh > flat.kwh)
        assertTrue(initial.segment(10.0, 90.0, -300.0).kwh < flat.kwh)

        val measured = Estimate(24.0, 24.0, 80, 12.0, 3, Confidence.LOW)
        val learned = PlanningEnergy(null, mapOf(ConsumptionLearner.Key(9, Terrain.LEVEL) to measured), 10)
        val measuredSegment = learned.segment(10.0, 90.0, 0.0)
        assertFalse(measuredSegment.usesReference)
        assertEquals(2.64, measuredSegment.kwh, .001)
        val uncovered = learned.segment(10.0, 125.0, 0.0)
        assertTrue(uncovered.usesReference)
        assertTrue(uncovered.kwh > initial.segment(10.0, 125.0, 0.0).kwh)
    }
}
