// ui/KeyHandler.java
package ui;

import agent.Rescuer;
import map.CityMap;
import playercontrol.DecisionInterface;
import util.MoveGuard;
import util.Position;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * --------------------
 * لایه: UI Layer
 * --------------------
 * مدیریت ورودی صفحه‌کلید:
 *  - تعویض نجات‌دهنده فعال (Tab)
 *  - اجرای عملیات (Enter)
 *  - حرکت چهارجانبه با W/A/S/D یا کلیدهای جهت
 */
public class KeyHandler extends KeyAdapter {

    private final List<Rescuer> allRescuers;
    private Rescuer currentRescuer;
    private final DecisionInterface decisionInterface;
    private final CityMap map;
    private final GamePanel panel; // برای repaint بعد از حرکت

    public KeyHandler(List<Rescuer> rescuers,
                      Rescuer initialRescuer,
                      DecisionInterface decisionInterface,
                      CityMap map,
                      GamePanel panel) {
        this.allRescuers = rescuers;
        this.currentRescuer = initialRescuer;
        this.decisionInterface = decisionInterface;
        this.map = map;
        this.panel = panel;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (currentRescuer == null || map == null) return;
        Position p = currentRescuer.getPosition();
        if (p == null) return;

        int x = p.getX();
        int y = p.getY();
        boolean moved = false;

        int code = e.getKeyCode();
        switch (code) {
            case KeyEvent.VK_TAB:
                currentRescuer = decisionInterface.switchToNextRescuer(currentRescuer, allRescuers);
                System.out.println("نجات‌دهنده فعال: " + currentRescuer.getId());
                break;

            case KeyEvent.VK_ENTER:
                System.out.println("عملیات برای ریسکیور " + currentRescuer.getId());
                break;

            case KeyEvent.VK_W:
            case KeyEvent.VK_UP:
                moved = MoveGuard.tryMoveTo(map, currentRescuer, x, y - 1, 3);
                break;

            case KeyEvent.VK_S:
            case KeyEvent.VK_DOWN:
                moved = MoveGuard.tryMoveTo(map, currentRescuer, x, y + 1, 0);
                break;

            case KeyEvent.VK_A:
            case KeyEvent.VK_LEFT:
                moved = MoveGuard.tryMoveTo(map, currentRescuer, x - 1, y, 1);
                break;

            case KeyEvent.VK_D:
            case KeyEvent.VK_RIGHT:
                moved = MoveGuard.tryMoveTo(map, currentRescuer, x + 1, y, 2);
                break;

            default:
                break;
        }

        // فقط وقتی حرکت انجام شد، رندر مجدد کن
        if (moved && panel != null) {
            panel.repaint();
        }
    }

    public Rescuer getCurrentRescuer() {
        return currentRescuer;
    }
}
