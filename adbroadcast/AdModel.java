package adbroadcast;

import qnet.*;
import qnet.Process;

public class AdModel {
    public static final double MINUTE_COST = 100;
    public static final double ONE_TIME_COST = 1_000_000;
    public static final double WAIT_LIMIT = 4 * 60;
    public static final double DAYS_PER_YEAR = 365;

    public record Result(int pauses, double pauseLength, double years,
                         int arrived, int immediateFull, int queuedFull, int immediateCut, int queuedCut, int expired,
                         double revenue, double airtime, double pauseTime, double meanWait, double meanQueue) {
        public double freePauseTime() { return pauseTime - airtime; }
        public double profitPerYear() { return (revenue - MINUTE_COST * freePauseTime()) / years; }
        public static double monthlyBroadcastCost() {
            return MINUTE_COST * Schedule.BROADCAST * DAYS_PER_YEAR / 12;
        }
        public double paybackT() { return monthlyBroadcastCost() / profitPerYear(); }
        public double paybackOneTime() { return ONE_TIME_COST / profitPerYear(); }
        public int placed() { return immediateFull + queuedFull + immediateCut + queuedCut; }
    }

    public static Result run(int pauses, double share, double years, boolean print) {
        Schedule schedule = new Schedule(pauses, share);

        Process program = new Process("PROGRAM", Distribution.constant(0));
        Process pause = new Process("PAUSE", Distribution.constant(schedule.pauseLength()));
        program.setServiceTime(e -> schedule.nextPauseStart(program.getTcurr()) - program.getTcurr());
        program.to(pause);
        pause.to(program);
        program.putInitial(new Entity(0), schedule.nextPauseStart(0));

        Create create = new Create("CREATE", Distribution.erl(20, 2));
        Distribution adSize = Distribution.unif(3, 0.5);
        create.setEntityFactory(t -> new AdMessage(t, adSize.next()));

        ImpatientProcess orders = new ImpatientProcess("ORDERS", Distribution.constant(0), WAIT_LIMIT);
        Process air = new Process("AIR", Distribution.constant(0));
        air.setMaxQueue(0);
        air.setServiceTime(e -> {
            AdMessage ad = (AdMessage) e;
            ad.startOnAir(air.getTcurr(), pause.getTnext() - air.getTcurr());
            return ad.aired();
        });
        AdPayment payment = new AdPayment("PAYMENT");
        Dispose expired = new Dispose("EXPIRED");

        create.to(orders);
        orders.to(air).block(t -> pause.occupied() == 0 || air.occupied() > 0);
        orders.setExpiredOutput(expired);
        air.to(payment);

        Model model = new Model(program, pause, create, orders, air, payment, expired);
        double time = years * DAYS_PER_YEAR * Schedule.DAY;
        model.simulate(time);
        if (print) model.printResult();

        return new Result(pauses, schedule.pauseLength(), years,
                create.getQuantity(), payment.immediateFull(), payment.queuedFull(),
                payment.immediateCut(), payment.queuedCut(), orders.getExpired(),
                payment.revenue(), payment.airtime(), pause.busyTime(),
                orders.meanWait(), orders.meanQueue(time));
    }
}
