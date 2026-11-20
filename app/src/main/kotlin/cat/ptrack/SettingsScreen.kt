package cat.ptrack

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

internal data class SettingsState(
    val vehicle: String,
    val adapter: String?,
    val hasRememberedAdapters: Boolean,
    val automaticRecording: Boolean,
    val companionSupported: Boolean,
    val carTrigger: String?,
    val backupDate: String,
    val backupConfigured: Boolean,
    val linkedBackups: Int,
    val chargingText: String = "",
    val chargingBusy: Boolean = false,
    val routingMapInstalled: Boolean = false,
    val routingMapDate: String? = null,
    val routingMapImporting: Boolean = false,
    val routingMapDirectUpdate: Boolean = false
)

internal enum class SettingsAction {
    CHOOSE_VEHICLE, ADD_VEHICLE, EDIT_VEHICLE, ADD_ADAPTER, LINK_ADAPTER, UNLINK_ADAPTER,
    SIMULATE, ASSOCIATE_OBD, ASSOCIATE_CAR, REMOVE_CAR, WAIT_OBD, EXPORT_BACKUP, IMPORT_BACKUP,
    SET_BACKUP, LINK_BACKUP, SYNC_BACKUP, UNLINK_BACKUP, UPDATE_CHARGING, IMPORT_ROUTING_MAP, UPDATE_ROUTING_MAP, LICENSES
}

@Composable
internal fun SettingsScreen(
    state: SettingsState,
    buildInfo: String,
    labels: Labels,
    onAction: (SettingsAction) -> Unit,
    onAutomatic: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
    routingMapInUse: Boolean = false
) {
    val wide = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(840)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val twoColumns = wide && maxWidth >= 700.dp
        Column(
            Modifier.widthIn(max = if (twoColumns) 1060.dp else 620.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(Destination.SETTINGS.label(labels), style = MaterialTheme.typography.headlineSmall)
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VehicleSettings(state, labels, onAction)
                        BackupSettings(state, labels, onAction)
                        AppSettings(labels, onAction, onLanguage)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ObdSettings(state, labels, onAction, onAutomatic)
                        ChargingSettings(state, labels, onAction)
                        RoutingMapSettings(state, labels, routingMapInUse, onAction)
                        BuildInfoSettings(buildInfo, labels)
                    }
                }
            } else {
                VehicleSettings(state, labels, onAction)
                ObdSettings(state, labels, onAction, onAutomatic)
                BackupSettings(state, labels, onAction)
                ChargingSettings(state, labels, onAction)
                RoutingMapSettings(state, labels, routingMapInUse, onAction)
                AppSettings(labels, onAction, onLanguage)
                BuildInfoSettings(buildInfo, labels)
            }
        }
    }
}

@Composable
private fun VehicleSettings(state: SettingsState, labels: Labels, onAction: (SettingsAction) -> Unit) {
    SettingsPanel(labels.t("Perfils de vehicle", "Perfiles de vehículo", "Vehicle profiles")) {
        SettingsNote(labels.t("Vehicle actiu", "Vehículo activo", "Active vehicle"))
        Text(state.vehicle, style = MaterialTheme.typography.titleLarge)
        SettingsButton(labels.t("Tria el vehicle", "Elige el vehículo", "Choose vehicle")) {
            onAction(SettingsAction.CHOOSE_VEHICLE)
        }
        SettingsButton(labels.t("Afegeix un perfil", "Añade un perfil", "Add a profile")) {
            onAction(SettingsAction.ADD_VEHICLE)
        }
        SettingsButton(labels.t("Edita un perfil", "Edita un perfil", "Edit a profile")) {
            onAction(SettingsAction.EDIT_VEHICLE)
        }
    }
}

