package cat.ptrack

import java.util.Locale

/** Read-only presentation shared by car details and phone history. */
object TripSummaryText {
    data class Line(val title: String, val value: String)
    data class Section(val title: String, val first: String, val second: String)
    private fun n(v: Double, decimals: Int, unit: String) = if (v.isFinite()) String.format(Locale.forLanguageTag("ca"), "%.${decimals}f", v) + " " + unit else "No disponible"
    fun lines(s: TripSummary, allowRange: Boolean = true): List<Line> = listOf(
        Line("Distància", n(s.km, 2, "km")),
        Line("Durada", if (s.count > 0) "${s.durationMs / 60000} min ${s.durationMs / 1000 % 60} s" else "No disponible"),
        Line("Velocitat mitjana", n(s.averageSpeed, 1, "km/h")),
        Line("Consum mitjà net", n(s.averageConsumption, 1, "kWh/100 km")),
        Line("Bateria consumida", n(s.socUsed(), 1, "punts percentuals")),
        Line("Energia consumida", n(s.consumed, 3, "kWh")),
        Line("Energia entrant · regen./càrrega", n(s.incoming, 3, "kWh")),
        Line("Energia neta · consumida − entrant", n(s.net(), 3, "kWh")),
        Line("Potència màxima de consum", n(s.maxDischargeKw, 1, "kW")),
        Line("Potència màxima entrant", n(s.maxIncomingKw, 1, "kW · regeneració o càrrega")),
        Line("Intensitat màxima de descàrrega", n(s.maxDischargeA, 1, "A")),
        Line("Intensitat màxima entrant", n(s.maxIncomingA, 1, "A · regeneració o càrrega")),
        Line("Pic de regeneració en moviment · inferit", n(s.maxMovingIncomingKw, 1, "kW")),
        Line("Pic de corrent de regeneració · inferit", n(s.maxMovingIncomingA, 1, "A")),
        Line("Potència mitjana neta", n(s.averagePower, 1, "kW")),
        Line("Velocitat màxima", n(s.maxSpeed, 0, "km/h")),
        Line("SOC inicial / final", n(s.startSoc, 1, "%") + " / " + n(s.endSoc, 1, "%")),
        Line("Energia BMS inicial / final", n(s.startEnergy, 2, "kWh") + " / " + n(s.endEnergy, 2, "kWh")),
        Line("Diferència d'energia BMS", n(s.energyUsed(), 2, "kWh")),
        Line("Autonomia calculada · estimació", n(if (allowRange) s.rangeKm else Double.NaN, 0, "km")))
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
