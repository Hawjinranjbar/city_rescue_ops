package playercontrol;

import agent.Rescuer;
import victim.Injured;

import java.util.List;

/**
 * --------------------
 * لایه: PlayerControl Layer
 * --------------------
 * رابط تصمیم‌گیری پلیر/UI برای:
 *  - انتخاب مجروح هدف
 *  - سوییچ بین نجات‌دهنده‌ها
 * متدهای default اختیاری‌اند و پیاده‌سازی‌شان اجباری نیست.
 * بدون استفاده از لامبدا.
 */
public interface DecisionInterface {

    /**
     * انتخاب مجروح هدف توسط بازیکن (یا UI).
     * @param rescuer    نجات‌دهنده فعلی
     * @param candidates لیست مجروح‌های «قابل نجات» (فیلتر شده)
     * @return مجروح انتخاب‌شده یا null اگر پلیر هیچ‌کدام را انتخاب نکرد
     */
    Injured chooseVictim(Rescuer rescuer, List<Injured> candidates);

    /**
     * گرفتن نجات‌دهنده بعدی (مثلاً با TAB).
     * @param currentRescuer نجات‌دهنده فعلی
     * @param allRescuers    همهٔ نجات‌دهنده‌ها
     * @return نجات‌دهنده انتخاب‌شده بعدی (می‌تواند همان فعلی باشد اگر تغییری مدنظر نبود)
     */
    Rescuer switchToNextRescuer(Rescuer currentRescuer, List<Rescuer> allRescuers);

    // =========================================================
    // متدهای اختیاری (default) — بدون شکستن پیاده‌سازی‌های قبلی
    // =========================================================

    /**
     * اگر کاندیدایی وجود نداشت؛ UI می‌تواند پیام بدهد یا صوت پخش کند.
     * @param rescuer نجات‌دهنده فعلی
     */
    default void onNoCandidateAvailable(Rescuer rescuer) { }

    /**
     * اگر پلیر انتخاب را لغو کرد (ESC/Right-Click)، این متد صدا زده می‌شود.
     * @param rescuer نجات‌دهنده فعلی
     */
    default void onSelectionCanceled(Rescuer rescuer) { }

    /**
     * تایید نهایی اساین‌کردن مجروح به نجات‌دهنده.
     * موتور بازی می‌تواند قبل از آغاز مسیر، تایید بگیرد.
     * @param rescuer نجات‌دهنده
     * @param victim  مجروح هدف
     * @return true اگر تایید شد؛ false برای لغو
     */
    default boolean confirmAssignment(Rescuer rescuer, Injured victim) { return true; }

    /**
     * اولویت‌بندی پیشنهادی: اگر UI/پلیر بخواهد ترتیب را تغییر دهد، می‌تواند
     * لیست را بازآرایی کند و برگرداند. (مثلاً بر اساس شدت جراحت/مسیر/زمان)
     * اگر تغییری ندهد، می‌تواند همان ورودی را برگرداند.
     * @param rescuer    نجات‌دهنده
     * @param candidates لیست کاندیداها (فیلتر شده)
     * @return لیست بازآرایی‌شده (می‌تواند همان باشد)
     */
    default List<Injured> reorderCandidates(Rescuer rescuer, List<Injured> candidates) { return candidates; }

    /**
     * اگر حالت‌های مختلف مسیریابی/پروفایل برخورد داریم (مثلاً "roadOnly" یا "default"),
     * UI می‌تواند پروفایل دلخواه را اعلام کند. مقدار null یعنی پیش‌فرض موتور.
     * @param rescuer نجات‌دهنده
     * @return نام پروفایل (مثلاً "roadOnly") یا null برای پیش‌فرض
     */
    default String choosePathProfile(Rescuer rescuer) { return null; }

    /**
     * برای سوییچ برعکس (قبلی)، اگر UI نیاز داشت. عدم پیاده‌سازی مشکلی ندارد.
     */
    default Rescuer switchToPrevRescuer(Rescuer currentRescuer, List<Rescuer> allRescuers) { return currentRescuer; }

    /**
     * هینت کوتاه روی HUD (اختیاری).
     * @param message پیام کوتاه جهت نمایش
     */
    default void showHint(String message) { }

    /**
     * اگر بازی pause/resume را از طریق UI کنترل می‌کند.
     * @param pause true برای توقف، false برای ادامه
     */
    default void setPaused(boolean pause) { }
}
