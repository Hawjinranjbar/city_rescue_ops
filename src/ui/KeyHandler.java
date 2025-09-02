package ui;

import agent.Rescuer;
import agent.Vehicle;
import controller.ScoreManager;
import map.Cell;
import map.CityMap;
import playercontrol.DecisionInterface;
import util.CollisionMap;
import util.Position;
import victim.Injured;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * ورودی کیبورد + منطق حمل/تحویل:
 * - شروع حمل: تلپورت به نزدیک‌ترین «جادهٔ آزاد» و اسپاون آمبولانس
 * - حرکت آمبولانس فقط روی RoadMask
 * - تحویل در «مجاورت HospitalMask»: آمبولانس ناپدید، امتیاز (۲×زمانِ باقی‌مانده)، HUD فوری
 */
public class KeyHandler extends KeyAdapter {

    // ---- Rescuer ----
    private final List<Rescuer> allRescuers;
    private Rescuer currentRescuer;
    private final DecisionInterface decisionInterface;

    // ---- Map / Collision ----
    private final CityMap map;
    private final CollisionMap collisionMap; // برای Rescuer

    // ---- UI ----
    private final GamePanel panel;
    private final HUDPanel hud;              // برای آپدیت فوری

    // ---- Vehicle ----
    private Vehicle vehicle;
    private boolean controlVehicle = false;
    private CollisionMap vehicleCollision = null;

    // ---- Victims ----
    private final List<Injured> victims;
    private Injured transportingVictim = null;

    public KeyHandler(List<Rescuer> rescuers,
                      Rescuer initialRescuer,
                      DecisionInterface decisionInterface,
                      CityMap map,
                      CollisionMap collisionMap,
                      GamePanel panel,
                      List<Injured> victims,
                      HUDPanel hud) {
        this.allRescuers = rescuers;
        this.currentRescuer = initialRescuer;
        this.decisionInterface = decisionInterface;
        this.map = map;
        this.collisionMap = collisionMap;
        this.panel = panel;
        this.victims = victims;
        this.hud = hud;
    }

    // اتصال/تنظیم ماشین
    public void attachVehicle(Vehicle v) {
        this.vehicle = v;
        if (panel != null) panel.setVehicle(v);
    }
    public void setControlVehicle(boolean on) { this.controlVehicle = on; }
    public void setVehicleCollision(CollisionMap cm) { this.vehicleCollision = cm; }

