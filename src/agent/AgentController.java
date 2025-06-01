package agent;

import map.Hospital;
import strategy.IPathFinder;
import strategy.IAgentDecision;
import util.Position;
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

    public AgentController(IPathFinder pathFinder, IAgentDecision decisionLogic) {
        this.pathFinder = pathFinder;
        this.decisionLogic = decisionLogic;
    }

    // اجرای عملیات برای یک نجات‌دهنده (پیدا کردن قربانی، مسیر، و حرکت)
    public void performAction(Rescuer rescuer, List<Injured> candidates, List<Hospital> hospitals) {
        if (!rescuer.isBusy()) {
            Injured target = decisionLogic.selectVictim(rescuer, candidates);
            if (target != null) {
                List<Position> pathToVictim = pathFinder.findPath(rescuer.getPosition(), target.getPosition());
                moveAlongPath(rescuer, pathToVictim);
                rescuer.pickUp(target);
            }
        }

        if (rescuer.isCarryingVictim()) {
            Hospital nearestHospital = findNearestHospital(rescuer.getPosition(), hospitals);
            List<Position> pathToHospital = pathFinder.findPath(rescuer.getPosition(), nearestHospital.getPosition());
            moveAlongPath(rescuer, pathToHospital);
            rescuer.dropVictim();
        }
    }

    // حرکت نجات‌دهنده در طول مسیر مشخص شده
    private void moveAlongPath(Rescuer rescuer, List<Position> path) {
        for (Position step : path) {
            rescuer.setPosition(step);
            // اینجا می‌تونی delay، animation یا repaint UI بزاری
        }
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
