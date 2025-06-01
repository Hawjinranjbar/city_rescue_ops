package map;

import util.Position;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس نمایندهٔ یک بیمارستان در نقشه‌ست
// نجات‌دهنده‌ها مجروح‌ها رو به این نقطه می‌رسونن
public class Hospital {

    private final Position position;  // موقعیت بیمارستان روی نقشه

    public Hospital(Position position) {
        this.position = position;
    }

    public Position getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return "Hospital at " + position.toString();
    }
}
