package controller;

import agent.AgentController;
import agent.AgentManager;
import agent.Rescuer;
import map.CityMap;
import map.Hospital;
import strategy.IAgentDecision;
import strategy.IPathFinder;
import util.CollisionMap;
import victim.Injured;
import victim.VictimManager;

import java.util.Collection;
import java.util.List;

/**
 * --------------------
 * لایه: Application Layer
 * --------------------
 * هماهنگ‌کننده‌ی عملیات نجات بین عامل‌ها، مجروح‌ها و بیمارستان‌ها.
 * - برای هر Rescuer، لیست جدیدی از قربانیانِ قابل نجات می‌گیرد و به AgentController می‌سپارد.
 * - پشتیبانی از Pause/Resume برای سازگاری با Save/Load.
 * - هیچ لامبدایی استفاده نشده.
 */
public class RescueCoordinator {

    private final AgentManager agentManager;
    private final VictimManager victimManager;
    private final List<Hospital> hospitals;
    private final AgentController agentController;

    public RescueCoordinator(AgentManager agentManager,
                             VictimManager victimManager,
                             List<Hospital> hospitals,
                             CityMap map,
                             CollisionMap collisionMap,
                             IPathFinder pathFinder,
                             IAgentDecision decisionLogic) {

        this.agentManager   = agentManager;
        this.victimManager  = victimManager;
        this.hospitals      = hospitals;
        this.agentController = new AgentController(map, collisionMap, pathFinder, decisionLogic);
    }

    /** اختیاری: اگر Logger داری، به AgentController پاس بده */
    public void setLogger(util.Logger logger) {
        if (this.agentController != null) {
            this.agentController.setLogger(logger);
        }
    }

    /** اجرای یک دور عملیات نجات */
    public void executeRescueCycle() {
        Collection<Rescuer> rescuers = agentManager.getAllRescuers();
        for (Rescuer r : rescuers) {
            if (r == null || !r.isAIControlled()) continue;
            List<Injured> candidates = victimManager.getRescuableVictims();
            agentController.performAction(r, candidates, hospitals);
        }
    }

    // ----------------------------------------------------------------
    // پشتیبانی Save/Load/Restart → Pause/Resume
    // ----------------------------------------------------------------
    public void pauseAll() {
        try { agentManager.pauseAll(); } catch (Throwable ignore) { }
        try { victimManager.pauseAll(); } catch (Throwable ignore) { }
        // AgentController فعلاً state فعال ندارد؛ در آینده اگر Thread داشت، همین‌جا pause شود.
    }

    public void resumeAll() {
        try { victimManager.resumeAll(); } catch (Throwable ignore) { }
        try { agentManager.resumeAll(); } catch (Throwable ignore) { }
    }
}
