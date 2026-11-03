package cat.ptrack;

public record Sample(long time, double soc, double voltage, double current, double speed,
                     Double latitude, Double longitude, Double altitude, String raw) {
    public double power() { return voltage * current / 1000.0; }
}
