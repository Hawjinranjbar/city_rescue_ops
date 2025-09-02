package victim;

import util.Position;
import util.Timer;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نمایندهٔ یک مجروح روی نقشه:
 * - موقعیت، شدت جراحت، تایمر نجات
 * - وضعیت‌ها: نجات‌شده / مرده / در حال نجات
 * - پشتیبانی از ناپدید شدن پس از مرگ/نجات (visible=false)
 * - نگهداری "زمان اولیهٔ تایمر" برای محاسبهٔ جریمهٔ مرگ (۲×زمان اولیه)
 */
public class Injured {

    // زمان‌های پیش‌فرض بر حسب ثانیه (یا واحد تیک)
    private static final int TIME_LOW_DEFAULT      = 180;
    private static final int TIME_MEDIUM_DEFAULT   = 120;
    private static final int TIME_CRITICAL_DEFAULT = 60;

    private final int id;                  // شناسه یکتا
    private final Position position;       // مختصات روی نقشه
    private final InjurySeverity severity; // شدت جراحت
    private final Timer rescueTimer;       // تایمر فرصت نجات
    private final int initialTimeLimit;    // زمان اولیهٔ تایمر (برای HUD/جریمه)

    private boolean isRescued;             // آیا نجات داده شده؟
    private boolean isDead;                // آیا مرده؟
    private boolean beingRescued;          // آیا در حال عملیات نجات است؟
    private boolean visible;               // کنترل رندر (بعد از مرگ/نجات false)

    // --- سازنده با زمان مشخص ---
    public Injured(int id, Position position, InjurySeverity severity, int timeLimit) {
        this.id = id;
        this.position = position;
        this.severity = severity;
        this.initialTimeLimit = timeLimit > 0 ? timeLimit : defaultTimeFor(severity);
        this.rescueTimer = new Timer(this.initialTimeLimit);
        this.isRescued = false;
        this.isDead = false;
        this.beingRescued = false;
        this.visible = true;
    }

    // --- سازنده با زمان پیش‌فرض بر اساس شدت ---
    public Injured(int id, Position position, InjurySeverity severity) {
        this(id, position, severity, defaultTimeFor(severity));
    }

    private static int defaultTimeFor(InjurySeverity sev) {
        if (sev == InjurySeverity.CRITICAL) return TIME_CRITICAL_DEFAULT;
        if (sev == InjurySeverity.MEDIUM)   return TIME_MEDIUM_DEFAULT;
        return TIME_LOW_DEFAULT;
    }

    // ===================== تایمر =====================
    /** کاهش یک تیک از تایمر (برای رفرش HUD یا منطق گیم‌لوپ) */
    public void tick() {
        if (isRescued || isDead) return;
        rescueTimer.tick();
    }

    /**
     * یک تیک کم می‌کند و اگر زمان تمام شده باشد، مجروح را "مرده" علامت می‌زند.
     * @return اگر همین حالا به مرگ رسید، true
     */
    public boolean updateAndCheckDeath() {
        if (isRescued || isDead) return false;
        rescueTimer.tick();
        if (rescueTimer.isFinished()) {
            markAsDead(); // ناپدید هم می‌شود
            return true;
        }
        return false;
    }

    // ===================== Getter ها =====================
    public int getId() { return id; }
    public Position getPosition() { return position; }
    public InjurySeverity getSeverity() { return severity; }
    public Timer getRescueTimer() { return rescueTimer; }

    /** باقیماندهٔ زمان (ثانیه/تیک) */
    public int getRemainingTime() { return rescueTimer.getRemainingTime(); }

    /** زمان اولیهٔ تایمر (برای HUD و محاسبهٔ جریمه) */
    public int getInitialTimeLimit() { return initialTimeLimit; }

    /** درصد زمان باقی‌مانده نسبت به زمان اولیه (۰..۱) */
    public float getTimePercent() {
        int rem = rescueTimer.getRemainingTime();
        int total = initialTimeLimit;
        if (total <= 0) return 0f;
        if (rem <= 0) return 0f;
        if (rem >= total) return 1f;
        return (float) rem / (float) total;
    }

    // ===================== وضعیت‌ها =====================
    public boolean isRescued() { return isRescued; }
    public boolean isDead() { return isDead; }
    public boolean isBeingRescued() { return beingRescued; }
    public void setBeingRescued(boolean beingRescued) { this.beingRescued = beingRescued; }
    public boolean isVisible() { return visible; }

    /** علامت‌گذاری به‌عنوان نجات‌یافته + ناپدید شدن از صحنه */
    public void markAsRescued() {
        if (isDead) return;
        isRescued = true;
        beingRescued = false;
        visible = false;     // ناپدید شود
        rescueTimer.stop();
    }

    /** علامت‌گذاری به‌عنوان مرده + ناپدید شدن از صحنه */
    public void markAsDead() {
        if (isRescued) return;
        isDead = true;
        beingRescued = false;
        visible = false;     // ناپدید شود
        rescueTimer.stop();
    }

    // ===================== محاسبات امتیاز =====================
    /**
     * جریمهٔ مرگ بر مبنای قانون پروژه:
     * penalty = 2 × initialTimeLimit
     */
    public int getDeathPenalty() {
        int base = initialTimeLimit;
        if (base < 0) base = 0;
        return 2 * base;
    }

    // ===================== منطق انتخاب =====================
    /** آیا این مجروح هنوز قابل نجات است؟ */
    public boolean canBeRescued() {
        return !isRescued && !isDead && !beingRescued && !rescueTimer.isFinished();
    }
}
