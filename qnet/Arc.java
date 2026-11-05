package qnet;

import java.util.function.DoublePredicate;

public final class Arc {
    static final Arc EXIT = new Arc(null);

    private final Element target;
    private double probability = 1.0;
    private int priority = 1;
    private DoublePredicate block;

    Arc(Element target) {
        this.target = target;
    }

    public Arc probability(double probability) {
        this.probability = probability;
        return this;
    }

    public Arc priority(int priority) {
        this.priority = priority;
        return this;
    }

    public Arc block(DoublePredicate block) {
        this.block = block;
        return this;
    }

    public Element target() { return target; }
    public double probability() { return probability; }
    public int priority() { return priority; }

    boolean isBlocked(double t) {
        return block != null && block.test(t);
    }

    void deliver(Entity e) {
        target.inAct(e);
    }
}
