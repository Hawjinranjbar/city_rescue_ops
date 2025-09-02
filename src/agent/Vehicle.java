package agent;

import map.Cell;
import map.CityMap;
import util.CollisionMap;
import util.MoveGuard;
import util.Position;

import java.util.ArrayList;
import java.util.List;

/**
 * مدل سادهٔ وسیله (آمبولانس) روی شبکهٔ تایل.
 * - پشتیبانی از حرکت مسیر‌محور (path) با گام‌های یک‌خانه‌ای.
 * - در حالت پیش‌فرض فقط روی ROAD حرکت می‌کند (roadOnly=true).
 * - برای اعمال حرکتِ ایمن (اشغال/برخورد/مرز) از MoveGuard.tryMoveToVehicle استفاده کنید.
 */
public class Vehicle {
    private final int id;
    private Position tile;                 // مختصات تایل
    private CollisionMap collisionMap;     // اگر لایهٔ برخورد جدا برای ماشین داری

    // مسیر اختیاری برای حرکت خودکار
    private List<Position> path = new ArrayList<Position>();
    private int pathIndex = 0;

    // فقط روی جاده حرکت کند؟
    private boolean roadOnly = true;

    /* ================= سازنده‌ها ================= */

    public Vehicle(int id, Position start, CollisionMap cm) {
        this.id = id;
        this.tile = (start != null) ? start : new Position(0, 0);
        this.collisionMap = cm;
        this.roadOnly = true; // پیش‌فرض: فقط جاده
    }

    /** سازندهٔ الحاقی: کنترل مستقیم روی roadOnly. */
    public Vehicle(int id, Position start, CollisionMap cm, boolean roadOnly) {
        this.id = id;
        this.tile = (start != null) ? start : new Position(0, 0);
        this.collisionMap = cm;
        this.roadOnly = roadOnly;
    }

    /* ================= دسترسی‌ها ================= */

    public int getId() { return id; }
    public Position getTile() { return tile; }
    public void setTile(Position p) { if (p != null) this.tile = p; }

    public CollisionMap getCollisionMap() { return collisionMap; }
    public void setCollisionMap(CollisionMap cm) { this.collisionMap = cm; }

    public boolean isRoadOnly() { return roadOnly; }
    public void setRoadOnly(boolean roadOnly) { this.roadOnly = roadOnly; }

    /* ============== جابه‌جایی‌های ابتدایی (بدون گارد) ============== */

    /** جابه‌جایی نسبی یک خانه‌ای (dx,dy) – بدون چک برخورد. */
    public void move(int dx, int dy) {
        if (tile == null) tile = new Position(0, 0);
        tile = new Position(tile.getX() + dx, tile.getY() + dy);
    }

    /** انتقال مطلق به یک تایل – بدون چک برخورد. */
    public void moveTo(Position p) {
        if (p != null) this.tile = p;
    }

    /* ================= مسیر خودکار ================= */

    public void setPath(List<Position> newPath) {
        this.path = (newPath != null) ? new ArrayList<Position>(newPath) : new ArrayList<Position>();
        this.pathIndex = 0;
    }

    public boolean hasPath() { return pathIndex < path.size(); }

    public void clearPath() {
        this.path = new ArrayList<Position>();
        this.pathIndex = 0;
    }

    /**
     * نسخهٔ قدیمیِ update: یک گام به سمت مقصد فعلی، بدون چک برخورد.
     * (برای سازگاری نگه داشته شده؛ توصیه می‌شود از {@link #update(CityMap)} استفاده کنی.)
     */
    public void update() {
        if (!hasPath() || tile == null) return;
        Position next = path.get(pathIndex);
        int dx = next.getX() - tile.getX();
        int dy = next.getY() - tile.getY();
        if (dx == 0 && dy == 0) { pathIndex++; return; }
        int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        int sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        move(sx, sy);
        if (tile.getX() == next.getX() && tile.getY() == next.getY()) pathIndex++;
    }

