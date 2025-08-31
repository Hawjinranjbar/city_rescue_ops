
package map;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نوع هر سلولِ نقشه + قوانین عبور و هزینهٔ حرکت.
 * نکته: نام‌ها با نسخه‌های قبلی سازگار نگه داشته شده‌اند.
 */
public enum CellType {

    // قابل عبور
    ROAD(true, 1.0f, false),
    HOSPITAL(true, 1.0f, true),

    // غیرقابل عبور
    RUBBLE(false, Float.POSITIVE_INFINITY, false),   // آوار/مانع
    OBSTACLE(false, Float.POSITIVE_INFINITY, false), // سد/ماشین/دیوار و...
    BUILDING(false, Float.POSITIVE_INFINITY, false), // ساختمان
    EMPTY(false, Float.POSITIVE_INFINITY, false);    // سلول خالی/نامشخص

    /** آیا روی این نوع سلول می‌توان راه رفت؟ */
    private final boolean walkable;
    /** هزینهٔ حرکت (برای الگوریتم‌های مسیریابی؛ ROAD/HOSPITAL = 1.0) */
    private final float moveCost;
    /** آیا این نوع سلول «نقطهٔ تحویل» (بیمارستان) است؟ */
    private final boolean hospital;

    CellType(boolean walkable, float moveCost, boolean hospital) {
        this.walkable = walkable;
        this.moveCost = moveCost;
        this.hospital = hospital;
    }

    /** true اگر تایل قابل‌عبور باشد. */
    public boolean isWalkable() { return walkable; }

    /** هزینهٔ حرکت روی این تایل. برای موانع «بی‌نهایت» است. */
    public float getMoveCost() { return moveCost; }

    /** true اگر این تایل بیمارستان (نقطهٔ تحویل) باشد. */
    public boolean isHospital() { return hospital; }

    /** true اگر این تایل مانع/غیرقابل عبور باشد. */
    public boolean isBlocked() { return !walkable; }

    // ===================== ابزار نگاشت از TMX =====================

    /**
     * نگاشت ایمن از مقدار property در TMX به CellType.
     * @param typeStr مقدار property با کلید "type" (مثلاً road/hospital/rubble/obstacle/building/empty)
     * @param walkableOpt مقدار property با کلید "walkable" اگر موجود بود (nullable)
     * @return نوع سلول مطابق ورودی‌ها
     *
     * قواعد:
     * - اگر typeStr مشخص و معتبر باشد همان برگردانده می‌شود.
     * - اگر typeStr خالی بود و walkableOpt=true → ROAD
     * - اگر typeStr خالی بود و walkableOpt=false → RUBBLE
     * - سینونیم‌ها: debris=rubble، car/vehicle/wall=obstacle، clinic=hospital، street=road
     */
    public static CellType parse(String typeStr, Boolean walkableOpt) {
        String s = (typeStr == null) ? "" : typeStr.trim().toLowerCase();

        switch (s) {
            case "road":
            case "street":
            case "asphalt":
                return ROAD;

            case "hospital":
            case "clinic":
                return HOSPITAL;

            case "rubble":
            case "debris":
                return RUBBLE;

            case "obstacle":
            case "barrier":
            case "car":
            case "vehicle":
            case "wall":
                return OBSTACLE;

            case "building":
            case "house":
                return BUILDING;

            case "empty":
            case "":
                // تصمیم بر اساس walkable
                if (walkableOpt != null) return walkableOpt ? ROAD : RUBBLE;
                return EMPTY;

            default:
                // ناشناس → اگر walkable مشخص بود، از آن نتیجه بگیر
                if (walkableOpt != null) return walkableOpt ? ROAD : RUBBLE;
                return EMPTY;
        }
    }
}
