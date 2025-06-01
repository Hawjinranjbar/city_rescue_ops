package map;

import util.Position;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس نمایندهٔ یک خانه (tile) روی نقشه‌ست
// می‌تونه جاده، مانع، بیمارستان، یا فضای آزاد باشه
public class Cell {

    public enum Type {
        ROAD, RUBBLE, HOSPITAL
    }

    private final Position position; // موقعیت این سلول
    private final Type type;         // نوع سلول (جاده، مانع، بیمارستان)
    private boolean occupied;        // آیا کسی روش هست یا نه (مثلاً نجات‌دهنده)

    public Cell(Position position, Type type) {
        this.position = position;
        this.type = type;
        this.occupied = false;
    }

    public Position getPosition() {
        return position;
    }

    public Type getType() {
        return type;
    }

    public boolean isWalkable() {
        return type == Type.ROAD || type == Type.HOSPITAL;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }
}
