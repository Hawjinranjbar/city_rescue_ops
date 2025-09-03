package strategy;

import map.CityMap;
import util.Position;

import java.util.*;

/**
 * --------------------
 * لایه: Strategy Layer
 * --------------------
 * A* Pathfinding بدون Stream/Lambda
 * - استفاده از CityMap.getWalkableNeighbors(...) برای همسایه‌های قابل عبور
 * - heuristic بر اساس فاصلهٔ مانهتن (فرض: Position.distanceTo من‌هتن است)
 * - تایی‌بریک روی gScore در صورت برابر بودن fScore
 * - شامل closedSet برای جلوگیری از پردازش تکراری
 * - چک ایمنی برای start/goal و ظرفیت جستجو
 *
 * افزوده‌های این نسخه:
 * - debugEnabled برای چاپ جزئیات جستجو (اختیاری)
 * - returnClosestOnFail: در صورت نیافتن مسیر، بهترین تقریب تا نزدیک‌ترین نود به goal را برگردان
 * - گزارش تعداد نودهای گسترش‌یافته
 */
public class AStarPathFinder implements IPathFinder {

    private final CityMap cityMap;

    /** محدودیت اختیاری روی تعداد نودهای پردازش‌شونده برای جلوگیری از جستجوی بی‌پایان. 0 یعنی بدون محدودیت. */
    private int maxExpandedNodes = 0;

    /** اگر true: در صورت شکست، مسیر تا نزدیک‌ترین نقطه‌ی دیده‌شده به goal برمی‌گردد. */
    private boolean returnClosestOnFail = false;

    /** اگر true: جزئیات در کنسول چاپ می‌شود. */
    private boolean debugEnabled = false;

    public AStarPathFinder(CityMap cityMap) {
        this.cityMap = cityMap;
    }

    /** ست‌کردن سقف نودهای قابل‌گسترش (اختیاری). مقدار <=0 یعنی نامحدود. */
    public void setMaxExpandedNodes(int limit) {
        this.maxExpandedNodes = limit;
    }

    /** فعال/غیرفعال کردن بازگشت مسیر تقریبی هنگام شکست. */
    public void setReturnClosestOnFail(boolean enabled) {
        this.returnClosestOnFail = enabled;
    }

