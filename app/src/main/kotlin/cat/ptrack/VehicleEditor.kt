package cat.ptrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal data class VehicleEditorRequest(val vehicle: VehicleProfile?, val canArchive: Boolean)

@Composable
internal fun VehicleEditor(
    request: VehicleEditorRequest,
    labels: Labels,
    onDismiss: () -> Unit,
    onSave: (VehicleProfile) -> Unit,
    onArchive: (VehicleProfile) -> Unit
) {
    val vehicle = request.vehicle
    var name by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.name.orEmpty()) }
    var model by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.model.orEmpty()) }
    var capacity by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.capacityKwh?.toString().orEmpty()) }
    var power by rememberSaveable(vehicle?.id) { mutableStateOf(vehicle?.maxDcChargeKw?.toString().orEmpty()) }
    var invalid by rememberSaveable(vehicle?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(labels.t("Perfil de vehicle", "Perfil de vehículo", "Vehicle profile")) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProfileField(name, { name = it; if (invalid == "name") invalid = null },
                    labels.t("Nom del perfil", "Nombre del perfil", "Profile name"),
                    labels.t("Escriu un nom d’1 a 80 caràcters", "Escribe un nombre de 1 a 80 caracteres",
                        "Enter a name of 1–80 characters").takeIf { invalid == "name" })
                ProfileField(model, { model = it; if (invalid == "model") invalid = null },
                    labels.t("Model (opcional)", "Modelo (opcional)", "Model (optional)"),
                    labels.t("Màxim 80 caràcters", "Máximo 80 caracteres", "Maximum 80 characters").takeIf { invalid == "model" })
                ProfileField(capacity, { capacity = it; if (invalid == "capacity") invalid = null },
                    labels.t("Capacitat útil de referència (kWh)", "Capacidad útil de referencia (kWh)",
                        "Reference usable capacity (kWh)"),
                    labels.t("Introdueix una capacitat positiva", "Introduce una capacidad positiva",
                        "Enter a positive capacity").takeIf { invalid == "capacity" }, KeyboardType.Decimal)
                ProfileField(power, { power = it; if (invalid == "power") invalid = null },
                    labels.t("Potència màxima de càrrega DC (kW)", "Potencia máxima de carga DC (kW)",
                        "Maximum DC charging power (kW)"),
                    labels.t("Introdueix una potència positiva", "Introduce una potencia positiva",
                        "Enter a positive power").takeIf { invalid == "power" }, KeyboardType.Decimal)
                Text(labels.t("Opcional. Limita la potència estimada als trams de bateria sense corba de càrrega apresa.",
                    "Opcional. Limita la potencia estimada en los tramos de batería sin curva de carga aprendida.",
                    "Optional. Caps estimated power for battery levels without a learned charging curve."),
                    style = MaterialTheme.typography.bodySmall)
                if (vehicle != null && request.canArchive) {
                    TextButton(onClick = { onArchive(vehicle) }) {
                        Text(if (vehicle.archived) labels.t("Recupera", "Recuperar", "Restore")
                            else labels.t("Arxiva", "Archivar", "Archive"))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val label = name.trim()
                val modelName = model.trim().ifEmpty { null }
                val kwh = capacity.trim().replace(',', '.').toDoubleOrNull()
                val kw = power.trim().replace(',', '.').toDoubleOrNull()
                invalid = when {
                    label.length !in 1..80 -> "name"
                    (modelName?.length ?: 0) > 80 -> "model"
                    capacity.isNotBlank() && (kwh == null || !kwh.isFinite() || kwh <= 0) -> "capacity"
                    power.isNotBlank() && (kw == null || !kw.isFinite() || kw <= 0) -> "power"
                    else -> null
                }
                if (invalid == null) {
                    onSave(vehicle?.copy(name = label, model = modelName, capacityKwh = kwh, maxDcChargeKw = kw)
                        ?: VehicleProfile(VehicleProfiles.newId(), label, modelName, kwh, null, maxDcChargeKw = kw))
                }
            }) { Text(labels.t("Desa", "Guardar", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(labels.t("Cancel·la", "Cancelar", "Cancel")) }
        }
    )
}

@Composable
private fun ProfileField(
    value: String, onChange: (String) -> Unit, label: String, error: String?, keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        isError = error != null, supportingText = error?.let { message -> { Text(message) } }
    )
}
