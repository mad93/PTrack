package cat.ptrack

import cat.ptrack.ConsumptionModel.Estimate
import cat.ptrack.ConsumptionModel.Terrain
import kotlin.math.abs

/** A snapshot of the vehicle estimates used throughout one route draft. */
internal class PlanningEnergy(
    val learnedCapacityKwh: Double?,
    private val estimates: Map<ConsumptionLearner.Key, Estimate>,
    private val binWidth: Int,
    referenceCapacityKwh: Double = INITIAL_CAPACITY_KWH
) {
    val capacityKwh = learnedCapacityKwh ?: referenceCapacityKwh

    private val flatEstimates = estimates.filter { (key, value) ->
        key.terrain == Terrain.LEVEL && value.kwhPer100Km?.let { it > 0 } == true
    }
    private val referenceScale = run {
        val calibration = flatEstimates.ifEmpty {
            estimates.filter { (key, value) ->
                key.terrain == Terrain.UNKNOWN && value.kwhPer100Km?.let { it > 0 } == true
            }
        }
        val distance = calibration.values.sumOf { it.distanceKm }
        if (distance <= 0) return@run 1.0
        calibration.entries.sumOf { (key, value) ->
            val speed = (key.speedBin + .5) * binWidth
            value.distanceKm * (checkNotNull(value.kwhPer100Km) / flatReference(speed)).coerceIn(.65, 1.5)
        } / distance
    }

    data class Segment(val kwh: Double, val usesReference: Boolean)

    fun segment(km: Double, speedKmh: Double, elevationChange: Double?): Segment {
        val speed = speedKmh.takeIf { it.isFinite() && it > 0 }?.coerceIn(5.0, 250.0) ?: 90.0
        val terrain = when {
            elevationChange == null -> Terrain.UNKNOWN
            elevationChange > km * 20 -> Terrain.UPHILL
            elevationChange < -km * 20 -> Terrain.DOWNHILL
            else -> Terrain.LEVEL
        }
        val bin = (speed / binWidth).toInt()
        val learned = estimates[ConsumptionLearner.Key(bin, terrain)]?.kwhPer100Km
        if (learned != null) return Segment(learned.coerceAtLeast(0.0) * km / 100 * 1.10, false)

        val flat = flatEstimates[ConsumptionLearner.Key(bin, Terrain.LEVEL)]?.kwhPer100Km
            ?: flatReference(speed) * referenceScale
        val levelEnergy = flat * km / 100 * 1.15
        val change = elevationChange ?: 0.0
        val hillEnergy = if (change >= 0) change * .006 else change * .003
        return Segment((levelEnergy + hillEnergy).coerceAtLeast(levelEnergy * .25), true)
    }

    companion object {
        const val INITIAL_CAPACITY_KWH = 46.0
        const val MAX_CAPACITY_TRIPS = 12

        fun capacityFrom(summary: TripSummary, stationaryCharging: Boolean): Double? {
            val drop = summary.socUsed()
            if (stationaryCharging || summary.count < 30 || summary.durationMs < 120_000 ||
                summary.startSoc !in 0.0..100.0 || summary.endSoc !in 0.0..100.0 || drop < 10) return null
            val measured = summary.energyUsed()
            val kwh = if (measured.isFinite()) measured
            else if (summary.coveredSeconds * 1000 >= summary.durationMs * .95) summary.net()
            else return null
            return (kwh * 100 / drop).takeIf { it in 23.0..69.0 }
        }

        fun learnedCapacity(recentEstimates: List<Double>): Double? {
            if (recentEstimates.size < 3) return null
            val center = median(recentEstimates)
            val consistent = recentEstimates.filter { abs(it - center) <= center * .15 }
            return if (consistent.size >= 3 && consistent.size * 2 > recentEstimates.size) median(consistent) else null
        }

        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            return (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
        }

        // Rolling load, auxiliary load per kilometre and aerodynamic load.
        private fun flatReference(speedKmh: Double): Double {
            val speed = speedKmh.coerceIn(15.0, 140.0)
            return 9.0 + 150 / speed + 7.33 * (speed / 90) * (speed / 90)
        }
    }
}
