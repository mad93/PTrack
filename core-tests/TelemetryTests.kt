package cat.ptrack

import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** No Android runtime, sockets or production database. */
class TelemetryTests {
    private var checks = 0
    private fun check(value: Boolean) { checks++; if (!value) throw AssertionError("Check $checks") }
    private fun near(value: Double, expected: Number) = check(value.isFinite() && abs(value - expected.toDouble()) < 1e-8)
    private fun s(time: Long, current: Number, speed: Number) = Sample(time, 70.0, 400.0, current.toDouble(), speed.toDouble(), null, null, null, "{}")
    private fun VehicleTelemetry.Snapshot.total() = checkNotNull(totals)
    @Test fun obdConnectionIsIndependentOfTelemetry() {
        val shared = VehicleTelemetry()
        check(!shared.snapshot().obdConnected)
        shared.accept(1, s(1000, 30, 60), true, 100)
        check(!shared.snapshot().obdConnected)

        val updates = AtomicInteger()
        shared.subscribe(Runnable { updates.incrementAndGet() })
        shared.obdConnection(true)
        shared.obdConnection(true)
        check(updates.get() == 1)
        shared.status("vehicle")
        check(shared.snapshot().current(100) == null)
        check(shared.snapshot().obdConnected)
        shared.obdConnection(false)
        check(!shared.snapshot().obdConnected)
        check(updates.get() == 3)
    }
    @Test fun sharedTelemetryAndLifecycle() {
        val shared = VehicleTelemetry()
        check(shared.snapshot().current(0) == null); check(shared.snapshot().connection(0) == "Desconnectat")
        val phone = AtomicInteger(); val car = AtomicInteger()
        val mobile = Runnable { phone.incrementAndGet() }; val headUnit = Runnable { car.incrementAndGet() }
        shared.subscribe(mobile); shared.subscribe(headUnit)
        shared.status("connecting"); check(shared.snapshot().connection(1) == "Connectant")
        val first = s(1000, 30, 60); shared.accept(7, first, false, 100)
        val initial = shared.snapshot()
        check(initial.sample === first); near(first.power(), 12)
        near(initial.instantaneous(100), 20); check(initial.flow(100) == "Consum de bateria")
        check(initial.current(10100) === first); check(initial.current(10101) == null); check(initial.current(99) == null)
        check(initial.connection(10101) == "Dades no disponibles"); check(phone.get() == 2 && car.get() == 2)
        val reference = TripStats(); reference.add(first)
        for (i in 1..60) { val sample = s(1000 + i * 1000L, 30, 60); shared.accept(7, sample, false, 100 + i * 1000L); reference.add(sample) }
        val total = shared.snapshot().total()
        near(total.km, reference.km); near(total.consumed, reference.consumed); near(total.recovered, reference.recovered)
        near(total.average, reference.consumption()); near(total.average, 20); check(total.samples == 61L); near(total.km, 1)
        shared.unsubscribe(headUnit); val before = shared.snapshot().total()
        shared.accept(7, s(62000, 30, 60), false, 61100)
        check(car.get() == 62); check(phone.get() == 63); check(shared.snapshot().total().samples == before.samples + 1)
        shared.subscribe(headUnit); shared.accept(7, s(63000, -30, 60), false, 62100)
        check(car.get() == 63); check(shared.snapshot().total().tripId == 7L)
        check(shared.snapshot().flow(62100).startsWith("Regeneració")); near(shared.snapshot().instantaneous(62100), -20)
        val frozen = shared.snapshot(); shared.status("missing"); check(shared.snapshot().current(62101) == null)
        check(shared.snapshot().connection(62101) == "Desconnectat"); near(shared.snapshot().total().consumed, frozen.total().consumed)
        shared.status("connecting"); shared.accept(7, s(90000, 30, 60), false, 89100)
        check(shared.snapshot().current(89100) != null); check(shared.snapshot().total().tripId == 7L)
        near(shared.snapshot().total().consumed, frozen.total().consumed); near(shared.snapshot().total().recovered, frozen.total().recovered)
        val samples = shared.snapshot().total().samples
        shared.accept(7, s(90000, 400, 60), false, 99999); shared.accept(7, s(89000, 400, 60), false, 99999)
        check(shared.snapshot().total().samples == samples); check(shared.snapshot().receivedAt == 89100L)
        shared.accept(7, s(91000, 10, 0), false, 90100); check(shared.snapshot().instantaneous(90100).isNaN())
        shared.accept(7, s(92000, 10, 4.99), false, 91100); check(shared.snapshot().instantaneous(91100).isNaN())
        shared.accept(7, s(93000, 10, 5), false, 92100); near(shared.snapshot().instantaneous(92100), 80)
        shared.accept(7, s(94000, -20, 0), false, 93100); check(shared.snapshot().flow(93100).contains("origen no confirmat"))
        shared.cable(1.0, 93100); check(shared.snapshot().flow(93100).contains("Càrrega probable"))
        shared.accept(7, s(100000, -20, 0), false, 99100); check(!shared.snapshot().flow(103101).contains("Càrrega"))
        shared.cable(Double.NaN, 99100); check(shared.snapshot().cable == null)
        shared.accept(7, s(101000, Double.NaN, 60), false, 100100); check(shared.snapshot().current(100100) == null); check(shared.snapshot().state == "vehicle")
        shared.accept(7, null, false, 100100); check(shared.snapshot().current(100100) == null)
        shared.accept(7, s(102000, 30, 60), false, 101100); shared.finish(7)
        check(!shared.snapshot().total().active); check(shared.snapshot().current(101100) == null)
        check(CarDashboard.primary(shared.snapshot(), 101100)[1].value.startsWith("--")); check(CarDashboard.details(shared.snapshot(), 101100)[1].value.startsWith("Darrer:"))
        shared.accept(8, s(103000, 30, 60), true, 102100)
        check(shared.snapshot().simulation); check(shared.snapshot().total().samples == 1L); near(shared.snapshot().total().consumed, 0); check(shared.snapshot().total().average.isNaN())
        check(CarDashboard.primary(shared.snapshot(), 102100)[2].detail.contains("SIMULACIÓ"))
        check(CarDashboard.primary(shared.snapshot(), 112101)[0].value.startsWith("-- kW")); check(CarDashboard.primary(shared.snapshot(), 112101)[2].value.contains("-- % SOC"))
        check(CarDashboard.primary(shared.snapshot(), 112101)[1].detail.contains("-- kWh")); check(CarDashboard.details(shared.snapshot(), 112101)[2].value == "-- V · -- A")
        check(CarDashboard.primary(shared.snapshot(), 112101)[0].title == CarDashboard.primary(shared.snapshot(), 102100)[0].title)
        shared.subscribe(Runnable { error("consumer broken") }); shared.status("missing"); check(shared.snapshot().state == "missing")
        val retained = shared.snapshot()
        val producer = Thread { for (i in 0 until 1000) shared.accept(9, s(200000 + i * 1000L, 30, 60), false, 200000 + i * 1000L) }
        producer.start()
        repeat(1000) { val v = shared.snapshot(); check(v.sample == null || (v.total().tripId == 9L && v.total().samples >= 1)) }
        producer.join(); check(retained.simulation); check(retained.total().tripId == 8L); check(shared.snapshot().total().samples == 1000L)
        near(Profile.Signal("current", "6B4", "694", "D816", "[B4:B7]/64-1200", "A", -500.0, 500.0).decode("62D81600014000"), 80)
        near(Profile.Signal("current", "6B4", "694", "D816", "[B4:B7]/64-1200", "A", -500.0, 500.0).decode("62D81600011800"), -80)
        println("PASS: $checks telemetry checks (shared publication, TripStats parity, freshness, gaps, signs, units, simulation and lifecycle independence)")
    }
}
