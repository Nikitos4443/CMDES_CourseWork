package adbroadcast;

import qnet.Dispose;
import qnet.Distribution;
import qnet.Entity;
import qnet.Model;
import qnet.Process;
import qnet.Create;
import qnet.Stat;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;

public class ExperimentModel {

    public static final class Config {
        public int pauses = 5;
        public double share = 0.10;
        public Distribution interval = Distribution.erl(20, 2);
        public Distribution size = Distribution.unif(3, 0.5);
        public double waitLimit = AdModel.WAIT_LIMIT;
        public int maxQueue = Integer.MAX_VALUE;
        public boolean pausesAtSegmentStart = false;
        public boolean fitFirst = false;
        public double queuedFactor = 0.7;
        public double cutFactor = 0.9;
        public boolean logAds = false;

        public Config pauses(int v) { pauses = v; return this; }
        public Config share(double v) { share = v; return this; }
        public Config interval(Distribution v) { interval = v; return this; }
        public Config size(Distribution v) { size = v; return this; }
        public Config waitLimit(double v) { waitLimit = v; return this; }
        public Config maxQueue(int v) { maxQueue = v; return this; }
        public Config pausesAtSegmentStart(boolean v) { pausesAtSegmentStart = v; return this; }
        public Config fitFirst(boolean v) { fitFirst = v; return this; }
        public Config queuedFactor(double v) { queuedFactor = v; return this; }
        public Config logAds(boolean v) { logAds = v; return this; }
    }

    public record Outcome(double minutes, int arrived, int nI, int nQ, int cI, int cQ, int expired, int refused,
                          double revenue, double airtime, double pauseTime,
                          double meanQueue, double meanBlocked, double meanWaitPlaced, double maxWaitPlaced) {
        public double years() { return minutes / (AdModel.DAYS_PER_YEAR * Schedule.DAY); }
        public int placed() { return nI + nQ + cI + cQ; }
        public int inSystem() { return arrived - placed() - expired - refused; }
        public double freePause() { return pauseTime - airtime; }
        public double broadcastCost() { return AdModel.MINUTE_COST * Schedule.BROADCAST * (minutes / Schedule.DAY); }
        public double profit() { return revenue - broadcastCost(); }
        public double profitPerYear() { return profit() / years(); }
        public double paybackT() { return AdModel.ONE_TIME_COST / profitPerYear(); }
        public double meanWaiting() { return meanQueue + meanBlocked; }
    }

    static final class TariffPayment extends AdPayment {
        private final double queuedFactor, cutFactor;
        private final boolean log;
        private final Stat wait = new Stat();
        private double tariffRevenue;

        TariffPayment(String name, double queuedFactor, double cutFactor, boolean log) {
            super(name);
            this.queuedFactor = queuedFactor;
            this.cutFactor = cutFactor;
            this.log = log;
        }

        @Override
        public void inAct(Entity entity) {
            super.inAct(entity);
            AdMessage ad = (AdMessage) entity;
            double pay = PRICE * ad.aired();
            if (ad.cut()) pay *= cutFactor;
            if (!ad.immediate()) pay *= queuedFactor;
            tariffRevenue += pay;
            double started = getTcurr() - ad.aired();
            wait.add(started - ad.getCreatedAt());
            if (log) {
                System.out.printf("AD arrive=%.3f start=%.3f size=%.3f aired=%.3f type=%s%s pay=%.2f%n",
                        ad.getCreatedAt(), started, ad.size(), ad.aired(),
                        ad.immediate() ? "I" : "Q", ad.cut() ? "-cut" : "-full", pay);
            }
        }
    }

    static final class ExpiredOut extends Dispose {
        private final boolean log;

        ExpiredOut(String name, boolean log) {
            super(name);
            this.log = log;
        }

        @Override
        public void inAct(Entity entity) {
            super.inAct(entity);
            if (log) {
                AdMessage ad = (AdMessage) entity;
                System.out.printf("AD arrive=%.3f expired=%.3f size=%.3f%n", ad.getCreatedAt(), getTcurr(), ad.size());
            }
        }
    }

    static final class FitFirstOrders extends ImpatientProcess {
        private final BooleanSupplier airOpen;
        private final DoubleSupplier pauseLeft;

