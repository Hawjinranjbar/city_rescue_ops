package strategy;

import agent.Rescuer;
import util.Position;
import victim.Injured;

import java.util.List;

// --------------------
// لایه: Strategy Layer
// --------------------
// این کلاس با توجه به شدت جراحت و فاصله، بهترین مجروح رو برای نجات انتخاب می‌کنه
// پیاده‌سازی ساده و مؤثر از IAgentDecision (بدون استفاده از Stream/Lambda)
public class InjuryPrioritySelector implements IAgentDecision {

    @Override
    public Injured selectVictim(Rescuer rescuer, List<Injured> candidates) {
        if (rescuer == null || rescuer.getPosition() == null) return null;
        if (candidates == null || candidates.isEmpty()) return null;

        Position rescuerPos = rescuer.getPosition();
        Injured best = null;
        int bestWeight = Integer.MAX_VALUE;

        for (int i = 0; i < candidates.size(); i++) {
            Injured v = candidates.get(i);
            if (v == null) continue;
            if (!v.canBeRescued()) continue;

            int dist = rescuerPos.distanceTo(v.getPosition());
            int w = weight(v.getSeverity().getPriorityLevel(), dist);

            if (w < bestWeight) {
                bestWeight = w;
                best = v;
            }
        }

        return best;
    }

    // تابع وزنی ترکیبی برای اولویت: شدت جراحت + فاصله
    // هر چی کمتر باشه، اولویت بالاتره
    private int weight(int severityPriority, int distance) {
        return severityPriority * 10 + distance;
    }
}
