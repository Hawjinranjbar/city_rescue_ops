package agent;

import map.CityMap;
import map.Hospital;
import strategy.IPathFinder;
import strategy.IAgentDecision;
import util.MoveGuard;
import util.Position;
import util.CollisionMap;
import victim.Injured;

import java.util.List;

/**
 * --------------------
 * Domain Layer
 * --------------------
 * کنترل رفتار نجات‌دهنده:
 *  - انتخاب مجروح هدف (IAgentDecision)
 *  - مسیریابی تا مجروح/بیمارستان (IPathFinder)
 *  - حرکت امن روی نقشه (MoveGuard + CollisionMap)
 */
public class AgentController {

    private final IPathFinder pathFinder;
    private final IAgentDecision decisionLogic;
    private final CityMap map;
    private final CollisionMap collisionMap;

    public AgentController(CityMap map,
                           CollisionMap collisionMap,
                           IPathFinder pathFinder,
                           IAgentDecision decisionLogic) {
        this.map = map;
        this.collisionMap = collisionMap;
        this.pathFinder = pathFinder;
        this.decisionLogic = decisionLogic;
    }

    /**
     * اجرای یک «تیک» از رفتار ریسکیور.
     * اگر حمل نمی‌کند → قربانیِ هدف را انتخاب و به سمتش حرکت می‌کند.
     * اگر حمل می‌کند → به نزدیک‌ترین بیمارستان می‌رود و تحویل می‌دهد.
     */
    public void performAction(Rescuer rescuer,
                              List<Injured> candidates,
                              List<Hospital> hospitals) {

        if (rescuer == null || rescuer.getPosition() == null || map == null) return;

        // حالت 1: در حال حمل نیست → به سمت مجروح هدف
        if (!rescuer.isCarryingVictim()) {
            Injured target = (decisionLogic != null)
                    ? decisionLogic.selectVictim(rescuer, candidates)
                    : null;

            if (target == null || target.isDead() || target.isRescued() || target.getPosition() == null) {
                return;
            }

            target.setBeingRescued(true);

            List<Position> pathToVictim = (pathFinder != null)
                    ? pathFinder.findPath(rescuer.getPosition(), target.getPosition())
                    : null;

            boolean reached = moveAlongPath(rescuer, pathToVictim);
            if (reached) {
                rescuer.pickUp(target);
            } else {
                target.setBeingRescued(false); // مسیر غیرقابل‌عبور/مسدود
            }
            return; // همین تیک کافی است
        }

        // حالت 2: در حال حمل است → بیمارستان
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

    /** حرکت طبق مسیر؛ اگر به انتهای مسیر برسد true. */
    private boolean moveAlongPath(Rescuer rescuer, List<Position> path) {
        if (rescuer == null || rescuer.getPosition() == null || path == null || path.isEmpty()) return false;

        Position current = rescuer.getPosition();
        for (Position step : path) {
            if (step == null) continue;
            // اولین خانه مسیر معمولاً همان موقعیت فعلی است
            if (step.getX() == current.getX() && step.getY() == current.getY()) continue;

            int dir = determineDirection(current, step);

            // اگر امضای شما فرق دارد، فقط همین خط را تنظیم کنید.
            boolean ok = MoveGuard.tryMoveTo(
                    map,
                    collisionMap,
                    rescuer,
                    step.getX(),
                    step.getY(),
                    dir
            );
            if (!ok) return false;

            // اگر جابه‌جایی داخل MoveGuard انجام نمی‌شود، این خط را باز کنید:
            // rescuer.setPosition(step);

            current = step;
        }
        return true;
    }

    /** تعیین جهت بر اساس دلتا بین دو خانه (0=پایین،1=چپ،2=راست،3=بالا). */
    private int determineDirection(Position from, Position to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 1 && dy == 0) return 2; // راست
        if (dx == -1 && dy == 0) return 1; // چپ
        if (dy == 1 && dx == 0) return 0; // پایین
        if (dy == -1 && dx == 0) return 3; // بالا
        return 0;
    }

    private Hospital findNearestHospital(Position from, List<Hospital> hospitals) {
        if (from == null || hospitals == null || hospitals.isEmpty()) return null;
        Hospital nearest = null;
        int best = Integer.MAX_VALUE;
        for (Hospital h : hospitals) {
            if (h == null || h.getPosition() == null) continue;
            int d = Math.abs(from.getX() - h.getPosition().getX())
                    + Math.abs(from.getY() - h.getPosition().getY());
            if (d < best) { best = d; nearest = h; }
        }
        return nearest;
    }
}
