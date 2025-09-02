package map;

import util.Position;
import util.CollisionMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * لایه‌ی دامنه‌ی نقشه‌ی شهر (Tile-based).
 * - پشتیبانی از CollisionMap پیش‌فرض و پروفایل‌ها
 * - پشتیبانی از لایه‌های دودویی از TMX (RoadMask, HospitalMask)
 * - ابزارهای کمکی حرکت/بررسی
 */
public class CityMap {

    private final int width;
    private final int height;
    private final int tileWidth;
    private final int tileHeight;
    private final Cell[][] grid; // grid[y][x]

    // --- برخورد: پیش‌فرض + پروفایل‌ها ---
    private CollisionMap collisionMap; // سازگاری عقب‌رو
    private final Map<String, CollisionMap> collisionProfiles = new HashMap<String, CollisionMap>();

    // --- خصوصیات تایل‌ها ---
    private final Map<Integer, Map<String, String>> tileProps = new HashMap<Integer, Map<String, String>>();

    // --- لایه‌های دودویی (از TMX) مثل RoadMask / HospitalMask ---
    private final Map<String, boolean[][]> binaryLayers = new HashMap<String, boolean[][]>();

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
    public void setCell(int x, int y, Cell cell) {
        if (!isValid(x, y)) return;
        grid[y][x] = cell;
        if (cell != null && cell.getPosition() != null) {
            cell.getPosition().setX(x);
            cell.getPosition().setY(y);
        }
    }

    public Cell getCell(int x, int y) {
        if (!isValid(x, y)) return null;
        return grid[y][x];
    }

    public Cell getCell(Position pos) {
        if (pos == null) return null;
        return getCell(pos.getX(), pos.getY());
    }

    public void registerTileProperties(int gid, Map<String, String> props) {
        if (props == null) return;
        tileProps.putIfAbsent(gid, new HashMap<String, String>(props));
    }

    public int getTileId(int x, int y) {
        Cell c = getCell(x, y);
        return c != null ? c.getTileId() : -1;
    }

    public String getTileProperty(int gid, String key) {
        Map<String, String> p = tileProps.get(gid);
        if (p == null) return null;
        return p.get(key);
    }

    public boolean setOccupied(int x, int y, boolean occupied) {
        if (!isValid(x, y)) return false;
        Cell c = grid[y][x];
        if (c == null) return false;
        c.setOccupied(occupied);
        return true;
    }

    // --- محدوده ---
    public boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    // --- عبورپذیری (پروفایل پیش‌فرض) ---
    public boolean isWalkable(int x, int y) {
        if (!isValid(x, y)) return false;
        Cell c = grid[y][x];
        if (c != null && c.isOccupied()) return false;

        if (collisionMap != null) {
            return collisionMap.isWalkable(x, y);
        }
        return c != null && c.isWalkable();
    }

    public boolean isWalkable(int x, int y, CollisionMap cm) {
        if (!isValid(x, y)) return false;
        Cell c = grid[y][x];
        if (c != null && c.isOccupied()) return false;
        if (cm != null) return cm.isWalkable(x, y);
        return c != null && c.isWalkable();
    }

    public boolean isWalkableFor(int x, int y, String profileName) {
        return isWalkable(x, y, collisionProfiles.get(profileName));
    }

    public boolean isWalkablePixel(int px, int py) {
        int tx = pixelToTileX(px);
        int ty = pixelToTileY(py);
        return isWalkable(tx, ty);
    }

    // --- همسایه‌ها ---
    public List<Position> getWalkableNeighbors(Position pos) {
        List<Position> neighbors = new ArrayList<Position>();
        if (pos == null) return neighbors;

        int x = pos.getX();
        int y = pos.getY();
        int[][] deltas = { {0, 1}, {1, 0}, {0, -1}, {-1, 0} };

        for (int i = 0; i < deltas.length; i++) {
            int nx = x + deltas[i][0];
            int ny = y + deltas[i][1];
            if (isWalkable(nx, ny)) {
                neighbors.add(new Position(nx, ny));
            }
        }
        return neighbors;
    }

    public List<Position> getWalkableNeighbors(Position pos, String profileName) {
        return getWalkableNeighbors(pos, collisionProfiles.get(profileName));
    }

    public List<Position> getWalkableNeighbors(Position pos, CollisionMap cm) {
        List<Position> neighbors = new ArrayList<Position>();
        if (pos == null) return neighbors;

        int x = pos.getX();
        int y = pos.getY();
        int[][] deltas = { {0, 1}, {1, 0}, {0, -1}, {-1, 0} };

        for (int i = 0; i < deltas.length; i++) {
            int nx = x + deltas[i][0];
            int ny = y + deltas[i][1];
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

    // --- CollisionMap (پیش‌فرض + پروفایل‌ها) ---
    public void setCollisionMap(CollisionMap cm) { this.collisionMap = cm; }
    public CollisionMap getCollisionMap() { return collisionMap; }

    public void setCollisionProfile(String name, CollisionMap cm) {
        if (name == null) return;
        if (cm == null) collisionProfiles.remove(name);
        else collisionProfiles.put(name, cm);
    }
    public CollisionMap getCollisionProfile(String name) {
        return collisionProfiles.get(name);
    }

    // --- لایه‌های دودویی TMX ---
    public void setBinaryLayer(String name, boolean[][] grid) {
        if (name == null) return;
        if (grid == null) { binaryLayers.remove(name); return; }
        binaryLayers.put(name, grid);
    }

    /** فقط برای دسترسی عمومی (مثلاً در لودر). */
    public Object getBinaryLayer(String name) {
        return binaryLayers.get(name);
    }

    public void setRoadMaskFromInts(int[][] ints) {
        setBinaryLayer("RoadMask", intsToBool(ints));
    }

    public void setHospitalMaskFromInts(int[][] ints) {
        setBinaryLayer("HospitalMask", intsToBool(ints));
    }

    /** true اگر (x,y) طبق RoadMask جاده باشد؛ در نبود ماسک، به نوع سلول فالبک می‌کند. */
    public boolean isRoad(int x, int y) {
        if (!isValid(x, y)) return false;
        boolean[][] m = binaryLayers.get("RoadMask");
        if (m != null) return m[y][x];
        Cell c = getCell(x, y);
        return c != null && c.getType() == Cell.Type.ROAD;
    }

    /** true اگر (x,y) در HospitalMask علامت خورده باشد. */
    public boolean isHospitalMask(int x, int y) {
        if (!isValid(x, y)) return false;
        boolean[][] m = binaryLayers.get("HospitalMask");
        return (m != null) && m[y][x];
    }

    private static boolean[][] intsToBool(int[][] a) {
        if (a == null) return null;
        boolean[][] b = new boolean[a.length][];
        for (int y = 0; y < a.length; y++) {
            b[y] = new boolean[a[y].length];
            for (int x = 0; x < a[y].length; x++) b[y][x] = (a[y][x] != 0);
        }
        return b;
    }

    // --- مدیریت نقشه ---
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
