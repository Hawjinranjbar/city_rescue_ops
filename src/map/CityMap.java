package map;

import util.Position;
import util.CollisionMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نگه‌دارنده‌ی شبکه‌ی شهر (Tile-based) + ابزارهای کمکی حرکت/بررسی.
 * - مختصات در این کلاس «تایل‌محور» است مگر جایی که خلافش ذکر شود (Pixel).
 *
 * تغییرات مهم:
 *  1) اگر CollisionMap ست شده باشد، نتیجه‌ی عبور/عدم‌عبور «فقط» بر اساس آن تعیین می‌شود
 *     (وضعیت قبلی که روی نوع سلول fallback می‌کرد ممکن بود منجر به عبور ناخواسته شود).
 *  2) پشتیبانی از چند پروفایل برخورد (vehicle, rescuer, ...) از طریق collisionProfiles.
 *     متدهای کمکی: setCollisionProfile/getCollisionProfile/isWalkableFor/getWalkableNeighbors(..., profile)
 *  3) سازگاری کامل با API قدیمی: setCollisionMap/getCollisionMap/isWalkable(...) همچنان کار می‌کند.
 */
public class CityMap {

    private final int width;       // تعداد تایل در محور X
    private final int height;      // تعداد تایل در محور Y
    private final int tileWidth;   // عرض هر تایل (پیکسل)
    private final int tileHeight;  // ارتفاع هر تایل (پیکسل)
    private final Cell[][] grid;   // grid[y][x]

    // --- برخورد: پیش‌فرض + پروفایل‌ها ---
    private CollisionMap collisionMap;                                // پروفایل پیش‌فرض (سازگاری عقب‌رو)
    private final Map<String, CollisionMap> collisionProfiles = new HashMap<>(); // نام → نقشه برخورد

    // خصوصیات تایل‌ها
    private final Map<Integer, Map<String, String>> tileProps = new HashMap<>();

    // --- سازنده‌ها ---
    public CityMap(int width, int height) { this(width, height, 32, 32); }

    public CityMap(int width, int height, int tileWidth, int tileHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.grid = new Cell[height][width];
    }

    // --- عملیات روی سلول‌ها ---

    /** مقداردهی یک سلول در مختصات تایل. */
    public void setCell(int x, int y, Cell cell) {
        if (!isValid(x, y)) return;
        grid[y][x] = cell;

        // هماهنگ‌سازی مختصات خود سلول (اگر null نیست)
        if (cell != null && cell.getPosition() != null) {
            cell.getPosition().setX(x);
            cell.getPosition().setY(y);
        }
    }

    /** گرفتن سلول در مختصات تایل. */
    public Cell getCell(int x, int y) {
        if (!isValid(x, y)) return null;
        return grid[y][x];
    }

    public Cell getCell(Position pos) {
        if (pos == null) return null;
        return getCell(pos.getX(), pos.getY());
    }

    /** ثبت خصوصیات یک تایل بر اساس GID. */
    public void registerTileProperties(int gid, Map<String, String> props) {
        if (props == null) return;
        tileProps.putIfAbsent(gid, new HashMap<>(props));
    }

    /** گرفتن شناسهٔ تایل در مختصات مشخص. */
    public int getTileId(int x, int y) {
        Cell c = getCell(x, y);
        return c != null ? c.getTileId() : -1;
    }

    /** گرفتن property یک تایل با GID و کلید. */
    public String getTileProperty(int gid, String key) {
        Map<String, String> p = tileProps.get(gid);
        if (p == null) return null;
        return p.get(key);
    }

    /** ست‌کردن وضعیت اشغال یک سلول، اگر معتبر باشد. */
    public boolean setOccupied(int x, int y, boolean occupied) {
        if (!isValid(x, y)) return false;
        Cell c = grid[y][x];
        if (c == null) return false;
        c.setOccupied(occupied);
        return true;
    }

    // --- بررسی محدوده ---

    /** معتبر بودن مختصات تایل. */
    public boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    // --- عبورپذیری (پروفایل پیش‌فرض) ---

