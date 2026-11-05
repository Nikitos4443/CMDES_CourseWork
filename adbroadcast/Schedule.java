package adbroadcast;

public final class Schedule {
    public static final double DAY = 24 * 60;
    public static final double BROADCAST = 16 * 60;

    private final int pauses;
    private final double pauseLength;
    private final double segment;

    public Schedule(int pauses, double share) {
        this.pauses = pauses;
        this.pauseLength = BROADCAST * share / pauses;
        this.segment = BROADCAST / pauses;
    }

    public double nextPauseStart(double t) {
        double day = Math.floor(t / DAY);
        double u = t - day * DAY;
        for (int k = 0; k < pauses; k++) {
            double start = (k + 1) * segment - pauseLength;
            if (start >= u) return day * DAY + start;
        }
        return (day + 1) * DAY + segment - pauseLength;
    }

    public int pauses() { return pauses; }
    public double pauseLength() { return pauseLength; }
}
