package controller;

import agent.AgentManager;
import agent.Rescuer;
import file.GameState;
import map.CityMap;
import map.Hospital;
import ui.GamePanel;
import ui.HUDPanel;
import ui.MiniMapPanel;
import util.Logger;
import util.Position;
import victim.Injured;
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
    private final GameState state;

    /** برای تشخیص «ورود تازه به حالت آمبولانس» جهت لاگ Pickup */
    private final Map<Integer, Boolean> prevAmbulanceState = new HashMap<Integer, Boolean>();

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

        // حلقه بازی هر ۱ ثانیه (1000ms) – بدون لامبدا
        this.gameLoopTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                updateGame(GameEngine.this.state.getMap(), GameEngine.this.state.getHospitals());
            }
        });
    }

    public void start() {
        gameLoopTimer.start();
    }

    public void stop() {
        gameLoopTimer.stop();
    }

    private void updateGame(CityMap map, List<Hospital> hospitals) {
        // 1) چرخه نجات (انتخاب/حرکت/برداشت مجروح → تبدیل به آمبولانس)
        rescueCoordinator.executeRescueCycle();

        // 2) تشخیص "ورود تازه" به حالت آمبولانس برای ثبت لاگ Pickup
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

        // 3) اگر ریسکیر در حالت آمبولانس است و کنار نزدیک‌ترین بیمارستان ایستاده → تحویل + لاگ Deliver
        for (Rescuer r : rescuers) {
            if (r != null && r.isAmbulanceMode() && hospitals != null && !hospitals.isEmpty()) {
                Hospital nearest = Hospital.findNearest(hospitals, r.getPosition());
                if (nearest != null && nearest.canDeliverFrom(r.getPosition(), map)) {
                    Injured v = r.getCarryingVictim();
                    if (v != null) {
                        // شمارندهٔ نجات در VictimManager
                        victimManager.onVictimRescued(v);
                        // مقدار پاداش قبل از تحویل (2×t0)
                        int reward = 2 * Math.max(0, v.getInitialTimeLimit());
                        // تحویل نهایی + اضافه شدن امتیاز در Rescuer.deliverVictimAtHospital()
                        r.deliverVictimAtHospital();
                        // لاگ تحویل
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
                    // خروج از حالت آمبولانس ثبت شود
                    prevAmbulanceState.put(r.getId(), false);
                }
            }
        }

        // 4) تیک‌زدن تایمر مجروح‌ها + بررسی مرگ (جریمه ۲×زمان اولیه) + لاگ Death
        List<Injured> victimsSnapshot = victimManager.getAll(); // کپی برای حلقه
        for (int i = 0; i < victimsSnapshot.size(); i++) {
            Injured injured = victimsSnapshot.get(i);

            if (!injured.isDead() && !injured.isRescued()) {
                injured.getRescueTimer().tick();

                if (injured.getRescueTimer().isFinished()) {
                    int penalty = 2 * Math.max(0, injured.getInitialTimeLimit());
                    // این متد هم markAsDead می‌کند و هم جریمه را طبق 2×زمان اولیه اعمال می‌کند
                    victimManager.onVictimDead(injured);
                    // لاگ مرگ بعد از اعمال جریمه تا امتیاز جدید صحیح باشد
                    try {
                        String sev = injured.getSeverity() != null ? injured.getSeverity().name() : "null";
                        logger.logVictimDeath(injured.getId(), sev, injured.getInitialTimeLimit(), penalty, ScoreManager.getScore());
                    } catch (Exception ex) {
                        logger.logError("GameEngine.updateGame/logVictimDeath", ex);
                    }
                }
            }
        }

        // 5) HUD با Score واقعی (جهانی)
        hudPanel.updateHUD(
                ScoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

        // 6) رندر پنل‌ها
        List<Rescuer> rescuersForRender = new ArrayList<Rescuer>(rescuers); // از Collection به List
        List<Injured> victimsForRender = victimManager.getAll();

        gamePanel.updateData(map, rescuersForRender, victimsForRender);
        miniMapPanel.updateMiniMap(map, rescuersForRender, victimsForRender);
    }

    private static Position safePos(Position p) {
        return p == null ? new Position(0, 0) : p;
    }
}
