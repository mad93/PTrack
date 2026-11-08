package cat.ptrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

internal data class TripsSelection(
    val month: YearMonth,
    val annual: Boolean,
    val day: LocalDate?,
    val trip: Long?,
    val allVehicles: Boolean
)

@Composable
internal fun TripsScreen(
    selection: TripsSelection,
    vehicle: VehicleProfile,
    history: TripHistory,
    revision: Int,
    labels: Labels,
    onFilter: (Boolean) -> Unit,
    onPeriod: (YearMonth, Boolean) -> Unit,
    onDay: (LocalDate?) -> Unit,
    onTrip: (Long?) -> Unit,
    onExport: (Long, String) -> Unit
) {
    val vehicleId = vehicle.id.takeUnless { selection.allVehicles }
    val filter: @Composable () -> Unit = {
        VehicleFilter(vehicle.label, selection.allVehicles, labels, onFilter)
    }
    val day = selection.day
    if (day == null) {
        key(selection.month, selection.annual, vehicleId) {
            val counts = historyResult(revision) { history.counts(selection.month, selection.annual, vehicleId) }
            TripsCalendarPane(labels, filter) {
                val data = counts?.getOrNull()
                if (data == null) {
                    HistoryFeedback(labels, counts?.isFailure == true)
                } else {
                    TripCalendar(selection.month, selection.annual, data, labels, onPeriod, onDay)
                }
            }
        }
        return
    }

    key(day, vehicleId) {
        val entries = historyResult(revision) { history.entries(day, vehicleId) }
        val rows = entries?.getOrNull()
        val openedTrip = selection.trip
        val selected = rows?.firstOrNull { it.id == openedTrip }
        TripDayLayout(
            selectedTrip = openedTrip,
            list = {
                TripsDayList(day, rows, entries?.isFailure == true, openedTrip, selection.allVehicles,
                    labels, filter, onCalendar = { onDay(null) }, onTrip = onTrip)
            },
            detail = {
                when {
                    openedTrip == null -> HistoryMessage(labels.t("Tria un viatge per veure’n el resum.",
                        "Elige un viaje para ver su resumen.", "Choose a trip to see its summary."))
                    selected == null && rows == null && entries?.isFailure != true ->
                        HistoryFeedback(labels, failed = false)
                    selected == null -> Column {
                        TextButton(onClick = { onTrip(null) }) { Text(backToTrips(labels)) }
                        HistoryFeedback(labels, failed = true)
                    }
                    else -> key(selected.id) {
                        val detail = historyResult(revision) { history.detail(selected.id) }
                        TripDetailPane(
                            selected, detail?.getOrNull(), detail?.isFailure == true, labels,
                            onBack = { onTrip(null) }, onExport = { onExport(selected.id, it) }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun <T> historyResult(revision: Int, load: () -> T): Result<T>? {
    val result by produceState<Result<T>?>(null, revision) {
        value = withContext(Dispatchers.IO) { runCatching(load) }
    }
    return result
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun TripDayLayout(
    selectedTrip: Long?,
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit
) {
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    val destination = ThreePaneScaffoldDestinationItem(
        if (selectedTrip == null) ListDetailPaneScaffoldRole.List else ListDetailPaneScaffoldRole.Detail,
        selectedTrip
    )
    val layout = calculateThreePaneScaffoldValue(
        directive.maxHorizontalPartitions,
        ListDetailPaneScaffoldDefaults.adaptStrategies(),
        destination
    )
    ListDetailPaneScaffold(
        directive = directive,
        value = layout,
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp),
        listPane = { AnimatedPane { list() } },
        detailPane = { AnimatedPane { detail() } }
    )
}

@Composable
internal fun TripsCalendarPane(
    labels: Labels,
    filter: @Composable () -> Unit,
    calendar: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(Destination.TRIPS.label(labels), style = MaterialTheme.typography.headlineSmall)
            filter()
            calendar()
        }
    }
}

@Composable
internal fun VehicleFilter(vehicle: String, all: Boolean, labels: Labels, onChange: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = labels.t("Tots els vehicles", "Todos los vehículos", "All vehicles")
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (!all) Text(vehicle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = all, onCheckedChange = onChange, modifier = Modifier.semantics {
                contentDescription = title
            })
        }
    }
}

@Composable
internal fun TripsDayList(
    day: LocalDate,
    rows: List<TripListEntry>?,
    failed: Boolean,
    selected: Long?,
    allVehicles: Boolean,
    labels: Labels,
    filter: @Composable () -> Unit,
    onCalendar: () -> Unit,
    onTrip: (Long) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            TextButton(onClick = onCalendar) {
                Text(labels.t("Torna al calendari", "Volver al calendario", "Back to calendar"))
            }
            Text(day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(labels.locale)),
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            filter()
        }
        when {
            rows == null -> item { HistoryFeedback(labels, failed) }
            rows.isEmpty() -> item { HistoryMessage(labels.t("Cap viatge aquest dia.", "Ningún viaje este día.", "No trips on this day.")) }
            else -> items(rows, key = { it.id }) { entry ->
                TripRow(entry, selected == entry.id, allVehicles, labels) { onTrip(entry.id) }
            }
        }
    }
}

@Composable
private fun TripRow(entry: TripListEntry, selected: Boolean, allVehicles: Boolean, labels: Labels, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val time = DateFormat.getTimeInstance(DateFormat.SHORT, labels.locale).format(Date(entry.startMs))
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
        color = if (selected) colors.primaryContainer else colors.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("$time · #${entry.id}", style = MaterialTheme.typography.titleMedium)
            if (allVehicles) Text(entry.vehicle, style = MaterialTheme.typography.bodyMedium)
            val badges = listOfNotNull(
                simulationLabel(labels).takeIf { entry.demo },
                when (entry.status) {
                    "recording" -> labels.t("En curs", "En curso", "Recording")
                    "interrupted" -> labels.t("Interromput", "Interrumpido", "Interrupted")
                    else -> null
                }
            )
            if (badges.isNotEmpty()) Text(badges.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun TripDetailPane(
    entry: TripListEntry,
    detail: TripDetail?,
    failed: Boolean,
    labels: Labels,
    onBack: () -> Unit,
    onExport: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(backToTrips(labels)) }
        Text(labels.t("Viatge", "Viaje", "Trip") + " #${entry.id}", style = type.headlineSmall)
        Text(entry.vehicle, style = type.titleMedium, color = colors.onSurfaceVariant)
        if (entry.demo) Text(simulationLabel(labels), style = type.labelLarge, color = colors.secondary)
        if (detail == null) {
            HistoryFeedback(labels, failed)
        } else {
            val lines = TripSummaryText.lines(detail.summary, labels = labels)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2
            ) {
                lines.take(4).forEach { line ->
                    Surface(Modifier.weight(1f), color = colors.primaryContainer,
                        shape = MaterialTheme.shapes.medium) {
                        SummaryMetric(line, Modifier.padding(12.dp), prominent = true)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { onExport("pdf") }, modifier = Modifier.fillMaxWidth()) {
                    Text(labels.t("Exporta informe PDF", "Exportar informe PDF", "Export PDF report"))
                }
                OutlinedButton(onClick = { onExport("json") }, modifier = Modifier.fillMaxWidth()) {
                    Text(labels.t("Exporta registre complet JSON", "Exportar registro completo JSON", "Export full JSON log"))
                }
            }
            Text(labels.t("Detall del trajecte", "Detalle del trayecto", "Trip details"), style = type.titleMedium)
            val altitude = if (detail.ascent != null && detail.descent != null) {
                labels.n(detail.ascent, 0) + " / " + labels.n(detail.descent, 0) + " m"
            } else "—"
            val metrics = lines.drop(4) + TripSummaryText.Line(
                labels.t("Pujada / baixada GPS", "Ascenso / descenso GPS", "GPS ascent / descent"), altitude
            )
            Surface(Modifier.fillMaxWidth(), color = colors.surfaceContainer, shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(horizontal = 12.dp)) {
                    metrics.forEachIndexed { index, line ->
                        if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                        SummaryMetric(line, Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(line: TripSummaryText.Line, modifier: Modifier, prominent: Boolean = false) {
    val type = MaterialTheme.typography
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(line.title, style = type.labelMedium)
        Text(line.value, style = if (prominent) type.titleMedium else type.bodyLarge)
    }
}

@Composable
private fun HistoryFeedback(labels: Labels, failed: Boolean) {
    HistoryMessage(if (failed) labels.t("No s’han pogut carregar les dades.", "No se han podido cargar los datos.", "Could not load the data.")
        else labels.t("Carregant viatges…", "Cargando viajes…", "Loading trips…"))
}

@Composable
private fun HistoryMessage(message: String) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun backToTrips(labels: Labels) = labels.t("Torna als viatges", "Volver a viajes", "Back to trips")
private fun simulationLabel(labels: Labels) = labels.t("SIMULACIÓ", "SIMULACIÓN", "SIMULATION")
