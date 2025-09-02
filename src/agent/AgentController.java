package agent;

import map.Cell;
import map.CityMap;
import map.Hospital;
import util.CollisionMap;
import util.MoveGuard;
import util.Position;
import util.Logger;
import victim.Injured;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * کنترل حرکت عامل‌ها روی شبکه.
 * - نزدیک شدن به مجروح → ورود به حالت آمبولانس و ضمیمه کردن مجروح
 * - حالت آمبولانس → حرکت فقط روی ROAD به یکی از کاشی‌های مجاور نزدیک‌ترین بیمارستان
 * - رسیدن کنار بیمارستان → deliverVictimAtHospital() (نجات + پاداش 2×t0)
 * - از MoveGuard برای اعمال حرکت و occupancy استفاده می‌شود.
 * - collisionMap می‌تواند null باشد.
 */
public class AgentController {

    private final CityMap map;
    private final CollisionMap collisionMap; // می‌تواند null باشد

    // ارجاعات اختیاری (برای سازگاری با سازندهٔ قدیمی)
    private final Object pathFinderRef;   // نگهداری صرف
    private final Object decisionRef;     // نگهداری صرف

    // Logger اختیاری
    private Logger logger;

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
        this.logger = null;
    }

    /** تزریق لاگر (اختیاری) */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /* === اکشن سطح بالا برای هماهنگ‌کننده === */
    public void performAction(Rescuer rescuer,
                              List<Injured> candidates,
                              List<Hospital> hospitals) {

        if (rescuer == null) return;

        // --- حالت آمبولانس: فقط ROAD → حرکت به سمت مجاورِ نزدیک‌ترین بیمارستان ---
        if (rescuer.isAmbulanceMode()) {
            Hospital h = findNearestHospital(hospitals, rescuer.getPosition());
            if (h == null) return;

            Position goal = pickBestAdjacentRoadTile(h, rescuer.getPosition());
            if (goal == null) return;

            // مسیر: فقط ROAD
            List<Position> path = bfs(rescuer.getPosition(), goal, true);
            if (!path.isEmpty()) {
                moveAlongPath(rescuer, path);
            }

            // اگر کنار بیمارستان هست و روی ROAD ایستاده، تحویل بده
            if (canDeliverFrom(rescuer.getPosition(), h)) {
                Injured v = rescuer.getCarryingVictim();
                if (v != null && logger != null) {
                    try {
                        int reward = 2 * Math.max(0, v.getInitialTimeLimit());
                        // خود deliverVictimAtHospital امتیاز را اضافه می‌کند
                        rescuer.deliverVictimAtHospital();
                        logger.logAmbulanceDeliver(rescuer.getId(), rescuer.getPosition(), v.getId(), reward, controller.ScoreManager.getScore());
                    } catch (Exception ex) {
                        logger.logError("AgentController.performAction/DeliverLog", ex);
                    }
                } else {
                    rescuer.deliverVictimAtHospital();
                }
            }
            return;
        }

        // --- حالت عادی: به سمت مجروح قابل نجات برو ---
        if (candidates == null || candidates.isEmpty()) return;

        Injured target = chooseNearestRescuable(rescuer.getPosition(), candidates);
        if (target == null) return;

        // اگر مجاور است → Pickup و ورود به آمبولانس
        if (rescuer.getPosition() != null && rescuer.getPosition().isAdjacent4(target.getPosition())) {
            rescuer.enterAmbulanceModeWith(target);
            if (logger != null) {
                try {
                    String sev = target.getSeverity() != null ? target.getSeverity().name() : "null";
                    logger.logAmbulancePickup(rescuer.getId(), rescuer.getPosition(), target.getId(), sev, target.getInitialTimeLimit());
                } catch (Exception ex) {
                    logger.logError("AgentController.performAction/PickupLog", ex);
                }
            }
            return;
        }

        // در غیر این صورت، به یکی از همسایه‌های عبوریِ مجروح حرکت کن (خود خانهٔ مجروح معمولاً آوار است)
        Position adj = pickBestAdjacentWalkable(target.getPosition(), rescuer.getPosition());
        if (adj == null) return;

        // مسیر: walkable عادی (SIDEWALK/ROAD و ...)
        List<Position> path = bfs(rescuer.getPosition(), adj, false);
        if (!path.isEmpty()) {
            moveAlongPath(rescuer, path);
        }
    }

    /* === حرکت روی مسیر (گام‌به‌گام با MoveGuard) === */
    public boolean moveAlongPath(Rescuer rescuer, List<Position> path) {
        if (rescuer == null || path == null || path.isEmpty()) return false;

        Position current = rescuer.getPosition();

        for (int i = 0; i < path.size(); i++) {
            Position step = path.get(i);
            if (current != null && current.getX() == step.getX() && current.getY() == step.getY()) {
                continue; // همین خانه
            }
            int dir = determineDirection(current, step);

            boolean ok = MoveGuard.tryMoveTo(
                    map,
                    collisionMap,
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

    /* === انتخاب هدف‌ها === */

    private Injured chooseNearestRescuable(Position from, List<Injured> list) {
        if (list == null || list.isEmpty()) return null;
        Injured best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Injured inj = list.get(i);
            if (inj == null) continue;
            if (inj.isDead() || inj.isRescued() || inj.getPosition() == null) continue;
            int d = manhattan(from, inj.getPosition());
            if (d < bestD) {
                bestD = d;
                best = inj;
            }
        }
        return best;
    }

    private Hospital findNearestHospital(List<Hospital> hospitals, Position from) {
        if (hospitals == null || hospitals.isEmpty() || from == null) return null;
        Hospital best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < hospitals.size(); i++) {
            Hospital h = hospitals.get(i);
            if (h == null) continue;
            int d = manhattan(from, h.getPosition());
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /* === انتخاب «کاشی مجاور» === */

    /** یکی از چهار تایل مجاورِ بیمارستان که ROAD و آزاد است (نزدیک‌ترین به from). */
    private Position pickBestAdjacentRoadTile(Hospital hospital, Position from) {
        if (hospital == null || hospital.getPosition() == null) return null;
        Position hpos = hospital.getPosition();
        Position[] adj = new Position[] {
                new Position(hpos.getX(),     hpos.getY() + 1), // DOWN
                new Position(hpos.getX() - 1, hpos.getY()),     // LEFT
                new Position(hpos.getX() + 1, hpos.getY()),     // RIGHT
                new Position(hpos.getX(),     hpos.getY() - 1)  // UP
        };

        Position best = null;
        int bestD = Integer.MAX_VALUE;

        for (int i = 0; i < adj.length; i++) {
            Position p = adj[i];
            if (!map.isValid(p.getX(), p.getY())) continue;
            Cell c = map.getCell(p.getX(), p.getY());
            if (c == null) continue;
            if (c.isHospital()) continue;
            if (!isRoadCell(c)) continue; // فقط ROAD
            if (collisionMap != null && !collisionMap.isWalkable(p.getX(), p.getY())) continue;
            if (c.isOccupied()) continue;

            int d = manhattan(from, p);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /** یکی از چهار تایل مجاور هدف که walkable و آزاد باشد (برای نزدیک شدن به مجروح). */
    private Position pickBestAdjacentWalkable(Position target, Position from) {
        if (target == null) return null;
        Position[] nbrs = new Position[] {
                new Position(target.getX(),     target.getY() + 1),
                new Position(target.getX() - 1, target.getY()),
                new Position(target.getX() + 1, target.getY()),
                new Position(target.getX(),     target.getY() - 1)
        };

        Position best = null;
        int bestD = Integer.MAX_VALUE;

        for (int i = 0; i < nbrs.length; i++) {
            Position p = nbrs[i];
            if (!map.isValid(p.getX(), p.getY())) continue;
            Cell c = map.getCell(p.getX(), p.getY());
            if (c == null) continue;
            if (!c.isWalkable()) continue;
            if (c.isHospital()) continue;
            if (collisionMap != null && !collisionMap.isWalkable(p.getX(), p.getY())) continue;
            if (c.isOccupied()) continue;

            int d = manhattan(from, p);
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    /* === BFS ساده چهارجهته === */
    /**
     * BFS با قید نوع حرکت:
     *  - roadOnly=false → هر سلول walkable
     *  - roadOnly=true  → فقط سلول‌هایی که ROAD هستند
     */
    private List<Position> bfs(Position start, Position goal, boolean roadOnly) {
        List<Position> empty = Collections.emptyList();
        if (start == null || goal == null) return empty;

        int w = map.getWidth(), h = map.getHeight();
        boolean[][] vis = new boolean[h][w];
        Position[][] prev = new Position[h][w];

        ArrayDeque<Position> q = new ArrayDeque<Position>();
        q.add(start);
        vis[start.getY()][start.getX()] = true;

        final int[] dx = new int[] { 0, -1, 1, 0 };
        final int[] dy = new int[] { 1, 0, 0, -1 };

        while (!q.isEmpty()) {
            Position cur = q.removeFirst();
            if (cur.getX() == goal.getX() && cur.getY() == goal.getY()) break;

            for (int k = 0; k < 4; k++) {
                int nx = cur.getX() + dx[k];
                int ny = cur.getY() + dy[k];

                if (!map.isValid(nx, ny)) continue;
                if (vis[ny][nx]) continue;

                Cell c = map.getCell(nx, ny);
                if (c == null) continue;
                if (c.isHospital()) continue; // خود کاشی بیمارستان ممنوع
                if (c.isOccupied()) continue;

                boolean pass;
                if (roadOnly) {
                    pass = isRoadCell(c);
                } else {
                    pass = c.isWalkable();
                }
                if (!pass) continue;

                if (collisionMap != null && !collisionMap.isWalkable(nx, ny)) continue;

                vis[ny][nx] = true;
                prev[ny][nx] = cur;
                q.addLast(new Position(nx, ny));
            }
        }

        if (!map.isValid(goal.getX(), goal.getY())) return empty;
        if (goal.getY() < 0 || goal.getY() >= h || goal.getX() < 0 || goal.getX() >= w) return empty;
        if (!vis[goal.getY()][goal.getX()]) return empty; // قابل دسترس نیست

        ArrayList<Position> path = new ArrayList<Position>();
        Position cur = goal;
        while (cur != null && !(cur.getX() == start.getX() && cur.getY() == start.getY())) {
            path.add(cur);
            Position p = prev[cur.getY()][cur.getX()];
            cur = p;
        }
        Collections.reverse(path);
        return path;
    }

    /** آیا از همین خانه می‌توان تحویل انجام داد؟ (مجاورِ بیمارستان و روی جاده) */
    private boolean canDeliverFrom(Position here, Hospital h) {
        if (here == null || h == null || h.getPosition() == null) return false;
        if (manhattan(here, h.getPosition()) != 1) return false;
        if (!map.isValid(here.getX(), here.getY())) return false;
        Cell c = map.getCell(here.getX(), here.getY());
        if (c == null) return false;
        if (c.isHospital()) return false;
        return isRoadCell(c);
    }

    /** تشخیص ROAD با متد مستقیم یا نام نوع */
    private boolean isRoadCell(Cell c) {
        try {
            // اگر Cell متدی به نام isRoad() دارد
            java.lang.reflect.Method m = c.getClass().getMethod("isRoad");
            Object r = m.invoke(c);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        } catch (Throwable ignored) { }
        // fallback: نوع شامل "ROAD"
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
        if (a == null || b == null) return Integer.MAX_VALUE;
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY());
    }
}
