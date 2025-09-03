package map;

import util.Position;

/**
 * --------------------
 * لایه: Domain Layer — map
 * --------------------
 * نمایندهٔ یک بیمارستان روی نقشه + ابزارهای کمکی تحویل مجروح:
 * - یافتن نزدیک‌ترین بیمارستان نسبت به یک نقطه
 * - تشخیص برد تحویل (پیش‌فرض: مجاورت یک‌تایل)
 * - انتخاب بهترین تایل مجاور برای تحویل روی جاده
 * بدون استفاده از لامبدا و ریفلکشن.
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
    /** برای سازگاری با CityMap.findNearestHospital(...) */
    public Position getTilePosition() {
        return position;
    }

    /** نام قدیمی‌تر (اگر جایی استفاده شده باشد) */
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

    // -------------------- منطق تحویل / انتخاب تایل مجاور --------------------
    /**
     * آیا می‌توان از «این تایل» تحویل انجام داد؟
     * شرط‌ها:
     *  1) موقعیت در برد تحویل (default: مجاورت یک‌تایل)
     *  2) خودِ تایل بیمارستان نباشد
     *  3) تایل روی جاده باشد (ترجیح حالت آمبولانس/ورود از جاده)
     */
    public boolean canDeliverFrom(Position rescuerPos, CityMap map) {
        if (rescuerPos == null || map == null) return false;
        if (!map.isValid(rescuerPos.getX(), rescuerPos.getY())) return false;

        if (!isWithinDeliveryRange(rescuerPos)) return false;
        if (isSameTile(rescuerPos)) return false;

        // اجازه نده دقیقاً روی خودِ تایل بیمارستان بایستد (تحویل در مجاورت انجام می‌شود)
        if (map.isHospitalMask(rescuerPos.getX(), rescuerPos.getY())) return false;

        // روی جاده باشد
        if (!map.isRoad(rescuerPos.getX(), rescuerPos.getY())) return false;

        // و قابل‌عبور باشد (occupied نباشد و CollisionMap اجازه بدهد)
        return map.isWalkable(rescuerPos.getX(), rescuerPos.getY());
    }

    /**
     * چهار تایل مجاور بیمارستان (بالا/پایین/چپ/راست) برای هدف مسیر
     * ترتیب: DOWN, LEFT, RIGHT, UP
     */
    public Position[] getAdjacentTiles() {
        if (position == null) return new Position[0];
        Position[] out = new Position[4];
        out[0] = new Position(position.getX(),     position.getY() + 1); // DOWN
        out[1] = new Position(position.getX() - 1, position.getY());     // LEFT
        out[2] = new Position(position.getX() + 1, position.getY());     // RIGHT
        out[3] = new Position(position.getX(),     position.getY() - 1); // UP
        return out;
    }

    /**
     * بهترین تایل برای تحویل: یکی از مجاورها که:
     * - داخل نقشه باشد
     * - روی جاده باشد
     * - walkable باشد (برخورد/اشغال نبودن)
     * اگر چیزی پیدا نشود، null.
     */
    public Position selectBestDeliveryTile(CityMap map) {
        if (map == null || position == null) return null;
        Position[] adj = getAdjacentTiles();
        for (int i = 0; i < adj.length; i++) {
            Position p = adj[i];
            if (!map.isValid(p.getX(), p.getY())) continue;
            if (map.isHospitalMask(p.getX(), p.getY())) continue; // خودِ بیمارستان نباشد
            if (!map.isRoad(p.getX(), p.getY())) continue;
            if (!map.isWalkable(p.getX(), p.getY())) continue;
            return p; // اولین گزینهٔ معتبر
        }
        return null;
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

    // -------------------- برابری/نمایش --------------------
    @Override
    public String toString() {
        return "Hospital at " + (position != null ? position.toString() : "(null)");
    }

    @Override
    public int hashCode() {
        if (position == null) return 0;
        int x = position.getX();
        int y = position.getY();
        int h = 17;
        h = 31 * h + x;
        h = 31 * h + y;
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (!(obj instanceof Hospital)) return false;
        Hospital other = (Hospital) obj;
        if (this.position == null || other.position == null) return this.position == other.position;
        return this.position.getX() == other.position.getX()
                && this.position.getY() == other.position.getY();
    }
}
