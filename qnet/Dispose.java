package qnet;

import java.util.Map;
import java.util.TreeMap;

public class Dispose extends Element {
    private final Stat all = new Stat();
    private final Map<Integer, Stat> byType = new TreeMap<>();

    public Dispose(String name) {
        super(name);
    }

    @Override
    public void inAct(Entity entity) {
        quantity++;
        double time = getTcurr() - entity.getCreatedAt();
        all.add(time);
        byType.computeIfAbsent(entity.getInitialType(), t -> new Stat()).add(time);
    }

    @Override
    public void outAct() {
    }

    public Stat timeInSystem() { return all; }
    public Stat timeInSystem(int type) { return byType.getOrDefault(type, new Stat()); }

    @Override
    public void printResult(double time) {
        System.out.printf("%-22s quantity = %d | time in system: mean = %.4f, std = %.4f, max = %.4f%n",
                getName(), quantity, all.mean(), all.std(), all.max());
    }
}