        FitFirstOrders(String name, double limit, BooleanSupplier airOpen, DoubleSupplier pauseLeft) {
            super(name, Distribution.constant(0), limit);
            this.airOpen = airOpen;
            this.pauseLeft = pauseLeft;
        }

        private void selectFitting() {
            if (serving[0] == null || !airOpen.getAsBoolean()) return;
            double left = pauseLeft.getAsDouble();
            if (((AdMessage) serving[0]).size() <= left && queue.isEmpty()) return;
            int best = -1;
            double bestSize = ((AdMessage) serving[0]).size() <= left ? ((AdMessage) serving[0]).size() : -1;
            for (int i = 0; i < queue.size(); i++) {
                double s = ((AdMessage) queue.get(i)).size();
                if (s <= left && s > bestSize) {
                    best = i;
                    bestSize = s;
                }
            }
            if (best < 0) return;
            Entity candidate = queue.remove(best);
            queue.addFirst(serving[0]);
            serving[0] = candidate;
        }

        @Override
        public void outAct() {
            if (state[0] == BUSY) selectFitting();
            super.outAct();
        }

        @Override
        public boolean tryUnblock() {
            if (state[0] == BLOCKED) selectFitting();
            return super.tryUnblock();
        }
    }

    public static Outcome run(Config c, double minutes) {
        return run(c, minutes, false);
    }

    public static Outcome run(Config c, double minutes, boolean trace) {
        Schedule schedule = new Schedule(c.pauses, c.share);
        double segment = Schedule.BROADCAST / c.pauses;
        DoubleUnaryOperator nextPause = c.pausesAtSegmentStart
                ? t -> {
                    double day = Math.floor(t / Schedule.DAY);
                    double u = t - day * Schedule.DAY;
                    for (int k = 0; k < c.pauses; k++) {
                        if (k * segment >= u) return day * Schedule.DAY + k * segment;
                    }
                    return (day + 1) * Schedule.DAY;
                }
                : schedule::nextPauseStart;

        Process program = new Process("PROGRAM", Distribution.constant(0));
        Process pause = new Process("PAUSE", Distribution.constant(schedule.pauseLength()));
        program.setServiceTime(e -> nextPause.applyAsDouble(program.getTcurr()) - program.getTcurr());
        program.to(pause);
        pause.to(program);
        program.putInitial(new Entity(0), nextPause.applyAsDouble(0));

        Create create = new Create("CREATE", c.interval);
        create.setEntityFactory(t -> new AdMessage(t, c.size.next()));

        Process air = new Process("AIR", Distribution.constant(0));
        air.setMaxQueue(0);
        air.setServiceTime(e -> {
            AdMessage ad = (AdMessage) e;
            ad.startOnAir(air.getTcurr(), pause.getTnext() - air.getTcurr());
            return ad.aired();
        });

        ImpatientProcess orders = c.fitFirst
                ? new FitFirstOrders("ORDERS", c.waitLimit,
                        () -> pause.occupied() > 0 && air.occupied() == 0,
                        () -> pause.getTnext() - pause.getTcurr())
                : new ImpatientProcess("ORDERS", Distribution.constant(0), c.waitLimit);
        orders.setMaxQueue(c.maxQueue);

        TariffPayment payment = new TariffPayment("PAYMENT", c.queuedFactor, c.cutFactor, c.logAds);
        ExpiredOut expired = new ExpiredOut("EXPIRED", c.logAds);

        create.to(orders);
        orders.to(air).block(t -> pause.occupied() == 0 || air.occupied() > 0);
        orders.setExpiredOutput(expired);
        air.to(payment);

        Model model = new Model(program, pause, create, orders, air, payment, expired);
        model.setTrace(trace);
        model.simulate(minutes);

        return new Outcome(minutes, create.getQuantity(),
                payment.immediateFull(), payment.queuedFull(), payment.immediateCut(), payment.queuedCut(),
                orders.getExpired(), orders.getFailures(),
                payment.tariffRevenue, payment.airtime(), pause.busyTime(),
                orders.meanQueue(minutes), orders.meanBlocked(minutes),
                payment.wait.mean(), payment.wait.max());
    }
}
