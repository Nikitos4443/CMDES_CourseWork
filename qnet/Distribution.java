package qnet;

@FunctionalInterface
public interface Distribution {
    double next();

    static Distribution constant(double value) {
        return () -> value;
    }

    static Distribution exp(double mean) {
        return () -> FunRand.Exp(mean);
    }

    static Distribution unif(double mean, double dev) {
        return () -> FunRand.Unif(mean - dev, mean + dev);
    }

    static Distribution norm(double mean, double dev) {
        return () -> Math.max(0.0, FunRand.Norm(mean, dev));
    }

    static Distribution erl(double mean, int k) {
        return () -> FunRand.Erlang(mean, k);
    }

    static Distribution empirical(double[] x, double[] cumulative) {
        return () -> FunRand.Empirical(x, cumulative);
    }
}
