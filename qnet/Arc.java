package qnet;

import java.util.function.ToDoubleFunction;

public final class Arc {
    static final Arc EXIT = new Arc(null);

    @FunctionalInterface
    public interface Block {
        boolean test(double t, Entity entity);
    }

    private final Element target;
    private double probability = 1.0;
    private int priority = 1;
    private Block block;
    private ToDoubleFunction<Entity> opensAt;

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

    public Arc block(Block block) {
        this.block = block;
        return this;
    }

    public Arc opensAt(ToDoubleFunction<Entity> opensAt) {
        this.opensAt = opensAt;
        return this;
    }

    public Element target() { return target; }
    public double probability() { return probability; }
    public int priority() { return priority; }

    boolean isBlocked(double t, Entity entity) {
        return block != null && block.test(t, entity);
    }

    double opensAt(Entity entity) {
        return opensAt == null ? Double.MAX_VALUE : opensAt.applyAsDouble(entity);
    }

    void deliver(Entity e) {
        target.inAct(e);
    }
}
