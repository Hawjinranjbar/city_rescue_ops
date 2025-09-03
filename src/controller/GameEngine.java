package controller;

import agent.AgentManager;
import agent.Rescuer;
import file.GameState;
import file.SaveManager;
import map.CityMap;
import map.Hospital;
import ui.GamePanel;
import ui.HUDPanel;
import ui.MiniMapPanel;
import util.Logger;
import util.Position;
import victim.Injured;
import victim.VictimManager;

// ✅ اضافه شد
import controller.ScoreManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * --------------------
 * لایه: Application Layer
 * --------------------
 * حلقه اصلی: چرخه نجات، تیک تایمر مجروح‌ها، تحویل کنار بیمارستان، بروزرسانی HUD و UI
 * + لاگ‌گذاری Pickup/Deliver/Death با util.Logger
 * + مدیریت Save/Load/Restart (بدون افزودن کلاس جدید)
 * بدون استفاده از لامبدا (ActionListener کلاسیک)
 */
public class GameEngine {

    private final RescueCoordinator rescueCoordinator;
    private final VictimManager victimManager;
    private final AgentManager agentManager;
    private final HUDPanel hudPanel;
    private final GamePanel gamePanel;
    private final MiniMapPanel miniMapPanel;
    private final Logger logger;

    private final javax.swing.Timer gameLoopTimer;
    /** وضعیت جاری بازی که در ctor تزریق شده است (برای Save/Load استفاده می‌شود). */
    private final GameState state;

    /** برای تشخیص «ورود تازه به حالت آمبولانس» جهت لاگ Pickup */
    private final Map<Integer, Boolean> prevAmbulanceState = new HashMap<Integer, Boolean>();

    /** مسیر پیش‌فرض برای کوییک‌سیو/لود */
    private static final String QUICK_SAVE_PATH = "saves/quick.sav";

