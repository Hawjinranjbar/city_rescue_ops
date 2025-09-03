// src/agent/AgentController.java
package agent;

import map.Cell;
import map.CityMap;
import map.Hospital;
import util.CollisionMap;
import util.MoveGuard;
import util.Position;
import util.Logger;
import victim.Injured;
import victim.VictimManager;
import strategy.IPathFinder;
import strategy.IAgentDecision;

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
 *
 * افزوده‌ها:
 * - حلقهٔ AI داخلی Thread-base: انتخاب کم‌زمان‌ترین مجروحِ آزاد و حرکت + حمل + تحویل
 * - تزریق VictimManager و فهرست بیمارستان‌ها (اختیاری)
 * - کنترل Start/Stop AI و تنظیم تاخیرها
 * - پشتیبانی از setPathFinder / setDecisionLogic برای سازگاری با RescueCoordinator
 *   (اگر نخواهی از آنها استفاده کنی، مانعی نیست؛ فقط ذخیره می‌شوند)
 */
public class AgentController {

    private final CityMap map;
    private final CollisionMap collisionMap; // می‌تواند null باشد

    // مراجع قابل‌تزریق (اختیاری)
    private IPathFinder pathFinderRef;   // اگر بخواهی به جای BFS داخلی از A* استفاده کنی
    private IAgentDecision decisionRef;  // اگر بخواهی منطق انتخاب قربانی را بیرونی کنی

    // Logger اختیاری
    private Logger logger;

    // ====== وابستگی‌ها برای AI داخلی ======
    private VictimManager victimManager;      // اختیاری: اگر null باشد باید candidates دستی داده شود
    private List<Hospital> hospitalsRef;      // اختیاری: اگر null باشد سعی می‌کنیم از map.getHospitals() بخوانیم

    private Thread aiThread;                  // نخِ داخلی
    private volatile boolean aiRunning;       // فلگ اجرا
    private Rescuer aiRescuer;                // ریسکیور تحت کنترل AI

    private int aiIdleDelayMs = 140;          // مکث حلقه
    private int aiStepDelayMs = 35;           // مکث بین قدم‌ها (در moveAlongPath)

    /* === سازنده‌ها === */

    public AgentController(CityMap map, CollisionMap cm) {
        this(map, cm, null, null);
    }

    public AgentController(CityMap map, CollisionMap cm, IPathFinder pathFinder, IAgentDecision decisionLogic) {
        this.map = map;
        this.collisionMap = cm;
        this.pathFinderRef = pathFinder;
        this.decisionRef = decisionLogic;
        this.logger = null;

        this.victimManager = null;
        this.hospitalsRef = null;
        this.aiThread = null;
        this.aiRunning = false;
        this.aiRescuer = null;
    }

    /* ==============================
       تزریق وابستگی‌ها / سازگاری با RescueCoordinator
       ============================== */

    /** تزریق لاگر (اختیاری) */
    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    /** تزریق VictimManager برای حلقهٔ AI (اختیاری ولی توصیه می‌شود) */
    public void setVictimManager(VictimManager vm) {
        this.victimManager = vm;
    }

    /** تزریق فهرست بیمارستان‌ها (اختیاری) */
    public void setHospitals(List<Hospital> hospitals) {
        this.hospitalsRef = hospitals;
    }

    /** تنظیم تاخیرهای حلقه و گام (اختیاری) */
    public void setAiDelays(int idleMs, int stepMs) {
        if (idleMs < 10) idleMs = 10;
        if (stepMs < 5)  stepMs  = 5;
        this.aiIdleDelayMs = idleMs;
        this.aiStepDelayMs = stepMs;
    }

    /** برای سازگاری با RescueCoordinator — ذخیره‌ی PathFinder (فعلاً الزام به استفاده نیست) */
    public void setPathFinder(IPathFinder pf) {
        this.pathFinderRef = pf;
    }

    /** برای سازگاری با RescueCoordinator — ذخیره‌ی DecisionLogic (فعلاً الزام به استفاده نیست) */
    public void setDecisionLogic(IAgentDecision dl) {
        this.decisionRef = dl;
    }

    /** آیا AI در حال اجراست؟ */
    public boolean isAIRunning() {
        return aiRunning;
    }

    /* ==============================
       کنترل AI داخلی (Thread-base)
       ============================== */