    @Override
    public void keyPressed(KeyEvent e) {
        boolean moved = false;
        int code = e.getKeyCode();

        // ---------- کنترل Vehicle ----------
        if (controlVehicle && vehicle != null) {
            Position t = vehicle.getTile();
            if (t != null) {
                int nx = t.getX(), ny = t.getY();
                int faceDir = -1; // 0=DOWN,1=LEFT,2=RIGHT,3=UP
                switch (code) {
                    case KeyEvent.VK_W:
                    case KeyEvent.VK_UP:    ny--; faceDir = 3; break;
                    case KeyEvent.VK_S:
                    case KeyEvent.VK_DOWN:  ny++; faceDir = 0; break;
                    case KeyEvent.VK_A:
                    case KeyEvent.VK_LEFT:  nx--; faceDir = 1; break;
                    case KeyEvent.VK_D:
                    case KeyEvent.VK_RIGHT: nx++; faceDir = 2; break;
                    default: break;
                }
                if (nx != t.getX() || ny != t.getY()) {
                    moved = tryMoveVehicle(nx, ny);
                    if (moved && faceDir >= 0) {
                        try { currentRescuer.setDirection(faceDir); } catch (Throwable ignored) {}
                    }
                }
                if (panel != null) panel.repaint();
            }
            return;
        }

        // ---------- حرکت Rescuer ----------
        if (currentRescuer == null || map == null) return;
        Position p = currentRescuer.getPosition();
        if (p == null) return;

        int x = p.getX(), y = p.getY();

        switch (code) {
            case KeyEvent.VK_TAB:
                if (decisionInterface != null) {
                    currentRescuer = decisionInterface.switchToNextRescuer(currentRescuer, allRescuers);
                    if (panel != null) panel.repaint();
                }
                break;

            case KeyEvent.VK_E:
                checkPickup();
                break;

            case KeyEvent.VK_UP:
                moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x, y - 1, 3);
                break;
            case KeyEvent.VK_DOWN:
                moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x, y + 1, 0);
                break;
            case KeyEvent.VK_LEFT:
                moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x - 1, y, 1);
                break;
            case KeyEvent.VK_RIGHT:
                moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x + 1, y, 2);
                break;

            case KeyEvent.VK_W:
                if (!controlVehicle) moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x, y - 1, 3);
                break;
            case KeyEvent.VK_S:
                if (!controlVehicle) moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x, y + 1, 0);
                break;
            case KeyEvent.VK_A:
                if (!controlVehicle) moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x - 1, y, 1);
                break;
            case KeyEvent.VK_D:
                if (!controlVehicle) moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x + 1, y, 2);
                break;
            default:
                break;
        }

        if (moved && !controlVehicle) checkPickup();
        if (panel != null) panel.repaint();
    }

    /** حرکت Vehicle با همهٔ چک‌ها (ROAD/Hospital/Occupied/Collision). */
    private boolean tryMoveVehicle(int nx, int ny) {
        if (vehicle == null) return false;
        boolean moved = util.MoveGuard.tryMoveToVehicle(map, vehicleCollision, vehicle, nx, ny);
        if (moved) {
            Position t = vehicle.getTile();
            if (t != null) currentRescuer.setPosition(t); // برای دنبال‌کردن Viewport
            checkHospitalArrival();
        }
        return moved;
    }

    /** اگر ریسکیور کنار مجروحی بود، حمل را آغاز کن. */
    private void checkPickup() {
        if (victims == null || currentRescuer == null) return;
        Position rp = currentRescuer.getPosition();
        if (rp == null) return;

        for (int i = 0; i < victims.size(); i++) {
            Injured v = victims.get(i);
            if (v == null || !v.canBeRescued()) continue;
            Position vp = v.getPosition();
            if (vp == null) continue;

            int d = Math.abs(vp.getX() - rp.getX()) + Math.abs(vp.getY() - rp.getY());
            if (d == 1) {
                startTransport(v);
                break;
            }
        }
    }

    /** شروع حمل: رفتن به نزدیک‌ترین جاده و اسپاون آمبولانس. */
    private void startTransport(Injured v) {
        transportingVictim = v;
        v.setBeingRescued(true);
        v.getRescueTimer().stop();

        currentRescuer.enterAmbulanceModeWith(v);

        Position from = currentRescuer.getPosition();
        Position road = findNearestRoadSpawn(from, 8);

        if (from != null) map.setOccupied(from.getX(), from.getY(), false);

        Position spawn = (road != null) ? road : from;

        if (vehicle == null) {
            vehicle = new Vehicle(1000, spawn, vehicleCollision);
            attachVehicle(vehicle);
        } else {
            vehicle.setTile(spawn);
            attachVehicle(vehicle);
        }
        if (spawn != null) {
            map.setOccupied(spawn.getX(), spawn.getY(), true);
            currentRescuer.setPosition(spawn);
        }

        controlVehicle = true;
        if (panel != null) panel.repaint();
    }

    /** مجاورت با HospitalMask → تحویل فوری + ناپدید شدن آمبولانس + آپدیت HUD. */
    private void checkHospitalArrival() {
        if (!controlVehicle || vehicle == null || transportingVictim == null) return;
        Position p = vehicle.getTile();
        if (p == null) return;

        if (isHospitalNeighborMask(p.getX(), p.getY())) {
            // آزاد کردن خانه‌ی خودرو
            map.setOccupied(p.getX(), p.getY(), false);

            // تحویل: امتیاز (۲× زمانِ باقی‌مانده) و خروج از آمبولانس
            currentRescuer.deliverVictimAtHospital();

            // ریسکیور همانجا بایستد و آن را اشغال کند
            currentRescuer.setPosition(p);
            map.setOccupied(p.getX(), p.getY(), true);

            // حذف آمبولانس از UI
            transportingVictim = null;
            controlVehicle = false;
            attachVehicle(null);
            vehicle = null;

            // HUD فوری: شمارش نجات‌یافته/مرده و امتیاز جاری
            if (hud != null && victims != null) {
                int resc = 0, dead = 0;
                for (int i = 0; i < victims.size(); i++) {
                    Injured v = victims.get(i);
                    if (v == null) continue;
                    if (v.isRescued()) resc++;
                    else if (v.isDead()) dead++;
                }
                hud.updateHUD(ScoreManager.getScore(), resc, dead);
            }

            if (panel != null) panel.repaint();
        }
    }

    private boolean isHospitalNeighborMask(int x, int y) {
        return isHospitalMaskAt(x + 1, y) || isHospitalMaskAt(x - 1, y)
                || isHospitalMaskAt(x, y + 1) || isHospitalMaskAt(x, y - 1);
    }

    private boolean isHospitalMaskAt(int x, int y) {
        if (!map.isValid(x, y)) return false;
        // اول HospitalMask
        try { return map.isHospitalMask(x, y); } catch (Throwable ignored) { }
        // فالبک به نوع سلول
        Cell c = map.getCell(x, y);
        return c != null && c.isHospital();
    }

    // -------------------- پیدا کردن نزدیک‌ترین جادهٔ آزاد --------------------
    private Position findNearestRoadSpawn(Position start, int maxRadius) {
        if (start == null) return null;

        // همسایه‌های فوری
        int sx = start.getX(), sy = start.getY();
        int[][] d4 = new int[][] { {0,-1}, {0,1}, {-1,0}, {1,0} };
        for (int i = 0; i < d4.length; i++) {
            int nx = sx + d4[i][0], ny = sy + d4[i][1];
            if (isRoadFreeForVehicle(nx, ny)) return new Position(nx, ny);
        }

        // BFS کوتاه
        boolean[][] vis = new boolean[map.getHeight()][map.getWidth()];
        Queue<Position> q = new ArrayDeque<Position>();
        q.offer(new Position(sx, sy));
        vis[sy][sx] = true;
        int[] dx = new int[] { 0, 0, -1, 1 };
        int[] dy = new int[] { -1, 1, 0, 0 };

        while (!q.isEmpty()) {
            Position cur = q.poll();
            int cx = cur.getX(), cy = cur.getY();
            int md = Math.abs(cx - sx) + Math.abs(cy - sy);
            if (md > maxRadius) continue;

            if (isRoadFreeForVehicle(cx, cy)) return new Position(cx, cy);

            for (int i = 0; i < 4; i++) {
                int nx = cx + dx[i], ny = cy + dy[i];
                if (map.isValid(nx, ny) && !vis[ny][nx]) {
                    vis[ny][nx] = true;
                    q.offer(new Position(nx, ny));
                }
            }
        }
        return null;
    }

    private boolean isRoadFreeForVehicle(int x, int y) {
        if (!map.isValid(x, y)) return false;
        // فقط جاده طبق RoadMask/نوع سلول
        boolean isRoad = false;
        try { isRoad = map.isRoad(x, y); } catch (Throwable ignored) {
            Cell c = map.getCell(x, y);
            isRoad = (c != null && c.getType() == Cell.Type.ROAD);
        }
        if (!isRoad) return false;

        Cell c = map.getCell(x, y);
        if (c == null) return false;
        if (c.isHospital()) return false;
        if (c.isOccupied()) return false;
        if (vehicleCollision != null && !vehicleCollision.isWalkable(x, y)) return false;
        return true;
    }

    public Rescuer getCurrentRescuer() { return currentRescuer; }
}