    /**
     * آیا تایل (x,y) قابل عبور و غیر اشغال است؟
     * اگر CollisionMap پیش‌فرض ست شده باشد، فقط بر اساس آن قضاوت می‌شود.
     */
    public boolean isWalkable(int x, int y) {
        if (!isValid(x, y)) return false;

        Cell c = grid[y][x];
        if (c != null && c.isOccupied()) return false;

        if (collisionMap != null) {
            // قرارداد CollisionMap: 0=عبور، 1=بلاک
            return collisionMap.isWalkable(x, y);
        }

        // fallback: بر اساس نوع سلول (بدون برخورد خارجی)
        return c != null && c.isWalkable();
    }

    /** نسخهٔ صریح با CollisionMap ورودی (بدون دست‌زدن به پروفایل پیش‌فرض). */
    public boolean isWalkable(int x, int y, CollisionMap cm) {
        if (!isValid(x, y)) return false;
        Cell c = grid[y][x];
        if (c != null && c.isOccupied()) return false;
        if (cm != null) return cm.isWalkable(x, y);
        return c != null && c.isWalkable();
    }

    /** بررسی بر اساس نام پروفایل (مثلاً "vehicle" یا "rescuer"). */
    public boolean isWalkableFor(int x, int y, String profileName) {
        return isWalkable(x, y, collisionProfiles.get(profileName));
    }

    /** آیا مختصات پیکسلی (px,py) روی تایلِ قابل عبور و غیر اشغال می‌افتد؟ */
    public boolean isWalkablePixel(int px, int py) {
        int tx = pixelToTileX(px);
        int ty = pixelToTileY(py);
        return isWalkable(tx, ty);
    }

    // --- همسایه‌ها ---

    /**
     * همسایه‌های ۴جهته که هم «walkable» باشند و هم «occupied=false».
     * از پروفایل پیش‌فرض استفاده می‌کند.
     */
    public List<Position> getWalkableNeighbors(Position pos) {
        List<Position> neighbors = new ArrayList<>();
        if (pos == null) return neighbors;

        int x = pos.getX();
        int y = pos.getY();

        int[][] deltas = { {0, 1}, {1, 0}, {0, -1}, {-1, 0} };

        for (int[] d : deltas) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isWalkable(nx, ny)) {
                neighbors.add(new Position(nx, ny));
            }
        }
        return neighbors;
    }

    /** نسخهٔ پروفایل‌دار (مثلاً برای ماشین: profileName="vehicle"). */
    public List<Position> getWalkableNeighbors(Position pos, String profileName) {
        return getWalkableNeighbors(pos, collisionProfiles.get(profileName));
    }

    /** نسخهٔ CollisionMap-محور. */
    public List<Position> getWalkableNeighbors(Position pos, CollisionMap cm) {
        List<Position> neighbors = new ArrayList<>();
        if (pos == null) return neighbors;

        int x = pos.getX();
        int y = pos.getY();

        int[][] deltas = { {0, 1}, {1, 0}, {0, -1}, {-1, 0} };

        for (int[] d : deltas) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isWalkable(nx, ny, cm)) {
                neighbors.add(new Position(nx, ny));
            }
        }
        return neighbors;
    }

    // --- نگاشت پیکسل <-> تایل ---

    public int pixelToTileX(int px) { return px / tileWidth; }
    public int pixelToTileY(int py) { return py / tileHeight; }
    public int tileToPixelX(int tx) { return tx * tileWidth; }
    public int tileToPixelY(int ty) { return ty * tileHeight; }

    // --- تزریق/دریافت CollisionMap (پیش‌فرض + پروفایل‌ها) ---

    /** پروفایل پیش‌فرض (سازگاری عقب‌رو). */
    public void setCollisionMap(CollisionMap cm) { this.collisionMap = cm; }
    public CollisionMap getCollisionMap() { return collisionMap; }

    /** پروفایل نام‌دار (مثلاً "vehicle", "rescuer"). */
    public void setCollisionProfile(String name, CollisionMap cm) {
        if (name == null) return;
        if (cm == null) collisionProfiles.remove(name);
        else collisionProfiles.put(name, cm);
    }

    public CollisionMap getCollisionProfile(String name) {
        return collisionProfiles.get(name);
    }

    // --- مدیریت نقشه ---

    /** پاک‌سازی کل گرید. */
    public void clear() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                grid[y][x] = null;
            }
        }
    }

    // --- Getter ها ---
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTileWidth() { return tileWidth; }
    public int getTileHeight() { return tileHeight; }
}
