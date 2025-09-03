package strategy;

import util.Position;

import java.util.Collections;
import java.util.List;

/**
 * --------------------
 * لایه: Strategy Layer
 * --------------------
 * اینترفیس مسیر‌یابی (A*، دایکسترا، ...).
 * - متد اصلی findPath(from,to) همچنان پابرجاست (Backward-Compatible).
 * - خروجی جزئیاتی اختیاری: PathResult (کلاس توکار).
 * - ورودی اختیاری: PathOptions (کلاس توکار) برای تنظیماتی مثل سقف نودها.
 * - بدون استفاده از Stream/Lambda.
 */
public interface IPathFinder {

    /**
     * پیدا کردن مسیر بین دو موقعیت.
     * @param from موقعیت شروع
     * @param to   موقعیت مقصد
     * @return لیست موقعیت‌هایی که نجات‌دهنده باید طی کند (در صورت عدم موفقیت: لیست خالی)
     */
    List<Position> findPath(Position from, Position to);

    // ------------------------------------------------------------
    // متدهای پیش‌فرض (اختیاری) برای جزئیات بیشتر/تنظیمات اضافه
    // پیاده‌سازی‌های فعلی نیازی به تغییر ندارند.
    // ------------------------------------------------------------

    /**
     * همان متد بالا، ولی با سقف گسترش نودها (برای جلوگیری از جستجوی سنگین/بی‌پایان).
     * پیاده‌سازی پیش‌فرض، مسیر ساده را صدا می‌زند و PathResult ساده می‌سازد.
     */
    default PathResult findPathDetailed(Position from, Position to, PathOptions options) {
        // پیاده‌سازی‌های کنونی که فقط findPath(from,to) دارند، از این مسیر می‌آیند:
        List<Position> path = findPath(from, to);
        boolean success = path != null && !path.isEmpty();
        PathResult r = new PathResult();
        r.path = (path != null) ? path : Collections.<Position>emptyList();
        r.success = success;
        r.totalCost = success ? (r.path.size() > 1 ? r.path.size() - 1 : 0) : Integer.MAX_VALUE;
        r.expandedNodes = -1; // نامشخص (پیاده‌سازیِ ساده این مقدار را ست نمی‌کند)
        return r;
    }

    /**
     * نسخه‌ی راحت‌تر: بدون options.
     */
    default PathResult findPathDetailed(Position from, Position to) {
        return findPathDetailed(from, to, new PathOptions());
    }

    // ------------------------------------------------------------
    // کلاس‌های توکار برای خروجی/تنظیمات (بدون ساخت فایل جدید)
    // ------------------------------------------------------------

    /**
     * نتیجه‌ی مسیر‌یابی با جزئیات (خروجی اختیاری).
     */
    public static class PathResult {
        List<Position> path;   // مسیر (خالی اگر ناموفق)
        boolean success;       // آیا مقصد قابل دسترس بود؟
        int totalCost;         // هزینه‌ی مسیر (معمولاً تعداد قدم‌ها). اگر ناموفق: Integer.MAX_VALUE
        int expandedNodes;     // تعداد نودهای گسترش‌داده‌شده (اگر پیاده‌سازی مقداردهی کند، وگرنه -1)

        public List<Position> getPath() { return path; }
        public boolean isSuccess() { return success; }
        public int getTotalCost() { return totalCost; }
        public int getExpandedNodes() { return expandedNodes; }

        public String toString() {
            return "PathResult{success=" + success +
                    ", totalCost=" + totalCost +
                    ", expandedNodes=" + expandedNodes +
                    ", pathLen=" + (path != null ? path.size() : 0) + "}";
        }
    }

    /**
     * گزینه‌های مسیر‌یابی (ورودی اختیاری).
     * می‌توانی بعداً فیلدهای بیشتری اضافه کنی (مثل diagonalMoves، وزن‌ها، …).
     */
    public static class PathOptions {
        /** سقف نودهای قابل گسترش؛ <=0 یعنی بدون محدودیت. */
        private int maxExpandedNodes = 0;

        /** اجازه حرکت مورب (اگر پشتیبانی شد). */
        private boolean allowDiagonals = false;

        public int getMaxExpandedNodes() { return maxExpandedNodes; }
        public void setMaxExpandedNodes(int maxExpandedNodes) { this.maxExpandedNodes = maxExpandedNodes; }

        public boolean isAllowDiagonals() { return allowDiagonals; }
        public void setAllowDiagonals(boolean allowDiagonals) { this.allowDiagonals = allowDiagonals; }
    }
}
