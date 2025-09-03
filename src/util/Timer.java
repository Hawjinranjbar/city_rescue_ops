package util;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * تایمر شمارشی نخ‌امن برای مدیریت زمان باقی‌مانده (به واحد «ثانیه» یا هر واحد دلخواه).
 * سازگار با سناریوهای Save/Load/Restart:
 *  - start()/stop(): فعال/غیرفعال‌کردن کلی تایمر
 *  - pause()/resume(): مکث/ادامه برای فریز سراسری (مثلاً هنگام Save/Load)
 *  - reset(): بازگشت به مقدار اولیه (initialTime)
 *  - reset(int newTime): بازنویسی مقدار اولیه و شروع مجدد
 *  - restart(): هم‌ارز reset(initialTime)
 *  - tick()/tickBy(units): کاهش زمان
 *  - setRemainingTime(int): برای بازیابی زمان از اسنپ‌شات لود‌شده
 *
 * نکته: همهٔ متدها synchronized هستند تا در محیط Thread-Base ایمن باشند.
 */
public class Timer {

    /** زمان اولیه‌ای که تایمر با آن شروع شده (برای Restart/Reset) */
    private int initialTime;

    /** زمان باقی‌مانده */
    private int remainingTime;

    /** تایمر فعال است یا نه (خاموش/روشن کلی) */
    private boolean active;

    /** مکث موقت (برای فریز هنگام سیو/لود) */
    private boolean paused;

    // -------------------- سازنده‌ها --------------------

    /** سازنده با مقدار اولیه؛ تایمر فعال و غیر مکث شروع می‌شود. */
    public Timer(int startTime) {
        if (startTime < 0) startTime = 0;
        this.initialTime = startTime;
        this.remainingTime = startTime;
        this.active = true;
        this.paused = false;
    }

    /** سازندهٔ بدون پارامتر (در صورت نیاز به فریم‌ورک‌های reflection) */
    public Timer() {
        this(0);
    }

    // -------------------- عملیات زمان --------------------

    /** کاهش یک واحد از زمان (اگر active و !paused و remaining>0) */
    public synchronized void tick() {
        if (active && !paused && remainingTime > 0) {
            remainingTime--;
        }
    }

    /** کاهش چند واحد از زمان؛ اگر مقدار منفی بدهی، اثری ندارد. */
    public synchronized void tickBy(int units) {
        if (units <= 0) return;
        if (active && !paused && remainingTime > 0) {
            int dec = Math.min(units, remainingTime);
            remainingTime -= dec;
        }
    }

    // -------------------- کنترل حالت --------------------

    /** راه‌اندازی/فعال‌کردن تایمر (بدون تغییر مقدار باقی‌مانده) */
    public synchronized void start() {
        this.active = true;
        // ادامه می‌دهیم مگر اینکه قبلاً pause شده باشد
    }

    /** توقف کلی تایمر (دیگر tick اثر ندارد) */
    public synchronized void stop() {
        this.active = false;
    }

    /** مکث موقت (برای فریز سراسری حین Save/Load) */
    public synchronized void pause() {
        this.paused = true;
    }

    /** ادامه از حالت مکث */
    public synchronized void resume() {
        this.paused = false;
    }

    /** ریست به مقدار اولیه و فعال‌سازی مجدد */
    public synchronized void reset() {
        this.remainingTime = Math.max(0, initialTime);
        this.active = true;
        this.paused = false;
    }

    /** ریست با مقدار جدید و ثبت آن به عنوان مقدار اولیه */
    public synchronized void reset(int newTime) {
        if (newTime < 0) newTime = 0;
        this.initialTime = newTime;
        this.remainingTime = newTime;
        this.active = true;
        this.paused = false;
    }

    /** هم‌ارز reset(initialTime) برای وضوح در ری‌استارت کلی بازی */
    public synchronized void restart() {
        reset();
    }

    // -------------------- دسترسی/تنظیم مقادیر --------------------

    /** آیا زمان تمام شده است؟ */
    public synchronized boolean isFinished() {
        return remainingTime <= 0;
    }

    /** زمان باقی‌مانده را برمی‌گرداند. */
    public synchronized int getRemainingTime() {
        return remainingTime;
    }

    /**
     * ست‌کردن مستقیم زمان باقی‌مانده (برای بازیابی بعد از Load).
     * مقدار منفی به 0 بریده می‌شود.
     */
    public synchronized void setRemainingTime(int remainingTime) {
        if (remainingTime < 0) remainingTime = 0;
        this.remainingTime = remainingTime;
    }

    /** مقدار اولیه‌ای که با آن تایمر تعریف شده بود. */
    public synchronized int getInitialTime() {
        return initialTime;
    }

    /** تغییر مقدار اولیه (مثلاً در تغییر سطح سختی) */
    public synchronized void setInitialTime(int initialTime) {
        if (initialTime < 0) initialTime = 0;
        this.initialTime = initialTime;
        // remaining را دست نمی‌زنیم تا کنترل دست caller باشد
    }

    /** آیا تایمر فعال است؟ */
    public synchronized boolean isActive() {
        return active;
    }

    /** آیا تایمر در حالت مکث است؟ */
    public synchronized boolean isPaused() {
        return paused;
    }

    @Override
    public synchronized String toString() {
        return "Timer{initial=" + initialTime +
                ", remaining=" + remainingTime +
                ", active=" + active +
                ", paused=" + paused +
                '}';
    }
}
