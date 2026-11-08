package cat.ptrack

import java.util.Locale

/** Read-only presentation shared by car details and phone history. */
object TripSummaryText {
    data class Line(val title: String, val value: String)
    data class Section(val title: String, val first: String, val second: String)
    private fun n(v: Double, decimals: Int, unit: String) = if (v.isFinite()) String.format(Locale.forLanguageTag("ca"), "%.${decimals}f", v) + " " + unit else "No disponible"
    fun lines(s: TripSummary, allowRange: Boolean = true, labels: Labels? = null): List<Line> {
        fun t(ca: String, es: String, en: String) = labels?.t(ca, es, en) ?: ca
        val unavailable = t("No disponible", "No disponible", "Unavailable")
        fun number(value: Double, decimals: Int, unit: String): String {
            if (!value.isFinite()) return unavailable
            return labels?.let { "${it.n(value, decimals)} $unit" } ?: n(value, decimals, unit)
        }

        val incomingKw = t("kW · regeneració o càrrega", "kW · regeneración o carga", "kW · regeneration or charging")
        val incomingA = t("A · regeneració o càrrega", "A · regeneración o carga", "A · regeneration or charging")
        return listOf(
            Line(t("Distància", "Distancia", "Distance"), number(s.km, 2, "km")),
            Line(t("Durada", "Duración", "Duration"),
                if (s.count > 0) "${s.durationMs / 60000} min ${s.durationMs / 1000 % 60} s" else unavailable),
            Line(t("Velocitat mitjana", "Velocidad media", "Average speed"), number(s.averageSpeed, 1, "km/h")),
            Line(t("Consum mitjà net", "Consumo medio neto", "Average net consumption"), number(s.averageConsumption, 1, "kWh/100 km")),
            Line(t("Bateria consumida", "Batería consumida", "Battery used"),
                number(s.socUsed(), 1, t("punts percentuals", "puntos porcentuales", "percentage points"))),
            Line(t("Energia consumida", "Energía consumida", "Energy used"), number(s.consumed, 3, "kWh")),
            Line(t("Energia entrant · regen./càrrega", "Energía entrante · regen./carga", "Incoming energy · regen./charging"),
                number(s.incoming, 3, "kWh")),
            Line(t("Energia neta · consumida − entrant", "Energía neta · consumida − entrante", "Net energy · used − incoming"),
                number(s.net(), 3, "kWh")),
            Line(t("Potència màxima de consum", "Potencia máxima de consumo", "Peak discharge power"),
                number(s.maxDischargeKw, 1, "kW")),
            Line(t("Potència màxima entrant", "Potencia máxima entrante", "Peak incoming power"),
                number(s.maxIncomingKw, 1, incomingKw)),
            Line(t("Intensitat màxima de descàrrega", "Intensidad máxima de descarga", "Peak discharge current"),
                number(s.maxDischargeA, 1, "A")),
            Line(t("Intensitat màxima entrant", "Intensidad máxima entrante", "Peak incoming current"),
                number(s.maxIncomingA, 1, incomingA)),
            Line(t("Pic de regeneració en moviment · inferit", "Pico de regeneración en movimiento · inferido", "Peak regeneration while moving · inferred"),
                number(s.maxMovingIncomingKw, 1, "kW")),
            Line(t("Pic de corrent de regeneració · inferit", "Pico de corriente de regeneración · inferido", "Peak regenerative current · inferred"),
                number(s.maxMovingIncomingA, 1, "A")),
            Line(t("Potència mitjana neta", "Potencia media neta", "Average net power"), number(s.averagePower, 1, "kW")),
            Line(t("Velocitat màxima", "Velocidad máxima", "Maximum speed"), number(s.maxSpeed, 0, "km/h")),
            Line(t("SOC inicial / final", "SOC inicial / final", "Start / end SOC"),
                number(s.startSoc, 1, "%") + " / " + number(s.endSoc, 1, "%")),
            Line(t("Energia BMS inicial / final", "Energía BMS inicial / final", "Start / end BMS energy"),
                number(s.startEnergy, 2, "kWh") + " / " + number(s.endEnergy, 2, "kWh")),
            Line(t("Diferència d'energia BMS", "Diferencia de energía BMS", "BMS energy difference"), number(s.energyUsed(), 2, "kWh")),
            Line(t("Autonomia calculada · estimació", "Autonomía calculada · estimación", "Estimated range"),
                number(if (allowRange) s.rangeKm else Double.NaN, 0, "km"))
        )
    }

    fun describe(s: TripSummary) = lines(s).joinToString("") { "${it.title}: ${it.value}\n" }
    fun sections(s: TripSummary, allowRange: Boolean): List<Section> {
        val result = listOf(
            Section("Informació general", n(s.km, 2, "km") + " · ${s.durationMs / 60000} min ${s.durationMs / 1000 % 60} s · " + n(s.averageSpeed, 1, "km/h"),
                n(s.averageConsumption, 1, "kWh/100 km") + " · bateria " + n(s.socUsed(), 1, "pp")),
            Section("Balanç energètic", "Consumida " + n(s.consumed, 3, "kWh") + " · entrant " + n(s.incoming, 3, "kWh"),
                "Net " + n(s.net(), 3, "kWh") + " · " + n(s.averageConsumption, 1, "kWh/100 km")),
            Section("Màxims · consum / regeneració en moviment", "Potència " + n(s.maxDischargeKw, 1, "kW") + " / " + n(s.maxMovingIncomingKw, 1, "kW"),
                "Intensitat " + n(s.maxDischargeA, 1, "A") + " / " + n(s.maxMovingIncomingA, 1, "A") + " · regen. inferida"),
            Section("Bateria inicial / final", "SOC " + n(s.startSoc, 1, "%") + " → " + n(s.endSoc, 1, "%") + " · Δ " + n(s.socUsed(), 1, "pp"),
                n(s.startEnergy, 2, "kWh") + " → " + n(s.endEnergy, 2, "kWh") + " · Δ " + n(s.energyUsed(), 2, "kWh")),
            Section("Autonomia calculada · estimació", n(if (allowRange) s.rangeKm else Double.NaN, 0, "km"),
                "Basada en energia BMS i consum net; entrada inclou regen./càrrega"))
        return if (s.count > 0) result else result.map { Section(it.title, "No disponible", "Sense lectures vàlides del trajecte") }
    }
}
