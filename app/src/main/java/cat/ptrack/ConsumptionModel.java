package cat.ptrack;

/** Query boundary for a future planner. No extrapolation to unobserved speeds or terrain. */
public interface ConsumptionModel {
    enum Terrain { UNKNOWN, LEVEL, UPHILL, DOWNHILL }
    enum Confidence { INSUFFICIENT, LOW, MEDIUM, HIGH }
    record Estimate(Double observedKwhPer100Km, Double kwhPer100Km, long validIntervals, double distanceKm,
                    int trips, Confidence confidence) {
        public String status() { return confidence == Confidence.INSUFFICIENT ? "dades insuficients" : "disponible"; }
    }
    Estimate estimate(double speedKmh, Terrain terrain);
}
