package cat.ptrack

import android.content.Context
import java.util.Locale

/** All UI/report copy uses the app locale; Catalan is the initial choice. */
class Labels(context: Context) {
    private val lang = context.getSharedPreferences("settings", 0).getString("lang", "ca") ?: "ca"
    val locale: Locale = Locale.forLanguageTag(lang)
    fun t(ca: String, es: String, en: String): String = when (lang) { "es" -> es; "en" -> en; else -> ca }
    fun n(value: Double, places: Int): String = if (value.isFinite()) String.format(locale, "%.${places}f", value) else "—"
    fun status(key: String): String = when (key) {
        "connecting" -> t("Connectant amb l’OBD…","Conectando al OBD…","Connecting to OBD…")
        "connected" -> t("OBD connectat · registrant","OBD conectado · grabando","OBD connected · recording")
        "demo" -> t("SIMULACIÓ · dades fictícies","SIMULACIÓN · datos ficticios","SIMULATION · synthetic data")
        "missing" -> t("No s’ha trobat l’OBD · reintentant","No se encuentra el OBD · reintentando","OBD not found · retrying")
        "vehicle" -> t("OBD connectat · vehicle sense dades","OBD conectado · vehículo sin datos","OBD connected · no vehicle data")
        "reportError" -> t("Viatge desat · error en generar el PDF","Viaje guardado · error al generar el PDF","Trip saved · PDF generation failed")
        "permission" -> t("Cal permís de Bluetooth","Se necesita permiso de Bluetooth","Bluetooth permission required")
        "unconfigured" -> t("Cap OBD configurat · selecciona l’ELM327","Ningún OBD configurado · selecciona el ELM327","No OBD configured · select the ELM327")
        else -> t("OBD desconnectat", "OBD desconectado", "OBD disconnected")
    }

    fun signal(key: String): String = when (key) {
        "current" -> t("Corrent","Corriente","Current")
        "voltage" -> t("Tensió de bateria","Voltaje de batería","Battery voltage")
        "soc" -> t("Estat de càrrega","Estado de carga","State of charge")
        "speed" -> t("Velocitat","Velocidad","Speed")
        "soh" -> t("Salut de bateria","Salud de batería","Battery health")
        "energy" -> t("Energia disponible","Energía disponible","Available energy")
        "temp_avg" -> t("Temperatura de bateria","Temperatura de batería","Battery temperature")
        "temp_min" -> t("Temperatura mínima","Temperatura mínima","Minimum temperature")
        "temp_max" -> t("Temperatura màxima","Temperatura máxima","Maximum temperature")
        "aux_voltage" -> t("Bateria auxiliar","Batería auxiliar","Auxiliary battery")
        "ambient" -> t("Temperatura exterior","Temperatura exterior","Outside temperature")
        "soc_raw" -> t("Càrrega sense filtrar","Carga sin filtrar","Unfiltered charge")
        "temp_delta" -> t("Diferència de temperatura","Diferencia de temperatura","Temperature spread")
        "isolation_pos" -> t("Aïllament positiu","Aislamiento positivo","Positive insulation")
        "isolation_neg" -> t("Aïllament negatiu","Aislamiento negativo","Negative insulation")
        "max_charge_power" -> t("Límit de càrrega","Límite de carga","Charge limit")
        "max_discharge_power" -> t("Límit de descàrrega","Límite de descarga","Discharge limit")
        "cell_min" -> t("Tensió mínima de cel·la","Voltaje mínimo de celda","Minimum cell voltage")
        "cell_max" -> t("Tensió màxima de cel·la","Voltaje máximo de celda","Maximum cell voltage")
        "lowest_cell" -> t("Cel·la amb menys tensió","Celda con menor voltaje","Lowest-voltage cell")
        "cells" -> t("Tensions de cel·les (V)","Voltajes de celdas (V)","Cell voltages (V)")
        "probes" -> t("Sondes de temperatura (°C)","Sondas de temperatura (°C)","Temperature probes (°C)")
        "cell_soh" -> t("Salut de cel·les (%)","Salud de celdas (%)","Cell health (%)")
        "motor_rpm" -> t("Règim del motor","Régimen del motor","Motor RPM")
        "drive_mode" -> t("Mode de conducció","Modo de conducción","Drive mode")
        "gear" -> t("Marxa seleccionada","Marcha seleccionada","Selected gear")
        "odometer" -> t("Comptaquilòmetres","Cuentakilómetros","Odometer")
        "charging" -> t("Estat de càrrega (codi)","Estado de carga (código)","Charging state (code)")
        "grid_voltage" -> t("Tensió de xarxa","Voltaje de red","Grid voltage")
        "motor_temp" -> t("Temperatura del motor","Temperatura del motor","Motor temperature")
        "inverter_temp" -> t("Temperatura de l’inversor","Temperatura del inversor","Inverter temperature")
        "dcdc_temp" -> t("Temperatura DC/DC","Temperatura DC/DC","DC/DC temperature")
        "coolant_temp" -> t("Temperatura del refrigerant","Temperatura del refrigerante","Coolant temperature")
        "aux_battery_temp" -> t("Temperatura de bateria auxiliar","Temperatura de batería auxiliar","Auxiliary battery temperature")
        "lifetime_charged_kwh" -> t("Energia acumulada entrant","Energía acumulada entrante","Lifetime energy in")
        "lifetime_discharged_kwh" -> t("Energia acumulada sortint","Energía acumulada saliente","Lifetime energy out")
        "battery_made" -> t("Fabricació de bateria (hex)","Fabricación de batería (hex)","Battery manufacture (hex)")
        "bms_software" -> t("Programari BMS (hex)","Software BMS (hex)","BMS software (hex)")
        "bms_hardware" -> t("Maquinari BMS (hex)","Hardware BMS (hex)","BMS hardware (hex)")
        "contactor_closes" -> t("Tancaments de contactors (hex)","Cierres de contactores (hex)","Contactor closures (hex)")
        "time_above_55c" -> t("Temps per sobre de 55 °C (hex)","Tiempo por encima de 55 °C (hex)","Time above 55 °C (hex)")
        else -> key.replace('_', ' ')
    }
}
