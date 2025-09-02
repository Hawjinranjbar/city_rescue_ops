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
 */
public class Injured {

    // زمان‌های پیش‌فرض بر حسب ثانیه (یا واحد تیک)
    private static final int TIME_LOW_DEFAULT      = 180;
    private static final int TIME_MEDIUM_DEFAULT   = 120;
    private static final int TIME_CRITICAL_DEFAULT = 60;

    private final int id;                 // شناسه یکتا
    private final Position position;      // مختصات روی نقشه
    private final InjurySeverity severity;// شدت جراحت
    private final Timer rescueTimer;      // تایمر فرصت نجات

    private boolean isRescued;            // آیا نجات داده شده؟
    private boolean isDead;               // آیا مرده؟
    private boolean beingRescued;         // آیا در حال عملیات نجات است؟

    // --- سازنده با زمان مشخص ---
    public Injured(int id, Position position, InjurySeverity severity, int timeLimit) {
        this.id = id;
        this.position = position;
        this.severity = severity;
        this.rescueTimer = new Timer(timeLimit);
        this.isRescued = false;
        this.isDead = false;
        this.beingRescued = false;
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
    /** کاهش یک تیک از تایمر */
    public void tick() {
        if (isRescued || isDead) return;
        rescueTimer.tick();
    }

    /** اگر زمان تمام شد و نجات داده نشده بود، او را مرده می‌کند. */
    public boolean updateAndCheckDeath() {
        if (isRescued || isDead) return false;
        rescueTimer.tick();
        if (rescueTimer.isFinished()) {
            markAsDead();
            return true;
        }
        return false;
    }

    // ===================== Getter ها =====================
    public int getId() { return id; }
    public Position getPosition() { return position; }
    public InjurySeverity getSeverity() { return severity; }
    public Timer getRescueTimer() { return rescueTimer; }

    public int getRemainingTime() { return rescueTimer.getRemainingTime(); }

    public float getTimePercent() {
        int rem = rescueTimer.getRemainingTime();
        int total = defaultTimeFor(severity);
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

    public void markAsRescued() {
        if (isDead) return;
        isRescued = true;
        beingRescued = false;
        rescueTimer.stop();
    }

    public void markAsDead() {
        if (isRescued) return;
        isDead = true;
        beingRescued = false;
        rescueTimer.stop();
    }

    // ===================== منطق انتخاب =====================
    /** آیا این مجروح هنوز قابل نجات است؟ */
    public boolean canBeRescued() {
        return !isRescued && !isDead && !beingRescued && !rescueTimer.isFinished();
    }
}
