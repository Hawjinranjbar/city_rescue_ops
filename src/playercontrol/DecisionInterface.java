package playercontrol;

import agent.Rescuer;
import victim.Injured;

import java.util.List;

// --------------------
// لایه: PlayerControl Layer
// --------------------
// اینترفیس برای گرفتن تصمیم از بازیکن یا رابط کنترلی
// مثل انتخاب مجروح برای نجات یا عوض کردن نجات‌دهنده
public interface DecisionInterface {

    /**
     * انتخاب مجروح هدف توسط بازیکن (یا ui).
     * @param rescuer نجات‌دهنده فعلی
     * @param candidates لیست مجروح‌های قابل نجات
     * @return مجروح انتخاب‌شده یا null اگه پلیر هیچ‌کدوم رو انتخاب نکرد
     */
    Injured chooseVictim(Rescuer rescuer, List<Injured> candidates);

    /**
     * گرفتن نجات‌دهنده بعدی (مثلاً با کلید TAB یا دکمه).
     * @param currentRescuer نجات‌دهنده فعلی
     * @param allRescuers لیست تمام نجات‌دهنده‌ها
     * @return نجات‌دهنده انتخاب‌شده بعدی
     */
    Rescuer switchToNextRescuer(Rescuer currentRescuer, List<Rescuer> allRescuers);
}
