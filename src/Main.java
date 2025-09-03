

import agent.Rescuer;
import agent.AgentManager;
import controller.GameEngine;
import controller.RescueCoordinator;
import controller.ScoreManager;
import file.GameState;
import map.Cell;
import map.CityMap;
import map.MapLoader;
import map.Hospital;
import playercontrol.DecisionInterface;
import strategy.AStarPathFinder;
import strategy.InjuryPrioritySelector;
import ui.GamePanel;
import ui.HUDPanel;
import ui.KeyHandler;
import util.CollisionMap;
import util.Logger;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;
import victim.VictimManager;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;

/**
 * نقطه شروع برنامه (بدون لامبدا).
 * - HUDPanel شامل MiniMapPanel است.
 * - RoadMask و HospitalMask از TMX خوانده می‌شوند.
 * - تایمر HUD هر ثانیه آپدیت می‌شود.
 */
public class Main {

    private static final String TMX_PATH = "assets/maps/rescue_city.tmx";
    private static int nextVictimId = 1; // شناسه یکتا برای مجروح‌ها

    // شمارش‌ها برای HUD
    private static volatile int rescuedCount = 0;
    private static volatile int deadCount = 0;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    // 1) لود نقشه از TMX
                    final CityMap cityMap = MapLoader.loadTMX(TMX_PATH);

                    // 1.1) RoadMask و HospitalMask را از TMX بخوان و داخل CityMap ست کن
                    ensureRoadMaskLoadedFromTMX(cityMap, TMX_PATH);
                    ensureHospitalMaskLoadedFromTMX(cityMap, TMX_PATH);

                    // 1.2) لود CollisionMap به‌صورت ایمن
                    final CollisionMap collisionMap = safeLoadCollisionMap(TMX_PATH, cityMap);

                    // 2) اسپاون «فقط روی ROAD»
                    Position preferred = new Position(cityMap.getWidth() - 2, cityMap.getHeight() - 2);
                    Position spawn = findNearestRoad(cityMap, preferred);
                    if (spawn == null) spawn = scanFirstRoad(cityMap);
                    if (spawn == null) {
                        spawn = new Position(1, 1);
                        System.err.println("[WARN] No ROAD found; fallback to (1,1)");
                    }

                    // 3) ساخت Rescuer + اشغال
                    final List<Rescuer> rescuers = new ArrayList<Rescuer>();
                    final Rescuer r1 = new Rescuer(1, spawn);
                    rescuers.add(r1);
                    cityMap.setOccupied(spawn.getX(), spawn.getY(), true);

                    // 4) اسپاون مجروح‌ها روی آوار/خودروهای خراب (OBSTACLE)
                    final List<Injured> victims = spawnVictimsOnRubble(cityMap, /*count*/ 10, /*minDistFromRescuer*/ 2, r1);

                    // 5) پنل‌های UI
                    final GamePanel panel = new GamePanel(cityMap, rescuers, victims);
                    panel.setDrawGrid(false);
                    panel.setDebugWalkable(false);
                    panel.setFocusable(true);

                    // امتیاز اولیه + HUD با MiniMap
                    ScoreManager.resetToDefault();
                    final int[] timeLeft = new int[]{300}; // ۵ دقیقه شروع
                    final HUDPanel hud = new HUDPanel(cityMap, rescuers, victims);
                    hud.updateHUD(ScoreManager.getScore(), rescuedCount, deadCount, timeLeft[0],
                            cityMap, rescuers, victims);

                    // 5.1) راه‌اندازی موتور بازی برای امکانات Save/Load
                    AgentManager agentManager = new AgentManager();
                    for (Rescuer r : rescuers) { agentManager.addRescuer(r); }
                    VictimManager victimManager = new VictimManager();
                    for (Injured v : victims) { victimManager.addInjured(v); }
                    List<Hospital> hospitals = new ArrayList<Hospital>();
                    RescueCoordinator rescueCoordinator = new RescueCoordinator(
                            agentManager,
                            victimManager,
                            hospitals,
                            cityMap,
                            collisionMap,
                            new AStarPathFinder(cityMap),
                            new InjuryPrioritySelector()
                    );
                    GameState gameState = new GameState(cityMap, rescuers, victims, hospitals, ScoreManager.getScore());
                    final GameEngine engine = new GameEngine(gameState, rescueCoordinator, agentManager, victimManager,
                            hud, panel, hud.getMiniMapPanel(), new Logger("logs/game.log", true));
                    hud.setGameEngine(engine);

