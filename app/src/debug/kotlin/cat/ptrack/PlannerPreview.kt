package cat.ptrack

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cat.ptrack.ElectricRoutePlanner.Priority

@Preview(name = "Planificador al mòbil · clar", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Mapa i ruta · finestra ampla", widthDp = 1024, heightDp = 768, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlannerPreview() {
    val labels = Labels("ca")
    val form = PlannerFormState(
        origin = "Barcelona Sants", destination = "Girona",
        originError = null, destinationError = null, suggestions = null, focusRequest = null,
        dismissInput = 0, vehicle = "Peugeot e-2008", batterySource = "Bateria inicial · lectura OBD",
        picking = false, locating = false, expanded = false, soc = 80, reserve = 10, minimum = 50,
        priority = Priority.entries.first(), departure = "Sortida · Ara", canCalculate = true, busy = false,
        status = ""
    )
    PTrackTheme {
        PTrackScaffold(Destination.PLANNER.id, labels, "PTrack · Previsualització", onDestination = {}) {
            PlannerLayout(
                form = { PlannerForm(form, labels, onAction = {}) },
                map = { PreviewMap(it) },
                results = {
                    PlannerPanel {
                        Text("Ruta proposada", style = MaterialTheme.typography.titleLarge)
                        RouteHighlights("104 km", "83 min", "39,2%", labels)
                        Text("Conducció 83 min · càrrega 0 min · 0 parades")
                        Text("Capacitat útil de referència · 46,0 kWh", style = MaterialTheme.typography.bodySmall)
                        PlannerActions("Google Maps", {}, "Waze", {})
                    }
                },
                saved = { SavedPlannerItinerary(null, labels, {}, {}, { _, _ -> }, {}) }
            )
        }
    }
}

@Composable
private fun PreviewMap(modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = Color(0xFFE3EDE2)) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val points = listOf(Offset(.20f, .78f), Offset(.31f, .65f), Offset(.46f, .59f),
                    Offset(.49f, .42f), Offset(.63f, .36f), Offset(.70f, .24f), Offset(.83f, .19f))
                    .map { Offset(it.x * size.width, it.y * size.height) }
                val route = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }
                repeat(9) { index ->
                    val x = (index + 1) * size.width / 10
                    drawLine(Color(0xFFF7FAF5), Offset(x, 0f), Offset(x * .62f, size.height), strokeWidth = 3.dp.toPx())
                }
                repeat(8) { index ->
                    val y = (index + 1) * size.height / 9
                    drawLine(Color(0xFFF7FAF5), Offset(0f, y), Offset(size.width, y * .74f), strokeWidth = 2.dp.toPx())
                }
                drawPath(route, Color.White, style = Stroke(width = 9.dp.toPx()))
                drawPath(route, Color(0xFF155BD7), style = Stroke(width = 5.dp.toPx()))
                listOf(points.first(), points.last()).forEach { point ->
                    drawCircle(Color.White, 9.dp.toPx(), point)
                    drawCircle(Color(0xFF155BD7), 6.dp.toPx(), point)
                }
            }
            Text("Mapa esquemàtic · previsualització", Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color(0xFF244035), style = MaterialTheme.typography.labelLarge)
        }
    }
}
