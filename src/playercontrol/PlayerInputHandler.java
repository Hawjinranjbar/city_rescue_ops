package playercontrol;

import agent.Rescuer;
import victim.Injured;

import java.util.List;

// --------------------
// لایه: PlayerControl Layer
// --------------------
// این کلاس پیاده‌سازی تعامل بازیکن با نجات‌دهنده‌ها و انتخاب مجروح‌هاست
// تصمیم می‌گیره کدوم مجروح انتخاب بشه و بین Rescuerها سوئیچ می‌کنه
public class PlayerInputHandler implements DecisionInterface {

    private int currentIndex = 0;

    @Override
    public Injured chooseVictim(Rescuer rescuer, List<Injured> candidates) {
        // انتخاب اولین مجروح (برای حالت کنسولی یا تست ساده)
        if (candidates.isEmpty()) return null;

        // در آینده: می‌تونی این رو با ورودی کاربر یا ui تکمیل کنی
        return candidates.get(0);
    }

    @Override
    public Rescuer switchToNextRescuer(Rescuer currentRescuer, List<Rescuer> allRescuers) {
        if (allRescuers.isEmpty()) return null;

        // پیدا کردن اندیس فعلی و رفتن به بعدی
        int index = allRescuers.indexOf(currentRescuer);
        if (index == -1 || index + 1 >= allRescuers.size()) {
            return allRescuers.get(0); // برمی‌گرده به اول لیست
        } else {
            return allRescuers.get(index + 1);
        }
    }
}
