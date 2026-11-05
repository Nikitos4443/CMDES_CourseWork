package adbroadcast;

import qnet.Distribution;
import qnet.Element;
import qnet.Entity;
import qnet.Process;

public class ImpatientProcess extends Process {
    private final double limit;
    private Element expiredOut;
    private int expired;

    public ImpatientProcess(String name, Distribution service, double limit) {
        super(name, service);
        this.limit = limit;
    }

    public void setExpiredOutput(Element expiredOut) {
        this.expiredOut = expiredOut;
    }

    @Override
    protected void updateTnext() {
        super.updateTnext();
        if (state[0] == BLOCKED) setTnext(Math.min(getTnext(), deadline()));
    }

    private double deadline() {
        return serving[0].getEnteredAt() + limit;
    }

    @Override
    public void outAct() {
        if (state[0] == BLOCKED && deadline() <= getTcurr()) {
            Entity e = serving[0];
            vacate(0);
            expire(e);
            return;
        }
        super.outAct();
    }

    private void expire(Entity e) {
        expired++;
        if (expiredOut != null) expiredOut.inAct(e);
    }

    public int getExpired() { return expired; }

    @Override
    public void printResult(double time) {
        super.printResult(time);
        System.out.printf("%-22s expired = %d%n", "", expired);
    }
}
