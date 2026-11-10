package cat.ptrack

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

private val Day = LocalDate.of(2026, 11, 8)
private val PreviewLabels = Labels("ca")

@Preview(name = "Calendari mensual · clar", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TripsCalendarPreview() = PreviewCalendar(annual = false)

@Preview(name = "Calendari anual · clar", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun TripsYearPreview() = PreviewCalendar(annual = true)

@Composable
private fun PreviewCalendar(annual: Boolean) {
    val month = YearMonth.from(Day)
    val counts = mapOf(month.atDay(3) to 1, Day to 3, month.atDay(12) to 1, month.atDay(24) to 2)
    PTrackTheme {
        PTrackScaffold(Destination.TRIPS.id, PreviewLabels, onDestination = {}) {
            TripsCalendarPane(
                PreviewLabels,
                filter = { VehicleFilter("Peugeot e-2008", false, PreviewLabels, onChange = {}) }
            ) {
                TripCalendar(month, annual, counts, PreviewLabels, navigate = { _, _ -> }, select = {})
            }
        }
    }
}

@Preview(name = "Detall al mòbil · fosc", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Llista i detall · fosc", widthDp = 1024, heightDp = 768, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripDayPreview() = PreviewDay(showDetail = true)

@Preview(name = "Llista al mòbil · fosc", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TripDayListPreview() = PreviewDay(showDetail = false)

@Composable
private fun PreviewDay(showDetail: Boolean) {
    val sample = TripListEntry(42, time(15, 34), true, "closed", "Peugeot e-2008")
    val entries = listOf(sample, sample.copy(id = 41, startMs = time(11, 25)),
        sample.copy(id = 40, startMs = time(8, 3), status = "interrupted"))
    val summary = TripSummary(
        count = 840, durationMs = 1_874_000, km = 27.31, coveredSeconds = 1820.0,
        consumed = 4.8, incoming = 0.7, averageSpeed = 52.4, averagePower = 8.1,
        averageConsumption = 15.0, maxDischargeKw = 69.0, maxIncomingKw = 28.0,
        maxDischargeA = 198.2, maxIncomingA = 76.5, maxSpeed = 90.0,
        startSoc = 79.0, endSoc = 70.0, startEnergy = 34.7, endEnergy = 30.6,
        rangeKm = 204.0, maxMovingIncomingKw = 27.0, maxMovingIncomingA = 74.0
    )

    PTrackTheme {
        val selection = sample.id.takeIf { showDetail }
        PTrackScaffold(Destination.TRIPS.id, PreviewLabels, onDestination = {}) {
            TripDayLayout(
                selectedTrip = selection,
                list = {
                    TripsDayList(Day, entries, false, selection, false, PreviewLabels,
                        filter = { VehicleFilter(sample.vehicle, false, PreviewLabels, onChange = {}) },
                        onCalendar = {}, onTrip = {})
                },
                detail = {
                    TripDetailPane(sample, TripDetail(summary, 205.0, 119.0), false, PreviewLabels,
                        onBack = {}, onExport = {})
                }
            )
        }
    }
}

private fun time(hour: Int, minute: Int) =
    Day.atTime(LocalTime.of(hour, minute)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
