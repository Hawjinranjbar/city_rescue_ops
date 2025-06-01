package ui;

import agent.Rescuer;
import playercontrol.DecisionInterface;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

// --------------------
// لایه: ui Layer
// --------------------
// این کلاس ورودی‌های صفحه‌کلید رو مدیریت می‌کنه
// مثلاً برای تعویض نجات‌دهنده با کلید Tab یا اجرای عملیات با Enter
public class KeyHandler extends KeyAdapter {

    private final List<Rescuer> allRescuers;
    private Rescuer currentRescuer;
    private final DecisionInterface decisionInterface;

    public KeyHandler(List<Rescuer> rescuers,
                      Rescuer initialRescuer,
                      DecisionInterface decisionInterface) {
        this.allRescuers = rescuers;
        this.currentRescuer = initialRescuer;
        this.decisionInterface = decisionInterface;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_TAB -> {
                currentRescuer = decisionInterface.switchToNextRescuer(currentRescuer, allRescuers);
                System.out.println("نجات‌دهنده فعال تغییر کرد: ID " + currentRescuer.getId());
            }

            case KeyEvent.VK_ENTER -> {
                // اینجا می‌تونی دستور اجرای عملیات برای Rescuer رو بذاری
                System.out.println("عملیات برای Rescuer ID " + currentRescuer.getId() + " شروع شد.");
            }

            // می‌تونی کلیدهای دیگه هم اضافه کنی
        }
    }

    public Rescuer getCurrentRescuer() {
        return currentRescuer;
    }
}
