package cat.ptrack

internal data class RecordingSection(val samples: List<Sample>, val charging: Boolean) {
    fun stats() = TripStats().also { samples.forEach(it::add) }

    companion object {
        const val CHARGING_SIGNAL = "external_charge_confirmed"

        fun split(samples: List<Sample>, confirmed: Set<Long> = emptySet()): List<RecordingSection> = buildList {
            var drivingStart = 0
            var index = 0
            while (index < samples.size) {
                if (!samples[index].stationaryInput()) {
                    index++
                    continue
                }
                val start = index++
                while (index < samples.size && samples[index].stationaryInput() &&
                    samples[index].time - samples[index - 1].time in 1..10_000
                ) index++
                val run = samples.subList(start, index)
                if (run.last().time - run.first().time >= 20_000 || run.any { it.time in confirmed }) {
                    if (drivingStart < start) add(RecordingSection(samples.subList(drivingStart, start), false))
                    add(RecordingSection(run, true))
                    drivingStart = index
                }
            }
            if (drivingStart < samples.size) add(RecordingSection(samples.subList(drivingStart, samples.size), false))
        }

        private fun Sample.stationaryInput() = valid() && speed <= 1 && power() < -0.05
    }
}
