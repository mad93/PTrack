package cat.ptrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdSessionTimingTests {
    @Test fun `OBD profile selection keeps a matching active vehicle and rejects ambiguous alternatives`() {
        assertEquals("a", VehicleProfiles.resolveObd("a", emptyList()))
        assertEquals("b", VehicleProfiles.resolveObd("a", listOf("b")))
        assertEquals("a", VehicleProfiles.resolveObd("a", listOf("b", "a")))
        assertNull(VehicleProfiles.resolveObd("a", listOf("b", "c")))
    }

    @Test fun `automatic attempt has a finite window without vehicle data`() {
        assertEquals(61_000L, ObdSessionTiming.automaticDeadline(1_000, null))
    }

    @Test fun `automatic attempt uses the trip gap once data arrives`() {
        assertEquals(55_000L, ObdSessionTiming.automaticDeadline(1_000, 10_000))
        assertEquals(104_000L, ObdSessionTiming.automaticDeadline(1_000, 59_000))
    }

    @Test fun `armed session can finish a trip that outlasts the wait window`() {
        assertEquals(1_801_000L, ObdSessionTiming.armedDeadline(1_000, null))
        assertEquals(1_801_000L, ObdSessionTiming.armedDeadline(1_000, 10_000))
        assertEquals(1_845_000L, ObdSessionTiming.armedDeadline(1_000, 1_800_000))
    }
}
