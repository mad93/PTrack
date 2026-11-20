package cat.ptrack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingSectionTests {
    private fun sample(second: Int, kw: Double, speed: Double) =
        Sample(second * 1000L, 60.0, 400.0, kw * 2.5, speed, null, null, null, null)

    @Test fun mixedRecordingKeepsRegenerationAndSeparatesChargeFromBothDrives() {
        val first = listOf(sample(0, 12.0, 60.0), sample(5, -8.0, 30.0), sample(8, -2.0, 0.0))
        val charging = (15..45 step 5).map { sample(it, -36.0, 0.0) }
        val last = listOf(sample(48, 12.0, 30.0), sample(53, 12.0, 60.0))
        // An isolated stationary regeneration reading ends before sustained charging begins.
        val sections = RecordingSection.split(first + sample(10, 0.0, 0.0) + charging + last)
        assertEquals(listOf(false, true, false), sections.map { it.charging })
        assertEquals(0.3, sections[1].stats().recovered, 1e-9)
        assertEquals(0.0, sections[1].stats().km, 1e-9)
        assertEquals(10_000L, sections.first().stats().summary().durationMs)
        assertEquals(5_000L, sections.last().stats().summary().durationMs)
        assertTrue(sections.first().stats().recovered > 0)
        assertEquals(0.0, sections.last().stats().recovered, 1e-9)
    }

    @Test fun shortStopsAndMissingSamplesNeedEvidenceOfCharging() {
        val samples = listOf(sample(0, -8.0, 0.0), sample(5, -8.0, 0.0), sample(50, -8.0, 0.0))
        assertFalse(RecordingSection.split(samples).single().charging)
        val sections = RecordingSection.split(samples, setOf(5000L, 50_000L))
        assertTrue(sections.all { it.charging })
        assertEquals(2, sections.size)
        assertEquals(0.0, sections.last().stats().recovered, 1e-9)
    }
}
