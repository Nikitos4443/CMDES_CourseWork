package qnet;

public class Entity {
    private final double createdAt;
    private final int initialType;
    private double enteredAt;

    public Entity(double createdAt) {
        this(createdAt, 0);
    }

    public Entity(double createdAt, int type) {
        this.createdAt = createdAt;
        this.initialType = type;
        this.enteredAt = createdAt;
    }

    public double getCreatedAt() { return createdAt; }
    public int getInitialType() { return initialType; }
    public double getEnteredAt() { return enteredAt; }
    public void setEnteredAt(double enteredAt) { this.enteredAt = enteredAt; }
}
