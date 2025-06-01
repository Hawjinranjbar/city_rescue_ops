package strategy;

import agent.Rescuer;
import util.Position;
import victim.Injured;

import java.util.Comparator;
import java.util.List;

// --------------------
// لایه: Strategy Layer
// --------------------
// این کلاس با توجه به شدت جراحت و فاصله، بهترین مجروح رو برای نجات انتخاب می‌کنه
// پیاده‌سازی ساده و مؤثر از IAgentDecision
public class InjuryPrioritySelector implements IAgentDecision {

    @Override
    public Injured selectVictim(Rescuer rescuer, List<Injured> candidates) {
        Position rescuerPos = rescuer.getPosition();

        return candidates.stream()
                .filter(Injured::canBeRescued)
                .min(Comparator.comparingInt(victim ->
                        weight(victim.getSeverity().getPriorityLevel(), rescuerPos.distanceTo(victim.getPosition()))
                ))
                .orElse(null);
    }

    // تابع وزنی ترکیبی برای اولویت: شدت جراحت + فاصله
    // هر چی کمتر باشه، اولویت بالاتره
    private int weight(int severityPriority, int distance) {
        return severityPriority * 10 + distance;
    }
}
