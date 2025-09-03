package controller;

import agent.AgentManager;
import agent.Rescuer;
import file.GameState;
import file.SaveManager;
import map.CityMap;
import map.Hospital;
import ui.GamePanel;
import ui.HUDPanel;
import ui.KeyHandler;
import ui.MiniMapPanel;
import util.Logger;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;
import victim.VictimManager;

// امتیاز
import controller.ScoreManager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application Layer — حلقه اصلی بازی + Save/Load/Restart + لاگ رویدادها
 * بدون استفاده از لامبدا
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
    /** وضعیت جاری (برای Save/Load) */
    private final GameState state;
    /** برای همگام‌سازی ورودی‌ها بعد از Load/Restart (اختیاری) */
    private KeyHandler keyHandler;

    /** برای تشخیص ورود تازه به حالت آمبولانس (Pickup) */
    private final Map<Integer, Boolean> prevAmbulanceState = new HashMap<Integer, Boolean>();

    /** مسیر کوییک‌سیو */
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
        this.logger = (logger != null) ? logger : new Logger("logs/game.log", true);

        // اتصال HUD به موتور
        if (this.hudPanel != null) {
            try { this.hudPanel.setGameEngine(this); } catch (Throwable ignored) {}
        }

        // امتیاز اولیه
        ScoreManager.resetToDefault();
        if (this.hudPanel != null) {
            this.hudPanel.updateHUD(
                    ScoreManager.getScore(),
                    (int) victimManager.countRescued(),
                    (int) victimManager.countDead()
            );
        }

        // لاگ شروع
        try {
            CityMap map = this.state.getMap();
            List<Hospital> hs = this.state.getHospitals();
            int w = (map != null) ? map.getWidth() : -1;
            int h = (map != null) ? map.getHeight() : -1;
            int hc = (hs != null) ? hs.size() : 0;
            logger.logGameStart(w, h, hc, ScoreManager.getScore());
        } catch (Exception ex) {
            logger.logError("GameEngine.<init>/logGameStart", ex);
        }

        // اسنپ‌شات اولیه برای Restart (فایل نمی‌سازد)
        try { SaveManager.setInitialState(captureGameState()); } catch (Throwable ignored) {}

        // حلقه بازی (۱۰۰۰ms)
        this.gameLoopTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGame(GameEngine.this.state.getMap(), GameEngine.this.state.getHospitals());
            }
        });
    }

    /** ثبت KeyHandler (اختیاری) */
    public void setKeyHandler(KeyHandler handler) {
        this.keyHandler = handler;
    }

    // --- اسپاون نجات‌دهندهٔ هوش مصنوعی از HUD ---
    public void spawnAIRescuer() {
        CityMap map = state.getMap();
        List<Rescuer> rescuerList = state.getRescuers();
        if (map == null || rescuerList == null) return;

        Position spawn = findSpawnTile(map, rescuerList);
        int newId = agentManager.size() + 1;
        Rescuer ai = new Rescuer(newId, spawn);
        ai.setAIControlled(true);
        rescuerList.add(ai);
        agentManager.addRescuer(ai);
        map.setOccupied(spawn.getX(), spawn.getY(), true);
        prevAmbulanceState.put(ai.getId(), Boolean.FALSE);

        if (miniMapPanel != null) {
            miniMapPanel.updateMiniMap(map, rescuerList, state.getVictims());
        }

        if (gamePanel != null) {
            gamePanel.updateData(map, rescuerList, state.getVictims());
        }
        if (hudPanel != null) hudPanel.updateHUD(
                ScoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

        if (gamePanel != null) gamePanel.repaint();
        if (hudPanel != null) hudPanel.repaint();

        start();
    }

    private Position findSpawnTile(CityMap map, List<Rescuer> rescuers) {
        if (rescuers != null && !rescuers.isEmpty()) {
            Position base = rescuers.get(0).getPosition();
            int[] dx = {0, 1, 0, -1};
            int[] dy = {1, 0, -1, 0};
            for (int i = 0; i < dx.length; i++) {
                int nx = base.getX() + dx[i];
                int ny = base.getY() + dy[i];
                if (map.isValid(nx, ny) && map.isRoad(nx, ny) && map.isWalkable(nx, ny)) {
                    return new Position(nx, ny);
                }
            }
        }
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                if (map.isRoad(x, y) && map.isWalkable(x, y)) {
                    return new Position(x, y);
                }
            }
        }
        return new Position(0, 0);
    }

    // ------------------------------
    // کنترل حلقه
    // ------------------------------
    public void start() { try { gameLoopTimer.start(); } catch (Throwable ignored) {} }
    public void stop()  { try { gameLoopTimer.stop();  } catch (Throwable ignored) {} }

    // ------------------------------
    // چرخه‌ی اصلی
    // ------------------------------
    private void updateGame(CityMap map, List<Hospital> hospitals) {
        // 1) چرخه نجات
        rescueCoordinator.executeRescueCycle();

        // 2) تشخیص Pickup
        Collection<Rescuer> rescuers = agentManager.getAllRescuers();
        for (Rescuer r : rescuers) {
            if (r == null) continue;
            boolean wasAmb = prevAmbulanceState.containsKey(r.getId()) &&
                    Boolean.TRUE.equals(prevAmbulanceState.get(r.getId()));
            boolean nowAmb = r.isAmbulanceMode();

            if (nowAmb && !wasAmb) {
                Injured v = r.getCarryingVictim();
                if (v != null) {
                    try {
                        String sev = (v.getSeverity() != null) ? v.getSeverity().name() : "null";
                        logger.logAmbulancePickup(
                                r.getId(),
                                safePos(r.getPosition()),
                                v.getId(),
                                sev,
                                v.getInitialTimeLimit()
                        );
                    } catch (Exception ex) {
                        logger.logError("GameEngine.updateGame/logAmbulancePickup", ex);
                    }
                }
            }
            prevAmbulanceState.put(r.getId(), nowAmb);
        }

        // 3) تحویل کنار بیمارستان (Deliver)
        for (Rescuer r : rescuers) {
            if (r == null || !r.isAmbulanceMode()) continue;
            if (hospitals == null || hospitals.isEmpty()) continue;

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
                prevAmbulanceState.put(r.getId(), Boolean.FALSE);
            }
        }

        // 4) تیک تایمر مجروح‌ها + مرگ
        List<Injured> victimsSnapshot = victimManager.getAll();
        for (int i = 0; i < victimsSnapshot.size(); i++) {
            Injured inj = victimsSnapshot.get(i);
            if (inj.isDead() || inj.isRescued()) continue;

            inj.getRescueTimer().tick();
            if (inj.getRescueTimer().isFinished()) {
                int penalty = 2 * Math.max(0, inj.getInitialTimeLimit());
                victimManager.onVictimDead(inj);
                try {
                    String sev = (inj.getSeverity() != null) ? inj.getSeverity().name() : "null";
                    logger.logVictimDeath(
                            inj.getId(), sev, inj.getInitialTimeLimit(),
                            penalty, ScoreManager.getScore()
                    );
                } catch (Exception ex) {
                    logger.logError("GameEngine.updateGame/logVictimDeath", ex);
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

        // 6) رندر UI
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

    // ------------------------------
    // Save / Load / Restart
    // ------------------------------
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
            SaveManager.saveGameToPath(captureGameState(), path);
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
        if (loaded == null) return;

        // --- امتیاز ---
        try { ScoreManager.setScore(loaded.getScore()); }
        catch (Throwable t) {
            try {
                ScoreManager.resetToDefault();
                int diff = loaded.getScore() - ScoreManager.getScore();
                if (diff > 0) ScoreManager.add(diff);
            } catch (Throwable ignored) {}
        }

        // --- بازسازی قربانی‌ها از DTO ---
        Map<Integer, Injured> victimMap = new HashMap<Integer, Injured>();
        List<Injured> newVictims = new ArrayList<Injured>();
        List<GameState.InjuredDTO> vSnap = loaded.getVictimSnapshot();
        if (vSnap != null) {
            for (int i = 0; i < vSnap.size(); i++) {
                GameState.InjuredDTO dto = vSnap.get(i);

                InjurySeverity sev = InjurySeverity.LOW;
                try { if (dto.severity != null) sev = InjurySeverity.valueOf(dto.severity); } catch (Throwable ignored) {}

                Injured inj = new Injured(dto.id, new Position(dto.tileX, dto.tileY), sev);
                if (dto.alive) inj.markAsAlive();
                if (dto.rescued) inj.markAsRescued();
                if (!dto.alive && !dto.rescued) inj.markAsDead();
                if (dto.critical) inj.markAsCritical();
                inj.setRemainingTime((int) dto.remainingMillis);

                victimMap.put(dto.id, inj);
                newVictims.add(inj);
            }
        }

        // --- بازسازی نجات‌دهنده‌ها از DTO ---
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
                    if (carried != null) r.attachVictim(carried);
                }
                newRescuers.add(r);
            }
        }

        // --- بازسازی بیمارستان‌ها از DTO ---
        List<Hospital> newHospitals = new ArrayList<Hospital>();
        List<GameState.HospitalDTO> hSnap = loaded.getHospitalSnapshot();
        if (hSnap != null) {
            for (int i = 0; i < hSnap.size(); i++) {
                GameState.HospitalDTO dto = hSnap.get(i);
                newHospitals.add(new Hospital(new Position(dto.tileX, dto.tileY)));
            }
        }

        // --- جایگزینی State ---
        try {
            this.state.replaceWith(loaded);
        } catch (Throwable t) {
            try {
                this.state.setMap(loaded.getMap());
                this.state.setHospitals(newHospitals);
                this.state.setRescuers(newRescuers);
                this.state.setVictims(newVictims);
                this.state.setScore(loaded.getScore());
            } catch (Throwable ignored) {}
        }

        // --- جایگزینی در منیجرها ---
        agentManager.replaceAll(newRescuers);
        victimManager.replaceAll(newVictims);

        // --- نوسازی UI ---
        try {
            if (hudPanel != null) {
                hudPanel.updateHUD(
                        ScoreManager.getScore(),
                        (int) victimManager.countRescued(),
                        (int) victimManager.countDead()
                );
            }
            CityMap map = this.state.getMap();
            List<Rescuer> rr = new ArrayList<Rescuer>(agentManager.getAllRescuers());
            List<Injured> vv = victimManager.getAll();

            if (gamePanel != null) {
                gamePanel.updateData(map, rr, vv);
                try { gamePanel.repaint(); } catch (Throwable ignored) {}
            }
            if (miniMapPanel != null) {
                miniMapPanel.updateMiniMap(map, rr, vv);
                try { miniMapPanel.repaint(); } catch (Throwable ignored) {}
            }

            if (keyHandler != null) {
                Rescuer first = rr.isEmpty() ? null : rr.get(0);
                keyHandler.onWorldReloaded(rr, first, vv, map);
            }
        } catch (Exception ex) {
            logger.logError("GameEngine.applyGameState/refreshUI", ex);
        }

        // --- ریست وضعیت Pickup ---
        try { prevAmbulanceState.clear(); } catch (Throwable ignored) {}
    }

    // ------------------------------
    // کنترل توقف/ادامه
    // ------------------------------
    private void pauseAll() {
        try { gameLoopTimer.stop(); } catch (Throwable ignored) {}
        try { rescueCoordinator.pauseAll(); } catch (Throwable ignored) {}
        try { agentManager.pauseAll(); } catch (Throwable ignored) {}
        try { victimManager.pauseAll(); } catch (Throwable ignored) {}
    }

    private void resumeAll() {
        try { victimManager.resumeAll(); } catch (Throwable ignored) {}
        try { agentManager.resumeAll(); } catch (Throwable ignored) {}
        try { rescueCoordinator.resumeAll(); } catch (Throwable ignored) {}
        try { gameLoopTimer.start(); } catch (Throwable ignored) {}
    }

    // ------------------------------
    // کمک‌متدها
    // ------------------------------
    private static Position safePos(Position p) {
        return (p == null) ? new Position(0, 0) : p;
    }

    private GameState captureGameState() {
        GameState snap = new GameState();
        snap.setScore(ScoreManager.getScore());

        CityMap map = state.getMap();
        if (map != null) {
            try {
                snap.setSnapshotMapInfo(
                        null,
                        map.getWidth(), map.getHeight(),
                        map.getTileWidth(), map.getTileHeight()
                );
            } catch (Throwable ignored) {}
        }

        // Rescuers
        Collection<Rescuer> rescuers = agentManager.getAllRescuers();
        for (Rescuer r : rescuers) {
            if (r == null) continue;
            Position p = r.getPosition();
            Integer cid = null;
            Injured cv = r.getCarryingVictim();
            if (cv != null) cid = cv.getId();

            int tx = (p != null) ? p.getX() : 0;
            int ty = (p != null) ? p.getY() : 0;

            snap.addRescuerSnapshot(
                    r.getId(), tx, ty, r.getDirection(),
                    r.isBusy(), r.isAmbulanceMode(), cid, r.isNoClip()
            );
        }

        // Victims
        List<Injured> victims = victimManager.getAll();
        for (int i = 0; i < victims.size(); i++) {
            Injured v = victims.get(i);
            if (v == null) continue;
            Position p = v.getPosition();

            String sev = null;
            try { if (v.getSeverity() != null) sev = v.getSeverity().name(); } catch (Throwable ignored) {}

            snap.addVictimSnapshot(
                    v.getId(),
                    (p != null) ? p.getX() : 0,
                    (p != null) ? p.getY() : 0,
                    sev,
                    !v.isDead(),
                    v.isRescued(),
                    v.isCritical(),
                    v.getRemainingTime()
            );
        }

        // Hospitals
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
