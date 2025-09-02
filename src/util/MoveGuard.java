package util;

import agent.Rescuer;
import agent.Vehicle;
import map.Cell;
import map.CityMap;

/**
 * MoveGuard: بررسی و اعمال حرکت روی شبکهٔ تایل.
 * جهت‌ها: 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
 */
public final class MoveGuard {

    private MoveGuard() {}

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

        if (!map.isValid(nx, ny)) return false;

        Cell dest = map.getCell(nx, ny);
        if (dest == null || dest.isOccupied()) return false;

        boolean pass = (collisionMap != null)
                ? collisionMap.isWalkable(nx, ny)
                : map.isWalkable(nx, ny);
        if (!pass) return false;

        // آزاد کردن قبلی
        map.setOccupied(cur.getX(), cur.getY(), false);

        // جابه‌جایی + انیمیشن
        safeMove(rescuer, nx, ny, dir);

        // اشغال جدید
        map.setOccupied(nx, ny, true);
        return true;
    }

    /** حرکت Vehicle با قید برخورد/اشغال. */
    public static boolean tryMoveToVehicle(CityMap map,
                                           CollisionMap vehicleCM, // می‌تواند null باشد
                                           Vehicle vehicle,
                                           int nx, int ny) {
        if (map == null || vehicle == null) return false;
        Position cur = vehicle.getTile();
        if (cur == null) return false;

        if (!map.isValid(nx, ny)) return false;
        Cell dest = map.getCell(nx, ny);
        if (dest == null || dest.isOccupied()) return false;

        boolean pass = (vehicleCM != null) ? vehicleCM.isWalkable(nx, ny) : map.isWalkable(nx, ny);
        if (!pass) return false;

        // آزاد کردن قبلی
        map.setOccupied(cur.getX(), cur.getY(), false);

        // حرکت
        vehicle.move(nx - cur.getX(), ny - cur.getY());

        // اشغال جدید
        Position after = vehicle.getTile();
        if (after != null) map.setOccupied(after.getX(), after.getY(), true);

        return true;
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
        } catch (Throwable ignored) {}
    }
    private static void safeSetDir(Rescuer r, int dir) {
        try { r.getClass().getMethod("setDirection", int.class).invoke(r, dir); }
        catch (Throwable ignored) {}
    }
    private static void safeNext(Rescuer r) {
        try { r.getClass().getMethod("nextFrame").invoke(r); }
        catch (Throwable ignored) {}
    }
}
