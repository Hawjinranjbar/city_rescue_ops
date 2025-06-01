package controller;

import agent.AgentManager;
import agent.Rescuer;
import map.Hospital;
import strategy.IAgentDecision;
import victim.Injured;
import victim.VictimManager;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

// --------------------
// لایه: Application Layer
// --------------------
// این کلاس برای تخصیص اولیه مجروح‌ها به نجات‌دهنده‌ها استفاده می‌شه
// می‌تونه برای شروع مأموریت‌ها یا سناریوی خاص استفاده بشه
public class MissionPlanner {

    private final AgentManager agentManager;
    private final VictimManager victimManager;
    private final IAgentDecision decisionLogic;

    public MissionPlanner(AgentManager agentManager,
                          VictimManager victimManager,
                          IAgentDecision decisionLogic) {
        this.agentManager = agentManager;
        this.victimManager = victimManager;
        this.decisionLogic = decisionLogic;
    }

    // برای هر Rescuer یک مجروح مناسب انتخاب می‌کنه و لیست تخصیص رو برمی‌گردونه
    public Map<Rescuer, Injured> planInitialMissions() {
        Map<Rescuer, Injured> assignments = new HashMap<>();
        List<Injured> available = victimManager.getRescuableVictims();

        for (Rescuer rescuer : agentManager.getAllRescuers()) {
            Injured target = decisionLogic.selectVictim(rescuer, available);
            if (target != null) {
                assignments.put(rescuer, target);
                available.remove(target); // یک مجروح به یک Agent
            }
        }

        return assignments;
    }
}