                    // 6) کنترل کیبورد (بدون لامبدا)
                    DecisionInterface decision = new DecisionInterface() {
                        @Override
                        public Rescuer switchToNextRescuer(Rescuer current, List<Rescuer> all) {
                            if (all == null || all.isEmpty() || current == null) return current;
                            int idx = all.indexOf(current);
                            if (idx < 0) return all.get(0);
                            return all.get((idx + 1) % all.size());
                        }
                        @Override
                        public victim.Injured chooseVictim(Rescuer current, List<victim.Injured> candidates) {
                            return (candidates == null || candidates.isEmpty()) ? null : candidates.get(0);
                        }
                    };

                    // KeyHandler با HUD و موتور بازی
                    KeyHandler kh = new KeyHandler(rescuers, r1, decision, cityMap, collisionMap, panel, victims, hud, engine);
                    panel.addKeyListener(kh);
                    kh.setVehicleCollision(collisionMap); // اگر خواستی آزاد باشد: kh.setVehicleCollision(null);

                    // 7) فریم و چیدمان
                    JFrame f = new JFrame("City Rescue Ops — Simulation");
                    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    f.setLayout(new BorderLayout());
                    f.add(panel, BorderLayout.CENTER);
                    f.add(hud, BorderLayout.EAST);
                    f.pack();
                    f.setLocationRelativeTo(null);
                    f.setVisible(true);
                    panel.requestFocusInWindow();

