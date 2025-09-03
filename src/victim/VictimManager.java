package victim;

import controller.ScoreManager;

import java.util.ArrayList;
import java.util.List;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * مدیریت لیست مجروح‌ها + اعمال جریمه/پاداش از طریق ScoreManager (سراسری).
 * پشتیبانی از Pause/Resume سراسری (برای Save/Load) و ReplaceAll (برای Load).
 */
public class VictimManager {

    private final List<Injured> injuredList;

    public VictimManager() {
        this.injuredList = new ArrayList<Injured>();
    }

    // -------------------- CRUD پایه --------------------

    /** اضافه کردن مجروح */
    public void addInjured(Injured injured) {
        if (injured == null) return;
        injuredList.add(injured);
    }

    /** همهٔ مجروح‌ها (کپی دفاعی) */
    public List<Injured> getAll() {
        return new ArrayList<Injured>(injuredList);
    }

    /** مجروح‌های قابل نجات (بدون Stream) */
    public List<Injured> getRescuableVictims() {
        List<Injured> out = new ArrayList<Injured>();
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null && v.canBeRescued()) {
                out.add(v);
            }
        }
        return out;
    }

    /** جستجو بر اساس ID */
    public Injured getById(int id) {
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null && v.getId() == id) return v;
        }
        return null;
    }

    /** حذف یک مجروح بر اساس ID */
    public boolean removeById(int id) {
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null && v.getId() == id) {
                injuredList.remove(i);
                return true;
            }
        }
        return false;
    }

    /** پاک‌سازی کامل لیست (مثلاً ریست بازی) */
    public void clear() {
        injuredList.clear();
    }

    /** جایگزینی کامل لیست مجروح‌ها (مثلاً بعد از Load) */
    public void replaceAll(List<Injured> newList) {
        injuredList.clear();
        if (newList != null) {
            for (int i = 0; i < newList.size(); i++) {
                Injured v = newList.get(i);
                if (v != null) {
                    injuredList.add(v);
                }
            }
        }
    }

    /** شمارش مجروح‌های فوت‌شده (بدون Stream) */
    public long countDead() {
        long c = 0;
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null && v.isDead()) c++;
        }
        return c;
    }

    /** شمارش مجروح‌های نجات‌یافته (بدون Stream) */
    public long countRescued() {
        long c = 0;
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null && v.isRescued()) c++;
        }
        return c;
    }

    // -------------------- هماهنگی با امتیاز --------------------

    /** اعلام مرگ یک مجروح: هم وضعیت، هم جریمه اعمال می‌شود. */
    public void onVictimDead(Injured injured) {
        if (injured == null) return;
        if (!injured.isDead() && !injured.isRescued()) {
            injured.markAsDead(); // ناپدیدشدن/غیرفعال‌شدن در خود Injured هندل شود
            ScoreManager.applyDeathPenalty(injured); // 2×زمان اولیه (سراسری)
        }
    }

    /** اعلام نجات یک مجروح: وضعیت نجات + پاداش امتیاز */
    public void onVictimRescued(Injured injured) {
        if (injured == null) return;
        if (!injured.isDead() && !injured.isRescued()) {
            injured.markAsRescued();
            ScoreManager.applyRescueReward(injured); // +2×زمان اولیه
        }
    }

    // -------------------- پشتیبانی Save/Load/Restart --------------------

    /**
     * مکث همهٔ تایمرهای نجاتِ مجروح‌ها (برای فریز بازی حین Save/Load).
     * اگر Injured تایمر داخلی دارد، باید آن را pause کند؛ در غیر اینصورت نادیده گرفته می‌شود.
     */
    public void pauseAll() {
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null) {
                try {
                    // اگر API تایمر وجود دارد:
                    if (v.getRescueTimer() != null) {
                        v.getRescueTimer().pause();
                    } else {
                        // اگر خود Injured متد pause دارد:
                        v.pause();
                    }
                } catch (Throwable ignore) { }
            }
        }
    }

    /**
     * ازسرگیری همهٔ تایمرهای مجروح‌ها (پس از Load/Restart).
     * برای قربانی‌هایی که مرده یا نجات یافتند، ادامه‌دادن تایمر ضرورتی ندارد.
     */
    public void resumeAll() {
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v != null) {
                try {
                    // فقط اگر زنده و نجات‌نشده است، ادامه بده
                    if (!v.isDead() && !v.isRescued()) {
                        if (v.getRescueTimer() != null) {
                            v.getRescueTimer().resume();
                        } else {
                            v.resume();
                        }
                    }
                } catch (Throwable ignore) { }
            }
        }
    }
}
