package victim;

import map.CityMap;
import map.Cell;
import map.Hospital;
import util.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * --------------------
 * لایه: Application/Domain Helper
 * --------------------
 * قوانین جای‌گذاری مجروح‌ها:
 *  - فقط روی "آوار" (ترجیحاً سلول‌های غیرقابل‌عبور)
 *  - هرگز روی جاده
 *  - هرگز روی بیمارستان (و اختیاری: فاصلهٔ امن از بیمارستان)
 *
 * نکته: اگر enum نوع سلول‌ها در پروژه‌ات فرق دارد،
 *      متد isRoad/isHospital/isRubble را مطابق نام‌هایت تنظیم کن.
 */
public final class VictimSpawner {

    /** حداقل فاصله بر حسب تایل از هر بیمارستان (اختیاری). */
    private static final int MIN_DIST_FROM_HOSPITAL = 3;

    private VictimSpawner() { /* no-op */ }

    /**
     * همهٔ موقعیت‌های مجاز برای مجروح‌ها را پیدا می‌کند.
     */
    public static List<Position> findAllValidSpots(CityMap map, List<Hospital> hospitals) {
        List<Position> spots = new ArrayList<Position>();
        if (map == null) return spots;

        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Cell c = map.getCell(x, y);
                if (c == null) continue;

                if (!isValidVictimCell(map, c, x, y, hospitals)) continue;

                spots.add(new Position(x, y));
            }
        }
        return spots;
    }

    /**
     * اسپاون کردن N مجروح روی نقاط مجاز (به‌صورت تصادفی و بدون تداخل).
     *
     * @param map         نقشه
     * @param hospitals   لیست بیمارستان‌ها (می‌تواند خالی باشد)
     * @param manager     VictimManager برای افزودن
     * @param count       تعداد مجروح
     * @param random      RNG (اگر null بود، داخلی ساخته می‌شود)
     * @param startId     شروع شناسه برای مجروح‌ها
     */
    public static void spawnVictimsOnRubble(CityMap map,
                                            List<Hospital> hospitals,
                                            VictimManager manager,
                                            int count,
                                            Random random,
                                            int startId)
    {
        if (manager == null || map == null) return;
        List<Position> candidates = findAllValidSpots(map, hospitals);
        if (candidates.isEmpty()) return;

        if (random == null) random = new Random();
        Collections.shuffle(candidates, random);

        int placed = 0;
        int id = startId;

        for (int i = 0; i < candidates.size() && placed < count; i++) {
            Position p = candidates.get(i);

            // اگر سلول اشغال باشد، رد کن
            Cell c = map.getCell(p.getX(), p.getY());
            if (c != null && c.isOccupied()) continue;

            // شدت جراحت را می‌توان بر اساس RNG تعیین کرد
            InjurySeverity sev = randomSeverity(random);

            // زمان نجات (ثانیه) بر اساس شدت
            int timeLimit = (sev == InjurySeverity.CRITICAL) ? 30
                    : (sev == InjurySeverity.MEDIUM)  ? 60
                    : 90;

            Injured inj = new Injured(id++, p, sev, timeLimit);
            manager.addInjured(inj);

            // اگر خواستی سلول را اشغال علامت بزنی
            if (c != null) c.setOccupied(true);

            placed++;
        }
    }

    // ---------------------- قوانین سلول ----------------------

    /** فقط جاده را تشخیص می‌دهد. مطابق enum پروژه‌ات تنظیم کن. */
    private static boolean isRoad(Cell c) {
        // اگر enum داری: Cell.Type.ROAD
        return c.getType() == Cell.Type.ROAD;
    }

    /** فقط بیمارستان را تشخیص می‌دهد. مطابق enum پروژه‌ات تنظیم کن. */
    private static boolean isHospital(Cell c) {
        // اگر enum داری: Cell.Type.HOSPITAL
        return c.getType() == Cell.Type.HOSPITAL;
    }

    /**
     * تشخیص "آوار":
     *  - اگر enum اختصاصی داری (RUBBLE / RUINS / WRECK) همان را چک کن.
     *  - در غیر این صورت، به شکل محافظه‌کارانه: سلول غیرقابل‌عبور و نه جاده/بیمارستان
     */
    private static boolean isRubble(Cell c) {
        // اگر Type اختصاصی داری، این‌ها را فعال کن:
        // if (c.getType() == Cell.Type.RUBBLE) return true;
        // if (c.getType() == Cell.Type.WRECK) return true;
        // if (c.getType() == Cell.Type.RUINS) return true;

        // fallback عمومی:
        return (!c.isWalkable()) && (!isRoad(c)) && (!isHospital(c));
    }

    /** آیا سلول برای مجروح مناسب است؟ */
    private static boolean isValidVictimCell(CityMap map, Cell c, int x, int y, List<Hospital> hospitals) {
        if (c == null) return false;

        // هرگز روی جاده یا بیمارستان
        if (isRoad(c)) return false;
        if (isHospital(c)) return false;

        // فقط روی آوار
        if (!isRubble(c)) return false;

        // فاصلهٔ امن از بیمارستان‌ها
        if (hospitals != null && !hospitals.isEmpty()) {
            for (int i = 0; i < hospitals.size(); i++) {
                Hospital h = hospitals.get(i);
                Position hp = hospitalPosition(h);
                if (hp != null) {
                    int dx = x - hp.getX();
                    int dy = y - hp.getY();
                    int manhattan = Math.abs(dx) + Math.abs(dy);
                    if (manhattan < MIN_DIST_FROM_HOSPITAL) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * گرفتن یک مختصات نماینده از بیمارستان.
     * اگر Hospital کلاس‌های دیگری مثل getArea()/getEntrance() دارد،
     * این متد را مطابق آن تغییر بده.
     */
    private static Position hospitalPosition(Hospital h) {
        if (h == null) return null;
        // اگر Hospital متدی مثل getPosition() دارد:
        try {
            Position p = h.getPosition();
            if (p != null) return p;
        } catch (Throwable ignored) { }
        // اگر محدوده دارد، می‌توانی مرکز تقریبی را برگردانی.
        // فعلاً null: یعنی فقط نوع سلول HOSPITAL را فیلتر کردیم.
        return null;
    }

    /** انتخاب شدت جراحت به‌صورت تصادفیِ ساده. */
    private static InjurySeverity randomSeverity(Random r) {
        int k = r.nextInt(100);
        if (k < 20) return InjurySeverity.CRITICAL; // 20%
        if (k < 60) return InjurySeverity.MEDIUM;   // 40%
        return InjurySeverity.LOW;                  // 40%
    }
}
