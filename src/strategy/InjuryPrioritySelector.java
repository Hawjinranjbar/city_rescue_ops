package strategy;

import agent.Rescuer;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import java.util.List;

/**
 * --------------------
 * لایه: Strategy Layer
 * --------------------
 * انتخاب مجروح با اولویت مطلق برای CRITICAL.
 * - بدون Stream/Lambda
 * - دارای لاگ‌های کنسولی (قابل روشن/خاموش شدن)
 *
 * نکات:
 * 1) اگر severity == CRITICAL → وزن = 0 (همیشه اولویت اول)
 * 2) برای MEDIUM و LOW وزن = distance + offset ثابت
 * 3) لاگ‌ها به‌صورت اختیاری با setDebugEnabled(true) فعال می‌شوند
 */
public class InjuryPrioritySelector implements IAgentDecision {

    /** اگر true باشد، اطلاعات انتخاب روی کنسول چاپ می‌شود. پیش‌فرض: خاموش */
    private boolean debugEnabled = false;

    /** فعال/غیرفعال کردن لاگ کنسولی */
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
    }

    @Override
    public Injured selectVictim(Rescuer rescuer, List<Injured> candidates) {
        if (rescuer == null || rescuer.getPosition() == null) {
            if (debugEnabled) {
                System.out.println("[InjuryPrioritySelector] rescuer یا موقعیت او null است → برمی‌گردیم null");
            }
            return null;
        }
        if (candidates == null || candidates.isEmpty()) {
            if (debugEnabled) {
                System.out.println("[InjuryPrioritySelector] لیست کاندیداها خالی/null است → برمی‌گردیم null");
            }
            return null;
        }

        Position rescuerPos = rescuer.getPosition();
        Injured best = null;
        int bestWeight = Integer.MAX_VALUE;

        if (debugEnabled) {
            System.out.println("—— [InjuryPrioritySelector] آغاز انتخاب —————————————————————————");
            System.out.println("Rescuer@" + coords(rescuerPos) + " | candidates=" + candidates.size());
        }

        for (int i = 0; i < candidates.size(); i++) {
            Injured v = candidates.get(i);
            if (v == null) {
                if (debugEnabled) {
                    System.out.println("  • candidate[" + i + "] = null → رد");
                }
                continue;
            }
            if (!v.canBeRescued()) {
                if (debugEnabled) {
                    System.out.println("  • victim#" + v.getId() + " قابل نجات نیست (canBeRescued=false) → رد");
                }
                continue;
            }

            Position vp = v.getPosition();
            int dist = (vp != null) ? rescuerPos.distanceTo(vp) : Integer.MAX_VALUE;
            InjurySeverity sev = v.getSeverity();
            int w = weight(sev, dist);

            if (debugEnabled) {
                String reason;
                if (sev == InjurySeverity.CRITICAL) {
                    reason = "Critical → وزن=0 (اولویت مطلق)";
                } else if (sev == InjurySeverity.MEDIUM) {
                    reason = "Medium → وزن=distance+50";
                } else {
                    reason = "Low → وزن=distance+100";
                }
                System.out.println("  • victim#" + v.getId()
                        + " sev=" + sev
                        + " dist=" + safeDist(dist)
                        + " → weight=" + w
                        + " | " + reason);
            }

            if (w < bestWeight) {
                bestWeight = w;
                best = v;
                if (debugEnabled) {
                    System.out.println("    → فعلاً بهترین: victim#" + v.getId() + " با weight=" + w);
                }
            }
        }

        if (debugEnabled) {
            if (best != null) {
                System.out.println("==> انتخاب نهایی: victim#" + best.getId()
                        + " (sev=" + best.getSeverity()
                        + ", weight=" + bestWeight + ")");
            } else {
                System.out.println("==> هیچ مجروح مناسبی پیدا نشد (best=null).");
            }
            System.out.println("———————————————————————————————————————————————————————————————");
        }

        return best;
    }

    // تابع وزنی: Critical همیشه صفر
    private int weight(InjurySeverity severity, int distance) {
        if (severity == InjurySeverity.CRITICAL) {
            return 0; // اولویت مطلق
        } else if (severity == InjurySeverity.MEDIUM) {
            return safeDistance(distance) + 50;
        } else { // LOW
            return safeDistance(distance) + 100;
        }
    }

    // محافظت در برابر distance نامعتبر
    private int safeDistance(int d) {
        return (d < 0 || d == Integer.MAX_VALUE) ? 999999 : d;
    }

    private String safeDist(int d) {
        return (d == Integer.MAX_VALUE) ? "INF" : String.valueOf(d);
    }

    private String coords(Position p) {
        if (p == null) return "(null)";
        return "(" + p.getX() + "," + p.getY() + ")";
    }
}
