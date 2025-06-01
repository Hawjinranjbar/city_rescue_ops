package agent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس مسئول مدیریت تمام نجات‌دهنده‌ها (Rescuerها) در بازیه
// اضافه، حذف، جستجو و گرفتن لیست کامل نجات‌دهنده‌ها رو انجام می‌ده
public class AgentManager {

    private final Map<Integer, Rescuer> rescuerMap;

    public AgentManager() {
        this.rescuerMap = new HashMap<>();
    }

    // اضافه کردن نجات‌دهنده جدید
    public void addRescuer(Rescuer rescuer) {
        rescuerMap.put(rescuer.getId(), rescuer);
    }

    // گرفتن نجات‌دهنده با آیدی مشخص
    public Rescuer getRescuerById(int id) {
        return rescuerMap.get(id);
    }

    // گرفتن تمام نجات‌دهنده‌ها
    public Collection<Rescuer> getAllRescuers() {
        return rescuerMap.values();
    }

    // حذف همه نجات‌دهنده‌ها (مثلاً ریست بازی)
    public void clear() {
        rescuerMap.clear();
    }

    // تعداد نجات‌دهنده‌ها
    public int size() {
        return rescuerMap.size();
    }

    // گرفتن اولین نجات‌دهنده (مثلاً برای شروع بازی یا تست)
    public Rescuer getFirstRescuer() {
        if (rescuerMap.isEmpty()) return null;
        return rescuerMap.values().iterator().next();
    }
}
