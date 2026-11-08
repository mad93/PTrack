package cat.ptrack

internal object ObdSessionTiming {
    const val TRIP_GAP_MS = 45_000L
    const val CONNECT_WINDOW_MS = 60_000L
    const val ARMED_WINDOW_MS = 30 * 60_000L

    fun automaticDeadline(startedAt: Long, lastValid: Long?): Long =
        if (lastValid == null) startedAt + CONNECT_WINDOW_MS else lastValid + TRIP_GAP_MS

    fun armedDeadline(startedAt: Long, lastValid: Long?): Long =
        maxOf(startedAt + ARMED_WINDOW_MS, lastValid?.plus(TRIP_GAP_MS) ?: 0L)
}
