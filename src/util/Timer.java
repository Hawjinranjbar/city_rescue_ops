package util;

// --------------------
// لایه: Utility Layer
// --------------------
// این کلاس برای مدیریت زمان‌های شمارشی استفاده میشه
// مثلاً زمان باقی‌مونده برای نجات یه مجروح یا کل زمان بازی
public class Timer {

    private int remainingTime;  // زمان باقی‌مونده به ثانیه (یا هر واحد دلخواه)
    private boolean active;     // نشون می‌ده تایمر فعاله یا نه

    // سازنده با مقدار اولیه
    public Timer(int startTime) {
        this.remainingTime = startTime;
        this.active = true;
    }

    // کاهش دادن زمان به اندازه یک واحد
    public void tick() {
        if (active && remainingTime > 0) {
            remainingTime--;
        }
    }

    // ریست کردن تایمر با مقدار جدید
    public void reset(int newTime) {
        this.remainingTime = newTime;
        this.active = true;
    }

    // توقف تایمر (مثلاً مجروح نجات پیدا کرده)
    public void stop() {
        this.active = false;
    }

    // فعال کردن تایمر مجدد
    public void start() {
        this.active = true;
    }

    // بررسی اینکه تایمر به پایان رسیده یا نه
    public boolean isFinished() {
        return remainingTime <= 0;
    }

    // گرفتن مقدار زمان باقی‌مونده
    public int getRemainingTime() {
        return remainingTime;
    }

    // بررسی اینکه تایمر فعاله یا نه
    public boolean isActive() {
        return active;
    }
}
