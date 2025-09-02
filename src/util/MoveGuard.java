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
 * - Rescuer عادی: dest.isWalkable() + CollisionMap
 * - حالت آمبولانس/خودرو (roadOnly): فقط اگر CityMap.isRoad(x,y) باشد (فالبک: dest.isRoad/type)
 * - Hospital ممنوع؛ occupied هم ممنوع.
 */
public final class MoveGuard {

    private MoveGuard() { }

    public static boolean tryMoveTo(CityMap map,
                                    CollisionMap collisionMap,
                                    Rescuer rescuer,
                                    int nx, int ny,
                                    int dir) {
        if (map == null || rescuer == null) return false;
        Position cur = rescuer.getPosition();
        if (cur == null) return false;

        safeSetDir(rescuer, dir);
        if (!map.isValid(nx, ny)) return false;

        Cell dest = map.getCell(nx, ny);
        if (dest == null) return false;
        if (dest.isOccupied()) return false;
        if (dest.isHospital()) return false;

        boolean roadOnly = false;
        try { roadOnly = rescuer.isRoadOnlyMode(); } catch (Throwable ignored) { }

        boolean passByType = roadOnly ? isRoadAt(map, nx, ny, dest) : dest.isWalkable();
        boolean passByCollision = (collisionMap == null) || collisionMap.isWalkable(nx, ny);
        if (!passByType || !passByCollision) return false;

        map.setOccupied(cur.getX(), cur.getY(), false);
        safeMove(rescuer, nx, ny, dir);
        map.setOccupied(nx, ny, true);
        return true;
    }

    public static boolean tryMoveToVehicle(CityMap map,
                                           CollisionMap vehicleCM,
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

        boolean passByType = isRoadAt(map, nx, ny, dest); // ← فقط روی RoadMask/road
        boolean passByCollision = (vehicleCM == null) || vehicleCM.isWalkable(nx, ny);
        if (!passByType || !passByCollision) return false;

        map.setOccupied(cur.getX(), cur.getY(), false);
        vehicle.move(nx - cur.getX(), ny - cur.getY());
        Position after = vehicle.getTile();
        if (after != null) map.setOccupied(after.getX(), after.getY(), true);
        return true;
    }

    // --- تشخیص ROAD: اولویت با CityMap.isRoad(x,y) ---
    private static boolean isRoadAt(CityMap map, int x, int y, Cell dest) {
        try { if (map.isRoad(x, y)) return true; } catch (Throwable ignored) {}
        if (dest != null) {
            try {
                java.lang.reflect.Method m = dest.getClass().getMethod("isRoad");
                Object r = m.invoke(dest);
                if (r instanceof Boolean && ((Boolean) r).booleanValue()) return true;
            } catch (Throwable ignored) {}
            try {
                java.lang.reflect.Method m2 = dest.getClass().getMethod("getType");
                Object t = m2.invoke(dest);
                if (t != null) {
                    String name = t.toString();
                    if (name != null && name.toUpperCase().indexOf("ROAD") >= 0) return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    // --- helperهای ایمن ---
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
