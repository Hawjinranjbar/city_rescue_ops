package map;

import util.Position;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نمایندهٔ یک بیمارستان روی نقشه + ابزارهای کمکی برای تحویل مجروح:
 * - یافتن نزدیک‌ترین بیمارستان نسبت به یک نقطه
 * - تشخیص مجاورت یک‌تایل (برای تحویل؛ چون ورود به خود کاشی بیمارستان بلاک است)
 * - گرفتن چهار کاشی مجاور (بالا/پایین/چپ/راست) برای هدف مسیر
 */
public class Hospital {

    /** موقعیت بیمارستان روی شبکهٔ تایل */
    private final Position position;

    /** برد تحویل: به‌صورت پیش‌فرض مجاورت یک‌تایل (Manhattan distance = 1) */
    private int deliveryRange = 1;

    public Hospital(Position position) {
        this.position = position;
    }

    // -------------------- دسترسی‌ها --------------------
    public Position getPosition() {
        return position;
    }

    /** برد تحویل فعلی (تعداد تایل) */
    public int getDeliveryRange() {
        return deliveryRange;
    }

    /** تغییر برد تحویل (در صورت نیاز) */
    public void setDeliveryRange(int deliveryRange) {
        if (deliveryRange < 0) deliveryRange = 0;
        this.deliveryRange = deliveryRange;
    }

    // -------------------- منطق مسافت/مجاورت --------------------
    /** فاصلهٔ منهتنی تا یک نقطه (|dx|+|dy|) */
    public int manhattanDistanceTo(Position p) {
        if (p == null || position == null) return Integer.MAX_VALUE;
        int dx = Math.abs(position.getX() - p.getX());
        int dy = Math.abs(position.getY() - p.getY());
        return dx + dy;
    }

    /** آیا دقیقاً روی همان تایل هستیم؟ (معمولاً false چون Hospital بلاک است) */
    public boolean isSameTile(Position p) {
        if (p == null || position == null) return false;
        return position.getX() == p.getX() && position.getY() == p.getY();
    }

    /** آیا در مجاورت یک‌تایل هستیم؟ (بالا/پایین/چپ/راست) */
    public boolean isAdjacent(Position p) {
        return manhattanDistanceTo(p) == 1;
    }

    /** آیا در برد تحویل قرار داریم؟ (به‌صورت پیش‌فرض ≤ 1) */
    public boolean isWithinDeliveryRange(Position p) {
        return manhattanDistanceTo(p) <= deliveryRange;
    }

    /**
     * آیا می‌توان از «این تایل» تحویل انجام داد؟
     * شرط‌ها:
     *  1) موقعیت در برد تحویل (default: مجاورت یک‌تایل)
     *  2) خود تایل «جاده» باشد (برای حالت آمبولانس که فقط روی جاده حرکت می‌کند)
     *  3) خودِ تایل بیمارستان نباشد (تحویل در مجاورت انجام می‌شود)
     */
    public boolean canDeliverFrom(Position rescuerPos, CityMap map) {
        if (rescuerPos == null || map == null) return false;
        if (!isWithinDeliveryRange(rescuerPos)) return false;
        if (isSameTile(rescuerPos)) return false;

        if (!map.isValid(rescuerPos.getX(), rescuerPos.getY())) return false;
        Cell c = map.getCell(rescuerPos.getX(), rescuerPos.getY());
        if (c == null) return false;
        if (c.isHospital()) return false;

        // ترجیحی: اگر Cell متد isRoad() دارد همان را چک می‌کنیم
        try {
            java.lang.reflect.Method m = c.getClass().getMethod("isRoad");
            Object r = m.invoke(c);
            if (r instanceof Boolean) {
                return ((Boolean) r).booleanValue();
            }
        } catch (Throwable ignored) { }

        // جایگزین: اگر نوع تایل نام "ROAD" دارد
        try {
            java.lang.reflect.Method m2 = c.getClass().getMethod("getType");
            Object t = m2.invoke(c);
            if (t != null) {
                String name = t.toString();
                if (name != null) {
                    name = name.toUpperCase();
                    if (name.indexOf("ROAD") >= 0) return true;
                }
            }
        } catch (Throwable ignored) { }

        return false;
    }

    /** چهار تایل مجاور بیمارستان (بالا/پایین/چپ/راست) برای هدف مسیر */
    public Position[] getAdjacentTiles() {
        if (position == null) return new Position[0];
        Position[] out = new Position[4];
        out[0] = new Position(position.getX(),     position.getY() + 1); // DOWN
        out[1] = new Position(position.getX() - 1, position.getY());     // LEFT
        out[2] = new Position(position.getX() + 1, position.getY());     // RIGHT
        out[3] = new Position(position.getX(),     position.getY() - 1); // UP
        return out;
    }

    // -------------------- ابزار انتخاب بیمارستان --------------------
    /** نزدیک‌ترین بیمارستان نسبت به یک موقعیت را برمی‌گرداند (فاصلهٔ منهتنی). */
    public static Hospital findNearest(java.util.List<Hospital> list, Position from) {
        if (list == null || from == null || list.isEmpty()) return null;
        Hospital best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Hospital h = list.get(i);
            if (h == null) continue;
            int d = h.manhattanDistanceTo(from);
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    @Override
    public String toString() {
        return "Hospital at " + (position != null ? position.toString() : "(null)");
    }
}
