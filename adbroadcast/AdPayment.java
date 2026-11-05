package adbroadcast;

import qnet.Dispose;
import qnet.Entity;

public class AdPayment extends Dispose {
    public static final double PRICE = 300;

    private int immediateFull, queuedFull, immediateCut, queuedCut;
    private double revenue, airtime;

    public AdPayment(String name) {
        super(name);
    }

    @Override
    public void inAct(Entity entity) {
        super.inAct(entity);
        AdMessage ad = (AdMessage) entity;
        double pay = PRICE * ad.aired();
        if (ad.cut()) pay *= 0.9;
        if (!ad.immediate()) pay *= 0.7;
        revenue += pay;
        airtime += ad.aired();
        if (ad.immediate()) {
            if (ad.cut()) immediateCut++; else immediateFull++;
        } else {
            if (ad.cut()) queuedCut++; else queuedFull++;
        }
    }

    public double revenue() { return revenue; }
    public double airtime() { return airtime; }
    public int immediateFull() { return immediateFull; }
    public int queuedFull() { return queuedFull; }
    public int immediateCut() { return immediateCut; }
    public int queuedCut() { return queuedCut; }

    @Override
    public void printResult(double time) {
        System.out.printf("%-22s N_I = %d, N_Q = %d, C_I = %d, C_Q = %d | airtime = %.1f хв | revenue = %.0f грн%n",
                getName(), immediateFull, queuedFull, immediateCut, queuedCut, airtime, revenue);
    }
}
