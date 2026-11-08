package cat.ptrack

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

@Composable
internal fun TripCalendar(
    month: YearMonth,
    annual: Boolean,
    counts: Map<LocalDate, Int>,
    labels: Labels,
    navigate: (YearMonth, Boolean) -> Unit,
    select: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val title = if (annual) month.year.toString()
        else month.format(DateTimeFormatter.ofPattern("LLLL yyyy", labels.locale))

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PeriodArrow("‹", labels.t("Període anterior", "Periodo anterior", "Previous period")) {
                navigate(if (annual) month.minusYears(1) else month.minusMonths(1), annual)
            }
            TextButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    DatePickerDialog(context, { _, year, index, _ ->
                        navigate(YearMonth.of(year, index + 1), annual)
                    }, month.year, month.monthValue - 1, 1).show()
                }
            ) {
                Text(title, style = typography.titleLarge, textAlign = TextAlign.Center)
            }
            PeriodArrow("›", labels.t("Període següent", "Periodo siguiente", "Next period")) {
                navigate(if (annual) month.plusYears(1) else month.plusMonths(1), annual)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(selected = !annual, onClick = { navigate(month, false) },
                label = { Text(labels.t("Mes", "Mes", "Month")) })
            FilterChip(selected = annual, onClick = { navigate(month, true) },
                label = { Text(labels.t("Any", "Año", "Year")) })
            Text(tripCount(counts.values.sum(), labels), Modifier.weight(1f), textAlign = TextAlign.End,
                style = typography.labelLarge, color = colors.onSurfaceVariant)
        }

        if (annual) {
            YearGrid(month, counts, labels) { navigate(it, false) }
        } else {
            MonthGrid(month, counts, labels, select)
        }
    }
}

@Composable
private fun PeriodArrow(symbol: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(symbol, Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun YearGrid(
    month: YearMonth,
    counts: Map<LocalDate, Int>,
    labels: Labels,
    onMonth: (YearMonth) -> Unit
) {
    val totals = counts.entries.groupingBy { it.key.month }.fold(0) { total, entry -> total + entry.value }
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(4) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { column ->
                    val target = month.withMonth(row * 3 + column + 1)
                    val count = totals[target.month] ?: 0
                    val name = target.month.getDisplayName(TextStyle.SHORT_STANDALONE, labels.locale)
                    val description = target.month.getDisplayName(TextStyle.FULL_STANDALONE, labels.locale)
                    Surface(
                        onClick = { onMonth(target) },
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = "$description, ${tripCount(count, labels)}"
                        },
                        color = if (count > 0) colors.primaryContainer else colors.surfaceContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            Modifier.clearAndSetSemantics {}.padding(vertical = 14.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(name, style = typography.titleMedium)
                            Text(count.toString(), style = typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    counts: Map<LocalDate, Int>,
    labels: Labels,
    select: (LocalDate) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val first = WeekFields.of(labels.locale).firstDayOfWeek.value
    val offset = (month.atDay(1).dayOfWeek.value - first + 7) % 7
    val weeks = (offset + month.lengthOfMonth() + 6) / 7
    val maxCount = (counts.values.maxOrNull() ?: 0).coerceAtLeast(2)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(labels.locale)

    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp)) {
            Row {
                repeat(7) { column ->
                    val weekday = DayOfWeek.of((first - 1 + column) % 7 + 1)
                    Text(
                        weekday.getDisplayName(TextStyle.SHORT_STANDALONE, labels.locale),
                        Modifier.weight(1f).padding(vertical = 8.dp),
                        style = typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            repeat(weeks) { row ->
                Row {
                    repeat(7) { column ->
                        val index = row * 7 + column - offset + 1
                        val cell = Modifier.weight(1f).heightIn(min = 48.dp)
                        if (index !in 1..month.lengthOfMonth()) {
                            Box(cell)
                        } else {
                            val date = month.atDay(index)
                            val count = counts[date] ?: 0
                            Box(
                                cell.clickable { select(date) }.semantics {
                                    contentDescription = "${date.format(dateFormat)}, ${tripCount(count, labels)}"
                                },
                                contentAlignment = Alignment.Center
                            ) {
                                if (count > 0) {
                                    Box(Modifier.size((26 + 12 * (count - 1) / (maxCount - 1)).dp)
                                        .background(colors.primaryContainer, CircleShape))
                                }
                                Text(
                                    index.toString(),
                                    Modifier.clearAndSetSemantics {},
                                    style = typography.bodyMedium,
                                    color = if (count > 0) colors.onPrimaryContainer else colors.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun tripCount(count: Int, labels: Labels) =
    if (count == 1) labels.t("1 viatge", "1 viaje", "1 trip")
    else labels.t("$count viatges", "$count viajes", "$count trips")
