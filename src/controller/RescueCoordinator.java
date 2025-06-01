package controller;

import agent.AgentController;
import agent.AgentManager;
import map.Hospital;
import strategy.IAgentDecision;
import strategy.IPathFinder;
import victim.Injured;
import victim.VictimManager;

import java.util.List;

// --------------------
// لایه: Application Layer
// --------------------
// این کلاس وظیفه هماهنگی بین agentها، مجروح‌ها و بیمارستان‌ها رو بر عهده داره
// از AgentController برای اجرای عملیات استفاده می‌کنه
public class RescueCoordinator {

    private final AgentManager agentManager;
    private final VictimManager victimManager;
    private final List<Hospital> hospitals;
    private final AgentController agentController;

    public RescueCoordinator(AgentManager agentManager,
                             VictimManager victimManager,
                             List<Hospital> hospitals,
                             IPathFinder pathFinder,
                             IAgentDecision decisionLogic) {

        this.agentManager = agentManager;
        this.victimManager = victimManager;
        this.hospitals = hospitals;
        this.agentController = new AgentController(pathFinder, decisionLogic);
    }

    // اجرای یک دور عملیات نجات برای همه نجات‌دهنده‌ها
    public void executeRescueCycle() {
        List<Injured> candidates = victimManager.getRescuableVictims();

        agentManager.getAllRescuers().forEach(rescuer ->
                agentController.performAction(rescuer, candidates, hospitals)
        );
    }
}
