package map;

import util.Position;
import util.CollisionMap;

import java.util.ArrayList;
import java.util.List;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نگه‌دارنده‌ی شبکه‌ی شهر (Tile-based) + ابزارهای کمکی حرکت/بررسی.
 * - مختصات در این کلاس «تایل‌محور» است مگر جایی که خلافش ذکر شود (Pixel).
 */
public class CityMap {

    private final int width;       // تعداد تایل در محور X
    private final int height;      // تعداد تایل در محور Y
    private final int tileWidth;   // عرض هر تایل (پیکسل)
    private final int tileHeight;  // ارتفاع هر تایل (پیکسل)
    private final Cell[][] grid;   // grid[y][x]
    private CollisionMap collisionMap;   // نگاشت برخورد (اختیاری)

    // --- سازنده‌ها ---
    public CityMap(int width, int height) {
        this(width, height, 32, 32);
    }

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

    // --- عبورپذیری ---

    /** آیا تایل (x,y) قابل عبور و غیر اشغال است؟ */
    public boolean isWalkable(int x, int y) {
        if (!isValid(x, y)) return false;

        // اگر CollisionMap موجود باشد، walkable بودن از آن خوانده می‌شود
        if (collisionMap != null && !collisionMap.isWalkable(x, y)) {
            return false;
        }

        Cell c = grid[y][x];
        return c != null && !c.isOccupied() && (collisionMap != null || c.isWalkable());
    }

    /** آیا مختصات پیکسلی (px,py) روی تایلِ قابل عبور و غیر اشغال می‌افتد؟ */
    public boolean isWalkablePixel(int px, int py) {
        int tx = pixelToTileX(px);
        int ty = pixelToTileY(py);
        return isWalkable(tx, ty);
    }

    // --- همسایه‌ها ---

    /**
     * برگرداندن همسایه‌های ۴جهته که هم «walkable» باشند و هم «occupied = false».
     * خروجی: لیست Position به واحد تایل.
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

    // --- نگاشت پیکسل <-> تایل ---

    public int pixelToTileX(int px) { return px / tileWidth; }
    public int pixelToTileY(int py) { return py / tileHeight; }
    public int tileToPixelX(int tx) { return tx * tileWidth; }
    public int tileToPixelY(int ty) { return ty * tileHeight; }

    // --- تزریق/دریافت CollisionMap ---
    public void setCollisionMap(CollisionMap cm) { this.collisionMap = cm; }
    public CollisionMap getCollisionMap() { return collisionMap; }

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
