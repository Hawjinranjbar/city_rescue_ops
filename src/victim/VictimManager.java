package victim;

import controller.ScoreManager;

import java.util.ArrayList;
import java.util.List;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * مدیریت لیست مجروح‌ها + اعمال جریمه/پاداش از طریق ScoreManager (سراسری).
 */
public class VictimManager {

    private final List<Injured> injuredList;

    public VictimManager() {
        this.injuredList = new ArrayList<Injured>();
    }

    // اضافه کردن مجروح
    public void addInjured(Injured injured) {
        injuredList.add(injured);
    }

    // همهٔ مجروح‌ها (کپی)
    public List<Injured> getAll() {
        return new ArrayList<Injured>(injuredList);
    }

    // مجروح‌های قابل نجات (بدون Stream)
    public List<Injured> getRescuableVictims() {
        List<Injured> out = new ArrayList<Injured>();
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v.canBeRescued()) {
                out.add(v);
            }
        }
        return out;
    }

    // جستجوی ID
    public Injured getById(int id) {
        for (int i = 0; i < injuredList.size(); i++) {
            Injured v = injuredList.get(i);
            if (v.getId() == id) return v;
        }
        return null;
    }

    // پاک‌سازی
    public void clear() {
        injuredList.clear();
    }

    // شمارش‌ها (بدون Stream)
    public long countDead() {
        long c = 0;
        for (int i = 0; i < injuredList.size(); i++) {
            if (injuredList.get(i).isDead()) c++;
        }
        return c;
    }

    public long countRescued() {
        long c = 0;
        for (int i = 0; i < injuredList.size(); i++) {
            if (injuredList.get(i).isRescued()) c++;
        }
        return c;
    }

    // -------------------- هماهنگی با امتیاز --------------------

    /** اعلام مرگ یک مجروح: هم وضعیت، هم جریمه اعمال می‌شود. */
    public void onVictimDead(Injured injured) {
        if (injured == null) return;
        if (!injured.isDead() && !injured.isRescued()) {
            injured.markAsDead(); // ناپدیدشدن در خود Injured هندل شود
            ScoreManager.applyDeathPenalty(injured); // 2×زمان اولیه (سراسری)
        }
    }

    /** اعلام نجات یک مجروح: وضعیت نجات + (اختیاری) پاداش */
    public void onVictimRescued(Injured injured) {
        if (injured == null) return;
        if (!injured.isDead() && !injured.isRescued()) {
            injured.markAsRescued();
            // اگر پاداش لازم داری، این خط را باز کن:
            // ScoreManager.addRescueRewardBySeverity(injured.getSeverity());
        }
    }
}
