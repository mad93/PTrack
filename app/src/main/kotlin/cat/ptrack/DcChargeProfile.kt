package cat.ptrack

import kotlin.math.abs
import kotlin.math.min

internal class DcChargeProfile(samples: List<Observation>) {
    data class Observation(val session: Long, val time: Long, val soc: Double, val kw: Double,
        val packC: Double, val ambientC: Double)
    data class Estimate(val minutes: Double, val learned: Boolean)

    private val bins = samples.groupBy { it.session }.values
        .filter { session -> session.size >= 3 && session.maxOf { it.time } - session.minOf { it.time } >= 20_000 }
        .flatMap { session ->
            session.groupBy { bin(it.soc) }.values.map { points ->
                Observation(points.first().session, points.first().time, points.first().soc,
                    median(points.map { it.kw }), median(points.map { it.packC }), median(points.map { it.ambientC }))
            }
        }.groupBy { bin(it.soc) }
    val hasData = bins.values.any { it.size >= 2 }

    fun curve(ambientC: Double?): Map<Int, Double> {
        if (ambientC == null) return emptyMap()
        return bins.mapNotNull { (bin, readings) ->
            val nearby = readings.filter { abs(it.ambientC - ambientC) <= 8 }
            if (nearby.size < 2) return@mapNotNull null
            val expectedPack = ambientC + median(nearby.map { it.packC - it.ambientC })
            val comparable = nearby.filter { abs(it.packC - expectedPack) <= 6 }
            if (comparable.size < 2) null else bin to median(comparable.map { it.kw })
        }.toMap()
    }

    companion object {
        fun isExternal(speed: Double, kw: Double, cable: Double) =
            speed <= 1 && kw < -.5 && cable == 1.0

        fun isDc(gridVoltage: Double) = gridVoltage in 0.0..5.0

        fun duration(start: Double, target: Double, capacity: Double, stopKw: Double,
            curve: Map<Int, Double>): Estimate {
            var minutes = 0.0
            var learned = false
            for (bin in 0 until 20) {
                val kwh = min(target, capacity * ((bin + 1) / 20.0)) -
                    maxOf(start, capacity * (bin / 20.0))
                if (kwh <= 0) continue
                val observed = curve[bin]
                val kw = observed?.let { min(it, stopKw) } ?: (stopKw * if (bin >= 16) .4 else 1.0)
                minutes += 60 * kwh / kw
                learned = learned || observed != null
            }
            return Estimate(minutes, learned)
        }

        private fun bin(soc: Double) = (soc / 5).toInt().coerceIn(0, 19)
        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
        }
    }
}
