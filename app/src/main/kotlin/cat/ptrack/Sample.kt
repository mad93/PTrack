package cat.ptrack

data class Sample(
    val time: Long, val soc: Double, val voltage: Double, val current: Double, val speed: Double,
    val latitude: Double?, val longitude: Double?, val altitude: Double?, val raw: String?
) {
    fun power(): Double = voltage * current / 1000.0
    fun valid(): Boolean = soc.isFinite() && soc in 0.0..101.0 && voltage.isFinite() && voltage > 0 &&
        current.isFinite() && power().isFinite() && speed.isFinite() && speed >= 0
}