@Composable
private fun ObdSettings(
    state: SettingsState, labels: Labels, onAction: (SettingsAction) -> Unit, onAutomatic: (Boolean) -> Unit
) {
    SettingsPanel(labels.t("Connexió OBD", "Conexión OBD", "OBD connection")) {
        SettingsNote(labels.t("Adaptador OBD actiu", "Adaptador OBD activo", "Active OBD adapter"))
        Text(state.adapter ?: labels.t("Cap adaptador seleccionat", "Ningún adaptador seleccionado", "No adapter selected"))
        SettingsButton(labels.t("Afegeix un OBD aparellat", "Añadir un OBD emparejado", "Add a paired OBD adapter")) {
            onAction(SettingsAction.ADD_ADAPTER)
        }
        if (state.hasRememberedAdapters) {
            SettingsButton(labels.t("Vincula un OBD recordat", "Vincula un OBD recordado", "Link a saved OBD adapter")) {
                onAction(SettingsAction.LINK_ADAPTER)
            }
        }
        if (state.adapter != null) {
            SettingsButton(labels.t("Desvincula l’OBD d’aquest perfil", "Desvincula el OBD de este perfil", "Unlink OBD from this profile")) {
                onAction(SettingsAction.UNLINK_ADAPTER)
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
            Row(
                Modifier.fillMaxWidth().toggleable(state.automaticRecording, role = Role.Switch, onValueChange = onAutomatic)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(labels.t("Registre automàtic en connectar", "Registro automático al conectar",
                    "Record automatically on connection"), Modifier.weight(1f))
                Switch(checked = state.automaticRecording, onCheckedChange = null)
            }
        }
        if (state.companionSupported) {
            SettingsButton(labels.t("Associa l’OBD per a l’inici automàtic", "Asociar OBD para inicio automático",
                "Associate OBD for automatic start")) { onAction(SettingsAction.ASSOCIATE_OBD) }
            SettingsButton(labels.t("Associa el Bluetooth del cotxe", "Asociar el Bluetooth del coche",
                "Associate the car’s Bluetooth")) { onAction(SettingsAction.ASSOCIATE_CAR) }
            state.carTrigger?.let { name ->
                SettingsNote(labels.t("Disparador del cotxe · $name", "Disparador del coche · $name", "Car trigger · $name"))
                SettingsButton(labels.t("Treu el disparador del cotxe", "Quitar el disparador del coche", "Remove car trigger")) {
                    onAction(SettingsAction.REMOVE_CAR)
                }
            }
        }
        SettingsButton(labels.t("Espera l’OBD durant 30 minuts", "Esperar al OBD durante 30 minutos",
            "Wait for OBD for 30 minutes")) { onAction(SettingsAction.WAIT_OBD) }
        SettingsNote(labels.t(
            "Amb el registre automàtic activat, una connexió Bluetooth de l’OBD o del cotxe pot iniciar el registre. Si l’ELM327 espera que el mòbil s’hi connecti i el cotxe no dona cap senyal, activa l’espera abans de sortir. L’espera manté una notificació i fa reintents amb la pantalla apagada.",
            "Con el registro automático activado, una conexión Bluetooth del OBD o del coche puede iniciar el registro. Si el ELM327 espera a que se conecte el móvil y el coche no da ninguna señal, activa la espera antes de salir. La espera mantiene una notificación y reintenta con la pantalla apagada.",
            "With automatic recording enabled, an OBD or car Bluetooth connection can start recording. If the ELM327 waits for the phone to connect and the car supplies no signal, turn on wait mode before leaving. It shows a notification and retries with the screen off."))
        SettingsButton(labels.t("Prova amb dades simulades", "Probar con datos simulados", "Try simulated data")) {
            onAction(SettingsAction.SIMULATE)
        }
    }
}

@Composable
private fun BackupSettings(state: SettingsState, labels: Labels, onAction: (SettingsAction) -> Unit) {
    SettingsPanel(labels.t("Còpies de seguretat", "Copias de seguridad", "Backups")) {
        Text(state.backupDate)
        val sync = if (state.backupConfigured) labels.t("Còpia automàtica activada", "Copia automática activada",
            "Automatic backup enabled")
        else labels.t("Còpia automàtica pendent de configurar", "Copia automática sin configurar", "Automatic backup not set up")
        val count = state.linkedBackups
        SettingsNote("$sync · " + labels.t("Fitxers vinculats: $count", "Archivos vinculados: $count", "Linked files: $count"))
        SettingsNote(labels.t(
            "Tria Google Drive al selector per desar o recuperar la còpia. Importar afegeix els viatges i les lectures de la còpia i conserva els que ja tens.",
            "Elige Google Drive en el selector para guardar o recuperar la copia. Importar añade los viajes y las lecturas de la copia y conserva los que ya tienes.",
            "Choose Google Drive in the picker to save or restore a backup. Import adds trips and readings from the backup and keeps your existing ones."))
        SettingsButton(labels.t("Exporta una còpia", "Exportar una copia", "Export backup")) { onAction(SettingsAction.EXPORT_BACKUP) }
        SettingsButton(labels.t("Importa una còpia", "Importar una copia", "Import backup")) { onAction(SettingsAction.IMPORT_BACKUP) }
        SettingsNote(labels.t("Crea un fitxer de còpia diferent per a cada dispositiu. Per sincronitzar-los, vincula el fitxer de l’altre dispositiu a cadascun.",
            "Crea un archivo de copia diferente para cada dispositivo. Para sincronizarlos, vincula el archivo del otro dispositivo en cada uno.",
            "Create a different backup file for each device. To sync them, link the other device’s file on each one."))
        SettingsButton(labels.t("Configura la còpia automàtica", "Configurar la copia automática", "Set up automatic backup")) {
            onAction(SettingsAction.SET_BACKUP)
        }
        SettingsButton(labels.t("Vincula la còpia d’un altre dispositiu", "Vincular la copia de otro dispositivo",
            "Link another device’s backup")) { onAction(SettingsAction.LINK_BACKUP) }
        if (state.backupConfigured || count > 0) {
            SettingsButton(labels.t("Sincronitza ara", "Sincronizar ahora", "Sync now")) { onAction(SettingsAction.SYNC_BACKUP) }
            SettingsButton(labels.t("Desvincula la sincronització", "Desvincular la sincronización", "Unlink sync")) {
                onAction(SettingsAction.UNLINK_BACKUP)
            }
        }
    }
}

@Composable
private fun ChargingSettings(state: SettingsState, labels: Labels, onAction: (SettingsAction) -> Unit) {
    SettingsPanel(labels.t("Punts de recàrrega", "Puntos de recarga", "Charging points")) {
        SettingsNote(labels.t("Font: NAP de la DGT. Actualització aproximadament setmanal quan hi ha connexió.",
            "Fuente: NAP de la DGT. Actualización aproximadamente semanal cuando hay conexión.",
            "Source: DGT National Access Point. Updates roughly weekly when connected."))
        if (state.chargingText.isNotEmpty()) Text(state.chargingText)
        SettingsButton(labels.t("Actualitza ara", "Actualizar ahora", "Update now"), enabled = !state.chargingBusy) {
            onAction(SettingsAction.UPDATE_CHARGING)
        }
    }
}

@Composable
private fun RoutingMapSettings(
    state: SettingsState,
    labels: Labels,
    inUse: Boolean,
    onAction: (SettingsAction) -> Unit
) {
    SettingsPanel(labels.t("Mapa de rutes", "Mapa de rutas", "Routing map")) {
        val direct = state.routingMapDirectUpdate
        SettingsNote(if (direct) labels.t("Ceuta · descàrrega i generació del mapa al telèfon.",
            "Ceuta · descarga y generación del mapa en el teléfono.", "Ceuta · download and build the map on this phone.")
        else labels.t("Paquet ZIP de Valhalla per calcular rutes sense connexió.",
            "Paquete ZIP de Valhalla para calcular rutas sin conexión.", "Valhalla ZIP package for offline routing."))
        val date = state.routingMapDate
        SettingsNote(when {
            !state.routingMapInstalled -> labels.t("Cap mapa actiu.", "Ningún mapa activo.", "No active map.")
            date == null -> labels.t("Data d’activació desconeguda.", "Fecha de activación desconocida.", "Activation date unknown.")
            else -> labels.t("Darrera activació · $date", "Última activación · $date", "Last activated · $date")
        })
        val action = when {
            direct && state.routingMapImporting -> labels.t("Actualitzant el mapa…", "Actualizando el mapa…", "Updating the map…")
            direct -> labels.t("Actualitza", "Actualiza", "Update")
            state.routingMapImporting -> labels.t("Important el mapa…", "Importando el mapa…", "Importing map…")
            state.routingMapInstalled -> labels.t("Importa un mapa nou", "Importa un mapa nuevo", "Import a new map")
            else -> labels.t("Importa un mapa de rutes", "Importa un mapa de rutas", "Import routing map")
        }
        SettingsButton(action, enabled = !state.routingMapImporting && !inUse) {
            onAction(if (direct) SettingsAction.UPDATE_ROUTING_MAP else SettingsAction.IMPORT_ROUTING_MAP)
        }
    }
}

@Composable
private fun AppSettings(labels: Labels, onAction: (SettingsAction) -> Unit, onLanguage: (String) -> Unit) {
    SettingsPanel(labels.t("Aplicació i informes", "Aplicación e informes", "App and reports")) {
        Text(labels.t("Idioma", "Idioma", "Language"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ca" to "Català", "es" to "Español", "en" to "English").forEach { (code, name) ->
                FilterChip(selected = labels.locale.language == code, onClick = { onLanguage(code) }, label = { Text(name) })
            }
        }
        SettingsNote(labels.t(
            "Android Auto · Integració experimental. PTrack Prova cotxe s’ha provat al Peugeot. Queda pendent la prova de PTrack habitual al vehicle.",
            "Android Auto · Integración experimental. PTrack Prueba coche se ha probado en el Peugeot. Queda pendiente probar PTrack habitual en el vehículo.",
            "Android Auto · Experimental integration. PTrack Car Test was tested in the Peugeot. Testing the regular PTrack app in the vehicle is pending."))
        SettingsButton(labels.t("Llicències i atribució", "Licencias y atribución", "Licenses and attribution")) {
            onAction(SettingsAction.LICENSES)
        }
    }
}

@Composable
private fun SettingsPanel(title: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun SettingsButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) { Text(label) }
}

@Composable
private fun SettingsNote(value: String) {
    Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BuildInfoSettings(buildInfo: String, labels: Labels) {
    SettingsPanel(labels.t("Versió i compilació", "Versión y compilación", "Version and build")) {
        SelectionContainer {
            Text(
                buildInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
