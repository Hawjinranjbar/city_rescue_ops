package agent;

import map.Cell;
import map.CityMap;
import map.Hospital;
import util.CollisionMap;
import util.MoveGuard;
import util.Position;
import victim.Injured;

import java.util.*;

/**
 * کنترل حرکت عامل‌ها روی شبکه.
 * - از MoveGuard برای اعمال حرکت و occupancy استفاده می‌کند.
 * - collisionMap می‌تواند null باشد (در این صورت از منطق CityMap استفاده می‌شود).
 * - سازگار با کال قدیمی: ctor(map, collisionMap, pathFinder, decisionLogic) و performAction(...)
 */
public class AgentController {

    private final CityMap map;
    private final CollisionMap collisionMap;    // می‌تواند null باشد

    // ارجاعات اختیاری (برای سازگاری با امضای قدیمی RescueCoordinator)
    private final Object pathFinderRef;         // استفاده نمی‌کنیم؛ فقط نگه می‌داریم
    private final Object decisionRef;           // استفاده نمی‌کنیم؛ فقط نگه می‌داریم

    /* === سازنده‌ها === */

    public AgentController(CityMap map, CollisionMap cm) {
        this(map, cm, null, null);
    }

    // سازگار با نسخه‌ی قدیمی (انواع هرچه باشد، این ctor match می‌شود)
    public AgentController(CityMap map, CollisionMap cm, Object pathFinder, Object decisionLogic) {
        this.map = map;
        this.collisionMap = cm;
        this.pathFinderRef = pathFinder;
        this.decisionRef = decisionLogic;
    }

    /* === حرکت روی مسیر === */
    public boolean moveAlongPath(Rescuer rescuer, List<Position> path) {
        if (rescuer == null || path == null || path.isEmpty()) return false;

        Position current = rescuer.getPosition();

        for (Position step : path) {
            if (current != null && current.getX() == step.getX() && current.getY() == step.getY()) {
                continue; // همین خانه
            }
            int dir = determineDirection(current, step);

            boolean ok = MoveGuard.tryMoveTo(
                    map,
                    collisionMap,      // اگر برخورد ویژه نداری: null
                    rescuer,
                    step.getX(),
                    step.getY(),
                    dir
            );
            if (!ok) return false;

            current = step;
        }
        return true;
    }

    /* === اکشن سطح بالا برای هماهنگ‌کننده (سازگار با کال قدیمی) === */
    public void performAction(Rescuer rescuer,
                              List<Injured> candidates,
                              List<Hospital> hospitals) {
        if (rescuer == null || candidates == null || candidates.isEmpty()) return;

        // 1) نزدیک‌ترین مجروح زنده/نجات‌نشده
        Injured target = null;
        int bestD = Integer.MAX_VALUE;
        Position rp = rescuer.getPosition();
        for (Injured inj : candidates) {
            if (inj == null || inj.isDead() || inj.isRescued() || inj.getPosition() == null) continue;
            int d = manhattan(rp, inj.getPosition());
            if (d < bestD) { bestD = d; target = inj; }
        }
        if (target == null) return;

        // 2) مسیر تا مجروح با BFS روی گرافِ walkable
        List<Position> path = bfs(rp, target.getPosition());
        if (path.isEmpty()) return;

        // 3) حرکت
        moveAlongPath(rescuer, path);
        // (اگر خواستی: برداشتن مجروح/رفتن تا بیمارستان را اینجا ادامه بدهی)
    }

    /* === BFS ساده روی همسایه‌های ۴جهته === */
    private List<Position> bfs(Position start, Position goal) {
        List<Position> empty = Collections.emptyList();
        if (start == null || goal == null) return empty;

        int w = map.getWidth(), h = map.getHeight();
        boolean[][] vis = new boolean[h][w];
        Position[][] prev = new Position[h][w];

        ArrayDeque<Position> q = new ArrayDeque<>();
        q.add(start);
        vis[start.getY()][start.getX()] = true;

        while (!q.isEmpty()) {
            Position cur = q.removeFirst();
            if (cur.getX() == goal.getX() && cur.getY() == goal.getY()) break;

            for (Position nb : (collisionMap != null
                    ? map.getWalkableNeighbors(cur, collisionMap)
                    : map.getWalkableNeighbors(cur))) {
                int nx = nb.getX(), ny = nb.getY();
                if (!vis[ny][nx]) {
                    vis[ny][nx] = true;
                    prev[ny][nx] = cur;
                    q.addLast(nb);
                }
            }
        }

        if (!vis[goal.getY()][goal.getX()]) return empty; // قابل دسترس نیست

        ArrayList<Position> path = new ArrayList<>();
        Position cur = goal;
        while (cur != null && !(cur.getX() == start.getX() && cur.getY() == start.getY())) {
            path.add(cur);
            cur = prev[cur.getY()][cur.getX()];
        }
        Collections.reverse(path);
        return path;
    }

    /** 0=DOWN,1=LEFT,2=RIGHT,3=UP */
    private static int determineDirection(Position from, Position to) {
        if (from == null || to == null) return 0;
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dy > 0) return 0;   // DOWN
        if (dx < 0) return 1;   // LEFT
        if (dx > 0) return 2;   // RIGHT
        return 3;               // UP
    }

    private static int manhattan(Position a, Position b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
