
package map;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نوعِ هر سلولِ نقشه + قوانین عبور و کُست حرکت.
 * نکته: نام‌ها با نسخهٔ قبلی یکسان نگه داشته شده‌اند.
 */
public enum CellType {

    // قابل عبور
    ROAD(true, 1.0f, false),
    HOSPITAL(true, 1.0f, true),

    // غیرقابل عبور
    OBSTACLE(false, Float.POSITIVE_INFINITY, false),
    BUILDING(false, Float.POSITIVE_INFINITY, false),
    EMPTY(false, Float.POSITIVE_INFINITY, false);

    /** آیا روی این نوع سلول می‌توان راه رفت؟ */
    private final boolean walkable;

    /** هزینهٔ حرکت (برای الگوریتم‌های مسیریابی؛ ROAD/HOSPITAL = 1.0) */
    private final float moveCost;

    /** آیا این نوع سلول نقش «نقطهٔ تحویل» (بیمارستان) دارد؟ */
    private final boolean hospital;

    CellType(boolean walkable, float moveCost, boolean hospital) {
        this.walkable = walkable;
        this.moveCost = moveCost;
        this.hospital = hospital;
    }

    /** true اگر تایل قابل‌عبور باشد. */
    public boolean isWalkable() {
        return walkable;
    }

    /** هزینهٔ حرکت روی این تایل. برای موانع «بی‌نهایت» است. */
    public float getMoveCost() {
        return moveCost;
    }

    /** true اگر این تایل بیمارستان (نقطهٔ تحویل) باشد. */
    public boolean isHospital() {
        return hospital;
    }
}

