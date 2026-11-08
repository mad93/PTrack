package cat.ptrack

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(name = "Mòbil · clar", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Mòbil · fosc", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Finestra ampla · fosc", widthDp = 1024, heightDp = 768, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LiveDashboardPreview() {
    val labels = Labels("ca")
    val sample = LiveUiState(
        vehicle = "Peugeot e-2008",
        status = "demo",
        charge = 68.4,
        power = 24.7,
        current = 66.2,
        detail = listOf("Velocitat" to "72,0 km/h", "Tensió de bateria" to "373,1 V")
    )

    PTrackTheme {
        PTrackScaffold(Destination.LIVE.id, labels, "PTrack · Previsualització", onDestination = {}) {
            Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
                LiveScreen(sample, labels, onConnect = {}, onStop = {})
            }
        }
    }
}