    /**
     * شروع AI داخلی برای یک Rescuer.
     * یک Thread داخلی اجرا می‌شود (بدون لامبدا).
     */
    public synchronized void startAI(final Rescuer rescuer) {
        if (rescuer == null) return;
        if (aiRunning) return;

        this.aiRescuer = rescuer;
        this.aiRunning = true;

        aiThread = new Thread() {
            @Override
            public void run() {
                setName("AgentController-AI-Rescuer-" + rescuer.getId());
                while (aiRunning) {
                    try {
                        // 1) اگر در حالت آمبولانس است: به سمت بیمارستان برو و تحویل بده
                        if (rescuer.isAmbulanceMode()) {
                            Hospital h = selectNearestHospital(rescuer.getPosition()); // ← میان‌بر جدید
                            if (h != null) {
                                Position goal = pickBestAdjacentRoadTile(h, rescuer.getPosition());
                                if (goal != null) {
                                    List<Position> path = bfs(rescuer.getPosition(), goal, true);
                                    if (!path.isEmpty()) moveAlongPath(rescuer, path);
                                }
                                if (canDeliverFrom(rescuer.getPosition(), h)) {
                                    Injured v = rescuer.getCarryingVictim();
                                    if (v != null && logger != null) {
                                        try {
                                            int reward = 2 * Math.max(0, v.getInitialTimeLimit());
                                            rescuer.deliverVictimAtHospital();
                                            logger.logAmbulanceDeliver(rescuer.getId(), rescuer.getPosition(), v.getId(), reward, controller.ScoreManager.getScore());
                                        } catch (Exception ex) {
                                            logger.logError("AgentController.AI/DeliverLog", ex);
                                        }
                                    } else {
                                        rescuer.deliverVictimAtHospital();
                                    }
                                }
                            }
                            Thread.sleep(aiIdleDelayMs);
                            continue;
                        }

                        // 2) حالت عادی: هدف = کم‌زمان‌ترین قربانی آزاد
                        List<Injured> candidates = gatherRescuableCandidates();
                        if (candidates == null || candidates.isEmpty()) {
                            Thread.sleep(aiIdleDelayMs);
                            continue;
                        }
                        Injured target = chooseLeastTime(rescuer.getPosition(), candidates);
                        if (target == null) {
                            Thread.sleep(aiIdleDelayMs);
                            continue;
                        }

                        // اگر مجاور بود → pickup و ورود به آمبولانس
                        if (rescuer.getPosition() != null &&
                                rescuer.getPosition().isAdjacent4(target.getPosition())) {
                            rescuer.enterAmbulanceModeWith(target);
                            if (logger != null) {
                                try {
                                    String sev = (target.getSeverity() != null) ? target.getSeverity().name() : "null";
                                    logger.logAmbulancePickup(rescuer.getId(), rescuer.getPosition(), target.getId(), sev, target.getInitialTimeLimit());
                                } catch (Exception ex) {
                                    logger.logError("AgentController.AI/PickupLog", ex);
                                }
                            }
                            warpAmbulanceToRoad(rescuer);
                            Thread.sleep(aiIdleDelayMs);
                            continue;
                        }

                        // در غیر این صورت، به یکی از همسایه‌های قابل عبورِ هدف حرکت کن
                        Position adj = pickBestAdjacentWalkable(target.getPosition(), rescuer.getPosition());
                        if (adj != null) {
                            List<Position> path = bfs(rescuer.getPosition(), adj, false);
                            if (!path.isEmpty()) moveAlongPath(rescuer, path);
                        }

                        Thread.sleep(aiIdleDelayMs);
                    } catch (InterruptedException ie) {
                        // ممکن است stopAI شده باشد
                    } catch (Exception ex) {
                        if (logger != null) logger.logError("AgentController.AI/Loop", ex);
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                }
            }
        };
        aiThread.setDaemon(true);
        aiThread.start();
    }

    /** توقف AI داخلی و آزادسازی نخ */
    public synchronized void stopAI() {
        aiRunning = false;
        if (aiThread != null) {
            try { aiThread.interrupt(); } catch (Throwable ignored) {}
            aiThread = null;
        }
        aiRescuer = null;
    }

    /* ==============================
       اکشن سطح بالا (برای فراخوانی RescueCoordinator)
       ============================== */

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

            List<Position> path = bfs(rescuer.getPosition(), goal, true); // فقط ROAD
            if (!path.isEmpty()) moveAlongPath(rescuer, path);

            if (canDeliverFrom(rescuer.getPosition(), h)) {
                Injured v = rescuer.getCarryingVictim();
                if (v != null && logger != null) {
                    try {
                        int reward = 2 * Math.max(0, v.getInitialTimeLimit());
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

        Injured target = chooseLeastTime(rescuer.getPosition(), candidates);
        if (target == null) return;

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
            warpAmbulanceToRoad(rescuer);
            return;
        }

        Position adj = pickBestAdjacentWalkable(target.getPosition(), rescuer.getPosition());
        if (adj == null) return;

        List<Position> path = bfs(rescuer.getPosition(), adj, false); // walkable عادی
        if (!path.isEmpty()) moveAlongPath(rescuer, path);
    }

    /** پس از سوار کردن مجروح، آمبولانس را به نزدیک‌ترین جاده منتقل می‌کند. */
    private void warpAmbulanceToRoad(Rescuer rescuer) {
        if (rescuer == null) return;
        Position road = findNearestRoad(rescuer.getPosition());
        if (road == null) return;
        Position cur = rescuer.getPosition();
        if (cur != null) map.setOccupied(cur.getX(), cur.getY(), false);
        rescuer.setTile(road.getX(), road.getY());
        map.setOccupied(road.getX(), road.getY(), true);
        Injured v = rescuer.getCarryingVictim();
        if (v != null) v.setPosition(road);
    }

    /** جست‌وجوی سادهٔ BFS برای پیدا کردن نزدیک‌ترین تایل جاده. */
    private Position findNearestRoad(Position start) {
        if (start == null) return null;
        int w = map.getWidth(), h = map.getHeight();
        boolean[][] vis = new boolean[h][w];
        ArrayDeque<Position> q = new ArrayDeque<Position>();
        q.add(start);
        vis[start.getY()][start.getX()] = true;
        final int[] dx = new int[] { 0, -1, 1, 0 };
        final int[] dy = new int[] { 1, 0, 0, -1 };
        while (!q.isEmpty()) {
            Position cur = q.removeFirst();
            if (map.isValid(cur.getX(), cur.getY())) {
                Cell c = map.getCell(cur.getX(), cur.getY());
                if (c != null && !c.isHospital() && isRoadCell(c)) return cur;
            }
            for (int k = 0; k < 4; k++) {
                int nx = cur.getX() + dx[k];
                int ny = cur.getY() + dy[k];
                if (!map.isValid(nx, ny)) continue;
                if (vis[ny][nx]) continue;
                vis[ny][nx] = true;
                Cell nc = map.getCell(nx, ny);
                if (nc == null) continue;
                if (!nc.isWalkable() && !isRoadCell(nc)) continue;
                q.addLast(new Position(nx, ny));
            }
        }
        return null;
    }

    /* ==============================
       حرکت روی مسیر (گام‌به‌گام با MoveGuard)
       ============================== */
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

            try { Thread.sleep(aiStepDelayMs); } catch (InterruptedException ignored) { }
        }
        return true;
    }