    /**
     * نسخهٔ ایمن: یک گام از مسیر را با رعایت برخورد/اشغال/مرز و قانون roadOnly جلو می‌برد.
     * - از MoveGuard.tryMoveToVehicle استفاده می‌کند.
     */
    public void update(CityMap map) {
        if (map == null || !hasPath() || tile == null) return;

        Position next = path.get(pathIndex);
        if (next == null) { pathIndex++; return; }

        // اگر به همان خانه رسیدیم، مرحلهٔ بعد
        if (tile.getX() == next.getX() && tile.getY() == next.getY()) {
            pathIndex++;
            return;
        }

        // تلاش برای حرکت یک قدم به سمت next
        boolean moved = tryStepToward(map, next);
        if (!moved) {
            // اگر گام مستقیم نشد، تلاش حریصانه‌ی ۴ جهته به سمت هدف
            int dx = next.getX() - tile.getX();
            int dy = next.getY() - tile.getY();

            // محور بزرگ‌تر اول
            if (Math.abs(dx) >= Math.abs(dy)) {
                if (dx > 0 && tryStepToward(map, new Position(tile.getX() + 1, tile.getY()))) moved = true;
                else if (dx < 0 && tryStepToward(map, new Position(tile.getX() - 1, tile.getY()))) moved = true;
                if (!moved) {
                    if (dy > 0) moved = tryStepToward(map, new Position(tile.getX(), tile.getY() + 1));
                    else if (dy < 0) moved = tryStepToward(map, new Position(tile.getX(), tile.getY() - 1));
                }
            } else {
                if (dy > 0 && tryStepToward(map, new Position(tile.getX(), tile.getY() + 1))) moved = true;
                else if (dy < 0 && tryStepToward(map, new Position(tile.getX(), tile.getY() - 1))) moved = true;
                if (!moved) {
                    if (dx > 0) moved = tryStepToward(map, new Position(tile.getX() + 1, tile.getY()));
                    else if (dx < 0) moved = tryStepToward(map, new Position(tile.getX() - 1, tile.getY()));
                }
            }
        }

        // اگر واقعاً به next رسیدیم، ایندکس مسیر را جلو ببر
        if (tile.getX() == next.getX() && tile.getY() == next.getY()) {
            pathIndex++;
        }
    }

    /* ================== هِلپرهای حرکت ایمن ================== */

    /** تلاش برای حرکت به سمت تایل هدف (یک‌خانه) با رعایت قانون «فقط جاده». */
    private boolean tryStepToward(CityMap map, Position target) {
        if (map == null || target == null || tile == null) return false;

        // اگر قوانین ما اجازه‌ی ورود به target را نمی‌دهد، اصلاً تلاش نکن
        if (!canStepTo(map, target.getX(), target.getY())) return false;

        // سپردنِ حرکت به MoveGuard (اشغال/مرز/برخورد)
        return MoveGuard.tryMoveToVehicle(map, collisionMap, this, target.getX(), target.getY());
    }

    /**
     * آیا ورود به مختصات (nx,ny) با قوانین فعلی وسیله مجاز است؟
     * - اگر roadOnly=true ⇒ فقط سلول‌های ROAD (و نه HOSPITAL) مجازند.
     * - اگر roadOnly=false ⇒ از منطق عمومی isWalkable استفاده می‌شود.
     * - در هر دو حالت: برخوردِ CollisionMap (اگر موجود باشد) نیز بررسی می‌شود.
     */
    public boolean canStepTo(CityMap map, int nx, int ny) {
        if (map == null) return false;
        if (!map.isValid(nx, ny)) return false;

        Cell dest = map.getCell(nx, ny);
        if (dest == null) return false;
        if (dest.isHospital()) return false;    // ورود به خود بیمارستان ممنوع
        if (dest.isOccupied()) return false;    // روی خانهٔ اشغال‌شده حرکت نکن

        boolean pass;
        if (roadOnly) {
            pass = isRoadCell(dest);
        } else {
            pass = dest.isWalkable();
        }
        if (!pass) return false;

        // اگر لایهٔ برخورد جدا داریم، آن هم باید اجازه دهد
        if (collisionMap != null && !collisionMap.isWalkable(nx, ny)) return false;

        return true;
    }

    /** تشخیص «جاده بودن» سلول با متد مستقیم یا نام نوع. */
    private boolean isRoadCell(Cell c) {
        if (c == null) return false;
        try {
            // اگر Cell متدی به نام isRoad() داشته باشد
            java.lang.reflect.Method m = c.getClass().getMethod("isRoad");
            Object r = m.invoke(c);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        } catch (Throwable ignored) { }
        // fallback: بر اساس نوع/نام
        try {
            java.lang.reflect.Method m2 = c.getClass().getMethod("getType");
            Object t = m2.invoke(c);
            if (t != null) {
                String name = t.toString();
                if (name != null && name.toUpperCase().indexOf("ROAD") >= 0) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }
}
