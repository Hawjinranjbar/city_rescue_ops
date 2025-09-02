import agent.Rescuer;
import controller.ScoreManager;
import map.Cell;
import map.CityMap;
import map.Hospital;
import map.MapLoader;
import playercontrol.DecisionInterface;
import ui.GamePanel;
import ui.HUDPanel;
import ui.KeyHandler;
import util.CollisionMap;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

public class Main {

    private static final String TMX_PATH = "assets/maps/rescue_city.tmx";
    private static int nextVictimId = 1; // شناسه یکتا برای مجروح‌ها

    // شمارش‌ها برای HUD
    private static int rescuedCount = 0;
    private static int deadCount = 0;
    private static Set<Integer> rewardedRescues = new HashSet<Integer>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    // 1) لود نقشه از TMX
                    CityMap cityMap = MapLoader.loadTMX(TMX_PATH);

                    // 1.1) لود CollisionMap به‌صورت ایمن (فقط یک‌بار مقدار می‌گیرد)
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
                    List<Rescuer> rescuers = new ArrayList<Rescuer>();
                    Rescuer r1 = new Rescuer(1, spawn);
                    rescuers.add(r1);
                    cityMap.setOccupied(spawn.getX(), spawn.getY(), true);

                    // 4) اسپاون مجروح‌ها روی آوار/خودروهای خراب (OBSTACLE)
                    List<Injured> victims = spawnVictimsOnRubble(cityMap, /*count*/ 10, /*minDistFromRescuer*/ 2, r1);

                    // 5) پنل‌های UI
                    final GamePanel panel = new GamePanel(cityMap, rescuers, victims);
                    panel.setDrawGrid(false);
                    panel.setDebugWalkable(false);
                    panel.setFocusable(true);

                    // امتیاز اولیه به 500 بازنشانی شود و در HUD نمایش یابد
                    ScoreManager.resetToDefault();
                    final HUDPanel hud = new HUDPanel();
                    hud.updateHUD(ScoreManager.getScore(), rescuedCount, deadCount);

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
                    KeyHandler kh = new KeyHandler(rescuers, r1, decision, cityMap, collisionMap, panel, victims);
                    panel.addKeyListener(kh);
                    kh.setVehicleCollision(collisionMap);

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

                    // 8) تایمر رندر سبک
                    javax.swing.Timer repaintTimer = new javax.swing.Timer(80, new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            panel.repaint();
                        }
                    });
                    repaintTimer.start();

                    // 9) تایمر منطقیِ مجروح‌ها: هر ۱ ثانیه
                    javax.swing.Timer victimTimer = new javax.swing.Timer(1000, new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            // تیک تایمر و تشخیص مرگ‌ها
                            for (int i = 0; i < victims.size(); i++) {
                                Injured v = victims.get(i);
                                if (v == null) continue;
                                if (!v.isRescued() && !v.isDead()) {
                                    boolean diedNow = v.updateAndCheckDeath();
                                    if (diedNow) {
                                        deadCount++;
                                        // جریمه بر اساس 2×زمان اولیه
                                        ScoreManager.applyDeathPenalty(v);
                                    }
                                }
                            }

                            // شمارش نجات‌یافته‌ها و پاداش برای نجات‌های جدید
                            int resc = 0;
                            for (int i = 0; i < victims.size(); i++) {
                                Injured v = victims.get(i);
                                if (v != null && v.isRescued()) {
                                    resc++;
                                    if (!rewardedRescues.contains(v.getId())) {
                                        ScoreManager.applyRescueReward(v);
                                        rewardedRescues.add(v.getId());
                                    }
                                }
                            }
                            rescuedCount = resc;

                            // HUD را با امتیاز سراسری به‌روز کن
                            hud.updateHUD(ScoreManager.getScore(), rescuedCount, deadCount);
                            // رندر مجدد
                            panel.repaint();
                        }
                    });
                    victimTimer.start();

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

    /** BFS: نزدیک‌ترین کاشی ROAD به نقطهٔ ترجیحی. */
    private static Position findNearestRoad(CityMap map, Position preferred) {
        if (preferred == null) return null;
        int px = clamp(preferred.getX(), 0, map.getWidth() - 1);
        int py = clamp(preferred.getY(), 0, map.getHeight() - 1);

        // اگر خودش ROAD بود
        if (map.isValid(px, py)) {
            Cell c0 = map.getCell(px, py);
            if (c0 != null && c0.getType() == Cell.Type.ROAD) {
                return new Position(px, py);
            }
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

            if (map.isValid(cx, cy)) {
                Cell c = map.getCell(cx, cy);
                if (c != null && c.getType() == Cell.Type.ROAD) {
                    return new Position(cx, cy);
                }
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

    /** اسکن سادهٔ کل نقشه برای یافتن اولین ROAD. */
    private static Position scanFirstRoad(CityMap map) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Cell c = map.getCell(x, y);
                if (c != null && c.getType() == Cell.Type.ROAD) {
                    return new Position(x, y);
                }
            }
        }
        return null;
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