    public GameEngine(GameState state,
                      RescueCoordinator rescueCoordinator,
                      AgentManager agentManager,
                      VictimManager victimManager,
                      HUDPanel hudPanel,
                      GamePanel gamePanel,
                      MiniMapPanel miniMapPanel,
                      Logger logger) {

        this.state = state;
        this.rescueCoordinator = rescueCoordinator;
        this.agentManager = agentManager;
        this.victimManager = victimManager;
        this.hudPanel = hudPanel;
        this.gamePanel = gamePanel;
        this.miniMapPanel = miniMapPanel;
        this.logger = logger != null ? logger : new Logger("logs/game.log", true);

        // اتصال HUD به موتور برای دکمه‌های Save/Load/Restart
        if (this.hudPanel != null) {
            try { this.hudPanel.setGameEngine(this); } catch (Throwable ignored) {}
        }

        // امتیاز اولیه و نمایش آن از همان شروع
        ScoreManager.resetToDefault();
        if (this.hudPanel != null) {
            this.hudPanel.updateHUD(
                    ScoreManager.getScore(),
                    (int) victimManager.countRescued(),
                    (int) victimManager.countDead()
            );
        }

        // لاگ شروع بازی
        try {
            CityMap map = this.state.getMap();
            List<Hospital> hs = this.state.getHospitals();
            int w = map != null ? map.getWidth() : -1;
            int h = map != null ? map.getHeight() : -1;
            int hc = hs != null ? hs.size() : 0;
            logger.logGameStart(w, h, hc, ScoreManager.getScore());
        } catch (Exception ex) {
            logger.logError("GameEngine.<init>/logGameStart", ex);
        }

        // ثبت اسنپ‌شات اولیه برای Restart (خود فایل ذخیره نمی‌شود)
        try { SaveManager.setInitialState(this.state); } catch (Exception ignored) { }

        // حلقه بازی هر ۱ ثانیه – بدون لامبدا
        this.gameLoopTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGame(GameEngine.this.state.getMap(), GameEngine.this.state.getHospitals());
            }
        });
    }

    // ------------------------------------------------------------------------
    // کنترل حلقه
    // ------------------------------------------------------------------------
    public void start() { try { gameLoopTimer.start(); } catch (Throwable ignore) {} }
    public void stop()  { try { gameLoopTimer.stop();  } catch (Throwable ignore) {} }

    // ------------------------------------------------------------------------
    // چرخه‌ی اصلی
    // ------------------------------------------------------------------------
    private void updateGame(CityMap map, List<Hospital> hospitals) {
        // 1) چرخه نجات
        rescueCoordinator.executeRescueCycle();

        // 2) تشخیص ورود تازه به حالت آمبولانس (لاگ Pickup)
        Collection<Rescuer> rescuers = agentManager.getAllRescuers();
        for (Rescuer r : rescuers) {
            if (r == null) continue;
            boolean wasAmb = prevAmbulanceState.containsKey(r.getId()) && Boolean.TRUE.equals(prevAmbulanceState.get(r.getId()));
            boolean nowAmb = r.isAmbulanceMode();
            if (nowAmb && !wasAmb) {
                Injured v = r.getCarryingVictim();
                if (v != null) {
                    try {
                        logger.logAmbulancePickup(
                                r.getId(),
                                safePos(r.getPosition()),
                                v.getId(),
                                v.getSeverity() != null ? v.getSeverity().name() : "null",
                                v.getInitialTimeLimit()
                        );
                    } catch (Exception ex) {
                        logger.logError("GameEngine.updateGame/logAmbulancePickup", ex);
                    }
                }
            }
            prevAmbulanceState.put(r.getId(), nowAmb);
        }

        // 3) تحویل کنار بیمارستان (لاگ Deliver)
        for (Rescuer r : rescuers) {
            if (r != null && r.isAmbulanceMode() && hospitals != null && !hospitals.isEmpty()) {
                Hospital nearest = Hospital.findNearest(hospitals, r.getPosition());
                if (nearest != null && nearest.canDeliverFrom(r.getPosition(), map)) {
                    Injured v = r.getCarryingVictim();
                    if (v != null) {
                        victimManager.onVictimRescued(v);
                        int reward = 2 * Math.max(0, v.getInitialTimeLimit());
                        r.deliverVictimAtHospital();
                        try {
                            logger.logAmbulanceDeliver(
                                    r.getId(),
                                    safePos(r.getPosition()),
                                    v.getId(),
                                    reward,
                                    ScoreManager.getScore()
                            );
                        } catch (Exception ex) {
                            logger.logError("GameEngine.updateGame/logAmbulanceDeliver", ex);
                        }
                    }
                    prevAmbulanceState.put(r.getId(), false);
                }
            }
        }

        // 4) تیک تایمر مجروح‌ها + مرگ (لاگ Death)
        List<Injured> victimsSnapshot = victimManager.getAll();
        for (int i = 0; i < victimsSnapshot.size(); i++) {
            Injured injured = victimsSnapshot.get(i);
            if (!injured.isDead() && !injured.isRescued()) {
                injured.getRescueTimer().tick();
                if (injured.getRescueTimer().isFinished()) {
                    int penalty = 2 * Math.max(0, injured.getInitialTimeLimit());
                    victimManager.onVictimDead(injured);
                    try {
                        String sev = injured.getSeverity() != null ? injured.getSeverity().name() : "null";
                        logger.logVictimDeath(injured.getId(), sev, injured.getInitialTimeLimit(),
                                penalty, ScoreManager.getScore());
                    } catch (Exception ex) {
                        logger.logError("GameEngine.updateGame/logVictimDeath", ex);
                    }
                }
            }
        }

        // 5) HUD
        if (hudPanel != null) {
            hudPanel.updateHUD(
                    ScoreManager.getScore(),
                    (int) victimManager.countRescued(),
                    (int) victimManager.countDead()
            );
        }

        // 6) رندر پنل‌ها و مینی‌مپ
        List<Rescuer> rescuersForRender = new ArrayList<Rescuer>(rescuers);
        List<Injured> victimsForRender = victimManager.getAll();

        if (gamePanel != null) {
            gamePanel.updateData(map, rescuersForRender, victimsForRender);
            try { gamePanel.repaint(); } catch (Throwable ignored) {}
        }
        if (miniMapPanel != null) {
            miniMapPanel.updateMiniMap(map, rescuersForRender, victimsForRender);
            try { miniMapPanel.repaint(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------------
    // Save / Load / Restart API
    // ------------------------------------------------------------------------
    public void saveQuick() {
        logger.logQuickSave(QUICK_SAVE_PATH);
        saveGame(QUICK_SAVE_PATH);
    }

    public void loadQuick() {
        logger.logQuickLoad(QUICK_SAVE_PATH);
        loadGame(QUICK_SAVE_PATH);
    }

    public void saveGame(String path) {
        pauseAll();
        try {
            SaveManager.saveGameToPath(this.state, path);
            logger.logSaveSuccess(path);
        } catch (Exception ex) {
            logger.logSaveFailed(path, ex);
        } finally {
            resumeAll();
        }
    }

    public void loadGame(String path) {
        pauseAll();
        try {
            GameState loaded = SaveManager.loadGame(path);
            if (loaded != null) {
                applyGameState(loaded);
                logger.logLoadSuccess(path);
            } else {
                logger.logLoadFailed(path, new RuntimeException("Loaded GameState is null"));
            }
        } catch (Exception ex) {
            logger.logLoadFailed(path, ex);
        } finally {
            resumeAll();
        }
    }

    public void restartGame() {
        pauseAll();
        try {
            logger.logRestartTriggered();
            GameState init = SaveManager.getInitialState();
            if (init != null) {
                applyGameState(init);
            } else {
                logger.logWarn("Initial state is null; restart ignored.");
            }
        } catch (Exception ex) {
            logger.logError("GameEngine.restartGame", ex);
        } finally {
            resumeAll();
        }
    }

    private void applyGameState(GameState loaded) {
        // 1) جایگزینی State سطح بالا
        try { this.state.replaceWith(loaded); } catch (Throwable ignore) {
            try {
                this.state.setMap(loaded.getMap());
                this.state.setHospitals(loaded.getHospitals());
            } catch (Throwable ignored) { }
        }

        // 2) به‌روزرسانی امتیاز از GameState  (اگر API داشت)
        try { ScoreManager.setScore(loaded.getScore()); } catch (Throwable ignored) { }

        // 3) جایگزینی لیست‌ها در منیجرها
        try {
            if (loaded.getRescuers() != null) {
                agentManager.replaceAll(new ArrayList<Rescuer>(loaded.getRescuers()));
            }
        } catch (Throwable ignore) { }

        try {
            if (loaded.getVictims() != null) {
                victimManager.replaceAll(new ArrayList<Injured>(loaded.getVictims()));
            }
        } catch (Throwable ignore) { }

        // 4) HUD و UI
        try {
            if (hudPanel != null) {
                hudPanel.updateHUD(
                        ScoreManager.getScore(),
                        (int) victimManager.countRescued(),
                        (int) victimManager.countDead()
                );
            }
            List<Rescuer> rescuersForRender = new ArrayList<Rescuer>(agentManager.getAllRescuers());
            List<Injured> victimsForRender = victimManager.getAll();
            CityMap map = this.state.getMap();

            if (gamePanel != null) {
                gamePanel.updateData(map, rescuersForRender, victimsForRender);
                try { gamePanel.repaint(); } catch (Throwable ignored) {}
            }
            if (miniMapPanel != null) {
                miniMapPanel.updateMiniMap(map, rescuersForRender, victimsForRender);
                try { miniMapPanel.repaint(); } catch (Throwable ignored) {}
            }
        } catch (Exception ex) {
            logger.logError("GameEngine.applyGameState/refreshUI", ex);
        }

        // 5) ریست وضعیت ورود به آمبولانس (برای لاگ Pickup پس از Load)
        try { prevAmbulanceState.clear(); } catch (Throwable ignored) { }
    }

    private void pauseAll() {
        try { gameLoopTimer.stop(); } catch (Throwable ignore) { }
        try { rescueCoordinator.pauseAll(); } catch (Throwable ignore) { }
        try { agentManager.pauseAll(); } catch (Throwable ignore) { }
        try { victimManager.pauseAll(); } catch (Throwable ignore) { }
    }

    private void resumeAll() {
        try { victimManager.resumeAll(); } catch (Throwable ignore) { }
        try { agentManager.resumeAll(); } catch (Throwable ignore) { }
        try { rescueCoordinator.resumeAll(); } catch (Throwable ignore) { }
        try { gameLoopTimer.start(); } catch (Throwable ignore) { }
    }

    private static Position safePos(Position p) {
        return p == null ? new Position(0, 0) : p;
    }
}
