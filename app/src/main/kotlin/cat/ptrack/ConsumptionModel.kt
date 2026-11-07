package cat.ptrack

/** Query boundary for a future planner. No extrapolation to unobserved speeds or terrain. */
interface ConsumptionModel {
    enum class Terrain { UNKNOWN, LEVEL, UPHILL, DOWNHILL }
    enum class Confidence { INSUFFICIENT, LOW, MEDIUM, HIGH }
    data class Estimate(val observedKwhPer100Km: Double?, val kwhPer100Km: Double?, val validIntervals: Long,
        val distanceKm: Double, val trips: Int, val confidence: Confidence) {
        fun status() = if (confidence == Confidence.INSUFFICIENT) "dades insuficients" else "disponible"
    }
    fun estimate(speedKmh: Double, terrain: Terrain?): Estimate
}
