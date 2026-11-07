package cat.ptrack

/** Immutable measured summary. NaN means absent, never an assumed battery capacity. */
data class TripSummary(
    val count: Long, val durationMs: Long, val km: Double, val coveredSeconds: Double,
    val consumed: Double, val incoming: Double, val averageSpeed: Double, val averagePower: Double,
    val averageConsumption: Double, val maxDischargeKw: Double, val maxIncomingKw: Double,
    val maxDischargeA: Double, val maxIncomingA: Double, val maxSpeed: Double,
    val startSoc: Double, val endSoc: Double, val startEnergy: Double, val endEnergy: Double,
    val rangeKm: Double, val maxMovingIncomingKw: Double, val maxMovingIncomingA: Double
) {
    fun values() = doubleArrayOf(count.toDouble(), durationMs.toDouble(), km, coveredSeconds, consumed, incoming,
        averageSpeed, averagePower, averageConsumption, maxDischargeKw, maxIncomingKw, maxDischargeA,
        maxIncomingA, maxSpeed, startSoc, endSoc, startEnergy, endEnergy, rangeKm, maxMovingIncomingKw, maxMovingIncomingA)
    fun net() = consumed - incoming
    fun socUsed() = startSoc - endSoc
    fun energyUsed() = startEnergy - endEnergy

    companion object {
        val KEYS = listOf("samples", "durationMs", "km", "coveredSeconds", "consumedKwh", "incomingKwh",
            "averageSpeedKmh", "averagePowerKw", "averageKwhPer100Km", "maxDischargeKw", "maxIncomingKw",
            "maxDischargeA", "maxIncomingA", "maxSpeedKmh", "startSoc", "endSoc", "startEnergyKwh",
            "endEnergyKwh", "estimatedRangeKm", "maxMovingIncomingKw", "maxMovingIncomingA")
        fun fromValues(a: DoubleArray): TripSummary {
            require(a.size == KEYS.size) { "Summary schema" }
            return TripSummary(a[0].toLong(), a[1].toLong(), a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9],
                a[10], a[11], a[12], a[13], a[14], a[15], a[16], a[17], a[18], a[19], a[20])
        }
    }
}
