package controller;

import victim.Injured;

// --------------------
// لایه: Application Layer
// --------------------
// این کلاس امتیاز کل بازی رو مدیریت می‌کنه
// نجات مجروح امتیاز مثبت، مرگ مجروح امتیاز منفی داره
public class ScoreManager {

    private int score;

    public ScoreManager() {
        this.score = 500; // امتیاز اولیه بازیکن
    }

    // امتیاز مربوط به نجات یک مجروح
    public void reward(Injured victim) {
        score += victim.getSeverity().getRescuePoints();
    }

    // جریمه بابت مرگ یک مجروح
    public void penalize() {
        score -= 250;
    }

    public int getScore() {
        return score;
    }

    // ریست امتیاز (مثلاً در شروع بازی جدید)
    public void reset() {
        score = 500;
    }
}
