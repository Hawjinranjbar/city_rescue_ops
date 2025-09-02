// src/ui/KeyHandler.java
package ui;

import agent.Rescuer;
import agent.Vehicle;
import map.Cell;
import map.CityMap;
import playercontrol.DecisionInterface;
import util.CollisionMap;
import util.Position;

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

    public KeyHandler(List<Rescuer> rescuers,
                      Rescuer initialRescuer,
                      DecisionInterface decisionInterface,
                      CityMap map,
                      CollisionMap collisionMap,
                      GamePanel panel) {
        this.allRescuers = rescuers;
        this.currentRescuer = initialRescuer;
        this.decisionInterface = decisionInterface;
        this.map = map;
        this.collisionMap = collisionMap;
        this.panel = panel;
    }

    // اتصال/تنظیم ماشین (اختیاری)
    public void attachVehicle(Vehicle v) { this.vehicle = v; }
    public void setControlVehicle(boolean on) { this.controlVehicle = on; }
    public void setVehicleCollision(CollisionMap cm) { this.vehicleCollision = cm; }

    @Override
    public void keyPressed(KeyEvent e) {
        boolean moved = false;
        int code = e.getKeyCode();

        // ---------- Vehicle با WASD ----------
        if (controlVehicle && vehicle != null) {
            Position t = vehicle.getTile();
            if (t != null) {
                int nx = t.getX();
                int ny = t.getY();
                switch (code) {
                    case KeyEvent.VK_W:
                        ny--;
                        break;
                    case KeyEvent.VK_S:
                        ny++;
                        break;
                    case KeyEvent.VK_A:
                        nx--;
                        break;
                    case KeyEvent.VK_D:
                        nx++;
                        break;
                    default:
                        break;
                }
                if (nx != t.getX() || ny != t.getY()) {
                    moved = tryMoveVehicle(nx, ny);
                    if (moved && panel != null) {
                        panel.repaint();
                        return;
                    }
                }
            }
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

        if (moved && panel != null) panel.repaint();
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
        boolean passByType = dest.isWalkable(); // HOSPITAL/OBSTACLE/BUILDING عبوری نیستند

        // عبوری‌بودن بر اساس CollisionMap (در صورت وجود)
        boolean passByCollision = (vehicleCollision == null) || vehicleCollision.isWalkable(nx, ny);

        if (!passByType || !passByCollision) return false;

        // واگذاری حرکت نهایی به MoveGuard (اشغال قبلی/جدید هم آن‌جا مدیریت می‌شود)
        return util.MoveGuard.tryMoveToVehicle(map, vehicleCollision, vehicle, nx, ny);
    }

    public Rescuer getCurrentRescuer() { return currentRescuer; }
}
