package qnet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;

public class Process extends Element {
    public static final int FREE = 0, BUSY = 1, BLOCKED = 2;

    public enum Discipline { FIFO, LIFO }

    protected final int channels;
    protected final int[] state;
    protected final double[] channelEnd;
    protected final double[] blockedSince;
    protected final Entity[] serving;
    protected final ArrayList<Entity> queue = new ArrayList<>();

    private int maxQueue = Integer.MAX_VALUE;
    private Function<Entity, Double> serviceTime;
    private Discipline discipline = Discipline.FIFO;
    private Comparator<Entity> queuePriority;

    private int arrivals, failures;
    private double queueIntegral, busyIntegral, blockedIntegral;

    public Process(String name, Distribution service) {
        this(name, service, 1);
    }

    public Process(String name, Distribution service, int channels) {
        super(name);
        this.channels = channels;
        this.state = new int[channels];
        this.channelEnd = new double[channels];
        this.blockedSince = new double[channels];
        this.serving = new Entity[channels];
        Arrays.fill(channelEnd, Double.MAX_VALUE);
        this.serviceTime = e -> service.next();
    }

    public void setMaxQueue(int maxQueue) { this.maxQueue = maxQueue; }

    public void setServiceTime(Function<Entity, Double> serviceTime) { this.serviceTime = serviceTime; }

    public void setDiscipline(Discipline discipline) { this.discipline = discipline; }

    public void setQueuePriority(Comparator<Entity> queuePriority) { this.queuePriority = queuePriority; }

    @Override
    public void inAct(Entity entity) {
        arrivals++;
        int c = freeChannel();
        if (c >= 0) {
            entity.setEnteredAt(getTcurr());
            start(entity, c);
        } else if (queue.size() < maxQueue) {
            entity.setEnteredAt(getTcurr());
            queue.add(entity);
        } else {
            failures++;
        }
    }

    @Override
    public void outAct() {
        int c = -1;
        for (int i = 0; i < channels; i++) {
            if (state[i] == BUSY && (c < 0 || channelEnd[i] < channelEnd[c])) c = i;
        }
        state[c] = BLOCKED;
        channelEnd[c] = Double.MAX_VALUE;
        blockedSince[c] = getTcurr();
        if (!release(c)) updateTnext();
    }

    @Override
    public boolean tryUnblock() {
        boolean changed = false;
        while (true) {
            int c = -1;
            for (int i = 0; i < channels; i++) {
                if (state[i] == BLOCKED && (c < 0 || blockedSince[i] < blockedSince[c])) c = i;
            }
            if (c < 0 || !release(c)) return changed;
            changed = true;
        }
    }

    private boolean release(int c) {
        Entity entity = serving[c];
        Arc arc = route();
        if (arc == null) return false;
        quantity++;
        vacate(c);
        deliver(arc, entity);
        return true;
    }

    protected void vacate(int c) {
        serving[c] = null;
        state[c] = FREE;
        channelEnd[c] = Double.MAX_VALUE;
        if (!queue.isEmpty()) start(takeFromQueue(), c);
        updateTnext();
    }

    protected void start(Entity entity, int c) {
        serving[c] = entity;
        state[c] = BUSY;
        channelEnd[c] = getTcurr() + serviceTime.apply(entity);
        updateTnext();
    }

    protected Entity takeFromQueue() {
        if (queuePriority != null) {
            int best = 0;
            for (int i = 1; i < queue.size(); i++) {
                int cmp = queuePriority.compare(queue.get(i), queue.get(best));
                if (cmp < 0 || (cmp == 0 && discipline == Discipline.LIFO)) best = i;
            }
            return queue.remove(best);
        }
        return discipline == Discipline.FIFO ? queue.removeFirst() : queue.removeLast();
    }

    protected int freeChannel() {
        for (int i = 0; i < channels; i++) {
            if (state[i] == FREE) return i;
        }
        return -1;
    }

    protected void updateTnext() {
        double min = Double.MAX_VALUE;
        for (double t : channelEnd) min = Math.min(min, t);
        setTnext(min);
    }

    @Override
    public boolean canAccept() {
        return freeChannel() >= 0 || queue.size() < maxQueue;
    }

    public void putInitial(Entity entity, double remaining) {
        entity.setEnteredAt(getTcurr());
        int c = freeChannel();
        if (c >= 0) {
            serving[c] = entity;
            state[c] = BUSY;
            channelEnd[c] = getTcurr() + remaining;
            updateTnext();
        } else {
            queue.add(entity);
        }
    }

    @Override
    public void doStatistics(double delta) {
        queueIntegral += queue.size() * delta;
        busyIntegral += count(BUSY) * delta;
        blockedIntegral += count(BLOCKED) * delta;
    }

    private int count(int s) {
        int n = 0;
        for (int st : state) if (st == s) n++;
        return n;
    }

    public int busy() { return count(BUSY); }
    public int blocked() { return count(BLOCKED); }
    public int occupied() { return channels - count(FREE); }
    public int getQueue() { return queue.size(); }
    public int getMaxQueue() { return maxQueue; }
    public int getChannels() { return channels; }
    public int getArrivals() { return arrivals; }
    public int getFailures() { return failures; }

    public double meanQueue(double time) { return queueIntegral / time; }
    public double meanBusy(double time) { return busyIntegral / time; }
    public double meanBlocked(double time) { return blockedIntegral / time; }
    public double busyTime() { return busyIntegral; }
    public double meanWait() {
        int accepted = arrivals - failures;
        return accepted > 0 ? queueIntegral / accepted : 0.0;
    }
    public double failureProbability() { return arrivals > 0 ? (double) failures / arrivals : 0.0; }

    @Override
    public void printResult(double time) {
        System.out.printf("%-22s quantity = %d | mean queue = %.4f | mean wait = %.4f | mean busy = %.4f"
                        + " | mean blocked = %.4f | failures = %d (p = %.4f)%n",
                getName(), quantity, meanQueue(time), meanWait(), meanBusy(time), meanBlocked(time),
                failures, failureProbability());
    }
}
