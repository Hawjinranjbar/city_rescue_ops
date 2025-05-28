package util;

import java.util.Objects;

// --------------------
// لایه: Utility Layer
// --------------------
// این کلاس برای نگه داشتن مختصات موقعیت توی نقشه‌ست
// مثلاً موقعیت مجروح، نجات‌دهنده یا بیمارستان
public class Position {

    private int x;
    private int y;

    // سازنده اصلی، موقعیت رو با x و y می‌سازه
    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // کپی‌کننده: یه پوزیشن جدید با مقادیر همون قبلی می‌سازه
    public Position(Position other) {
        this.x = other.x;
        this.y = other.y;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    // فاصله من‌هتن (Manhattan Distance) بین این موقعیت و یه موقعیت دیگه
    public int distanceTo(Position other) {
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    // مقایسه اینکه دو موقعیت برابرن یا نه (برای مقایسه یا استفاده تو Map و Set)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position other = (Position) o;
        return this.x == other.x && this.y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
