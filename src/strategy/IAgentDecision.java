package strategy;

import agent.Rescuer;
import victim.Injured;

import java.util.List;

// --------------------
// لایه: Strategy Layer
// --------------------
// اینترفیس برای الگوریتم‌های انتخاب مجروح مناسب جهت نجات
// پیاده‌سازی‌ها می‌تونن بر اساس شدت جراحت، فاصله، زمان باقی‌مانده و... تصمیم بگیرن
public interface IAgentDecision {

    /**
     * انتخاب یک مجروح مناسب برای نجات توسط یک نجات‌دهنده خاص.
     *
     * @param rescuer نجات‌دهنده‌ای که قراره عملیات انجام بده
     * @param candidates لیست مجروح‌های قابل نجات
     * @return مجروح انتخاب‌شده یا null اگه چیزی انتخاب نشد
     */
    Injured selectVictim(Rescuer rescuer, List<Injured> candidates);
}
