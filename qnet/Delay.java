package qnet;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Function;

public class Delay extends Element {
    private record Item(double end, long seq, Entity entity) {}

    private final PriorityQueue<Item> items =
            new PriorityQueue<>(Comparator.comparingDouble(Item::end).thenComparingLong(Item::seq));
    private final ArrayDeque<Entity> blocked = new ArrayDeque<>();
    private Function<Entity, Double> time;
    private long seq;
    private double busyIntegral;

    public Delay(String name, Distribution time) {
        super(name);
        this.time = e -> time.next();
    }

    public void setServiceTime(Function<Entity, Double> time) { this.time = time; }

    @Override
    public void inAct(Entity entity) {
        entity.setEnteredAt(getTcurr());
        items.add(new Item(getTcurr() + time.apply(entity), seq++, entity));
        updateTnext();
    }

    @Override
    public void outAct() {
        Entity entity = items.poll().entity();
        Arc arc = blocked.isEmpty() ? route() : null;
        if (arc == null) {
            blocked.add(entity);
        } else {
            quantity++;
            deliver(arc, entity);
        }
        updateTnext();
    }

    @Override
    public boolean tryUnblock() {
        boolean changed = false;
        while (!blocked.isEmpty()) {
            Arc arc = route();
            if (arc == null) break;
            quantity++;
            deliver(arc, blocked.poll());
            changed = true;
        }
        return changed;
    }

    private void updateTnext() {
        setTnext(items.isEmpty() ? Double.MAX_VALUE : items.peek().end());
    }

    @Override
    public void doStatistics(double delta) {
        busyIntegral += (items.size() + blocked.size()) * delta;
    }

    public int size() { return items.size() + blocked.size(); }
    public double meanSize(double time) { return busyIntegral / time; }

    @Override
    public void printResult(double time) {
        System.out.printf("%-22s quantity = %d | mean in delay = %.4f%n", getName(), quantity, meanSize(time));
    }
}
