package controller;

import agent.AgentController;
import agent.AgentManager;
import agent.Rescuer;
import map.CityMap;
import map.Hospital;
import strategy.AStarPathFinder;
import strategy.InjuryPrioritySelector;
import util.CollisionMap;
import util.Position;
import victim.Injured;
import victim.VictimManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * --------------------
 * لایه: Application Layer
 * --------------------
 * هماهنگ‌کننده‌ی عملیات نجات بین عامل‌ها، مجروح‌ها و بیمارستان‌ها.
 * - ساخت لیست کاندیدهای قابل‌نجات برای هر Rescuer
 * - مرتب‌سازی بر اساس اولویت (TTL کمتر، سپس فاصله کمتر)
 * - واگذاری اجرای حرکت/حمل/تحویل به AgentController.performAction(...)
 * بدون استفاده از لامبدا.
 */
public class RescueCoordinator {

    // وابستگی‌ها
    private final AgentManager agentManager;
    private final VictimManager victimManager;
    private final List<Hospital> hospitals;
    private final CityMap cityMap;
    private final CollisionMap collisionMap;
    private final AStarPathFinder pathFinder;          // رزرو برای آینده
    private final InjuryPrioritySelector prioritySel;  // فعلاً استفاده نمی‌شود

    private final AgentController agentController;     // اجرای حرکت‌ها

    // Logger اختیاری (نوع عام برای جلوگیری از وابستگی به امضاهای متفاوت)
    private Object logger;

    private volatile boolean paused = false;

    public RescueCoordinator(AgentManager agentManager,
                             VictimManager victimManager,
                             List<Hospital> hospitals,
                             CityMap cityMap,
                             CollisionMap collisionMap,
                             AStarPathFinder pathFinder,
                             InjuryPrioritySelector prioritySel) {

        this.agentManager   = agentManager;
        this.victimManager  = victimManager;
        this.hospitals      = (hospitals != null) ? hospitals : new ArrayList<Hospital>();
        this.cityMap        = cityMap;
        this.collisionMap   = collisionMap;
        this.pathFinder     = pathFinder;
        this.prioritySel    = prioritySel;

        this.agentController = new AgentController(cityMap, collisionMap);
        this.agentController.setVictimManager(victimManager);
        this.agentController.setHospitals(this.hospitals);
    }

    // ---------- Logger اختیاری ----------
    public void setLogger(Object logger) { this.logger = logger; }
    private void log(String msg) {
        if (logger != null) {
            try {
                java.lang.reflect.Method m = logger.getClass().getMethod("log", String.class);
                m.invoke(logger, msg);
            } catch (Throwable ignored) { }
        }
        System.out.println("[RescueCoordinator] " + msg);
    }

    // ---------- کنترل Pause ----------
    public void setPaused(boolean paused) { this.paused = paused; }
    public boolean isPaused() { return paused; }

    // ---------- دسترسی AgentController ----------
    public AgentController getAgentController() { return agentController; }

    // =========================================================
    // متدهای موردنیاز GameEngine (wrapper روی AgentController)
    // =========================================================

    /** تزریق/به‌روزرسانی کانتکست AI از سوی GameEngine (VictimManager و فهرست بیمارستان‌ها). */
    public void configureAIContext(VictimManager vm, List<Hospital> hs) {
        if (vm != null) {
            try { agentController.setVictimManager(vm); } catch (Throwable ignored) {}
        }
        if (hs != null) {
            this.hospitals.clear();
            this.hospitals.addAll(hs);
            try { agentController.setHospitals(this.hospitals); } catch (Throwable ignored) {}
        }
        log("AI context configured (vm=" + (vm != null) + ", hospitals=" + this.hospitals.size() + ").");
    }

    /** شروع AI برای یک Rescuer (Thread-base در AgentController). */
    public void startAIFor(Rescuer r) {
        if (r == null) return;
        paused = false;
        agentController.startAI(r);
        log("startAIFor: rescuer#" + r.getId());
    }

    /** توقف حلقهٔ AI و فعال‌کردن حالت pause. */
    public void pauseAI() {
        paused = true;
        agentController.stopAI();
        log("pauseAI");
    }

    /** خروج از حالت pause؛ در صورت نیاز، AI یکی از نجات‌دهنده‌ها را دوباره روشن می‌کند. */
    public void resumeAI() {
        paused = false;
        List<Rescuer> list = getRescuersSafe();
        if (list != null && !list.isEmpty()) {
            try { agentController.startAI(list.get(0)); } catch (Throwable ignored) {}
        }
        log("resumeAI");
    }

