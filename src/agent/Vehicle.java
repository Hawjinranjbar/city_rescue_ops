package agent;

import util.CollisionMap;
import util.Position;

import java.util.ArrayList;
import java.util.List;

/** مدل سادهٔ وسیله (آمبولانس) روی شبکهٔ تایل. */
public class Vehicle {
    private final int id;
    private Position tile;                 // مختصات تایل
    private CollisionMap collisionMap;     // اگر لایهٔ برخورد جدا برای ماشین داری

    // مسیر اختیاری برای حرکت خودکار
    private List<Position> path = new ArrayList<>();
    private int pathIndex = 0;

    public Vehicle(int id, Position start, CollisionMap cm) {
        this.id = id;
        this.tile = (start != null) ? start : new Position(0, 0);
        this.collisionMap = cm;
    }

    public int getId() { return id; }
    public Position getTile() { return tile; }
    public void setTile(Position p) { if (p != null) this.tile = p; }
    public CollisionMap getCollisionMap() { return collisionMap; }
    public void setCollisionMap(CollisionMap cm) { this.collisionMap = cm; }

    /** جابه‌جایی نسبی یک خانه‌ای (dx,dy) */
    public void move(int dx, int dy) {
        if (tile == null) tile = new Position(0, 0);
        tile = new Position(tile.getX() + dx, tile.getY() + dy);
    }

    // --- مسیر خودکار (اختیاری) ---
    public void setPath(List<Position> newPath) {
        this.path = (newPath != null) ? new ArrayList<>(newPath) : new ArrayList<>();
        this.pathIndex = 0;
    }
    public boolean hasPath() { return pathIndex < path.size(); }

    /** هر بار یک گام به سمت مقصد فعلی. (بدون چک برخورد؛ بیرون با MoveGuard حرکت بده) */
    public void update() {
        if (!hasPath() || tile == null) return;
        Position next = path.get(pathIndex);
        int dx = next.getX() - tile.getX();
        int dy = next.getY() - tile.getY();
        if (dx == 0 && dy == 0) { pathIndex++; return; }
        int sx = Integer.signum(dx);
        int sy = Integer.signum(dy);
        move(sx, sy);
        if (tile.getX() == next.getX() && tile.getY() == next.getY()) pathIndex++;
    }
}
