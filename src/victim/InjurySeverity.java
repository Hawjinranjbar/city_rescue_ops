package victim;

// --------------------
// لایه: Domain Layer
// --------------------
// این enum شدت جراحت مجروح‌ها رو مشخص می‌کنه
// برای تعیین اولویت نجات و امتیازدهی استفاده می‌شه
public enum InjurySeverity {

    LOW(1, 250),
    MEDIUM(2, 175),
    CRITICAL(3, 100);

    private final int priorityLevel;   // اولویت نجات: هر چی بالاتر، حساس‌تر
    private final int rescuePoints;    // امتیازی که بازیکن با نجات این سطح می‌گیره

    // سازنده خصوصی برای مقداردهی به هر گزینه
    InjurySeverity(int priorityLevel, int rescuePoints) {
        this.priorityLevel = priorityLevel;
        this.rescuePoints = rescuePoints;
    }

    // گرفتن اولویت
    public int getPriorityLevel() {
        return priorityLevel;
    }

    // گرفتن امتیاز مربوط به نجات
    public int getRescuePoints() {
        return rescuePoints;
    }
}
