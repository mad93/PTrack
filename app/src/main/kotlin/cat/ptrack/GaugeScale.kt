package cat.ptrack

import kotlin.math.ceil
import kotlin.math.max

object GaugeScale {
    fun angle(value: Double, negative: Double, positive: Double): Double =
        if (!value.isFinite()) Double.NaN else 270 + 90 * (value / if (value < 0) negative else positive).coerceIn(-1.0, 1.0)
    fun battery(value: Double): Double = if (value.isFinite()) 180 + 1.8 * value.coerceIn(0.0, 100.0) else Double.NaN
    fun speed(value: Double, maxKmh: Double = 150.0): Double =
        if (!value.isFinite()) Double.NaN else 180 + 1.8 * (value.coerceIn(0.0, maxKmh) / maxKmh * 100.0)
    fun limit(baseline: Double, observed: Double, step: Double): Double =
        ceil(max(baseline, observed) / step) * step
}
