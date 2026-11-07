package cat.ptrack

import cat.ptrack.ConsumptionModel.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class ConsumptionTests {
    private var checks = 0
    private fun check(value: Boolean) { checks++; if (!value) throw AssertionError("check $checks") }
    private fun near(a: Double?, b: Number) = check(a != null && a.isFinite() && abs(a - b.toDouble()) < 1e-7)
    private fun trip(speed: Number, power: Number, seconds: Int, grade: Double?): MutableList<Sample> {
        val list = mutableListOf<Sample>()
        for (t in 0..seconds step 5) {
            val km = speed.toDouble() * t / 3600
            list.add(Sample(1000 + t * 1000L, 60.0, 400.0, power.toDouble() * 2.5, speed.toDouble(), km / 6371 * 180 / PI,
                0.0, grade?.let { 100 + km * 1000 * it }, "{}"))
        }
        return list
    }
    private fun bucket(model: ConsumptionLearner, bin: Int, terrain: Terrain) = model.buckets()[ConsumptionLearner.Key(bin, terrain)]
    @Test fun learnedConsumption() {
        var model = ConsumptionLearner(10)
        val steady = trip(60, 12, 600, null)
        model.addTrip(steady)
        val b = checkNotNull(bucket(model, 6, Terrain.UNKNOWN))
        near(b.km, 10); near(b.consumedKwh, 2); near(b.recoveredKwh, 0)
        check(b.intervals == 120L && b.trips == 1)
        check(b.estimate().kwhPer100Km == null && b.estimate().status() == "dades insuficients")
        near(b.estimate().observedKwhPer100Km, 20)
        model.addTrip(steady); model.addTrip(steady)
        near(b.estimate().kwhPer100Km, 20); check(b.estimate().confidence == Confidence.LOW)
        model.addTrip(steady); model.addTrip(steady); check(b.estimate().confidence == Confidence.MEDIUM)
        repeat(5) { model.addTrip(steady) }
        check(b.estimate().confidence == Confidence.HIGH); check(b.trips == 10)
        model = ConsumptionLearner(20); model.addTrip(trip(60, -6, 600, null)); near(checkNotNull(bucket(model, 3, Terrain.UNKNOWN)).recoveredKwh, 1)
        model = ConsumptionLearner(10)
        model.addTrip(trip(60, 12, 30, null)); check(model.buckets().isEmpty())
        model.addTrip(trip(0, -12, 600, null)); check(model.buckets().isEmpty())
        for (mode in 0 until 9) {
            var invalid = steady.map { s -> Sample(s.time, if (mode == 0) Double.NaN else s.soc, if (mode == 1) 0.0 else s.voltage,
                if (mode == 2) 2000.0 else s.current, if (mode == 3) 300.0 else s.speed,
                if (mode == 4) null else if (mode == 5) 91.0 else s.latitude, if (mode == 6) null else s.longitude, null, "{}") }
            if (mode == 7) invalid = invalid.map { Sample(it.time, 60.0, 400.0, 30.0, 60.0, 0.0, 0.0, null, "{}") }
            if (mode == 8) invalid = invalid.map { Sample(it.time * 10, 60.0, 400.0, 30.0, 60.0, it.latitude, it.longitude, null, "{}") }
            val bad = ConsumptionLearner(10); bad.addTrip(invalid); check(bad.buckets().isEmpty())
        }
        for (terrain in arrayOf(Terrain.LEVEL, Terrain.UPHILL, Terrain.DOWNHILL)) {
            model = ConsumptionLearner(10)
            model.addTrip(trip(60, 12, 600, when (terrain) { Terrain.LEVEL -> 0.0; Terrain.UPHILL -> .04; else -> -.04 }))
            check(bucket(model, 6, terrain) != null)
        }
        val changing = mutableListOf<Sample>(); var km = 0.0
        for (i in 0..120) {
            val speed = if (i % 2 == 0) 55.0 else 65.0
            if (i > 0) km += 60 * 5 / 3600.0
            changing.add(Sample(1000 + i * 5000L, 60.0, 400.0, if (i % 2 == 0) 30.0 else -30.0, speed, km / 6371 * 180 / PI, 0.0, null, "{}"))
        }
        model = ConsumptionLearner(10); model.addTrip(changing)
        var total = 0.0; var consumed = 0.0; var recovered = 0.0
        for (value in model.buckets().values) { total += value.km; consumed += value.consumedKwh; recovered += value.recoveredKwh }
        near(total, 10); near(consumed, .5); near(recovered, .5); check(model.buckets().size == 2)
        val fragmented = trip(60, 12, 90, null)
        val sample = fragmented[9]; fragmented[9] = Sample(sample.time, Double.NaN, 400.0, 30.0, 60.0, sample.latitude, sample.longitude, null, "{}")
        model = ConsumptionLearner(10); model.addTrip(fragmented); check(model.buckets().isEmpty())
        for (mode in 0 until 5) {
            val broken = trip(60, 12, 90, null); val a = broken[8]; val bad = broken[9]
            broken[9] = Sample(when (mode) { 0 -> a.time; 1 -> a.time - 1; 2 -> bad.time + 20000; else -> bad.time },
                if (mode == 3) 90.0 else 60.0, 400.0, 30.0, 60.0, if (mode == 4) 45.0 else bad.latitude, bad.longitude, null, "{}")
            val rejectedModel = ConsumptionLearner(10); rejectedModel.addTrip(broken); check(rejectedModel.buckets().isEmpty())
        }
        model = ConsumptionLearner(10); model.addTrip(trip(60, 6, 600, null)); model.addTrip(trip(69, 12, 600, null)); model.addTrip(trip(60, 6, 600, null))
        near(checkNotNull(bucket(model, 6, Terrain.UNKNOWN)).estimate().kwhPer100Km, 400 / 31.5)
        model = ConsumptionLearner(10); model.addTrip(trip(60, 12, 600, .02)); check(bucket(model, 6, Terrain.UNKNOWN) != null)
        var rejected = false; try { ConsumptionLearner(0) } catch (_: IllegalArgumentException) { rejected = true }; check(rejected)
        println("PASS: $checks consumption-learning assertions")
    }
}
