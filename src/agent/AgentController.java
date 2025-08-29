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

// --------------------
// لایه: Domain Layer
// --------------------
// این کلاس مسئول کنترل رفتار و حرکت نجات‌دهنده‌هاست
// شامل مسیریابی، حمل مجروح و رسیدن به بیمارستان
public class AgentController {

    private final IPathFinder pathFinder;
    private final IAgentDecision decisionLogic;
    private final CityMap map;
    private final CollisionMap collisionMap;

    public AgentController(CityMap map, CollisionMap collisionMap, IPathFinder pathFinder, IAgentDecision decisionLogic) {
        this.map = map;
        this.collisionMap = collisionMap;
        this.pathFinder = pathFinder;
        this.decisionLogic = decisionLogic;
    }

    // اجرای عملیات برای یک نجات‌دهنده (پیدا کردن قربانی، مسیر، و حرکت)
    public void performAction(Rescuer rescuer, List<Injured> candidates, List<Hospital> hospitals) {
        if (!rescuer.isBusy()) {
            Injured target = decisionLogic.selectVictim(rescuer, candidates);
            if (target != null) {
                target.setBeingRescued(true);
                List<Position> pathToVictim = pathFinder.findPath(rescuer.getPosition(), target.getPosition());
                if (moveAlongPath(rescuer, pathToVictim)) {
                    rescuer.pickUp(target);
                } else {
                    target.setBeingRescued(false);
                }
            }
        }

        if (rescuer.isCarryingVictim()) {
            Injured carried = rescuer.getCarryingVictim();
            Hospital nearestHospital = findNearestHospital(rescuer.getPosition(), hospitals);
            List<Position> pathToHospital = pathFinder.findPath(rescuer.getPosition(), nearestHospital.getPosition());
            if (moveAlongPath(rescuer, pathToHospital)) {
                rescuer.dropVictim();
                if (carried != null) carried.setBeingRescued(false);
            }
        }
    }

    // حرکت نجات‌دهنده در طول مسیر مشخص شده
    private boolean moveAlongPath(Rescuer rescuer, List<Position> path) {
        if (rescuer == null || path == null || path.isEmpty()) return false;
        Position current = rescuer.getPosition();
        for (Position step : path) {
            if (step.equals(current)) continue;
            int dir = determineDirection(current, step);
            if (!MoveGuard.tryMoveTo(map, collisionMap, rescuer, step.getX(), step.getY(), dir)) {
                return false;
            }
            current = step;
        }
        return true;
    }

    // تعیین جهت بر اساس دلتا بین دو موقعیت (0=پایین،1=چپ،2=راست،3=بالا)
    private int determineDirection(Position from, Position to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 1) return 2;      // راست
        if (dx == -1) return 1;     // چپ
        if (dy == 1) return 0;      // پایین
        if (dy == -1) return 3;     // بالا
        return 0;                   // پیش‌فرض
    }

    // پیدا کردن نزدیک‌ترین بیمارستان به موقعیت فعلی
    private Hospital findNearestHospital(Position from, List<Hospital> hospitals) {
        Hospital nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Hospital h : hospitals) {
            int dist = from.distanceTo(h.getPosition());
            if (dist < minDistance) {
                minDistance = dist;
                nearest = h;
            }
        }
        return nearest;
    }
}
