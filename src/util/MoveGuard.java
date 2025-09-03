package util;

import agent.Rescuer;
import agent.Vehicle;
import map.Cell;
import map.CityMap;
import util.CollisionMap;
import util.Position;

/**
 * MoveGuard: بررسی و اعمال حرکت روی شبکهٔ تایل.
 * جهت‌ها: 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
 *
 * - Rescuer (پیاده): آزاد کامل؛ فقط مرز نقشه چک می‌شود. occupied/collision دست نمی‌زنیم.
 * - Vehicle/آمبولانس: فقط روی «جاده» (ترجیحاً از RoadMask/CityMap.isRoad) + ممنوعیت ورود به خودِ بیمارستان
 *   و رعایت occupied. CollisionMap اگر داده شود اعمال می‌شود.
 */
public final class MoveGuard {

    private MoveGuard() { }

    /** حرکت Rescuer به (nx,ny). */
    public static boolean tryMoveTo(CityMap map,
                                    CollisionMap collisionMap, // می‌تواند null باشد
                                    Rescuer rescuer,
                                    int nx, int ny,
                                    int dir) {
        if (map == null || rescuer == null) return false;
        Position cur = rescuer.getPosition();
        if (cur == null) return false;

        // جهت را حتی در عدم حرکت هم ست کن
        safeSetDir(rescuer, dir);

        // مرز
        if (!map.isValid(nx, ny)) return false;

        // اگر در حالت آمبولانس نیست → آزاد کامل
        boolean roadOnly = false;
        try { roadOnly = rescuer.isRoadOnlyMode(); } catch (Throwable ignored) {}
        if (!roadOnly) {
            safeMove(rescuer, nx, ny, dir);
            return true;
        }

        // از اینجا به بعد: محدودیت‌های آمبولانس (مثل قبل ولی با تشخیص جاده از RoadMask/CityMap)
        Cell dest = map.getCell(nx, ny);
        if (dest == null) return false;
        if (dest.isOccupied()) return false;
        if (dest.isHospital()) return false;

        boolean onRoad = isRoad(map, nx, ny, dest);
        if (!onRoad) return false;

        boolean passByCollision = (collisionMap == null) || collisionMap.isWalkable(nx, ny);
        if (!passByCollision) return false;

        // آزادسازی قبلی و ثبت اشغال مقصد
        map.setOccupied(cur.getX(), cur.getY(), false);
        safeMove(rescuer, nx, ny, dir);
        map.setOccupied(nx, ny, true);
        return true;
    }

    /** حرکت Vehicle (آمبولانس مستقل) با قید برخورد/اشغال. */
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

        // تشخیص جاده: اولویت با CityMap.isRoad/RoadMask، بعد نوع سلول
        boolean onRoad = isRoad(map, nx, ny, dest);
        if (!onRoad) return false;

        boolean passByCollision = (vehicleCM == null) || vehicleCM.isWalkable(nx, ny);
        if (!passByCollision) return false;

        // آزاد کردن قبلی
        map.setOccupied(cur.getX(), cur.getY(), false);

        // حرکت
        vehicle.move(nx - cur.getX(), ny - cur.getY());

        // اشغال جدید
        Position after = vehicle.getTile();
        if (after != null) {
            map.setOccupied(after.getX(), after.getY(), true);
        }

        return true;
    }

    // ----------------- تشخیص «جاده بودن» با اولویت RoadMask/CityMap -----------------
    private static boolean isRoad(CityMap map, int x, int y, Cell dest) {
        // 1) اگر CityMap.isRoad(int,int) وجود دارد
        try {
            java.lang.reflect.Method m = map.getClass().getMethod("isRoad", int.class, int.class);
            Object r = m.invoke(map, x, y);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        } catch (Throwable ignored) { }

        // 2) اگر CityMap.getBinaryLayer("RoadMask") داریم (boolean[][] یا int[][])
        try {
            java.lang.reflect.Method m = map.getClass().getMethod("getBinaryLayer", String.class);
            Object layer = m.invoke(map, "RoadMask");
            if (layer instanceof boolean[][]) {
                boolean[][] mask = (boolean[][]) layer;
                if (y >= 0 && y < mask.length && x >= 0 && x < mask[y].length) {
                    if (mask[y][x]) return true;
                }
            } else if (layer instanceof int[][]) {
                int[][] mask = (int[][]) layer;
                if (y >= 0 && y < mask.length && x >= 0 && x < mask[y].length) {
                    if (mask[y][x] != 0) return true;
                }
            }
        } catch (Throwable ignored) { }

        // 3) اگر خود سلول متد isRoad() دارد
        if (dest != null) {
            try {
                java.lang.reflect.Method m = dest.getClass().getMethod("isRoad");
                Object r = m.invoke(dest);
                if (r instanceof Boolean) return ((Boolean) r).booleanValue();
            } catch (Throwable ignored) { }

            // 4) فالبک: نام نوع سلول شامل "ROAD"
            try {
                java.lang.reflect.Method m2 = dest.getClass().getMethod("getType");
                Object t = m2.invoke(dest);
                if (t != null) {
                    String name = t.toString();
                    if (name != null && name.toUpperCase().contains("ROAD")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) { }
        }

        return false;
    }

    // ----------------- Helperهای ایمن -----------------
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
        try { r.getClass().getMethod("setDirection", int.class).invoke(r, dir); }
        catch (Throwable ignored) { }
    }

    private static void safeNext(Rescuer r) {
        try { r.getClass().getMethod("nextFrame").invoke(r); }
        catch (Throwable ignored) { }
    }
}
