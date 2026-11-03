package cat.ptrack;

/** Positive battery power is discharge; negative power is energy entering the pack. */
public final class TripStats {
    public long start, end, count;
    public double startSoc=Double.NaN, endSoc=Double.NaN;
    public double km, consumed, recovered, ascent, descent, coveredSeconds;
    public boolean hasAltitude;
    private Sample previous;
    private Double altitudeAnchor;
    private long altitudeTime;

    public void add(Sample s) {
        if (previous != null && s.time() <= previous.time()) return;
        if (previous == null) { start = s.time(); startSoc = s.soc(); }
        end = s.time(); endSoc = s.soc(); count++;
        if (previous != null) {
            double seconds = (s.time() - previous.time()) / 1000.0;
            // Never interpolate across Bluetooth outages, suspension or an absent vehicle.
            if (seconds <= 10) {
                coveredSeconds += seconds;
                km += (previous.speed() + s.speed()) * 0.5 * seconds / 3600;
                double a = previous.power(), b = s.power();
                if (a * b < 0) {
                    double fraction = Math.abs(a) / (Math.abs(a) + Math.abs(b));
                    integrate(a * 0.5, seconds * fraction);
                    integrate(b * 0.5, seconds * (1 - fraction));
                } else integrate((a + b) * 0.5, seconds);
            }
        }
        // A 5 m deadband limits GPS altitude noise; reset after an altitude outage.
        if (s.altitude() != null) {
            hasAltitude = true;
            if (altitudeAnchor == null || s.time() - altitudeTime > 30000) altitudeAnchor = s.altitude();
            double delta = s.altitude() - altitudeAnchor;
            if (Math.abs(delta) >= 5) {
                if (delta > 0) ascent += delta; else descent -= delta;
                altitudeAnchor = s.altitude();
            }
            altitudeTime = s.time();
        }
        previous = s;
    }
    private void integrate(double kw, double seconds) {
        if (kw >= 0) consumed += kw * seconds / 3600; else recovered -= kw * seconds / 3600;
    }
    public double averageSpeed() { return coveredSeconds > 0 ? km * 3600 / coveredSeconds : Double.NaN; }
    public double consumption() { return km >= 0.1 ? (consumed - recovered) * 100 / km : Double.NaN; }
}