    // --- رپرهای سازگاری با نسخه‌های قدیمی (درخواست شما: روش ۲) ---
    public void pauseAll()  { pauseAI(); }
    public void resumeAll() { resumeAI(); }

    /**
     * اجرای یک سیکل هماهنگی:
     * - اگر pause نیست، برای همهٔ نجات‌دهنده‌ها runTickFor اجرا می‌شود.
     * GameEngine می‌تواند این متد را در حلقه‌ی بازی صدا بزند.
     */
    public void executeRescueCycle() {
        if (paused) return;
        List<Rescuer> list = getRescuersSafe();
        if (list == null || list.isEmpty()) return;
        for (int i = 0; i < list.size(); i++) {
            runTickFor(list.get(i));
        }
    }

    // =========================================================
    // ساخت لیست کاندیدها و مرتب‌سازی اولویت
    // =========================================================
    public List<Injured> buildCandidateListFor(Rescuer rescuer) {
        ArrayList<Injured> out = new ArrayList<Injured>();
        if (rescuer == null || victimManager == null) return out;

        List<Injured> all = null;
        try {
            all = victimManager.getAllVictimsSafe();
        } catch (Throwable ignored) { }
        if (all == null || all.isEmpty()) return out;

        // فیلتر پایه: زنده، نجات‌نشده، درحال‌نجات‌نبودن
        for (int i = 0; i < all.size(); i++) {
            Injured v = all.get(i);
            if (v == null) continue;
            try {
                if (!v.isAlive()) continue;
                if (v.isRescued()) continue;
                if (v.isBeingRescued()) continue;
            } catch (Throwable ignored) { continue; }
            out.add(v);
        }

        // مرتب‌سازی: 1) زمان باقیمانده کمتر، 2) فاصله منهتنی کمتر
        final Position rp = rescuer.getPosition();
        Collections.sort(out, new Comparator<Injured>() {
            @Override
            public int compare(Injured a, Injured b) {
                int ta = (a != null) ? a.getRemainingTime() : Integer.MAX_VALUE;
                int tb = (b != null) ? b.getRemainingTime() : Integer.MAX_VALUE;
                if (ta != tb) return (ta < tb) ? -1 : 1;

                int da = (rp != null && a != null) ? manhattan(rp, a.getPosition()) : Integer.MAX_VALUE;
                int db = (rp != null && b != null) ? manhattan(rp, b.getPosition()) : Integer.MAX_VALUE;
                if (da != db) return (da < db) ? -1 : 1;
                return 0;
            }
        });

        return out;
    }

    // =========================================================
    // اجرای چرخه برای یک Rescuer
    // =========================================================
    /**
     * یک «تیک» هماهنگی برای یک نجات‌دهنده:
     * - اگر در حالت آمبولانس است: حرکت تا بیمارستان و تحویل
     * - اگر حالت عادی است: به سمت قربانی اولویت‌دار حرکت و در صورت مجاورت pickup
     */
    public void runTickFor(Rescuer rescuer) {
        if (paused || rescuer == null) return;

        List<Injured> candidates = buildCandidateListFor(rescuer);
        agentController.performAction(rescuer, candidates, hospitals);
    }

    // -------------------- ابزارها --------------------
    private int manhattan(Position a, Position b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        if (dx < 0) dx = -dx;
        if (dy < 0) dy = -dy;
        return dx + dy;
    }

    /** استخراج ایمن لیست نجات‌دهنده‌ها از AgentManager بدون دانستن امضاهای دقیق. */
    @SuppressWarnings("unchecked")
    private List<Rescuer> getRescuersSafe() {
        if (agentManager == null) return new ArrayList<Rescuer>();
        try {
            // حالت 1: getAll()
            java.lang.reflect.Method m = agentManager.getClass().getMethod("getAll");
            Object r = m.invoke(agentManager);
            if (r instanceof List) return (List<Rescuer>) r;
        } catch (Throwable ignored) { }
        try {
            // حالت 2: getRescuers()
            java.lang.reflect.Method m2 = agentManager.getClass().getMethod("getRescuers");
            Object r2 = m2.invoke(agentManager);
            if (r2 instanceof List) return (List<Rescuer>) r2;
        } catch (Throwable ignored) { }
        return new ArrayList<Rescuer>();
    }
}
