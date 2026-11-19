package adbroadcast;

import qnet.FunRand;

import java.util.ArrayList;
import java.util.List;

public class Main {
    static final double SHARE = 0.10;
    static final double YEARS = 5;
    static final int RUNS = 10;
    static final int MAX_PAUSES = 32;

    public static void main(String[] args) {
        FunRand.setSeed(42);
        AdModel.Result base = AdModel.run(6, SHARE, 1, true);
        printDetails(base);

        System.out.printf("%nЕксперимент: %d прогонів по %.0f р. для кожного n (спільні випадкові числа)%n", RUNS, YEARS);
        System.out.println("  n |  L, хв | p, тис. грн/рік | T = c/p, роки (±95%) | відразу | обірв. | застар. | невикор. пауз");
        List<double[]> table = new ArrayList<>();
        int bestN = -1;
        double bestT = Double.MAX_VALUE;
        for (int n = 1; n <= MAX_PAUSES; n++) {
            double[] t = new double[RUNS];
            double p = 0, imm = 0, cut = 0, exp = 0, free = 0;
            AdModel.Result r = null;
            for (int k = 0; k < RUNS; k++) {
                FunRand.setSeed(1000 + k);
                r = AdModel.run(n, SHARE, YEARS, false);
                t[k] = r.paybackT();
                p += r.profitPerYear() / RUNS;
                imm += (double) (r.immediateFull() + r.immediateCut()) / r.arrived() / RUNS;
                cut += (double) (r.immediateCut() + r.queuedCut()) / r.placed() / RUNS;
                exp += (double) r.expired() / r.arrived() / RUNS;
                free += r.freePauseTime() / r.pauseTime() / RUNS;
            }
            double mean = mean(t), half = 2.262 * std(t, mean) / Math.sqrt(RUNS);
            System.out.printf("%3d | %6.2f | %15.1f | %8.5f ± %8.5f     | %6.1f%% | %5.1f%% | %6.1f%% | %8.1f%%%n",
                    n, r.pauseLength(), p / 1000, mean, half, imm * 100, cut * 100, exp * 100, free * 100);
            if (mean < bestT) {
                bestT = mean;
                bestN = n;
            }
            table.add(new double[]{n, mean, half});
        }
        System.out.printf("%nМінімальний період окупності: n = %d пауз (L = %.2f хв), T = %.5f р. (≈ %.1f доби)%n",
                bestN, Schedule.BROADCAST * SHARE / bestN, bestT, bestT * AdModel.DAYS_PER_YEAR);
        double[] best = table.get(bestN - 1);
        System.out.print("n, середнє T яких статистично не відрізняється від найкращого (довірчі інтервали перетинаються):");
        for (double[] row : table) {
            if (row[1] - row[2] <= best[1] + best[2]) System.out.printf(" %d", (int) row[0]);
        }
        System.out.println();
    }

    private static void printDetails(AdModel.Result r) {
        System.out.printf("%nБазовий прогін: n = %d, L = %.2f хв, %.0f р.%n", r.pauses(), r.pauseLength(), r.years());
        System.out.printf("  надійшло повідомлень: %d; розміщено: %d (відразу цілком %d, з черги цілком %d, обірвано %d + %d); застаріло: %d%n",
                r.arrived(), r.placed(), r.immediateFull(), r.queuedFull(), r.immediateCut(), r.queuedCut(), r.expired());
        System.out.printf("  середня довжина черги замовлень: %.3f; середнє очікування: %.1f хв%n", r.meanQueue(), r.meanWait());
        System.out.printf("  час пауз: %.0f хв, у т.ч. реклама %.0f хв, вільний %.0f хв%n", r.pauseTime(), r.airtime(), r.freePauseTime());
        System.out.printf("  дохід від реклами: %.0f грн/рік; витрати на мовлення: %.0f грн/рік (у т.ч. вільний час пауз %.0f грн/рік)%n",
                r.revenue() / r.years(), AdModel.Result.yearlyBroadcastCost(), AdModel.MINUTE_COST * r.freePauseTime() / r.years());
        System.out.printf("  p = %.0f грн/рік; c = %.0f грн; T = c/p = %.4f р.%n",
                r.profitPerYear(), AdModel.ONE_TIME_COST, r.paybackT());
    }

    private static double mean(double[] x) {
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    private static double std(double[] x, double mean) {
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (x.length - 1));
    }
}
