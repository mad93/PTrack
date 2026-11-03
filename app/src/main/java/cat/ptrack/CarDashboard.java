package cat.ptrack;

import java.util.List;
import java.util.Locale;

/** Presentation only; fixed row titles allow Car App Library value refreshes. */
public final class CarDashboard {
    public record Line(String title, String value, String detail) {}
    private static String n(double value, int digits) {
        return Double.isFinite(value) ? String.format(Locale.forLanguageTag("ca"), "%."+digits+"f", value) : "--";
    }
    public static List<Line> primary(VehicleTelemetry.Snapshot v, long now) {
        Sample s = v.current(now);
        var t = v.totals();
        String mode = v.simulation() ? "SIMULACIÓ · dades fictícies" : "Lectures OBD · bateria";
        return List.of(
                new Line("Potència de bateria", n(s == null ? Double.NaN : s.power(),1)+" kW · "+v.flow(now),
                        "Velocitat "+n(s == null ? Double.NaN : s.speed(),0)+" km/h"),
                new Line("Consum del trajecte", n(t == null || !t.active() ? Double.NaN : t.average(),1)+" kWh/100 km · mitjà",
                        "Instantani "+n(v.instantaneous(now),1)+" kWh/100 km"),
                new Line("Bateria i connexió", (v.simulation()?"SIMULACIÓ · ":"")+n(s == null ? Double.NaN : s.soc(),1)+" % SOC · "+v.connection(now), mode));
    }
    public static List<Line> details(VehicleTelemetry.Snapshot v, long now) {
        Sample s = v.current(now);
        var t = v.totals();
        return List.of(
                new Line("Consum instantani i velocitat", n(v.instantaneous(now),1)+" kWh/100 km", n(s == null ? Double.NaN : s.speed(),0)+" km/h"),
                new Line("Energia del trajecte", (t!=null&&!t.active()?"Darrer: ":"")+"Consumida "+n(t == null ? Double.NaN : t.consumed(),3)+" kWh",
                        "Entrant "+n(t == null ? Double.NaN : t.recovered(),3)+" kWh · regen./càrrega"),
                new Line("Tensió i intensitat", n(s == null ? Double.NaN : s.voltage(),1)+" V · "+n(s == null ? Double.NaN : s.current(),1)+" A", "Signe positiu: descàrrega segons el perfil e-CMP"),
                new Line("Estat i distància", v.connection(now)+" · "+n(t == null ? Double.NaN : t.km(),2)+" km", v.simulation() ? "SIMULACIÓ · no s'escriu al registre real" : "Sense dades recents: lectures instantànies --"));
    }
    private CarDashboard() {}
}
