package cat.ptrack;

import android.content.Context;
import java.util.Locale;

/** All UI and report copy uses an explicit app locale; Catalan is the initial choice. */
public final class Labels {
    private final String lang;
    public final Locale locale;
    public Labels(Context c) {
        lang=c.getSharedPreferences("settings",0).getString("lang","ca"); locale=Locale.forLanguageTag(lang);
    }
    public String t(String ca, String es, String en) { return switch(lang) { case "es" -> es; case "en" -> en; default -> ca; }; }
    public String n(double value, int places) { return Double.isFinite(value) ? String.format(locale,"%."+places+"f",value) : "—"; }
    public String status(String key) {
        return switch(key) {
            case "connecting" -> t("Connectant amb l’OBD…","Conectando al OBD…","Connecting to OBD…");
            case "connected" -> t("OBD connectat · registrant","OBD conectado · grabando","OBD connected · recording");
            case "demo" -> t("SIMULACIÓ · dades fictícies","SIMULACIÓN · datos ficticios","SIMULATION · synthetic data");
            case "missing" -> t("No s’ha trobat l’OBD · reintentant","No se encuentra el OBD · reintentando","OBD not found · retrying");
            case "vehicle" -> t("OBD connectat · vehicle sense dades","OBD conectado · vehículo sin datos","OBD connected · no vehicle data");
            case "reportError" -> t("Viatge desat · error en generar el PDF","Viaje guardado · error al generar el PDF","Trip saved · PDF generation failed");
            case "permission" -> t("Cal permís de Bluetooth","Se necesita permiso de Bluetooth","Bluetooth permission required");
            case "unconfigured" -> t("Cap OBD configurat · selecciona l’ELM327","Ningún OBD configurado · selecciona el ELM327","No OBD configured · select the ELM327");
            default -> t("OBD desconnectat","OBD desconectado","OBD disconnected");
        };
    }
    public String signal(String key) {
        return switch(key) {
            case "current" -> t("Corrent","Corriente","Current");
            case "voltage" -> t("Tensió de bateria","Voltaje de batería","Battery voltage");
            case "soc" -> t("Estat de càrrega","Estado de carga","State of charge");
            case "speed" -> t("Velocitat","Velocidad","Speed");
            case "soh" -> t("Salut de bateria","Salud de batería","Battery health");
            case "energy" -> t("Energia disponible","Energía disponible","Available energy");
            case "temp_avg" -> t("Temperatura de bateria","Temperatura de batería","Battery temperature");
            case "temp_min" -> t("Temperatura mínima","Temperatura mínima","Minimum temperature");
            case "temp_max" -> t("Temperatura màxima","Temperatura máxima","Maximum temperature");
            case "aux_voltage" -> t("Bateria auxiliar","Batería auxiliar","Auxiliary battery");
            case "ambient" -> t("Temperatura exterior","Temperatura exterior","Outside temperature");
            case "soc_raw" -> t("Càrrega sense filtrar","Carga sin filtrar","Unfiltered charge");
            case "temp_delta" -> t("Diferència de temperatura","Diferencia de temperatura","Temperature spread");
            case "isolation_pos" -> t("Aïllament positiu","Aislamiento positivo","Positive insulation");
            case "isolation_neg" -> t("Aïllament negatiu","Aislamiento negativo","Negative insulation");
            case "max_charge_power" -> t("Límit de càrrega","Límite de carga","Charge limit");
            case "max_discharge_power" -> t("Límit de descàrrega","Límite de descarga","Discharge limit");
            case "cell_min" -> t("Tensió mínima de cel·la","Voltaje mínimo de celda","Minimum cell voltage");
            case "cell_max" -> t("Tensió màxima de cel·la","Voltaje máximo de celda","Maximum cell voltage");
            case "lowest_cell" -> t("Cel·la amb menys tensió","Celda con menor voltaje","Lowest-voltage cell");
            case "cells" -> t("Tensions de cel·les (V)","Voltajes de celdas (V)","Cell voltages (V)");
            case "probes" -> t("Sondes de temperatura (°C)","Sondas de temperatura (°C)","Temperature probes (°C)");
            case "cell_soh" -> t("Salut de cel·les (%)","Salud de celdas (%)","Cell health (%)");
            case "motor_rpm" -> t("Règim del motor","Régimen del motor","Motor RPM");
            case "drive_mode" -> t("Mode de conducció","Modo de conducción","Drive mode");
            case "gear" -> t("Marxa seleccionada","Marcha seleccionada","Selected gear");
            case "odometer" -> t("Comptaquilòmetres","Cuentakilómetros","Odometer");
            case "charging" -> t("Estat de càrrega (codi)","Estado de carga (código)","Charging state (code)");
            case "grid_voltage" -> t("Tensió de xarxa","Voltaje de red","Grid voltage");
            case "motor_temp" -> t("Temperatura del motor","Temperatura del motor","Motor temperature");
            case "inverter_temp" -> t("Temperatura de l’inversor","Temperatura del inversor","Inverter temperature");
            case "dcdc_temp" -> t("Temperatura DC/DC","Temperatura DC/DC","DC/DC temperature");
            case "coolant_temp" -> t("Temperatura del refrigerant","Temperatura del refrigerante","Coolant temperature");
            case "aux_battery_temp" -> t("Temperatura de bateria auxiliar","Temperatura de batería auxiliar","Auxiliary battery temperature");
            case "lifetime_charged_kwh" -> t("Energia acumulada entrant","Energía acumulada entrante","Lifetime energy in");
            case "lifetime_discharged_kwh" -> t("Energia acumulada sortint","Energía acumulada saliente","Lifetime energy out");
            case "battery_made" -> t("Fabricació de bateria (hex)","Fabricación de batería (hex)","Battery manufacture (hex)");
            case "bms_software" -> t("Programari BMS (hex)","Software BMS (hex)","BMS software (hex)");
            case "bms_hardware" -> t("Maquinari BMS (hex)","Hardware BMS (hex)","BMS hardware (hex)");
            case "contactor_closes" -> t("Tancaments de contactors (hex)","Cierres de contactores (hex)","Contactor closures (hex)");
            case "time_above_55c" -> t("Temps per sobre de 55 °C (hex)","Tiempo por encima de 55 °C (hex)","Time above 55 °C (hex)");
            default -> key.replace('_',' ');
        };
    }
}
