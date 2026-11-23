package qnet;

import java.util.function.DoubleFunction;

public class Create extends Element {
    private final Distribution interval;
    private double[] typeProbabilities;
    private DoubleFunction<Entity> factory;
    private int lost;

    public Create(String name, Distribution interval) {
        super(name);
        this.interval = interval;
        setTnext(0.0);
    }

    public void setFirstArrival(double t) {
        setTnext(t);
    }

    public void setTypeProbabilities(double... probabilities) {
        this.typeProbabilities = probabilities;
    }

    public void setEntityFactory(DoubleFunction<Entity> factory) {
        this.factory = factory;
    }

    @Override
    public void outAct() {
        quantity++;
        setTnext(getTcurr() + interval.next());
        Entity entity;
        if (factory != null) {
            entity = factory.apply(getTcurr());
        } else {
            int type = typeProbabilities == null ? 0 : FunRand.choose(typeProbabilities) + 1;
            entity = new Entity(getTcurr(), type);
        }
        Arc arc = route(entity);
        if (arc == null) {
            lost++;
        } else {
            deliver(arc, entity);
        }
    }

    public int getLost() { return lost; }

    @Override
    public void printResult(double time) {
        System.out.printf("%-22s created = %d%s%n", getName(), quantity, lost > 0 ? ", lost (blocked) = " + lost : "");
    }
}