    /* ==============================
       انتخاب هدف‌ها و ابزارها
       ============================== */

    private List<Injured> gatherRescuableCandidates() {
        if (victimManager == null) return null;
        List<Injured> all = victimManager.getAllVictimsSafe();
        if (all == null || all.isEmpty()) return all;
        ArrayList<Injured> out = new ArrayList<Injured>();
        for (int i = 0; i < all.size(); i++) {
            Injured v = all.get(i);
            if (v == null) continue;
            if (!v.isAlive()) continue;
            if (v.isRescued()) continue;
            if (v.isBeingRescued()) continue;
            out.add(v);
        }
        return out;
    }

    private Injured chooseLeastTime(Position from, List<Injured> list) {
        if (list == null || list.isEmpty()) return null;
        Injured best = null;
        int bestTime = Integer.MAX_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Injured inj = list.get(i);
            if (inj == null) continue;
            if (inj.isDead() || inj.isRescued() || inj.getPosition() == null) continue;
            int rem = inj.getRemainingTime();
            int d = manhattan(from, inj.getPosition());
            if (rem < bestTime || (rem == bestTime && d < bestDist)) {
                bestTime = rem;
                bestDist = d;
                best = inj;
            }
        }
        return best;
    }

    private Hospital findNearestHospital(List<Hospital> hospitals, Position from) {
        if (from == null) return null;
        List<Hospital> hs = hospitals;
        if ((hs == null || hs.isEmpty()) && this.hospitalsRef != null) hs = this.hospitalsRef;
        if ((hs == null || hs.isEmpty())) {
            try {
                List<Hospital> fromMap = map.getHospitals();
                if (fromMap != null && !fromMap.isEmpty()) hs = fromMap;
            } catch (Throwable ignored) {}
        }
        if (hs == null || hs.isEmpty()) return null;

        Hospital best = null;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < hs.size(); i++) {
            Hospital h = hs.get(i);
            if (h == null) continue;
            int d = manhattan(from, h.getPosition());
            if (d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /** ← میان‌بر جدید: نزدیک‌ترین بیمارستان بر اساس hospitalsRef یا map */
    private Hospital selectNearestHospital(Position from) {
        return findNearestHospital(this.hospitalsRef, from);
    }

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
            java.lang.reflect.Method m = c.getClass().getMethod("isRoad");
            Object r = m.invoke(c);
            if (r instanceof Boolean) return ((Boolean) r).booleanValue();
        } catch (Throwable ignored) { }
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
