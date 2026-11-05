package adbroadcast;

import qnet.Entity;

public class AdMessage extends Entity {
    private final double size;
    private double aired;
    private boolean immediate;

    public AdMessage(double createdAt, double size) {
        super(createdAt);
        this.size = size;
    }

    void startOnAir(double t, double pauseLeft) {
        aired = Math.min(size, pauseLeft);
        immediate = t == getCreatedAt();
    }

    public double size() { return size; }
    public double aired() { return aired; }
    public boolean immediate() { return immediate; }
    public boolean cut() { return aired < size; }
}
