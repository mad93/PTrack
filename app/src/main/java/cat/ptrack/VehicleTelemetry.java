package cat.ptrack;

import java.util.concurrent.CopyOnWriteArraySet;

/** One in-process publication of samples already persisted by RecordingService.
 * No Android, Bluetooth or database dependencies. All times for freshness are monotonic.
 */
public final class VehicleTelemetry {
    public static final VehicleTelemetry LIVE = new VehicleTelemetry();
    public static final long FRESH_MS = 10_000;
    public record Totals(long tripId, boolean active, long samples, double km,
                         double consumed, double recovered, double average) {}
    public record Snapshot(String state, Sample sample, long receivedAt, boolean simulation,
                           Totals totals, Double cable, long cableAt) {
        public Sample current(long now) {
            return (state.equals("connected") || state.equals("demo")) && sample != null
                    && now >= receivedAt && now - receivedAt <= FRESH_MS ? sample : null;
        }
        public double instantaneous(long now) {
            Sample s = current(now);
            return s != null && s.speed() >= 5 ? s.power() * 100 / s.speed() : Double.NaN;
        }
        public String connection(long now) {
            if (state.equals("connecting")) return "Connectant";
            if (current(now) != null) return "Connectat";
            if (state.equals("connected") || state.equals("demo") || state.equals("vehicle")) return "Dades no disponibles";
            if (state.equals("permission")) return "Desconnectat · cal permís Bluetooth";
            return "Desconnectat";
        }
        public String flow(long now) {
            Sample s = current(now);
            if (s == null) return "Sense dades actuals";
            if (s.power() > 0.05) return "Consum de bateria";
            if (s.power() >= -0.05) return "Potència gairebé nul·la";
            boolean recentCable = cable != null && cable == 1 && now >= cableAt && now-cableAt <= FRESH_MS;
            if (s.speed() <= 1 && recentCable) return "Càrrega probable · cable detectat";
            return s.speed() >= 5 ? "Regeneració · energia entrant" : "Energia entrant · origen no confirmat";
        }
    }
    private final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
    private TripStats stats = new TripStats();
    private long tripId = -1;
    private boolean active;
    private Snapshot snapshot = new Snapshot("disconnected", null, 0, false, null, null, 0);

    public synchronized Snapshot snapshot() { return snapshot; }
    public void subscribe(Runnable listener) { listeners.add(listener); }
    public void unsubscribe(Runnable listener) { listeners.remove(listener); }
    private void changed() {
        // A presentation failure must never terminate recording.
        for (Runnable listener : listeners) try { listener.run(); } catch (RuntimeException ignored) {}
    }
    public void status(String state) {
        synchronized (this) {
            Sample sample = state.equals("connected") || state.equals("demo") ? snapshot.sample() : null;
            if (state.equals(snapshot.state()) && sample == snapshot.sample()) return;
            snapshot = new Snapshot(state, sample, snapshot.receivedAt(), snapshot.simulation(),
                    snapshot.totals(), snapshot.cable(), snapshot.cableAt());
        }
        changed();
    }
    public void accept(long id, Sample sample, boolean simulation, long elapsed) {
        if (!valid(sample)) { status("vehicle"); return; }
        synchronized (this) {
            if (id != tripId || !active) { tripId = id; stats = new TripStats(); active = true; }
            // Ignore duplicate/out-of-order publication, including its freshness timestamp.
            if (stats.count > 0 && sample.time() <= stats.end) return;
            stats.add(sample); // The existing report/integration algorithm, once per saved sample.
            snapshot = new Snapshot(simulation ? "demo" : "connected", sample, elapsed, simulation,
                    totals(), snapshot.cable(), snapshot.cableAt());
        }
        changed();
    }
    public void cable(Double value, long elapsed) {
        synchronized (this) {
            snapshot = new Snapshot(snapshot.state(), snapshot.sample(), snapshot.receivedAt(),
                    snapshot.simulation(), snapshot.totals(), value != null && Double.isFinite(value) ? value : null, elapsed);
        }
        changed();
    }
    public void finish(long id) {
        synchronized (this) {
            if (id != tripId || !active) return;
            active = false;
            snapshot = new Snapshot(snapshot.state(), null, snapshot.receivedAt(), snapshot.simulation(), totals(), null, 0);
        }
        changed();
    }
    private Totals totals() { return new Totals(tripId, active, stats.count, stats.km, stats.consumed, stats.recovered, stats.consumption()); }
    private static boolean valid(Sample s) {
        return s != null && Double.isFinite(s.soc()) && s.soc() >= 0 && s.soc() <= 101
                && Double.isFinite(s.voltage()) && s.voltage() > 0 && Double.isFinite(s.current())
                && Double.isFinite(s.power()) && Double.isFinite(s.speed()) && s.speed() >= 0;
    }
}
