package agent;

import util.Position;
import victim.Injured;

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس نماینده‌ی یک نجات‌دهنده‌ست
// که می‌تونه در نقشه حرکت کنه، مجروح رو برداره و به بیمارستان برسونه
public class Rescuer {

    private final int id;                   // شناسه یکتا برای هر نجات‌دهنده
    private Position position;              // موقعیت فعلی در نقشه
    private boolean isBusy;                 // آیا در حال انجام عملیات نجاته؟
    private Injured carryingVictim;         // اگه مجروحی همراهشه، اینجا نگه‌داری میشه

    // سازنده
    public Rescuer(int id, Position startPos) {
        this.id = id;
        this.position = startPos;
        this.isBusy = false;
        this.carryingVictim = null;
    }

    public int getId() {
        return id;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position newPos) {
        this.position = newPos;
    }

    public boolean isBusy() {
        return isBusy;
    }

    // گرفتن مجروح توسط نجات‌دهنده
    public void pickUp(Injured victim) {
        if (!isBusy) {
            this.carryingVictim = victim;
            this.isBusy = true;
        }
    }

    // آزاد کردن نجات‌دهنده بعد از تحویل مجروح به بیمارستان
    public void dropVictim() {
        if (carryingVictim != null) {
            carryingVictim.markAsRescued();
            carryingVictim = null;
            isBusy = false;
        }
    }

    // گرفتن مجروحی که باهاشه (در حال حمل)
    public Injured getCarryingVictim() {
        return carryingVictim;
    }

    // آیا نجات‌دهنده الان مجروح حمل می‌کنه؟
    public boolean isCarryingVictim() {
        return carryingVictim != null;
    }
}
