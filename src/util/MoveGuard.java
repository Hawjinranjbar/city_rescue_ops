package util;

import agent.Rescuer;
import agent.Vehicle;
import map.Cell;
import map.CityMap;

/**
 * MoveGuard: بررسی و اعمال حرکت روی شبکهٔ تایل.
 * جهت‌ها: 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
 *
 * سیاست عبور:
 * - حالت عادی (ریسکیور): dest.isWalkable() و CollisionMap باید اجازه بدهند.
 * - حالت آمبولانس (rescuer.isRoadOnlyMode()==true): فقط روی ROAD اجازه دارد
 *   (SIDEWALK/پیاده‌رو و ... ممنوع)، Hospital ممنوع (تحویل در خانه‌های مجاور).
 * - اگر سلول مقصد occupied باشد، حرکت انجام نمی‌شود.
 */
public final class MoveGuard {

    private MoveGuard() { }

    /** تلاش برای حرکت Rescuer به (nx,ny). در صورت موفقیت، occupied مبدأ/مقصد به‌روز می‌شود. */
    public static boolean tryMoveTo(CityMap map,
                                    CollisionMap collisionMap, // می‌تواند null باشد
                                    Rescuer rescuer,
                                    int nx, int ny,
                                    int dir) {
        if (map == null || rescuer == null) return false;
        Position cur = rescuer.getPosition();
        if (cur == null) return false;

        // حتی اگر حرکت نکند، جهت را تنظیم کن
        safeSetDir(rescuer, dir);

        // محدودهٔ نقشه
        if (!map.isValid(nx, ny)) return false;

        // بررسی سلول مقصد
        Cell dest = map.getCell(nx, ny);
        if (dest == null) return false;
        if (dest.isOccupied()) return false;

        // بیمارستان به‌طور صریح بلاک است (تحویل در مجاورت انجام می‌شود)
        if (dest.isHospital()) return false;

        // --- قاعدهٔ راه: اگر آمبولانس هست → فقط ROAD، وگرنه walkable ---
        boolean roadOnly = false;
        try {
            roadOnly = rescuer.isRoadOnlyMode();
        } catch (Throwable ignored) { }

        boolean passByType;
        if (roadOnly) {
            passByType = isRoadCell(dest);   // فقط جاده
        } else {
            passByType = dest.isWalkable();  // جاده/پیاده‌رو و... که walkable هستند
        }

        boolean passByCollision = (collisionMap == null) || collisionMap.isWalkable(nx, ny);
        if (!passByType || !passByCollision) return false;

        // آزاد کردن قبلی
        map.setOccupied(cur.getX(), cur.getY(), false);

        // جابه‌جایی + انیمیشن/فریم
        safeMove(rescuer, nx, ny, dir);

        // اشغال جدید
        map.setOccupied(nx, ny, true);
        return true;
    }

    /**
     * حرکت Vehicle با قید برخورد/اشغال.
     * وسایط نقلیه (از جمله آمبولانس اختصاصی، اگر استفاده شد) به‌صورت پیش‌فرض «فقط ROAD».
     */
    public static boolean tryMoveToVehicle(CityMap map,
                                           CollisionMap vehicleCM, // می‌تواند null باشد
                                           Vehicle vehicle,
                                           int nx, int ny) {
        if (map == null || vehicle == null) return false;
        Position cur = vehicle.getTile();
        if (cur == null) return false;

        if (!map.isValid(nx, ny)) return false;

        Cell dest = map.getCell(nx, ny);
        if (dest == null) return false;
        if (dest.isOccupied()) return false;
        if (dest.isHospital()) return false;


        boolean passByType = dest.isRoad();


        boolean passByType = dest.isRoad();

        boolean passByType = isRoadCell(dest); // وسایل نقلیه: فقط جاده

        boolean passByCollision = (vehicleCM == null) || vehicleCM.isWalkable(nx, ny);
        if (!passByType || !passByCollision) return false;

        // آزاد کردن قبلی
        map.setOccupied(cur.getX(), cur.getY(), false);

        // حرکت
        vehicle.move(nx - cur.getX(), ny - cur.getY());

        // اشغال جدید (پس از حرکت مختصات جدید را دوباره بگیر)
        Position after = vehicle.getTile();
        if (after != null) {
            map.setOccupied(after.getX(), after.getY(), true);
        }

        return true;
    }

    // ---- Helper: تعیین اینکه یک سلول «ROAD» است یا نه (سازگار با انواع پیاده‌سازی Cell) ----
    private static boolean isRoadCell(Cell dest) {
        // 1) اگر متدی به نام isRoad() داشته باشی
        try {
            java.lang.reflect.Method m = dest.getClass().getMethod("isRoad");
            Object r = m.invoke(dest);
            if (r instanceof Boolean) {
                if (((Boolean) r).booleanValue()) return true;
                else return false;
            }
        } catch (Throwable ignored) { }

        // 2) اگر getType() برگردان Enum/String بده و نامش ROAD باشد
        try {
            java.lang.reflect.Method m2 = dest.getClass().getMethod("getType");
            Object t = m2.invoke(dest);
            if (t != null) {
                String name = t.toString();
                if (name != null) {
                    name = name.toUpperCase();
                    if (name.indexOf("ROAD") >= 0) return true;
                }
            }
        } catch (Throwable ignored) { }

        // اگر چیزی تشخیص ندادیم، به‌صورت محافظه‌کارانه false
        return false;
    }

    // ---- هِلپرهای ایمن (سازگاری با نسخه‌های مختلف Rescuer) ----
    private static void safeMove(Rescuer r, int x, int y, int dir) {
        try {
            r.getClass().getMethod("onMoveStep", int.class, int.class, int.class)
                    .invoke(r, x, y, dir);
        } catch (NoSuchMethodException e) {
            r.setPositionXY(x, y);
            safeSetDir(r, dir);
            safeNext(r);
        } catch (Throwable ignored) { }
    }

    private static void safeSetDir(Rescuer r, int dir) {
        try {
            r.getClass().getMethod("setDirection", int.class).invoke(r, dir);
        } catch (Throwable ignored) { }
    }

    private static void safeNext(Rescuer r) {
        try {
            r.getClass().getMethod("nextFrame").invoke(r);
        } catch (Throwable ignored) { }
    }
}
