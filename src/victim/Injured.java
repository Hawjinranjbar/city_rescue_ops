package victim;

import util.Position;
import util.Timer;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس نمایندهٔ یک مجروح توی نقشه‌ست
// شامل موقعیت، شدت جراحت، تایمر نجات و وضعیت نجات/مرگ
public class Injured {

    private final int id;                      // شناسه یکتا برای هر مجروح
    private final Position position;           // موقعیت مجروح روی نقشه
    private final InjurySeverity severity;     // شدت جراحت (LOW, MEDIUM, CRITICAL)
    private final Timer rescueTimer;           // تایمر باقی‌مانده برای نجات
    private boolean isRescued;                 // آیا نجات داده شده؟
    private boolean isDead;                    // آیا مرده؟
    private boolean beingRescued;              // آیا در حال نجات است؟

    // سازنده
    public Injured(int id, Position position, InjurySeverity severity, int timeLimit) {
        this.id = id;
        this.position = position;
        this.severity = severity;
        this.rescueTimer = new Timer(timeLimit);
        this.isRescued = false;
        this.isDead = false;
        this.beingRescued = false;
    }

    // شناسه مجروح
    public int getId() {
        return id;
    }

    // گرفتن موقعیت
    public Position getPosition() {
        return position;
    }

    // شدت جراحت
    public InjurySeverity getSeverity() {
        return severity;
    }

    // گرفتن تایمر
    public Timer getRescueTimer() {
        return rescueTimer;
    }

    // چک نجات
    public boolean isRescued() {
        return isRescued;
    }

    // چک مرگ
    public boolean isDead() {
        return isDead;
    }

    // مارک کردن به‌عنوان نجات‌یافته
    public void markAsRescued() {
        isRescued = true;
        beingRescued = false;
        rescueTimer.stop();
    }

    // مارک کردن به‌عنوان مرده
    public void markAsDead() {
        isDead = true;
        beingRescued = false;
        rescueTimer.stop();
    }

    // بررسی اینکه نجات ممکنه یا نه
    public boolean isBeingRescued() { return beingRescued; }

    public void setBeingRescued(boolean beingRescued) { this.beingRescued = beingRescued; }

    public boolean canBeRescued() {
        return !isRescued && !isDead && !beingRescued && !rescueTimer.isFinished();
    }
}
