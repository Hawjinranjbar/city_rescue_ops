package map;

import util.Position;

import java.util.ArrayList;
import java.util.List;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس نقشهٔ اصلی شهر رو نگه می‌داره
// شامل تمام سلول‌ها، ابعاد نقشه، و ابزارهایی برای دسترسی و بررسی
public class CityMap {

    private final int width;
    private final int height;
    private final Cell[][] grid;

    public CityMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new Cell[height][width];
    }

    // مقداردهی یک سلول در موقعیت مشخص
    public void setCell(int x, int y, Cell cell) {
        if (isValid(x, y)) {
            grid[y][x] = cell;
        }
    }

    // گرفتن سلول در موقعیت مشخص
    public Cell getCell(int x, int y) {
        if (isValid(x, y)) {
            return grid[y][x];
        }
        return null;
    }

    public Cell getCell(Position pos) {
        return getCell(pos.getX(), pos.getY());
    }

    // بررسی اینکه مختصات در محدوده نقشه هست یا نه
    public boolean isValid(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    // گرفتن همسایه‌های قابل عبور از یک موقعیت
    public List<Position> getWalkableNeighbors(Position pos) {
        List<Position> neighbors = new ArrayList<>();
        int x = pos.getX();
        int y = pos.getY();

        int[][] deltas = {{0,1}, {1,0}, {0,-1}, {-1,0}}; // بالا، راست، پایین، چپ

        for (int[] d : deltas) {
            int nx = x + d[0];
            int ny = y + d[1];
            if (isValid(nx, ny)) {
                Cell neighbor = getCell(nx, ny);
                if (neighbor != null && neighbor.isWalkable()) {
                    neighbors.add(neighbor.getPosition());
                }
            }
        }

        return neighbors;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
