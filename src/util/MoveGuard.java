// util/MoveGuard.java
package util;

import agent.Rescuer;
import map.Cell;
import map.CityMap;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * درگاه واحدِ حرکت ایمن روی نقشه (تایل‌محور).
 * - قبل از جابه‌جایی: بررسی walkable و occupied
 * - آزاد/اشغال‌کردن سلول‌ها
 * - ست جهت و جلو بردن انیمیشن
 */
public final class MoveGuard {

    private MoveGuard() { }

    /** حرکت به مقصد مطلق (nx,ny) با جهت dir و بررسی CollisionMap. */
    public static boolean tryMoveTo(CityMap map, CollisionMap collision, Rescuer r, int nx, int ny, int dir) {
        if (map == null || collision == null || r == null || r.getPosition() == null) return false;
        if (!map.isValid(nx, ny) || !collision.isWalkable(nx, ny)) return false;

        // اگر مقصد همان جای فعلی است: فقط جهت/فریم را آپدیت کن
        if (r.getPosition().getX() == nx && r.getPosition().getY() == ny) {
            r.setDirection(dir);
            r.nextFrame();
            return true;
        }

        final Cell dest = map.getCell(nx, ny);


        if (dest == null || dest.isOccupied()) return false;

        if (dest == null) return false;

        // فقط اجازه‌ی حرکت روی سلول‌های جاده یا بیمارستان که خالی باشند
        Cell.Type type = dest.getType();
        if ((type != Cell.Type.ROAD && type != Cell.Type.HOSPITAL) || dest.isOccupied()) {
            return false;
        }

        // فقط اجازه‌ی حرکت روی سلول‌های قابل عبور و خالی
        if (!dest.isWalkable() || dest.isOccupied()) return false;



        // فقط اجازه‌ی حرکت روی جاده‌های خالی
        if (dest.getType() != Cell.Type.ROAD || dest.isOccupied()) return false;


        // آزاد کردن سلول فعلی (اگر معتبر بود)
        int cx = r.getPosition().getX();
        int cy = r.getPosition().getY();
        if (map.isValid(cx, cy)) {
            Cell cur = map.getCell(cx, cy);
            if (cur != null) cur.setOccupied(false);
        }

        // اعمال حرکت بدون ساخت Position جدید
        r.setDirection(dir);
        r.setPositionXY(nx, ny);

        // اشغال مقصد
        dest.setOccupied(true);

        // جلو بردن انیمیشن
        r.nextFrame();

        return true;
    }

    /** حرکت نسبی بر اساس دلتا (dx,dy) با جهت dir. */
    public static boolean tryMoveDelta(CityMap map, CollisionMap collision, Rescuer r, int dx, int dy, int dir) {
        if (map == null || collision == null || r == null || r.getPosition() == null) return false;
        final int nx = r.getPosition().getX() + dx;
        final int ny = r.getPosition().getY() + dy;
        return tryMoveTo(map, collision, r, nx, ny, dir);
    }
}
