package qnet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public abstract class Element {
    private static int nextId = 0;

    private final int id;
    private String name;
    private double tcurr = 0.0;
    private double tnext = Double.MAX_VALUE;
    protected int quantity;

    protected final List<Arc> arcs = new ArrayList<>();
    private List<List<Arc>> levels;

    protected Element(String name) {
        this.id = nextId++;
        this.name = name == null ? "element" + id : name;
    }

    public Arc to(Element target) {
        Arc arc = new Arc(target);
        arcs.add(arc);
        levels = null;
        return arc;
    }

    public void inAct(Entity entity) {
        throw new UnsupportedOperationException(name + " не приймає замовлень");
    }

    public abstract void outAct();

    public boolean canAccept() {
        return true;
    }

    public boolean tryUnblock() {
        return false;
    }

    public void doStatistics(double delta) {
    }

    protected Arc route(Entity entity) {
        if (arcs.isEmpty()) return Arc.EXIT;
        if (levels == null) levels = buildLevels();

        boolean byPriority = levels.size() > 1;
        Arc fallback = null;
        for (List<Arc> level : levels) {
            Arc arc = level.size() == 1
                    ? level.getFirst()
                    : level.get(FunRand.choose(level.stream().mapToDouble(Arc::probability).toArray()));
            if (arc.isBlocked(tcurr, entity)) continue;
            if (byPriority && !arc.target().canAccept()) {
                if (fallback == null) fallback = arc;
                continue;
            }
            return arc;
        }
        return fallback;
    }

    protected double opensAt(Entity entity) {
        double t = Double.MAX_VALUE;
        for (Arc a : arcs) t = Math.min(t, a.opensAt(entity));
        return t;
    }

    private List<List<Arc>> buildLevels() {
        Map<Integer, List<Arc>> byPriority = new TreeMap<>(Comparator.reverseOrder());
        for (Arc a : arcs) {
            byPriority.computeIfAbsent(a.priority(), _ -> new ArrayList<>()).add(a);
        }
        return new ArrayList<>(byPriority.values());
    }

    protected static void deliver(Arc arc, Entity entity) {
        if (arc != Arc.EXIT) arc.deliver(entity);
    }

    public void printResult(double time) {
        System.out.printf("%-22s quantity = %d%n", name, quantity);
    }

    public int getQuantity() { return quantity; }
    public double getTcurr() { return tcurr; }
    public void setTcurr(double tcurr) { this.tcurr = tcurr; }
    public double getTnext() { return tnext; }
    public void setTnext(double tnext) { this.tnext = tnext; }
    public int getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
