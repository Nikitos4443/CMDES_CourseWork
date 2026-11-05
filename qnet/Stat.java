package qnet;

public final class Stat {
    private long count;
    private double sum, sumSq;
    private double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
    private final double histFrom, histWidth;
    private final long[] hist;

    public Stat() {
        this(0, 1, 0);
    }

    public Stat(double from, double width, int bins) {
        this.histFrom = from;
        this.histWidth = width;
        this.hist = new long[bins];
    }

    public void add(double x) {
        count++;
        sum += x;
        sumSq += x * x;
        min = Math.min(min, x);
        max = Math.max(max, x);
        if (hist.length > 0) {
            int i = (int) Math.floor((x - histFrom) / histWidth);
            hist[Math.max(0, Math.min(hist.length - 1, i))]++;
        }
    }

    public long count() { return count; }
    public double sum() { return sum; }
    public double mean() { return count > 0 ? sum / count : 0.0; }
    public double std() {
        if (count < 2) return 0.0;
        double m = mean();
        return Math.sqrt(Math.max(0.0, (sumSq - count * m * m) / (count - 1)));
    }
    public double min() { return count > 0 ? min : 0.0; }
    public double max() { return count > 0 ? max : 0.0; }
    public long[] histogram() { return hist.clone(); }
    public double histFrom() { return histFrom; }
    public double histWidth() { return histWidth; }
}
