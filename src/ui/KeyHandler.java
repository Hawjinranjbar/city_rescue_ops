// src/ui/KeyHandler.java
package ui;

import agent.Rescuer;
import agent.Vehicle;
import map.Cell;
import map.CityMap;
import playercontrol.DecisionInterface;
import util.CollisionMap;
import util.Position;
import victim.Injured;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * مدیریت ورودی صفحه‌کلید:
 *  - تعویض نجات‌دهنده فعال (Tab)
 *  - حرکت Rescuer با Arrowها (و WASD وقتی کنترل خودرو خاموش است)
 *  - حرکت Vehicle با WASD وقتی controlVehicle=true
 */
public class KeyHandler extends KeyAdapter {

    // ---- Rescuer ----
    private final List<Rescuer> allRescuers;
    private Rescuer currentRescuer;
    private final DecisionInterface decisionInterface;

    // ---- Map / Collision ----
    private final CityMap map;
    private final CollisionMap collisionMap; // برای Rescuer (می‌تواند null باشد)

    // ---- UI ----
    private final GamePanel panel;

    // ---- Vehicle ----
    private Vehicle vehicle;
    private boolean controlVehicle = false;       // اگر true، WASD برای ماشین است
    private CollisionMap vehicleCollision = null; // می‌تواند null باشد
    private final List<Injured> victims;
    private Injured transportingVictim = null;

    public KeyHandler(List<Rescuer> rescuers,
                      Rescuer initialRescuer,
                      DecisionInterface decisionInterface,
                      CityMap map,
                      CollisionMap collisionMap,
                      GamePanel panel,
                      List<Injured> victims) {
        this.allRescuers = rescuers;
        this.currentRescuer = initialRescuer;
        this.decisionInterface = decisionInterface;
        this.map = map;
        this.collisionMap = collisionMap;
        this.panel = panel;
        this.victims = victims;
    }

    // اتصال/تنظیم ماشین (اختیاری)
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

        // ---------- کنترل Vehicle با WASD یا Arrow ----------
        if (controlVehicle && vehicle != null) {
            Position t = vehicle.getTile();
            if (t != null) {
                int nx = t.getX();
                int ny = t.getY();
                switch (code) {
                    case KeyEvent.VK_W:
                    case KeyEvent.VK_UP:
                        ny--;
                        break;
                    case KeyEvent.VK_S:
                    case KeyEvent.VK_DOWN:
                        ny++;
                        break;
                    case KeyEvent.VK_A:
                    case KeyEvent.VK_LEFT:
                        nx--;
                        break;
                    case KeyEvent.VK_D:
                    case KeyEvent.VK_RIGHT:
                        nx++;
                        break;
                    default:
                        break;
                }
                if (nx != t.getX() || ny != t.getY()) {
                    moved = tryMoveVehicle(nx, ny);
                }
                if (panel != null) panel.repaint();
            }
            return; // وقتی Vehicle فعال است، حرکت Rescuer ممنوع
        }

        // ---------- کلیدهای عمومی ----------
        if (currentRescuer == null || map == null) return;
        Position p = currentRescuer.getPosition();
        if (p == null) return;

        int x = p.getX();
        int y = p.getY();

        switch (code) {
            case KeyEvent.VK_TAB:
                if (decisionInterface != null) {
                    currentRescuer = decisionInterface.switchToNextRescuer(currentRescuer, allRescuers);
                    if (panel != null) panel.repaint();
                }
                break;

            case KeyEvent.VK_ENTER:
                // جای عملیات/اکشن
                break;

            // Arrow ها همیشه برای Rescuer
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

            // اگر کنترل خودرو خاموش باشد، WASD هم برای Rescuer کار کند
            case KeyEvent.VK_W:
                if (!controlVehicle) {
                    moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x, y - 1, 3);
                }
                break;
            case KeyEvent.VK_S:
                if (!controlVehicle) {
                    moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x, y + 1, 0);
                }
                break;
            case KeyEvent.VK_A:
                if (!controlVehicle) {
                    moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x - 1, y, 1);
                }
                break;
            case KeyEvent.VK_D:
                if (!controlVehicle) {
                    moved = util.MoveGuard.tryMoveTo(map, collisionMap, currentRescuer, x + 1, y, 2);
                }
                break;
            default:
                break;
        }
        if (!controlVehicle) checkPickup();
        if (panel != null) panel.repaint();
    }

    /**
     * حرکت Vehicle با قید نوع سلول + CollisionMap + occupied.
     * بیمارستان و موانع صراحتاً بلاک‌اند.
     */
    private boolean tryMoveVehicle(int nx, int ny) {
        if (!map.isValid(nx, ny)) return false;
        Cell dest = map.getCell(nx, ny);
        if (dest == null) return false;
        if (dest.isOccupied()) return false;

        // بلاک صریح بیمارستان
        if (dest.isHospital()) return false;

        // عبوری‌بودن بر اساس نوع سلول (فقط Road/Sidewalk)
        boolean passByType = dest.isRoad();

        // عبوری‌بودن بر اساس CollisionMap (در صورت وجود)
        boolean passByCollision = (vehicleCollision == null) || vehicleCollision.isWalkable(nx, ny);

        if (!passByType || !passByCollision) return false;

        // واگذاری حرکت نهایی به MoveGuard (اشغال قبلی/جدید هم آن‌جا مدیریت می‌شود)
        boolean moved = util.MoveGuard.tryMoveToVehicle(map, vehicleCollision, vehicle, nx, ny);
        if (moved) {
            currentRescuer.setPosition(vehicle.getTile());
            checkHospitalArrival();
        }
        return moved;
    }

    private void checkPickup() {
        if (victims == null || currentRescuer == null) return;
        Position rp = currentRescuer.getPosition();
        if (rp == null) return;
        for (Injured v : victims) {
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

    private void startTransport(Injured v) {
        transportingVictim = v;
        v.setBeingRescued(true);
        v.getRescueTimer().stop();
        currentRescuer.pickUp(v);
        Vehicle amb = new Vehicle(1000, currentRescuer.getPosition(), vehicleCollision);
        attachVehicle(amb);
        controlVehicle = true;
    }

    private void checkHospitalArrival() {
        if (!controlVehicle || vehicle == null || transportingVictim == null) return;
        Position p = vehicle.getTile();
        if (p == null) return;
        if (isHospitalNeighbor(p.getX(), p.getY())) {
            currentRescuer.dropVictim();
            transportingVictim = null;
            controlVehicle = false;
            attachVehicle(null);
        }
    }

    private boolean isHospitalNeighbor(int x, int y) {
        return isHospitalAt(x + 1, y) || isHospitalAt(x - 1, y) ||
                isHospitalAt(x, y + 1) || isHospitalAt(x, y - 1);
    }

    private boolean isHospitalAt(int x, int y) {
        if (!map.isValid(x, y)) return false;
        Cell c = map.getCell(x, y);
        return c != null && c.isHospital();
    }

    public Rescuer getCurrentRescuer() { return currentRescuer; }
}
