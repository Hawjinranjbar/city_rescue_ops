package controller;

import agent.AgentManager;
import agent.Rescuer;
import file.GameState;
import map.CityMap;
import map.Hospital;
import ui.GamePanel;
import ui.HUDPanel;
import ui.MiniMapPanel;
import victim.Injured;
import victim.VictimManager;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

// --------------------
// لایه: Application Layer
// --------------------
// این کلاس چرخه‌ی اصلی بازی رو اجرا می‌کنه
// عملیات نجات، به‌روزرسانی HUD و UI رو مدیریت می‌کنه
public class GameEngine {

    private final RescueCoordinator rescueCoordinator;
    private final VictimManager victimManager;
    private final AgentManager agentManager;
    private final HUDPanel hudPanel;
    private final GamePanel gamePanel;
    private final MiniMapPanel miniMapPanel;
    private final ScoreManager scoreManager;
    private final Timer timer;

    public GameEngine(GameState state,
                      RescueCoordinator rescueCoordinator,
                      AgentManager agentManager,
                      VictimManager victimManager,
                      HUDPanel hudPanel,
                      GamePanel gamePanel,
                      MiniMapPanel miniMapPanel,
                      ScoreManager scoreManager) {

        this.rescueCoordinator = rescueCoordinator;
        this.agentManager = agentManager;
        this.victimManager = victimManager;
        this.hudPanel = hudPanel;
        this.gamePanel = gamePanel;
        this.miniMapPanel = miniMapPanel;
        this.scoreManager = scoreManager;

        // اجرای چرخه بازی هر ۱ ثانیه
        this.timer = new Timer(1000, e -> updateGame(state.getMap(), state.getHospitals()));
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
    }

    private void updateGame(CityMap map, List<Hospital> hospitals) {
        // اجرای عملیات نجات
        rescueCoordinator.executeRescueCycle();

        // به‌روزرسانی وضعیت مجروح‌ها و امتیاز
        for (Injured injured : victimManager.getAll()) {
            if (!injured.isDead() && !injured.isRescued()) {
                injured.getRescueTimer().tick();

                if (injured.getRescueTimer().isFinished()) {
                    injured.markAsDead();
                    scoreManager.penalize();
                }
            }
        }

        // بروزرسانی HUD
        hudPanel.updateHUD(
                scoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

        // بروزرسانی نقشه‌ها (تبدیل Collection به List)
        gamePanel.updateData(
                map,
                new ArrayList<>(agentManager.getAllRescuers()),
                victimManager.getAll()
        );
        miniMapPanel.updateMiniMap(
                map,
                new ArrayList<>(agentManager.getAllRescuers()),
                victimManager.getAll()
        );
    }
}
