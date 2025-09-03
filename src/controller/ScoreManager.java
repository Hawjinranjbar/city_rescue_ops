package controller;

import victim.Injured;
import victim.InjurySeverity;

/**
 * مدیریت امتیاز بازی (جهانی/Static).
 * - امتیاز شروع: 500
 * - جریمه مرگ: 2 × زمان اولیه تایمر مجروح
 * - پاداش نجات: 2 × زمان اولیه تایمر مجروح
 * - (اختیاری) پاداش نجات بر اساس شدت
 *
 * نکته: همه‌ی متدها روی یک "score" سراسری کار می‌کنند؛
 * حتی اگر چند نمونه از ScoreManager بسازی، باز هم یک امتیاز مشترک خواهید داشت.
 */
public final class ScoreManager {

    private static int DEFAULT_SCORE = 500;
    private static int score = DEFAULT_SCORE;

    private ScoreManager() { }

    // ===== دسترسی و تنظیم مقدار =====
    public static synchronized int getScore() {
        return score;
    }

    /** ست‌کردن مستقیم امتیاز (برای Load/Restart از GameState) */
    public static synchronized void setScore(int newScore) {
        score = newScore;
    }

    /** اگر خواستی مقدار پیش‌فرض را در زمان اجرا تغییر بدهی (اختیاری) */
    public static synchronized void setDefaultScore(int defaultScore) {
        DEFAULT_SCORE = defaultScore;
    }

    public static synchronized int getDefaultScore() {
        return DEFAULT_SCORE;
    }

    public static synchronized void resetToDefault() {
        score = DEFAULT_SCORE;
    }

    // ===== عملیات پایه =====
    public static synchronized void add(int amount) {
        if (amount > 0) score += amount;
    }

    public static synchronized void deduct(int amount) {
        if (amount > 0) score -= amount;
    }

    // ===== جریمه/پاداش بر اساس زمان اولیه =====
    /** جریمه مرگ بر اساس زمان اولیه (ثانیه/تیک) */
    public static synchronized void applyDeathPenaltyByInitialTime(int initialSeconds) {
        if (initialSeconds < 0) initialSeconds = 0;
        score -= (2 * initialSeconds);
    }

    /** نسخه راحت با خود آبجکت مجروح (نیازمند getInitialTimeLimit) */
    public static synchronized void applyDeathPenalty(Injured injured) {
        if (injured == null) return;
        int initial = injured.getInitialTimeLimit();
        if (initial < 0) initial = 0;
        score -= (2 * initial);
    }

    /** پاداش نجات بر اساس زمان اولیه (ثانیه/تیک) */
    public static synchronized void applyRescueRewardByInitialTime(int initialSeconds) {
        if (initialSeconds < 0) initialSeconds = 0;
        score += (2 * initialSeconds);
    }

    /** نسخه راحت با خود آبجکت مجروح */
    public static synchronized void applyRescueReward(Injured injured) {
        if (injured == null) return;
        int initial = injured.getInitialTimeLimit();
        if (initial < 0) initial = 0;
        score += (2 * initial);
    }

    // ===== پاداش بر اساس شدت (اختیاری) =====
    public static synchronized void addRescueRewardBySeverity(InjurySeverity severity) {
        int reward = 0;
        if (severity == InjurySeverity.LOW) reward = 100;
        else if (severity == InjurySeverity.MEDIUM) reward = 175;
        else if (severity == InjurySeverity.CRITICAL) reward = 250;
        if (reward > 0) score += reward;
    }

    // ===== سازگاری با کد قدیمی (نمونه‌ای) =====
    public synchronized int getScoreInstance() { return getScore(); }
    public synchronized void setScoreInstance(int s) { setScore(s); }                   // ✅ اضافه شد
    public synchronized void setDefaultScoreInstance(int d) { setDefaultScore(d); }     // ✅ اضافه شد
    public synchronized int  getDefaultScoreInstance() { return getDefaultScore(); }     // ✅ اضافه شد
    public synchronized void resetToDefaultInstance() { resetToDefault(); }
    public synchronized void addInstance(int amount) { add(amount); }
    public synchronized void deductInstance(int amount) { deduct(amount); }
    public synchronized void applyDeathPenaltyByInitialTimeInstance(int s){ applyDeathPenaltyByInitialTime(s); }
    public synchronized void applyDeathPenaltyInstance(Injured injured){ applyDeathPenalty(injured); }
    public synchronized void applyRescueRewardByInitialTimeInstance(int s){ applyRescueRewardByInitialTime(s); }
    public synchronized void applyRescueRewardInstance(Injured injured){ applyRescueReward(injured); }
    public synchronized void addRescueRewardBySeverityInstance(InjurySeverity sev){ addRescueRewardBySeverity(sev); }
}
