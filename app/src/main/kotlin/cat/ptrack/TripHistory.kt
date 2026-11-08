package cat.ptrack

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

internal data class TripListEntry(
    val id: Long,
    val startMs: Long,
    val demo: Boolean,
    val status: String,
    val vehicle: String
)

internal data class TripDetail(
    val summary: TripSummary,
    val ascent: Double?,
    val descent: Double?
)

internal class TripHistory(private val store: Store) {
    fun counts(month: YearMonth, annual: Boolean, vehicleId: String?): Map<LocalDate, Int> {
        val zone = ZoneId.systemDefault()
        val period = if (annual) month.withMonth(1) else month
        val next = if (annual) period.plusYears(1) else period.plusMonths(1)
        return buildMap {
            store.tripsBetween(period.atDay(1).startMs(zone), next.atDay(1).startMs(zone), vehicleId).use { rows ->
                while (rows.moveToNext()) {
                    val day = Instant.ofEpochMilli(rows.getLong(1)).atZone(zone).toLocalDate()
                    put(day, (get(day) ?: 0) + 1)
                }
            }
        }
    }

    fun entries(day: LocalDate, vehicleId: String?): List<TripListEntry> {
        val zone = ZoneId.systemDefault()
        return buildList {
            store.tripsBetween(day.startMs(zone), day.plusDays(1).startMs(zone), vehicleId).use { rows ->
                while (rows.moveToNext()) {
                    add(TripListEntry(rows.getLong(0), rows.getLong(1), rows.getInt(3) == 1,
                        rows.getString(4), rows.getString(5)))
                }
            }
        }
    }

    fun detail(id: Long): TripDetail {
        val stats = TripStats()
        store.samples(id).forEach(stats::add)
        return TripDetail(store.summary(id), stats.ascent.takeIf { stats.hasAltitude },
            stats.descent.takeIf { stats.hasAltitude })
    }

    private fun LocalDate.startMs(zone: ZoneId) = atStartOfDay(zone).toInstant().toEpochMilli()
}
