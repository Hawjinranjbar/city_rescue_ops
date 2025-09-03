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
import victim.InjurySeverity;
import victim.VictimManager;

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

        // ⛳️ مهم: HUD را به موتور وصل کن تا دکمه‌های Pause/Save/Load/Restart کار کنند
        if (this.hudPanel != null) {
            try { this.hudPanel.setGameEngine(this); } catch (Throwable ignored) {}
        }

        // امتیاز اولیه و نمایش آن از همان شروع
        ScoreManager.resetToDefault();
        hudPanel.updateHUD(
                ScoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

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

        // در شروع بازی یک اسنپ‌شات اولیه برای Restart ثبت می‌کنیم (خود فایل ذخیره نمی‌شود)
        try { SaveManager.setInitialState(captureGameState()); } catch (Exception ignored) { }

        // حلقه بازی هر ۱ ثانیه (1000ms) – بدون لامبدا
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
    public void start() { gameLoopTimer.start(); }
    public void stop()  { gameLoopTimer.stop();  }

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
        hudPanel.updateHUD(
                ScoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

        // 6) رندر پنل‌ها و مینی‌مپ
        List<Rescuer> rescuersForRender = new ArrayList<Rescuer>(rescuers);
        List<Injured> victimsForRender = victimManager.getAll();

        gamePanel.updateData(map, rescuersForRender, victimsForRender);
        if (miniMapPanel != null) {
            miniMapPanel.updateMiniMap(map, rescuersForRender, victimsForRender);
        }
    }

    // ------------------------------------------------------------------------
    // Save / Load / Restart API
    // ------------------------------------------------------------------------
    public void saveQuick() { saveGame(QUICK_SAVE_PATH); }
    public void loadQuick() { loadGame(QUICK_SAVE_PATH); }

    public void saveGame(String path) {
        pauseAll();
        try {
            SaveManager.saveGameToPath(captureGameState(), path);
            logger.logInfo("Game saved to: " + path);
        } catch (Exception ex) {
            logger.logError("GameEngine.saveGame", ex);
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
                logger.logInfo("Game loaded from: " + path);
            }
        } catch (Exception ex) {
            logger.logError("GameEngine.loadGame", ex);
        } finally {
            resumeAll();
        }
    }

    public void restartGame() {
        pauseAll();
        try {
            GameState init = SaveManager.getInitialState();
            if (init != null) {
                applyGameState(init);
                logger.logInfo("Game restarted from initial state.");
            } else {
                logger.logInfo("Initial state is null; restart ignored.");
            }
        } catch (Exception ex) {
            logger.logError("GameEngine.restartGame", ex);
        } finally {
            resumeAll();
        }
    }

    private void applyGameState(GameState loaded) {
        if (loaded == null) return;

        // امتیاز
        ScoreManager.resetToDefault();
        int loadedScore = loaded.getScore();
        int baseScore = ScoreManager.getScore();
        if (loadedScore > baseScore) {
            ScoreManager.add(loadedScore - baseScore);
        } else if (loadedScore < baseScore) {
            ScoreManager.deduct(baseScore - loadedScore);
        }

        // بازسازی قربانی‌ها از اسنپ‌شات
        Map<Integer, Injured> victimMap = new HashMap<Integer, Injured>();
        List<Injured> newVictims = new ArrayList<Injured>();
        List<GameState.InjuredDTO> vSnap = loaded.getVictimSnapshot();
        if (vSnap != null) {
            for (int i = 0; i < vSnap.size(); i++) {
                GameState.InjuredDTO dto = vSnap.get(i);
                InjurySeverity sev = InjurySeverity.LOW;
                try { if (dto.severity != null) sev = InjurySeverity.valueOf(dto.severity); } catch (Throwable ignore) { }
                Injured inj = new Injured(dto.id, new Position(dto.tileX, dto.tileY), sev);
                if (dto.alive) { inj.markAsAlive(); }
                if (dto.rescued) { inj.markAsRescued(); }
                if (!dto.alive && !dto.rescued) { inj.markAsDead(); }
                if (dto.critical) { inj.markAsCritical(); }
                inj.setRemainingTime((int) dto.remainingMillis);
                victimMap.put(dto.id, inj);
                newVictims.add(inj);
            }
        }

        // بازسازی نجات‌دهنده‌ها از اسنپ‌شات
        List<Rescuer> newRescuers = new ArrayList<Rescuer>();
        List<GameState.RescuerDTO> rSnap = loaded.getRescuerSnapshot();
        if (rSnap != null) {
            for (int i = 0; i < rSnap.size(); i++) {
                GameState.RescuerDTO dto = rSnap.get(i);
                Rescuer r = new Rescuer(dto.id, new Position(dto.tileX, dto.tileY));
                r.setDirection(dto.direction);
                r.setBusy(dto.busy);
                r.setAmbulanceMode(dto.ambulanceMode);
                r.setNoClip(dto.noClip);
                if (dto.carryingVictimId != null) {
                    Injured carried = victimMap.get(dto.carryingVictimId.intValue());
                    if (carried != null) {
                        r.attachVictim(carried);
                    }
                }
                newRescuers.add(r);
            }
        }

        // بازسازی بیمارستان‌ها
        List<Hospital> currentHospitals = this.state.getHospitals();
        if (currentHospitals != null) currentHospitals.clear();
        List<GameState.HospitalDTO> hSnap = loaded.getHospitalSnapshot();
        if (hSnap != null) {
            for (int i = 0; i < hSnap.size(); i++) {
                GameState.HospitalDTO dto = hSnap.get(i);
                if (currentHospitals != null) {
                    currentHospitals.add(new Hospital(new Position(dto.tileX, dto.tileY)));
                }
            }
        }

        agentManager.replaceAll(newRescuers);
        victimManager.replaceAll(newVictims);
        state.setRescuers(newRescuers);
        state.setVictims(newVictims);

        try {
            hudPanel.updateHUD(
                    ScoreManager.getScore(),
                    (int) victimManager.countRescued(),
                    (int) victimManager.countDead()
            );
            List<Rescuer> rescuersForRender = new ArrayList<Rescuer>(agentManager.getAllRescuers());
            List<Injured> victimsForRender = victimManager.getAll();
            CityMap map = this.state.getMap();
            gamePanel.updateData(map, rescuersForRender, victimsForRender);
            if (miniMapPanel != null) {
                miniMapPanel.updateMiniMap(map, rescuersForRender, victimsForRender);
            }
        } catch (Exception ex) {
            logger.logError("GameEngine.applyGameState/refreshUI", ex);
        }
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

    private GameState captureGameState() {
        GameState snap = new GameState();
        snap.setScore(ScoreManager.getScore());

        CityMap map = state.getMap();
        if (map != null) {
            try { snap.setSnapshotMapInfo(null, map.getWidth(), map.getHeight(), map.getTileWidth(), map.getTileHeight()); } catch (Throwable ignore) { }
        }

        Collection<Rescuer> rescuers = agentManager.getAllRescuers();
        for (Rescuer r : rescuers) {
            if (r == null) continue;
            Position p = r.getPosition();
            Integer cid = null;
            Injured cv = r.getCarryingVictim();
            if (cv != null) cid = cv.getId();
            snap.addRescuerSnapshot(r.getId(), p != null ? p.getX() : 0, p != null ? p.getY() : 0, r.getDirection(), r.isBusy(), r.isAmbulanceMode(), cid, r.isNoClip());
        }

        List<Injured> victims = victimManager.getAll();
        for (int i = 0; i < victims.size(); i++) {
            Injured v = victims.get(i);
            if (v == null) continue;
            Position p = v.getPosition();
            String sev = null;
            try { if (v.getSeverity() != null) sev = v.getSeverity().name(); } catch (Throwable ignore) { }
            snap.addVictimSnapshot(v.getId(), p != null ? p.getX() : 0, p != null ? p.getY() : 0, sev, !v.isDead(), v.isRescued(), v.isCritical(), v.getRemainingTime());
        }

        List<Hospital> hosp = state.getHospitals();
        if (hosp != null) {
            for (int i = 0; i < hosp.size(); i++) {
                Hospital h = hosp.get(i);
                if (h != null && h.getPosition() != null) {
                    snap.addHospitalSnapshot(h.getPosition().getX(), h.getPosition().getY());
                }
            }
        }
        return snap;
    }
}
