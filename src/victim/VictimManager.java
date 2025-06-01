package victim;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس مسئول مدیریت لیست تمام مجروح‌هاست
// می‌تونه اضافه کنه، لیست بده، یا مجروح‌های قابل نجات رو فیلتر کنه
public class VictimManager {

    private final List<Injured> injuredList;

    public VictimManager() {
        this.injuredList = new ArrayList<>();
    }

    // اضافه کردن مجروح جدید به لیست
    public void addInjured(Injured injured) {
        injuredList.add(injured);
    }

    // گرفتن تمام مجروح‌ها
    public List<Injured> getAll() {
        return new ArrayList<>(injuredList); // کپی برای جلوگیری از تغییر مستقیم
    }

    // گرفتن مجروح‌های قابل نجات (هنوز نمردن یا نجات داده نشدن)
    public List<Injured> getRescuableVictims() {
        return injuredList.stream()
                .filter(Injured::canBeRescued)
                .collect(Collectors.toList());
    }

    // گرفتن مجروح خاص با ID
    public Injured getById(int id) {
        for (Injured i : injuredList) {
            if (i.getId() == id)
                return i;
        }
        return null;
    }

    // حذف همه مجروح‌ها (مثلاً ریست بازی)
    public void clear() {
        injuredList.clear();
    }

    // شمارش مجروح‌های مرده
    public long countDead() {
        return injuredList.stream().filter(Injured::isDead).count();
    }

    // شمارش مجروح‌های نجات‌داده‌شده
    public long countRescued() {
        return injuredList.stream().filter(Injured::isRescued).count();
    }
}