    /** فعال/غیرفعال کردن لاگ. */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    @Override
    public List<Position> findPath(Position start, Position goal) {
        // --- چک‌های ایمنی ---
        if (cityMap == null || start == null || goal == null) return Collections.emptyList();
        if (start.equals(goal)) {
            List<Position> single = new ArrayList<Position>();
            single.add(start);
            return single;
        }
        // نکته: اگر CityMap متد isWalkable دارد، می‌توان اینجا چک کرد:
        // if (!cityMap.isWalkable(start) || !cityMap.isWalkable(goal)) return Collections.emptyList();

        // --- ساختارهای A* ---
        final Map<Position, Position> cameFrom = new HashMap<Position, Position>();
        final Map<Position, Integer> gScore = new HashMap<Position, Integer>();
        final Map<Position, Integer> fScore = new HashMap<Position, Integer>();

        // Comparator بدون لامبدا: fScore کمتر اولویت بالاتر
        Comparator<Position> cmp = new Comparator<Position>() {
            @Override
            public int compare(Position a, Position b) {
                int fa = getScoreSafe(fScore, a);
                int fb = getScoreSafe(fScore, b);
                if (fa != fb) return (fa < fb) ? -1 : 1;
                // تایی‌بریک: gScore کمتر بهتر
                int ga = getScoreSafe(gScore, a);
                int gb = getScoreSafe(gScore, b);
                if (ga != gb) return (ga < gb) ? -1 : 1;
                // تایی‌بریک دوم: فاصله‌ی heuristic تا مقصد
                int ha = heuristic(a, goal);
                int hb = heuristic(b, goal);
                if (ha != hb) return (ha < hb) ? -1 : 1;
                // در نهایت برای ثبات، مختصات
                if (a.getY() != b.getY()) return (a.getY() < b.getY()) ? -1 : 1;
                if (a.getX() != b.getX()) return (a.getX() < b.getX()) ? -1 : 1;
                return 0;
            }
        };

        PriorityQueue<Position> openSet = new PriorityQueue<Position>(64, cmp);
        Set<Position> openLookup = new HashSet<Position>();
        Set<Position> closedSet = new HashSet<Position>();

        gScore.put(start, 0);
        fScore.put(start, heuristic(start, goal));
        openSet.add(start);
        openLookup.add(start);

        int expanded = 0;

        // برای حالت Closest-on-Fail: بهترین تقریبی که تاکنون دیده‌ایم
        Position bestSoFar = start;
        int bestSoFarH = heuristic(start, goal);

        if (debugEnabled) {
            System.out.println("[A*] start=" + coord(start) + " goal=" + coord(goal));
        }

        // --- حلقه‌ی اصلی A* ---
        while (!openSet.isEmpty()) {
            Position current = openSet.poll();
            openLookup.remove(current);

            if (current.equals(goal)) {
                if (debugEnabled) {
                    System.out.println("[A*] Reached goal. Expanded=" + expanded);
                }
                return reconstructPath(cameFrom, current);
            }

            if (closedSet.contains(current)) {
                continue;
            }
            closedSet.add(current);

            // به‌روزرسانی بهترین تقریب
            int hCur = heuristic(current, goal);
            if (hCur < bestSoFarH) {
                bestSoFar = current;
                bestSoFarH = hCur;
            }

            // محدودیت اختیاری برای جلوگیری از جستجوی سنگین
            expanded++;
            if (maxExpandedNodes > 0 && expanded > maxExpandedNodes) {
                if (debugEnabled) {
                    System.out.println("[A*] Reached node expansion limit (" + maxExpandedNodes + ").");
                }
                if (returnClosestOnFail && bestSoFar != null && !bestSoFar.equals(start)) {
                    if (debugEnabled) {
                        System.out.println("[A*] Return closest-on-fail from " + coord(bestSoFar));
                    }
                    return reconstructPath(cameFrom, bestSoFar);
                }
                return Collections.emptyList();
            }

            // همسایه‌های قابل عبور از CityMap
            List<Position> neighbors = cityMap.getWalkableNeighbors(current);
            if (neighbors == null || neighbors.isEmpty()) continue;

            for (int i = 0; i < neighbors.size(); i++) {
                Position nb = neighbors.get(i);
                if (nb == null) continue;
                if (closedSet.contains(nb)) continue;

                int tentativeG = getScoreSafe(gScore, current) + 1; // هزینه‌ی یکنواخت 1 برای هر قدم

                Integer nbG = gScore.get(nb);
                if (nbG == null || tentativeG < nbG.intValue()) {
                    cameFrom.put(nb, current);
                    gScore.put(nb, tentativeG);
                    fScore.put(nb, tentativeG + heuristic(nb, goal));

                    if (!openLookup.contains(nb)) {
                        openSet.add(nb);
                        openLookup.add(nb);
                    } else {
                        // PriorityQueue decrease-key ندارد → دوباره add تا با مقایسه‌گر در جای درست بنشیند.
                        openSet.add(nb);
                    }
                }
            }
        }

        if (debugEnabled) {
            System.out.println("[A*] No path to goal. Expanded=" + expanded);
        }

        // مسیر پیدا نشد
        if (returnClosestOnFail && bestSoFar != null && !bestSoFar.equals(start)) {
            if (debugEnabled) {
                System.out.println("[A*] Return closest-on-fail from " + coord(bestSoFar));
            }
            return reconstructPath(cameFrom, bestSoFar);
        }
        return Collections.emptyList();
    }

    // --- heuristic (مانهتن) ---
    private int heuristic(Position a, Position b) {
        return a.distanceTo(b); // فرض: distanceTo = |dx| + |dy|
    }

    // --- بازسازی مسیر از goal به start ---
    private List<Position> reconstructPath(Map<Position, Position> cameFrom, Position current) {
        List<Position> path = new ArrayList<Position>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    // --- گرفتن امتیاز ایمن از Mapها (برای جلوگیری از NPE) ---
    private int getScoreSafe(Map<Position, Integer> scores, Position p) {
        Integer v = scores.get(p);
        return v != null ? v.intValue() : Integer.MAX_VALUE / 4;
    }

    private String coord(Position p) {
        if (p == null) return "(null)";
        return "(" + p.getX() + "," + p.getY() + ")";
    }
}
