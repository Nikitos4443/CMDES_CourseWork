package qnet;

import java.util.Random;

public final class FunRand {
    private static Random rnd = new Random(42);

    private FunRand() {}

    public static void setSeed(long seed) {
        rnd = new Random(seed);
    }

    public static double uniform01() {
        double a;
        do {
            a = rnd.nextDouble();
        } while (a == 0);
        return a;
    }

    public static double Exp(double timeMean) {
        return -timeMean * Math.log(uniform01());
    }

    public static double Unif(double timeMin, double timeMax) {
        return timeMin + uniform01() * (timeMax - timeMin);
    }

    public static double Norm(double timeMean, double timeDeviation) {
        return timeMean + timeDeviation * rnd.nextGaussian();
    }

    public static double Erlang(double timeMean, int k) {
        double sum = 0;
        for (int i = 0; i < k; i++) {
            sum += Exp(timeMean / k);
        }
        return sum;
    }

    public static double Empirical(double[] x, double[] cumulative) {
        double r = uniform01();
        for (int i = 1; i < x.length; i++) {
            if (r <= cumulative[i]) {
                double share = (r - cumulative[i - 1]) / (cumulative[i] - cumulative[i - 1]);
                return x[i - 1] + share * (x[i] - x[i - 1]);
            }
        }
        return x[x.length - 1];
    }

    public static int choose(double... probabilities) {
        double total = 0;
        for (double p : probabilities) total += p;
        double r = rnd.nextDouble() * total;
        double cumulative = 0;
        for (int i = 0; i < probabilities.length; i++) {
            cumulative += probabilities[i];
            if (r < cumulative) return i;
        }
        return probabilities.length - 1;
    }
}