                    // 8) حلقه‌ی رندر در یک Thread جداگانه
                    Thread repaintThread = new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                while (true) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override public void run() { panel.repaint(); }
                                    });
                                    Thread.sleep(80);
                                }
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                    repaintThread.setDaemon(true);
                    repaintThread.start();

                    // 9) حلقه‌ی منطق مجروح‌ها + HUD هر ۱ ثانیه در Thread
                    Thread victimThread = new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                while (true) {
                                    SwingUtilities.invokeLater(new Runnable() {
                                        @Override public void run() {
                                            // کم کردن زمان
                                            if (timeLeft[0] > 0) {
                                                timeLeft[0]--;
                                            }

                                            // تیک تایمر و تشخیص مرگ‌ها
                                            for (int i = 0; i < victims.size(); i++) {
                                                Injured v = victims.get(i);
                                                if (v == null) continue;
                                                if (!v.isRescued() && !v.isDead()) {
                                                    boolean diedNow = v.updateAndCheckDeath();
                                                    if (diedNow) {
                                                        deadCount++;
                                                        ScoreManager.applyDeathPenalty(v);
                                                    }
                                                }
                                            }

                        // شمارش نجات‌یافته‌ها
                                            int resc = 0;
                                            for (int i = 0; i < victims.size(); i++) {
                                                Injured v = victims.get(i);
                                                if (v != null && v.isRescued()) resc++;
                                            }
                                            rescuedCount = resc;

                                            // HUD با مینی‌مپ آپدیت میشه
                                            hud.updateHUD(ScoreManager.getScore(), rescuedCount, deadCount, timeLeft[0],
                                                    cityMap, rescuers, victims);
                                            panel.repaint();
                                        }
                                    });
                                    Thread.sleep(1000);
                                }
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    });
                    victimThread.setDaemon(true);
                    victimThread.start();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "خطا: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    /** لود ایمن CollisionMap: در صورت خطا null برمی‌گرداند و cityMap را هم ست می‌کند. */
    private static CollisionMap safeLoadCollisionMap(String tmxPath, CityMap cityMap) {
        try {
            CollisionMap cm = CollisionMap.fromTMX(tmxPath);
            if (cm != null) {
                try { cityMap.setCollisionMap(cm); } catch (Throwable ignored) {}
            }
            return cm;
        } catch (Throwable ex) {
            System.err.println("[WARN] CollisionMap load failed: " + ex.getMessage());
            return null;
        }
    }


    /** RoadMask را از TMX می‌خواند و در CityMap ست می‌کند (CSV → int[][] → setRoadMaskFromInts). */
    private static void ensureRoadMaskLoadedFromTMX(CityMap map, String tmxPath) {
        try {
            Object existing = map.getBinaryLayer("RoadMask");
            if (existing instanceof boolean[][]) {
                int cnt = countTrue((boolean[][]) existing);
                System.out.println("[RoadMask] already present. road-tiles=" + cnt);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            String xml = new String(Files.readAllBytes(Paths.get(tmxPath)), StandardCharsets.UTF_8);
            String tag = "name=\"RoadMask\"";
            int i = xml.indexOf(tag);
            if (i < 0) { System.err.println("[RoadMask] layer not found in TMX."); return; }
            int layerStart = xml.lastIndexOf("<layer", i);
            int layerEnd = xml.indexOf("</layer>", i);
            if (layerStart < 0 || layerEnd < 0) { System.err.println("[RoadMask] malformed layer block."); return; }
            String layerBlock = xml.substring(layerStart, layerEnd);
            int d1 = layerBlock.indexOf("<data");
            int d2 = layerBlock.indexOf("</data>");
            if (d1 < 0 || d2 < 0) { System.err.println("[RoadMask] data tag not found."); return; }
            int gt = layerBlock.indexOf('>', d1);
            if (gt < 0 || gt >= d2) { System.err.println("[RoadMask] data tag malformed."); return; }
            String csv = layerBlock.substring(gt + 1, d2).trim();
            int[][] grid01 = parseCSVToGrid(csv, map.getWidth(), map.getHeight());
            if (grid01 != null) {
                map.setRoadMaskFromInts(grid01);
                int cnt = countNonZero(grid01);
                System.out.println("[RoadMask] loaded from TMX. road-tiles=" + cnt);
            }
        } catch (Throwable ex) {
            System.err.println("[RoadMask] load failed: " + ex.getMessage());
        }
    }

    /** HospitalMask را از TMX می‌خواند و در CityMap ست می‌کند. */
    private static void ensureHospitalMaskLoadedFromTMX(CityMap map, String tmxPath) {
        try {
            Object existing = map.getBinaryLayer("HospitalMask");
            if (existing instanceof boolean[][]) {
                int cnt = countTrue((boolean[][]) existing);
                System.out.println("[HospitalMask] already present. tiles=" + cnt);
                return;
            }
        } catch (Throwable ignored) {}

        try {
            String xml = new String(Files.readAllBytes(Paths.get(tmxPath)), StandardCharsets.UTF_8);
            String tag = "name=\"HospitalMask\"";
            int i = xml.indexOf(tag);
            if (i < 0) { System.err.println("[HospitalMask] layer not found in TMX."); return; }
            int layerStart = xml.lastIndexOf("<layer", i);
            int layerEnd = xml.indexOf("</layer>", i);
            if (layerStart < 0 || layerEnd < 0) { System.err.println("[HospitalMask] malformed layer block."); return; }
            String layerBlock = xml.substring(layerStart, layerEnd);
            int d1 = layerBlock.indexOf("<data");
            int d2 = layerBlock.indexOf("</data>");
            if (d1 < 0 || d2 < 0) { System.err.println("[HospitalMask] data tag not found."); return; }
            int gt = layerBlock.indexOf('>', d1);
            if (gt < 0 || gt >= d2) { System.err.println("[HospitalMask] data tag malformed."); return; }
            String csv = layerBlock.substring(gt + 1, d2).trim();
            int[][] grid01 = parseCSVToGrid(csv, map.getWidth(), map.getHeight());
            if (grid01 != null) {
                map.setHospitalMaskFromInts(grid01);
                int cnt = countNonZero(grid01);
                System.out.println("[HospitalMask] loaded from TMX. tiles=" + cnt);
            }
        } catch (Throwable ex) {
            System.err.println("[HospitalMask] load failed: " + ex.getMessage());
        }
    }

    /** پارس CSV به آرایهٔ [height][width] با ۰/۱. */
    private static int[][] parseCSVToGrid(String csv, int width, int height) {
        if (csv == null) return null;
        String[] lines = csv.split("\\r?\\n");
        int[][] out = new int[height][width];
        int y = 0;
        for (int li = 0; li < lines.length && y < height; li++) {
            String line = lines[li].trim();
            if (line.length() == 0) continue;
            String[] toks = line.split(",");
            int x = 0;
            for (int ti = 0; ti < toks.length && x < width; ti++) {
                String t = toks[ti].trim();
                if (t.length() == 0) continue;
                try {
                    int v = Integer.parseInt(t);
                    out[y][x] = (v != 0) ? 1 : 0;
                } catch (NumberFormatException nfe) {
                    out[y][x] = 0;
                }
                x++;
            }
            y++;
        }
        return out;
    }

    private static int countNonZero(int[][] a) {
        int c = 0;
        if (a == null) return 0;
        for (int y = 0; y < a.length; y++)
            for (int x = 0; x < a[y].length; x++)
                if (a[y][x] != 0) c++;
        return c;
    }

    private static int countTrue(boolean[][] a) {
        int c = 0;
        if (a == null) return 0;
        for (int y = 0; y < a.length; y++)
            for (int x = 0; x < a[y].length; x++)
                if (a[y][x]) c++;
        return c;
    }

    /** --- اسپاون مجروح روی آوار/خودروهای خراب (OBSTACLE) --- */
    private static List<Injured> spawnVictimsOnRubble(CityMap map, int count, int minDistFromRescuer, Rescuer rescuer) {
        List<Position> rubble = new ArrayList<Position>();
        int w = map.getWidth(), h = map.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Cell c = map.getCell(x, y);
                if (c == null) continue;
                if (c.getType() == Cell.Type.OBSTACLE && !c.isOccupied()) {
                    rubble.add(new Position(x, y));
                }
            }
        }

        List<Injured> out = new ArrayList<Injured>();
        if (rubble.isEmpty()) return out;

        Random rnd = new Random();
        int placed = 0;
        int safety = rubble.size() * 3;

        int rx = (rescuer != null && rescuer.getPosition() != null) ? rescuer.getPosition().getX() : -999;
        int ry = (rescuer != null && rescuer.getPosition() != null) ? rescuer.getPosition().getY() : -999;

        while (placed < count && safety-- > 0 && !rubble.isEmpty()) {
            int idx = rnd.nextInt(rubble.size());
            Position p = rubble.get(idx);

            // فاصلهٔ حداقلی از ریسکیور
            if (rx != -999) {
                int dx = Math.abs(p.getX() - rx);
                int dy = Math.abs(p.getY() - ry);
                if (dx + dy < minDistFromRescuer) {
                    rubble.remove(idx);
                    continue;
                }
            }

            // شدت مجروح چرخشی
            InjurySeverity sev;
            if (placed % 3 == 0) sev = InjurySeverity.CRITICAL;
            else if (placed % 3 == 1) sev = InjurySeverity.MEDIUM;
            else sev = InjurySeverity.LOW;

            int ttl = (sev == InjurySeverity.CRITICAL) ? 60
                    : (sev == InjurySeverity.MEDIUM) ? 120 : 180;

            Injured inj = new Injured(nextVictimId++, p, sev, ttl);
            out.add(inj);
            placed++;

            rubble.remove(idx);
        }

        System.out.println("[VictimSpawner] spawned " + out.size() + " victims on OBSTACLE tiles.");
        return out;
    }

    /** BFS: نزدیک‌ترین کاشی ROAD به نقطهٔ ترجیحی (اولویت با RoadMask). */
    private static Position findNearestRoad(CityMap map, Position preferred) {
        if (preferred == null) return null;
        int px = clamp(preferred.getX(), 0, map.getWidth() - 1);
        int py = clamp(preferred.getY(), 0, map.getHeight() - 1);

        // اگر خودش ROAD بود
        if (map.isValid(px, py)) {
            if (safeIsRoad(map, px, py)) return new Position(px, py);
        }

        boolean[][] vis = new boolean[map.getHeight()][map.getWidth()];
        Queue<Position> q = new ArrayDeque<Position>();
        q.offer(new Position(px, py));
        vis[py][px] = true;

        int[] dx = new int[] { 0, 0, -1, 1 };
        int[] dy = new int[] { -1, 1, 0, 0 };

        while (!q.isEmpty()) {
            Position cur = q.poll();
            int cx = cur.getX(), cy = cur.getY();

            if (map.isValid(cx, cy) && safeIsRoad(map, cx, cy)) {
                return new Position(cx, cy);
            }

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i];
                int ny = cy + dy[i];
                if (map.isValid(nx, ny) && !vis[ny][nx]) {
                    vis[ny][nx] = true;
                    q.offer(new Position(nx, ny));
                }
            }
        }
        return null;
    }

    /** اسکن سادهٔ کل نقشه برای یافتن اولین ROAD (اولویت با RoadMask). */
    private static Position scanFirstRoad(CityMap map) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                if (safeIsRoad(map, x, y)) return new Position(x, y);
            }
        }
        return null;
    }

    /** true اگر RoadMask حاضر باشد و (x,y) جاده باشد؛ در غیر این‌صورت فالبک به Cell.Type.ROAD */
    private static boolean safeIsRoad(CityMap map, int x, int y) {
        try {
            if (map.isRoad(x, y)) return true;
        } catch (Throwable ignored) { }
        Cell c = map.getCell(x, y);
        return c != null && c.getType() == Cell.Type.ROAD;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
