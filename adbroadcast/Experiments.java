package adbroadcast;

import qnet.Distribution;
import qnet.FunRand;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.IntFunction;

public class Experiments {
    static final double YEAR = AdModel.DAYS_PER_YEAR * Schedule.DAY;
    static final double RUN_TIME = 5 * YEAR;
    static final int RUNS = 10;
    static final long BASE_SEED = 1000;

    static Path dir;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.ROOT);
        dir = Path.of(args.length > 0 ? args[0] : "results");
        Files.createDirectories(dir);
        String only = args.length > 1 ? args[1] : "all";
        regression();
        if (only.equals("all")) {
            generators();
            deterministicVerification();
            stochasticVerification();
            protocol();
            transientPeriod();
            replications();
            pauseSearch();
            anova();
            demandSensitivity();
            proposals();
        } else {
            for (String stage : only.split(",")) {
                switch (stage) {
                    case "verification" -> stochasticVerification();
                    case "transient" -> transientPeriod();
                    case "proposals" -> proposals();
                    default -> throw new IllegalArgumentException(stage);
                }
            }
        }
    }

    static void regression() {
        for (int n : new int[]{1, 5, 6, 17, 32}) {
            FunRand.setSeed(42);
            AdModel.Result a = AdModel.run(n, 0.10, 2, false);
            FunRand.setSeed(42);
            ExperimentModel.Outcome b = ExperimentModel.run(new ExperimentModel.Config().pauses(n), 2 * YEAR);
            boolean same = a.arrived() == b.arrived() && a.expired() == b.expired() && a.placed() == b.placed()
                    && Math.abs(a.revenue() - b.revenue()) < 1e-6 && Math.abs(a.profitPerYear() - b.profitPerYear()) < 1e-6;
            System.out.printf("regression n=%d: %s (p = %.2f / %.2f)%n", n, same ? "OK" : "FAIL", a.profitPerYear(), b.profitPerYear());
            if (!same) throw new IllegalStateException("ExperimentModel diverged from AdModel");
        }
    }

    static void generators() {
        try (PrintWriter w = csv("generators.csv")) {
            w.println("name,count,mean,meanTheory,std,stdTheory,min,max,chi2,df");
            int count = 1_000_000, bins = 20;
            FunRand.setSeed(7);
            Distribution erl = Distribution.erl(20, 2);
            double[] x = new double[count];
            for (int i = 0; i < count; i++) x[i] = erl.next();
            double[] edges = new double[bins + 1];
            for (int b = 1; b < bins; b++) edges[b] = erlangQuantile((double) b / bins);
            edges[bins] = Double.MAX_VALUE;
            write(w, "erl(20;k=2)", x, 20, 20 / Math.sqrt(2), chi2(x, edges), bins - 1 - 0);

            Distribution uni = Distribution.unif(3, 0.5);
            for (int i = 0; i < count; i++) x[i] = uni.next();
            for (int b = 0; b <= bins; b++) edges[b] = 2.5 + (double) b / bins;
            write(w, "unif(3;0.5)", x, 3, 1 / Math.sqrt(12), chi2(x, edges), bins - 1);
        }
    }

    private static double erlangQuantile(double q) {
        double lo = 0, hi = 500;
        for (int i = 0; i < 80; i++) {
            double m = (lo + hi) / 2, a = m / 10;
            if (1 - Math.exp(-a) * (1 + a) < q) lo = m; else hi = m;
        }
        return (lo + hi) / 2;
    }

    private static double chi2(double[] x, double[] edges) {
        int bins = edges.length - 1;
        long[] h = new long[bins];
        for (double v : x) {
            int lo = 0, hi = bins - 1;
            while (lo < hi) {
                int m = (lo + hi) / 2;
                if (v < edges[m + 1]) hi = m; else lo = m + 1;
            }
            h[lo]++;
        }
        double e = (double) x.length / bins, s = 0;
        for (long c : h) s += (c - e) * (c - e) / e;
        return s;
    }

    private static void write(PrintWriter w, String name, double[] x, double m, double sd, double chi2, int df) {
        double mean = mean(x), std = std(x, mean), min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double v : x) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        w.printf("%s,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.2f,%d%n", name, x.length, mean, m, std, sd, min, max, chi2, df);
    }

    static void deterministicVerification() {
        try (PrintWriter w = csv("verification_det.csv")) {
            w.println("case,n,interval,size,minutes,arrived,nI,nQ,cI,cQ,expired,inSystem,airtime,freePause,revenue,profit");
            detRow(w, "D1", 6, 150, 2, Schedule.DAY);
            detRow(w, "D2", 6, 155, 8, Schedule.DAY);
            detRow(w, "D3", 6, 150, 20, Schedule.DAY);
            detRow(w, "D4", 1, 1440, 3, Schedule.DAY);
        }
    }

    private static void detRow(PrintWriter w, String name, int n, double interval, double size, double minutes) {
        ExperimentModel.Config c = new ExperimentModel.Config().pauses(n)
                .interval(Distribution.constant(interval)).size(Distribution.constant(size));
        ExperimentModel.Outcome o = ExperimentModel.run(c, minutes);
        w.printf("%s,%d,%.0f,%.0f,%.0f,%d,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.2f,%.2f%n", name, n, interval, size, minutes,
                o.arrived(), o.nI(), o.nQ(), o.cI(), o.cQ(), o.expired(), o.inSystem(),
                o.airtime(), o.freePause(), o.revenue(), o.profit());
    }

    static void stochasticVerification() {
        try (PrintWriter w = csv("verification_stoch.csv")) {
            w.println("case,years,n,share,meanInterval,meanSize,limit,arrived,placed,nI,nQ,cI,cQ,expired,inSystem,airtime,pauseTime,freePause,revenue,profitPerYear,T,meanWaiting,meanWaitPlaced,maxWaitPlaced");
            stRow(w, "V1", 6, 0.10, 20, 3, 240);
            stRow(w, "V2", 1, 0.10, 20, 3, 240);
            stRow(w, "V3", 12, 0.10, 20, 3, 240);
            stRow(w, "V4", 6, 0.05, 20, 3, 240);
            stRow(w, "V4a", 6, 0.15, 20, 3, 240);
            stRow(w, "V5", 6, 0.10, 10, 3, 240);
            stRow(w, "V6", 6, 0.10, 40, 3, 240);
            stRow(w, "V7", 6, 0.10, 20, 2, 240);
            stRow(w, "V8", 6, 0.10, 20, 4, 240);
            stRow(w, "V9", 6, 0.10, 20, 3, 120);
            stRow(w, "V10", 6, 0.10, 20, 3, 480);
            stRow(w, "V11", 6, 0.10, 1000, 3, 240, 200 * YEAR);
            stRow(w, "V12", 1, 0.10, 1000, 3, 240, 200 * YEAR);
            stRow(w, "V13", 6, 0.10, 1, 3, 240);
        }
    }

    private static void stRow(PrintWriter w, String name, int n, double share, double interval, double size, double limit) {
        stRow(w, name, n, share, interval, size, limit, RUN_TIME);
    }

    private static void stRow(PrintWriter w, String name, int n, double share, double interval, double size, double limit, double minutes) {
        FunRand.setSeed(BASE_SEED);
        ExperimentModel.Config c = new ExperimentModel.Config().pauses(n).share(share)
                .interval(Distribution.erl(interval, 2)).size(Distribution.unif(size, 0.5)).waitLimit(limit);
        ExperimentModel.Outcome o = ExperimentModel.run(c, minutes);
        w.printf("%s,%.0f,%d,%.2f,%.0f,%.0f,%.0f,%d,%d,%d,%d,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.5f,%.4f,%.3f,%.3f%n",
                name, minutes / YEAR, n, share, interval, size, limit, o.arrived(), o.placed(), o.nI(), o.nQ(), o.cI(), o.cQ(), o.expired(),
                o.inSystem(), o.airtime(), o.pauseTime(), o.freePause(), o.revenue(), o.profitPerYear(), o.paybackT(),
                o.meanWaiting(), o.meanWaitPlaced(), o.maxWaitPlaced());
    }

    static void protocol() {
        var console = System.out;
        try (PrintWriter w = csv("protocol_trace.txt")) {
            System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
                @Override public void write(int b) { w.write(b); }
                @Override public void write(byte[] b, int off, int len) { w.write(new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8)); }
            }, true, java.nio.charset.StandardCharsets.UTF_8));
            FunRand.setSeed(42);
            ExperimentModel.run(new ExperimentModel.Config().pauses(6), 170, true);
        } finally {
            System.setOut(console);
        }
        try (PrintWriter w = csv("protocol_ads.txt")) {
            System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
                @Override public void write(int b) { w.write(b); }
                @Override public void write(byte[] b, int off, int len) { w.write(new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8)); }
            }, true, java.nio.charset.StandardCharsets.UTF_8));
            FunRand.setSeed(42);
            ExperimentModel.run(new ExperimentModel.Config().pauses(6).logAds(true), Schedule.DAY);
        } finally {
            System.setOut(console);
        }
    }

    static void transientPeriod() {
        double[] days = {1, 2, 3, 5, 7, 10, 15, 20, 30, 45, 60, 90, 120, 180, 270, 365, 545, 730, 1095, 1460, 1825};
        int seeds = 5;
        try (PrintWriter w = csv("transient.csv")) {
            w.println("n,seed,days,profitPerYear,T,meanWaiting");
            for (int n : new int[]{5, 2, 1}) {
                for (int s = 0; s < seeds; s++) {
                    for (double d : days) {
                        FunRand.setSeed(BASE_SEED + s);
                        ExperimentModel.Outcome o = ExperimentModel.run(new ExperimentModel.Config().pauses(n), d * Schedule.DAY);
                        w.printf("%d,%d,%.0f,%.2f,%.6f,%.4f%n", n, s, d, o.profitPerYear(), o.paybackT(), o.meanWaiting());
                    }
                }
            }
        }
        try (PrintWriter w = csv("warmup.csv")) {
            w.println("n,seed,withWarmup,withoutWarmup");
            double warm = 30 * Schedule.DAY;
            for (int n : new int[]{1, 2, 5, 6, 32}) {
                for (int s = 0; s < RUNS; s++) {
                    FunRand.setSeed(BASE_SEED + s);
                    ExperimentModel.Outcome all = ExperimentModel.run(new ExperimentModel.Config().pauses(n), RUN_TIME);
                    FunRand.setSeed(BASE_SEED + s);
                    ExperimentModel.Outcome head = ExperimentModel.run(new ExperimentModel.Config().pauses(n), warm);
                    double steady = (all.profit() - head.profit()) / ((RUN_TIME - warm) / YEAR);
                    w.printf("%d,%d,%.2f,%.2f%n", n, s, all.profitPerYear(), steady);
                }
            }
        }
    }

    static void replications() {
        int pilot = 20;
        try (PrintWriter w = csv("replications.csv")) {
            w.println("n,meanT,stdT");
            for (int n = 1; n <= 32; n++) {
                double[] t = new double[pilot];
                for (int k = 0; k < pilot; k++) {
                    FunRand.setSeed(50_000 + k);
                    t[k] = ExperimentModel.run(new ExperimentModel.Config().pauses(n), RUN_TIME).paybackT();
                }
                double m = mean(t);
                w.printf("%d,%.6f,%.7f%n", n, m, std(t, m));
            }
        }
    }

    static void pauseSearch() {
        try (PrintWriter w = csv("search.csv")) {
            w.println("n,L,meanP,meanT,stdT,halfT,meanT1,immediate,cut,expired,free,meanWaiting,meanWaitPlaced");
            int[] extra = {36, 40, 48, 64, 96};
            for (int i = 1; i <= 32 + extra.length; i++) {
                int n = i <= 32 ? i : extra[i - 33];
                Series s = series(k -> new ExperimentModel.Config().pauses(n));
                w.printf("%d,%.4f,%.2f,%.6f,%.7f,%.7f,%.6f,%.5f,%.5f,%.5f,%.5f,%.4f,%.3f%n",
                        n, 96.0 / n, s.meanP, s.meanT, s.stdT, s.halfT, s.meanT1, s.immediate, s.cut, s.expired, s.free, s.waiting, s.waitPlaced);
            }
        }
        try (PrintWriter w = csv("paired.csv")) {
            w.println("nA,nB,meanDiff,stdDiff,t");
            int[][] pairs = {{5, 4}, {5, 6}, {5, 7}, {5, 10}, {5, 14}};
            for (int[] p : pairs) {
                double[] d = new double[RUNS];
                for (int k = 0; k < RUNS; k++) {
                    FunRand.setSeed(BASE_SEED + k);
                    double a = ExperimentModel.run(new ExperimentModel.Config().pauses(p[0]), RUN_TIME).paybackT();
                    FunRand.setSeed(BASE_SEED + k);
                    double b = ExperimentModel.run(new ExperimentModel.Config().pauses(p[1]), RUN_TIME).paybackT();
                    d[k] = b - a;
                }
                double m = mean(d), sd = std(d, m);
                w.printf("%d,%d,%.7f,%.7f,%.3f%n", p[0], p[1], m, sd, m / (sd / Math.sqrt(RUNS)));
            }
        }
    }

    static void anova() {
        int[] levels = {3, 4, 5, 6, 7, 8};
        double[][] y = new double[levels.length][RUNS];
        double grand = 0;
        for (int j = 0; j < levels.length; j++) {
            for (int s = 0; s < RUNS; s++) {
                FunRand.setSeed(BASE_SEED + s);
                y[j][s] = ExperimentModel.run(new ExperimentModel.Config().pauses(levels[j]), RUN_TIME).paybackT();
                grand += y[j][s];
            }
        }
        grand /= levels.length * RUNS;
        double ssF = 0, ssR = 0;
        for (double[] row : y) {
            double m = mean(row);
            ssF += RUNS * (m - grand) * (m - grand);
            for (double v : row) ssR += (v - m) * (v - m);
        }
        int dfF = levels.length - 1, dfR = levels.length * (RUNS - 1);
        try (PrintWriter w = csv("anova.csv")) {
            w.println("ssFactor,dfFactor,ssResidual,dfResidual,dFactor,dResidual,F");
            w.printf("%.10f,%d,%.10f,%d,%.10f,%.12f,%.1f%n", ssF, dfF, ssR, dfR, ssF / dfF, ssR / dfR, (ssF / dfF) / (ssR / dfR));
        }
    }

    /** Вплив попиту (середнього інтервалу надходження) на оптимальну кількість пауз. */
    static void demandSensitivity() {
        double[] means = {10, 20, 30, 40, 60, 80, 120};
        try (PrintWriter w = csv("demand.csv")) {
            w.println("meanInterval,n,meanP,meanT,halfT,immediate,cut,expired,free");
            for (double m : means) {
                for (int n = 1; n <= 32; n++) {
                    int nn = n;
                    Series s = series(k -> new ExperimentModel.Config().pauses(nn).interval(Distribution.erl(m, 2)));
                    w.printf("%.0f,%d,%.2f,%.6f,%.7f,%.5f,%.5f,%.5f,%.5f%n", m, n, s.meanP, s.meanT, s.halfT, s.immediate, s.cut, s.expired, s.free);
                }
            }
        }
    }

    /** Пробні запуски пропозицій щодо поліпшення системи. */
    static void proposals() {
        try (PrintWriter w = csv("proposals.csv")) {
            w.println("name,bestN,L,meanP,meanT,halfT,cut,expired,refused,free,meanWaiting,meanWaitPlaced");
            propose(w, "base", n -> new ExperimentModel.Config().pauses(n));
            propose(w, "start", n -> new ExperimentModel.Config().pauses(n).pausesAtSegmentStart(true));
            propose(w, "fit", n -> new ExperimentModel.Config().pauses(n).fitFirst(true));
            propose(w, "start+fit", n -> new ExperimentModel.Config().pauses(n).pausesAtSegmentStart(true).fitFirst(true));
            for (int q : new int[]{0, 2, 5, 10, 20}) {
                propose(w, "maxQueue=" + q, n -> new ExperimentModel.Config().pauses(n).maxQueue(q));
            }
            propose(w, "queuedFactor=0.85", n -> new ExperimentModel.Config().pauses(n).queuedFactor(0.85));
            propose(w, "queuedFactor=0.85;interval=30", n -> new ExperimentModel.Config().pauses(n).queuedFactor(0.85).interval(Distribution.erl(30, 2)));
            propose(w, "share=0.12", n -> new ExperimentModel.Config().pauses(n).share(0.12));
            propose(w, "share=0.15", n -> new ExperimentModel.Config().pauses(n).share(0.15));
        }
        try (PrintWriter w = csv("proposals_curve.csv")) {
            w.println("name,n,meanP,meanT,halfT,cut,free");
            for (int n = 1; n <= 32; n++) {
                int nn = n;
                curve(w, "base", nn, new ExperimentModel.Config().pauses(nn));
                curve(w, "start", nn, new ExperimentModel.Config().pauses(nn).pausesAtSegmentStart(true));
                curve(w, "fit", nn, new ExperimentModel.Config().pauses(nn).fitFirst(true));
                curve(w, "start+fit", nn, new ExperimentModel.Config().pauses(nn).pausesAtSegmentStart(true).fitFirst(true));
            }
        }
    }

    private static void curve(PrintWriter w, String name, int n, ExperimentModel.Config c) {
        Series s = series(k -> c);
        w.printf("%s,%d,%.2f,%.6f,%.7f,%.5f,%.5f%n", name, n, s.meanP, s.meanT, s.halfT, s.cut, s.free);
    }

    private static void propose(PrintWriter w, String name, IntFunction<ExperimentModel.Config> factory) {
        Series best = null;
        int bestN = -1;
        for (int n = 1; n <= 32; n++) {
            ExperimentModel.Config c = factory.apply(n);
            Series s = series(k -> c);
            if (best == null || s.meanP > best.meanP) {
                best = s;
                bestN = n;
            }
        }
        double share = factory.apply(bestN).share;
        w.printf("%s,%d,%.3f,%.2f,%.6f,%.7f,%.5f,%.5f,%.5f,%.5f,%.4f,%.3f%n", name, bestN, 960 * share / bestN,
                best.meanP, best.meanT, best.halfT, best.cut, best.expired, best.refused, best.free, best.waiting, best.waitPlaced);
        System.out.printf("%-20s n*=%d T=%.5f p=%.0f cut=%.3f exp=%.3f ref=%.3f wait=%.1f%n", name, bestN, best.meanT, best.meanP, best.cut, best.expired, best.refused, best.waitPlaced);
    }

    static final class Series {
        double meanP, meanT, stdT, halfT, meanT1, immediate, cut, expired, refused, free, waiting, waitPlaced;
    }

    static Series series(IntFunction<ExperimentModel.Config> factory) {
        Series s = new Series();
        double[] t = new double[RUNS];
        for (int k = 0; k < RUNS; k++) {
            FunRand.setSeed(BASE_SEED + k);
            ExperimentModel.Outcome o = ExperimentModel.run(factory.apply(k), RUN_TIME);
            t[k] = o.paybackT();
            s.meanP += o.profitPerYear() / RUNS;
            s.meanT1 += o.paybackOneTime() / RUNS;
            s.immediate += (double) (o.nI() + o.cI()) / o.arrived() / RUNS;
            s.cut += o.placed() == 0 ? 0 : (double) (o.cI() + o.cQ()) / o.placed() / RUNS;
            s.expired += (double) o.expired() / o.arrived() / RUNS;
            s.refused += (double) o.refused() / o.arrived() / RUNS;
            s.free += o.freePause() / o.pauseTime() / RUNS;
            s.waiting += o.meanWaiting() / RUNS;
            s.waitPlaced += o.meanWaitPlaced() / RUNS;
        }
        s.meanT = mean(t);
        s.stdT = std(t, s.meanT);
        s.halfT = 2.262 * s.stdT / Math.sqrt(RUNS);
        return s;
    }

    private static PrintWriter csv(String name) {
        try {
            return new PrintWriter(dir.resolve(name).toFile(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    static double std(double[] x, double mean) {
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (x.length - 1));
    }
}
