package agent;

import map.CityMap;
import map.Hospital;
import strategy.IPathFinder;
import strategy.IAgentDecision;
import util.MoveGuard;
import util.Position;
import victim.Injured;

import java.util.List;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * کنترل رفتار نجات‌دهنده:
 *  - انتخاب مجروح هدف (با IAgentDecision)
 *  - مسیریابی تا مجروح/بیمارستان (با IPathFinder)
 *  - حرکت امن روی نقشه (با MoveGuard)
 */
public class AgentController {

    private final IPathFinder pathFinder;
    private final IAgentDecision decisionLogic;
    private final CityMap map;

    public AgentController(CityMap map, IPathFinder pathFinder, IAgentDecision decisionLogic) {
        this.map = map;
        this.pathFinder = pathFinder;
        this.decisionLogic = decisionLogic;
    }

    /**
     * اجرای یک تیک از رفتار ریسکیور.
     * اگر در حال حمل نیست: مجروح هدف را انتخاب کرده، به سمتش می‌رود و اگر رسید، او را برمی‌دارد.
     * اگر در حال حمل است: کوتاه‌ترین مسیر تا نزدیک‌ترین بیمارستان را رفته و اگر رسید، تحویل می‌دهد.
     */
    public void performAction(Rescuer rescuer, List<Injured> candidates, List<Hospital> hospitals) {
        if (rescuer == null || map == null) return;

        // --- 1) اگر در حال حمل نیست: به سمت مجروح هدف برو ---
        if (!rescuer.isBusy()) {
            Injured target = (decisionLogic != null)
                    ? decisionLogic.selectVictim(rescuer, candidates)
                    : null;

            if (target != null && !target.isDead() && !target.isRescued() && target.getPosition() != null) {
                target.setBeingRescued(true);

                List<Position> pathToVictim = (pathFinder != null)
                        ? pathFinder.findPath(rescuer.getPosition(), target.getPosition())
                        : null;

                boolean reached = moveAlongPath(rescuer, pathToVictim);
                if (reached) {
                    rescuer.pickUp(target);
                } else {
                    // اگر به هر دلیل نرسید (مسیر مسدود شد)، فلگ را آزاد کن
                    target.setBeingRescued(false);
                }
            }
            return; // همین تیک کافی است؛ تیک بعدی اگر حمل می‌کند می‌رود بیمارستان
        }

        // --- 2) اگر در حال حمل است: به نزدیک‌ترین بیمارستان برو ---
        if (rescuer.isCarryingVictim()) {
            Injured carried = rescuer.getCarryingVictim();
            Hospital nearest = findNearestHospital(rescuer.getPosition(), hospitals);
            if (nearest == null || nearest.getPosition() == null) return;

            List<Position> pathToHospital = (pathFinder != null)
                    ? pathFinder.findPath(rescuer.getPosition(), nearest.getPosition())
                    : null;

            boolean delivered = moveAlongPath(rescuer, pathToHospital);
            if (delivered) {
                rescuer.dropVictim();
                if (carried != null) carried.setBeingRescued(false);
            }
        }
    }

    // -------------------- کمکی‌های حرکت --------------------

    /** حرکت طبق مسیر؛ اگر به آخر مسیر برسد true برمی‌گرداند. */
    private boolean moveAlongPath(Rescuer rescuer, List<Position> path) {
        if (rescuer == null || rescuer.getPosition() == null || path == null || path.isEmpty()) return false;

        Position current = rescuer.getPosition();
        for (Position step : path) {
            if (step == null) continue;
            // اگر اولین المان برابر موقعیت فعلی بود، ردش کن
            if (step.getX() == current.getX() && step.getY() == current.getY()) {
                continue;
            }

            int dir = determineDirection(current, step);
            boolean ok = MoveGuard.tryMoveTo(map, rescuer, step.getX(), step.getY(), dir);
            if (!ok) {
                // مسیر مسدود شد یا تایل مقصد walkable نبود
                return false;
            }
            current = step;
        }
        return true;
    }

    /** تعیین جهت بر اساس دلتا بین دو خانه (0=پایین،1=چپ،2=راست،3=بالا) */
    private int determineDirection(Position from, Position to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();

        if (dx == 1 && dy == 0) return 2; // راست
        if (dx == -1 && dy == 0) return 1; // چپ
        if (dy == 1 && dx == 0) return 0; // پایین
        if (dy == -1 && dx == 0) return 3; // بالا

        // در صورت حرکت غیر چهارجهته (مثلاً مورب) پیش‌فرض پایین
        return 0;
    }

    // -------------------- کمکی‌های انتخاب بیمارستان --------------------

    private Hospital findNearestHospital(Position from, List<Hospital> hospitals) {
        if (from == null || hospitals == null || hospitals.isEmpty()) return null;

        Hospital nearest = null;
        int best = Integer.MAX_VALUE;

        for (Hospital h : hospitals) {
            if (h == null || h.getPosition() == null) continue;
            int d = manhattan(from, h.getPosition());
            if (d < best) {
                best = d;
                nearest = h;
            }
        }
        return nearest;
    }

    /** فاصله‌ی منهتن بین دو مختصات تایل. */
    private int manhattan(Position a, Position b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        return dx + dy;
    }
}
