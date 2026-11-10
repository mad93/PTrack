package cat.ptrack

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class Destination(val id: Int, @get:DrawableRes val icon: Int) {
    LIVE(0, R.drawable.ic_live),
    TRIPS(1, R.drawable.ic_trips),
    SETTINGS(2, R.drawable.ic_settings),
    PLANNER(3, R.drawable.ic_planner);

    fun label(labels: Labels): String = when (this) {
        LIVE -> labels.t("En directe", "En directo", "Live")
        TRIPS -> labels.t("Viatges", "Viajes", "Trips")
        PLANNER -> labels.t("Planificador", "Planificador", "Planner")
        SETTINGS -> labels.t("Configuració", "Ajustes", "Settings")
    }
}

private val Tabs = listOf(Destination.LIVE, Destination.TRIPS, Destination.PLANNER)

@Composable
internal fun PTrackScaffold(
    page: Int,
    labels: Labels,
    onDestination: (Destination) -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val settings = Destination.SETTINGS
    val settingsSelected = page == settings.id

    Surface(Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            navigationSuiteItems = {
                Tabs.forEach { destination ->
                    item(
                        selected = page == destination.id,
                        onClick = { onDestination(destination) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(destination.label(labels)) }
                    )
                }
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PTrack",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.primary
                    )
                    IconButton(
                        onClick = { onDestination(settings) },
                        modifier = Modifier.semantics { selected = settingsSelected }
                    ) {
                        Icon(
                            painterResource(settings.icon),
                            contentDescription = settings.label(labels),
                            tint = if (settingsSelected) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}
