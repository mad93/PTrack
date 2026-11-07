package cat.ptrack

import org.junit.Test
import kotlin.math.abs

class SummaryTests {
    private var checks = 0
    private fun check(value: Boolean) { checks++; if (!value) throw AssertionError("Summary check $checks") }
    private fun near(a: Double, b: Number) = check(a.isFinite() && abs(a - b.toDouble()) < 1e-8)
    private fun s(time: Long, current: Number, speed: Number) = Sample(time, 70 - time / 1_000_000.0, 400.0, current.toDouble(), speed.toDouble(), null, null, null, "{}")
    @Test fun summariesAndGauges() {
        val trip = TripStats(); for (i in 0..60) trip.add(s(1000 + i * 1000L, 30, 60))
        trip.energy(1000, 32.0); trip.energy(61000, 31.8); val a = trip.summary()
        near(a.km, 1); near(a.consumed, .2); near(a.incoming, 0); near(a.net(), .2); near(a.averageConsumption, 20)
        near(a.averagePower, 12); near(a.averageSpeed, 60); near(a.maxDischargeKw, 12); near(a.maxDischargeA, 30)
        near(a.maxIncomingKw, 0); near(a.maxIncomingA, 0); near(a.maxSpeed, 60); near(a.socUsed(), .06)
        near(a.startEnergy, 32); near(a.endEnergy, 31.8); near(a.energyUsed(), .2); near(a.rangeKm, 159)
        check(a.count == 61L && a.durationMs == 60000L)
        val copy = TripSummary.fromValues(a.values()); near(copy.net(), a.net()); near(copy.rangeKm, a.rangeKm)
        trip.add(s(62000, -250, 40)); trip.add(s(63000, 350, 90)); val peaks = trip.summary()
        near(peaks.maxIncomingKw, 100); near(peaks.maxIncomingA, 250); near(peaks.maxDischargeKw, 140); near(peaks.maxDischargeA, 350); near(peaks.maxSpeed, 90)
        check(peaks.incoming > 0); near(peaks.net(), peaks.consumed - peaks.incoming)
        near(peaks.maxMovingIncomingKw, 100); near(peaks.maxMovingIncomingA, 250)
        trip.add(s(64000, -400, 0)); near(trip.summary().maxIncomingKw, 160); near(trip.summary().maxMovingIncomingKw, 100)
        near(GaugeScale.angle(-200.0, 200.0, 300.0), 180); near(GaugeScale.angle(0.0, 200.0, 300.0), 270); near(GaugeScale.angle(300.0, 200.0, 300.0), 360)
        near(GaugeScale.angle(-500.0, 200.0, 300.0), 180); near(GaugeScale.angle(900.0, 200.0, 300.0), 360)
        near(GaugeScale.battery(0.0), 180); near(GaugeScale.battery(50.0), 270); near(GaugeScale.battery(100.0), 360)
        near(GaugeScale.speed(0.0, 150.0), 180); near(GaugeScale.speed(75.0, 150.0), 270); near(GaugeScale.speed(150.0, 150.0), 360)
        near(GaugeScale.limit(300.0, 351.0, 50.0), 400); check(GaugeScale.battery(Double.NaN).isNaN()); check(GaugeScale.speed(Double.NaN).isNaN())
        val missing = TripStats(); missing.add(Sample(1, Double.NaN, 400.0, 30.0, 60.0, null, null, null, null))
        check(missing.summary().count == 0L); check(missing.summary().maxDischargeKw.isNaN()); check(missing.summary().startEnergy.isNaN())
        check(TripSummaryText.describe(missing.summary()).contains("No disponible"))
        missing.add(s(1000, 30, 60)); missing.add(Sample(2000, 70.0, 0.0, 30.0, 60.0, null, null, null, null)); missing.add(s(3000, 30, 60))
        near(missing.km, 0); near(missing.consumed, 0); check(missing.start == 1000L && missing.end == 3000L && missing.count == 2L)
        check(missing.summary().rangeKm.isNaN())
        trip.energy(90000, 31.0); check(trip.summary().endEnergy.isNaN())
        val late = TripStats(); for (i in 0..60) late.add(s(1000 + i * 1000L, 30, 60))
        late.energy(20000, 31.9); late.energy(61000, 31.8); check(late.summary().startEnergy.isNaN()); near(late.summary().endEnergy, 31.8)
        late.add(s(62000, -20, 0)); late.energy(62000, 31.9); check(late.summary().rangeKm.isNaN())
        val regen = TripStats(); for (i in 0..60) regen.add(s(1000 + i * 1000L, -30, 60)); regen.energy(61000, 32.0)
        check(regen.summary().averageConsumption < 0); check(regen.summary().rangeKm.isNaN())
        val source = VehicleTelemetry(); source.accept(1, s(1000, 30, 60), false, 100); source.energy(32.0, 100, 1000)
        near(source.snapshot().remaining(10100), 32); check(source.snapshot().remaining(10101).isNaN())
        source.status("missing"); check(source.snapshot().remaining(100).isNaN())
        near(Profile.Signal("energy", "6B4", "694", "D865", "[B4:B5]/64", "kWh", 0.0, 100.0).decode("62D8650800"), 32)
        check(TripSummaryText.lines(a, false).last().value == "No disponible")
        check(TripSummaryText.sections(a, true).size == 5); check(TripSummaryText.sections(a, false)[4].first == "No disponible")
        println("PASS: $checks summary/gauge checks (units, peaks, endpoints, range quality, absence, schema roundtrip and zero-centred geometry)")
    }
}
