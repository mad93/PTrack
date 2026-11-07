package cat.ptrack

import java.util.Locale

/** Presentation only; fixed row titles allow Car App Library value refreshes. */
object CarDashboard {
    data class Line(val title: String, val value: String, val detail: String)
    private fun n(value: Double, digits: Int) = if (value.isFinite()) String.format(Locale.forLanguageTag("ca"), "%.${digits}f", value) else "--"
    fun primary(v: VehicleTelemetry.Snapshot, now: Long): List<Line> {
        val s = v.current(now); val t = v.totals
        val mode = if (v.simulation) "SIMULACIÓ · dades fictícies" else "Lectures OBD · bateria"
        return listOf(
            Line("Potència de bateria", n(s?.power() ?: Double.NaN, 1) + " kW · " + v.flow(now), "Velocitat " + n(s?.speed ?: Double.NaN, 0) + " km/h"),
            Line("Consum del trajecte", n(t?.takeIf { it.active }?.average ?: Double.NaN, 1) + " kWh/100 km · mitjà", "Instantani " + n(v.instantaneous(now), 1) + " kWh/100 km"),
            Line("Bateria i connexió", (if (v.simulation) "SIMULACIÓ · " else "") + n(s?.soc ?: Double.NaN, 1) + " % SOC · " + v.connection(now), mode))
    }
    fun details(v: VehicleTelemetry.Snapshot, now: Long): List<Line> {
        val s = v.current(now); val t = v.totals
        return listOf(
            Line("Consum instantani i velocitat", n(v.instantaneous(now), 1) + " kWh/100 km", n(s?.speed ?: Double.NaN, 0) + " km/h"),
            Line("Energia del trajecte", (if (t != null && !t.active) "Darrer: " else "") + "Consumida " + n(t?.consumed ?: Double.NaN, 3) + " kWh", "Entrant " + n(t?.recovered ?: Double.NaN, 3) + " kWh · regen./càrrega"),
            Line("Tensió i intensitat", n(s?.voltage ?: Double.NaN, 1) + " V · " + n(s?.current ?: Double.NaN, 1) + " A", "Signe positiu: descàrrega segons el perfil e-CMP"),
            Line("Estat i distància", v.connection(now) + " · " + n(t?.km ?: Double.NaN, 2) + " km", if (v.simulation) "SIMULACIÓ · no s'escriu al registre real" else "Sense dades recents: lectures instantànies --"))
    }
}
