package cat.ptrack

import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

class CoreTests {
    private var assertions = 0
    private fun near(actual: Double, expected: Number) { assertions++; if (abs(actual - expected.toDouble()) > 1e-8 || !actual.isFinite()) throw AssertionError("$actual != $expected") }
    private fun check(value: Boolean) { assertions++; if (!value) throw AssertionError() }
    private fun sample(time: Long, kw: Number, speed: Number, altitude: Double?) = Sample(time, 60 - time / 100000.0, 400.0, kw.toDouble() * 2.5, speed.toDouble(), null, null, altitude, "{}")
    @Test fun integrationFramingAndProfile() {
        val steady = TripStats(); for (i in 0..360) steady.add(sample(i * 10000L, 10, 100, null))
        near(steady.km, 100); near(steady.consumed, 10); near(steady.consumption(), 10); near(steady.averageSpeed(), 100); check(!steady.hasAltitude)
        val cross = TripStats(); cross.add(sample(0, 10, 0, 100.0)); cross.add(sample(10000, -10, 0, 106.0))
        near(cross.consumed, 25.0 / 3600); near(cross.recovered, 25.0 / 3600); near(cross.ascent, 6); check(cross.consumption().isNaN())
        cross.add(sample(20000, -10, 0, 102.0)); near(cross.descent, 0); cross.add(sample(30000, -10, 0, 99.0)); near(cross.descent, 7)
        val gap = TripStats(); gap.add(sample(1000, 40, 100, null)); gap.add(sample(61000, 40, 100, null)); near(gap.km, 0); near(gap.consumed, 0); gap.add(sample(61000, 40, 100, null)); check(gap.count == 2L)
        var payload = ElmProtocol.payload("22D410\r62 D4 10 78 00\r>", "D410"); near(ElmProtocol.unsigned(payload, 0, 2) / 512, 60)
        check(ElmProtocol.payload("62D8107800", "D410") == null); check(ElmProtocol.payload("62D4107", "D410") == null); check(ElmProtocol.payload("NO DATA", "D410") == null)
        payload = ElmProtocol.payload("007\r0:62D8160001\r1:2C00\r", "D816"); near(ElmProtocol.unsigned(payload, 0, 4) / 64 - 1200, 0)
        check(ElmProtocol.payload("0:62D8160001\r2:2C00", "D816") == null)
        var rejected = false
        try { ElmProtocol(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream()).exchange("2ED4100000") } catch (_: IOException) { rejected = true }
        check(rejected)
        near(Profile.Signal("soc", "6B4", "694", "D410", "[B4:B5]/512", "%", 0.0, 101.0).decode("62D4107800"), 60)
        near(Profile.Signal("current", "6B4", "694", "D816", "[B4:B7]/64-1200", "A", -500.0, 500.0).decode("62D81600011800"), -80)
        near(Profile.Signal("speed", "6A2", "682", "D402", "[B4:B5]/128", "km/h", 0.0, 250.0).decode("62D4023200"), 100)
        near(Profile.Signal("signed", "6A2", "682", "D000", "S[B4:B5]/10", "", -100.0, 100.0).decode("62D000FF9C"), -10)
        near(Profile.Signal("ambient", "6A2", "682", "D434", "SB4", "°C", -50.0, 70.0).decode("62D434F6"), -10)
        near(Profile.Signal("cell_min", "6B4", "694", "D86F", "[B4:B5]", "V", 2.0, 4.5, .001, 0.0).decode("62D86F0E74"), 3.7)
        check(ElmProtocol.payload("00A\r0:62D8160001\r1:2C00\r", "D816") == null)
        val longReply = StringBuilder("0F3\r"); val original = StringBuilder("62D440")
        for (i in 0 until 240) original.append(String.format(Locale.ROOT, "%02X", i))
        var start = 0; var n = 0
        while (start < original.length) { longReply.append((n % 16).toString(16)).append(':').append(original, start, min(original.length, start + 14)).append('\r'); start += 14; n++ }
        val cells = ElmProtocol.payload(longReply.toString(), "D440"); check(cells != null && cells.size == 240); near((checkNotNull(cells)[239].toInt() and 255).toDouble(), 239)
        check(ElmProtocol.payload(longReply.toString().replace("0F3", "0F4"), "D440") == null)
        check(Profile.Signal("soc", "6B4", "694", "D410", "[B4:B5]/512", "%", 0.0, 101.0).decode("62D410FFFF").isNaN())
        println("PASS: $assertions assertions (integration, regeneration, gaps, elevation, ELM framing, profile formulas and read-only guard)")
    }
}
