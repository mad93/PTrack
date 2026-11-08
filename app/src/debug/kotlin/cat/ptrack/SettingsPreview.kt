package cat.ptrack

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Mòbil · català", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun SettingsPreview() = PreviewSettings("ca")

@Preview(name = "Finestra ampla · castellà", widthDp = 1024, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsWidePreview() = PreviewSettings("es")

@Preview(name = "Text gran · anglès", widthDp = 360, heightDp = 820, fontScale = 1.5f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsLargeTextPreview() = PreviewSettings("en")

@Preview(name = "Editor de vehicle · català", widthDp = 393, heightDp = 852, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun VehicleEditorPreview() = PreviewSettings("ca", showEditor = true)

@Composable
private fun PreviewSettings(language: String, showEditor: Boolean = false) {
    val labels = Labels(language)
    val vehicle = VehicleProfile("preview", "Peugeot e-2008", null, 46.0, null, maxDcChargeKw = 100.0)
    val settings = SettingsState(
        vehicle = vehicle.label,
        adapter = null,
        hasRememberedAdapters = true,
        automaticRecording = true,
        companionSupported = true,
        carTrigger = "Peugeot",
        backupDate = labels.t("Darrera còpia desada · 8 nov. 2026", "Última copia guardada · 8 nov. 2026", "Last backup saved · Nov 8, 2026"),
        backupConfigured = true,
        linkedBackups = 1,
        chargingText = labels.t("Darrera actualització · 2.410 emplaçaments",
            "Última actualización · 2.410 ubicaciones", "Last update · 2,410 sites")
    )
    PTrackTheme {
        PTrackScaffold(Destination.SETTINGS.id, labels, "PTrack · Previsualització", onDestination = {}) {
            SettingsScreen(settings, labels, onAction = {}, onAutomatic = {}, onLanguage = {})
            if (showEditor) {
                VehicleEditor(VehicleEditorRequest(vehicle, canArchive = false), labels,
                    onDismiss = {}, onSave = {}, onArchive = {})
            }
        }
    }
}
