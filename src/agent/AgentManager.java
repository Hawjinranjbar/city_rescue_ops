package agent;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * این کلاس مسئول مدیریت تمام نجات‌دهنده‌ها (Rescuerها) در بازیه:
 *  - اضافه، حذف، جستجو و گرفتن لیست کامل نجات‌دهنده‌ها
 *  - پشتیبانی از pause/resume برای هماهنگی با Save/Load
 *  - قابلیت جایگزینی کامل لیست (replaceAll) برای لود از GameState
 */
public class AgentManager {

    private final Map<Integer, Rescuer> rescuerMap;

    public AgentManager() {
        this.rescuerMap = new HashMap<Integer, Rescuer>();
    }

    // -------------------- CRUD پایه --------------------

    /** اضافه کردن نجات‌دهنده جدید */
    public void addRescuer(Rescuer rescuer) {
        if (rescuer == null) return;
        rescuerMap.put(rescuer.getId(), rescuer);
    }

    /** گرفتن نجات‌دهنده با آیدی مشخص */
    public Rescuer getRescuerById(int id) {
        return rescuerMap.get(id);
    }

    /** معادل getRescuerById (اسم کوتاه‌تر) */
    public Rescuer getById(int id) {
        return rescuerMap.get(id);
    }

    /** گرفتن تمام نجات‌دهنده‌ها */
    public Collection<Rescuer> getAllRescuers() {
        return rescuerMap.values();
    }

    /** حذف همه نجات‌دهنده‌ها (مثلاً ریست بازی) */
    public void clear() {
        rescuerMap.clear();
    }

    /** حذف یک نجات‌دهنده خاص */
    public void removeRescuer(int id) {
        rescuerMap.remove(id);
    }

    /** تعداد نجات‌دهنده‌ها */
    public int size() {
        return rescuerMap.size();
    }

    /** گرفتن اولین نجات‌دهنده (مثلاً برای شروع بازی یا تست) */
    public Rescuer getFirstRescuer() {
        if (rescuerMap.isEmpty()) return null;
        return rescuerMap.values().iterator().next();
    }

    // -------------------- پشتیبانی Save/Load/Restart --------------------

    /**
     * جایگزینی کامل لیست Rescuerها با لیست جدید (مثلاً بعد از Load).
     * کل Map پاک می‌شود و Rescuerهای جدید اضافه می‌شوند.
     */
    public void replaceAll(List<Rescuer> newRescuers) {
        rescuerMap.clear();
        if (newRescuers != null) {
            for (int i = 0; i < newRescuers.size(); i++) {
                Rescuer r = newRescuers.get(i);
                if (r != null) {
                    rescuerMap.put(r.getId(), r);
                }
            }
        }
    }

    /**
     * مکث تمام Rescuerها (برای فریز کلی بازی).
     * اگر Rescuer ترد جدا داشته باشد، اینجا stop/pause می‌شود.
     */
    public void pauseAll() {
        for (Rescuer r : rescuerMap.values()) {
            if (r != null) {
                try {
                    r.pause();
                } catch (Throwable ignore) { }
            }
        }
    }

    /**
     * ازسرگیری تمام Rescuerها (پس از Load/Restart).
     */
    public void resumeAll() {
        for (Rescuer r : rescuerMap.values()) {
            if (r != null) {
                try {
                    r.resume();
                } catch (Throwable ignore) { }
            }
        }
    }
}
