package cat.ptrack

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp

internal data class LiveUiState(
    val vehicle: String,
    val status: String,
    val charge: Double?,
    val power: Double?,
    val current: Double?,
    val detail: List<Pair<String, String>>
)

@Composable
internal fun LiveScreen(state: LiveUiState, labels: Labels, onConnect: () -> Unit, onStop: () -> Unit) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(600)
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.background
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    labels.t("En directe", "En directo", "Live"),
                    style = type.labelLarge,
                    color = colors.primary
                )
                Text(state.vehicle, style = type.headlineSmall, fontWeight = FontWeight.SemiBold)
                ConnectionStatus(state.status, labels.status(state.status))
            }

            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ChargeCard(state.charge, labels, Modifier.weight(1f))
                    PowerMetrics(state, labels, Modifier.weight(1f))
                }
            } else {
                ChargeCard(state.charge, labels)
                PowerMetrics(state, labels)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Text(labels.t("Connecta OBD", "Conectar OBD", "Connect OBD"))
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(labels.t("Atura i desa el viatge", "Detener y guardar viaje", "Stop and save trip"))
                }
            }

            TelemetryDetails(state.detail, labels)
        }
    }
}

@Composable
private fun ConnectionStatus(status: String, label: String) {
    val colors = MaterialTheme.colorScheme
    val background = when (status) {
        "connected" -> colors.primaryContainer
        "demo" -> colors.secondaryContainer
        "permission", "reportError" -> colors.errorContainer
        else -> colors.surfaceContainerHigh
    }
    val content = when (status) {
        "connected" -> colors.onPrimaryContainer
        "demo" -> colors.onSecondaryContainer
        "permission", "reportError" -> colors.onErrorContainer
        else -> colors.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.medium, color = background, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(Modifier.size(7.dp).background(content, CircleShape))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ChargeCard(charge: Double?, labels: Labels, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(labels.signal("soc"), style = type.titleMedium)
            Text(
                readingText(charge, "%", labels),
                style = type.displaySmall,
                fontWeight = FontWeight.SemiBold
            )
            if (charge != null && charge.isFinite()) {
                LinearProgressIndicator(
                    progress = { (charge / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primary,
                    trackColor = colors.outlineVariant
                )
            }
        }
    }
}

@Composable
private fun PowerMetrics(state: LiveUiState, labels: Labels, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(labels.t("Potència", "Potencia", "Power"), state.power, "kW",
                -80.0, 120.0, labels, Modifier.weight(1f))
            MetricCard(labels.signal("current"), state.current, "A",
                -200.0, 300.0, labels, Modifier.weight(1f))
        }
        Text(
            labels.t("+ Consum · − Energia cap a la bateria", "+ Consumo · − Energía hacia la batería",
                "+ Discharge · − Energy into the battery"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: Double?,
    unit: String,
    min: Double,
    max: Double,
    labels: Labels,
    modifier: Modifier
) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val accent = if (value != null && value < 0) colors.secondary else colors.primary
    Surface(modifier, shape = MaterialTheme.shapes.large, color = colors.surfaceContainer) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = type.labelLarge, color = colors.onSurfaceVariant)
            val sign = if (value != null && value.isFinite() && value > 0) "+" else ""
            Text(sign + readingText(value, unit, labels), style = type.titleLarge,
                fontWeight = FontWeight.SemiBold)
            Canvas(Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 3.dp)) {
                val middle = size.height / 2
                val zero = size.width * (-min / (max - min)).toFloat()
                drawLine(colors.outlineVariant, Offset(0f, middle), Offset(size.width, middle),
                    strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                if (value != null && value.isFinite()) {
                    val tip = size.width * ((value.coerceIn(min, max) - min) / (max - min)).toFloat()
                    drawLine(accent, Offset(zero, middle), Offset(tip, middle),
                        strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
                }
                drawLine(colors.onSurfaceVariant, Offset(zero, 0f), Offset(zero, size.height),
                    strokeWidth = 1.dp.toPx())
            }
        }
    }
}

private fun readingText(value: Double?, unit: String, labels: Labels): String =
    (value?.let { labels.n(it, 1) } ?: "—") + " " + unit

@Composable
private fun TelemetryDetails(detail: List<Pair<String, String>>, labels: Labels) {
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labels.t("Telemetria del vehicle", "Telemetría del vehículo", "Vehicle telemetry"),
            style = type.titleMedium)
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainer
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                if (detail.isEmpty()) {
                    Text(
                        labels.t("Les dades apareixeran quan el vehicle respongui.",
                            "Los datos aparecerán cuando responda el vehículo.",
                            "Data appears when the vehicle responds."),
                        Modifier.padding(vertical = 10.dp),
                        style = type.bodyMedium,
                        color = colors.onSurfaceVariant
                    )
                }
                detail.forEachIndexed { index, (label, value) ->
                    if (index > 0) HorizontalDivider(color = colors.outlineVariant)
                    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = type.labelMedium, color = colors.onSurfaceVariant)
                        Text(value, style = type.bodyMedium.copy(lineBreak = LineBreak.Paragraph))
                    }
                }
            }
        }
    }
}
