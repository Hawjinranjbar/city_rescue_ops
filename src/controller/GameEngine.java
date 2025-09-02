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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * --------------------
 * لایه: Application Layer
 * --------------------
 * حلقه اصلی: چرخه نجات، تیک تایمر مجروح‌ها، بروزرسانی HUD و UI
 * بدون استفاده از لامبدا (ActionListener کلاسیک)
 */
public class GameEngine {

    private final RescueCoordinator rescueCoordinator;
    private final VictimManager victimManager;
    private final AgentManager agentManager;
    private final HUDPanel hudPanel;
    private final GamePanel gamePanel;
    private final MiniMapPanel miniMapPanel;

    private final javax.swing.Timer gameLoopTimer;
    private final GameState state;

    public GameEngine(GameState state,
                      RescueCoordinator rescueCoordinator,
                      AgentManager agentManager,
                      VictimManager victimManager,
                      HUDPanel hudPanel,
                      GamePanel gamePanel,
                      MiniMapPanel miniMapPanel) {

        this.state = state;
        this.rescueCoordinator = rescueCoordinator;
        this.agentManager = agentManager;
        this.victimManager = victimManager;
        this.hudPanel = hudPanel;
        this.gamePanel = gamePanel;
        this.miniMapPanel = miniMapPanel;

        // اطمینان از امتیاز اولیه و نمایش آن از همان شروع
        ScoreManager.resetToDefault();
        hudPanel.updateHUD(
                ScoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

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
        // 1) چرخه نجات
        rescueCoordinator.executeRescueCycle();

        // 2) تیک‌زدن تایمر مجروح‌ها + بررسی مرگ
        List<Injured> victimsSnapshot = victimManager.getAll(); // کپی برای حلقه
        for (int i = 0; i < victimsSnapshot.size(); i++) {
            Injured injured = victimsSnapshot.get(i);

            if (!injured.isDead() && !injured.isRescued()) {
                injured.getRescueTimer().tick();

                if (injured.getRescueTimer().isFinished()) {
                    // این متد هم markAsDead می‌کند و هم جریمه را طبق 2×زمان اولیه اعمال می‌کند
                    victimManager.onVictimDead(injured);
                }
            }
        }

        // 3) HUD با Score واقعی (جهانی)
        hudPanel.updateHUD(
                ScoreManager.getScore(),
                (int) victimManager.countRescued(),
                (int) victimManager.countDead()
        );

        // 4) رندر پنل‌ها
        List<Rescuer> rescuers = new ArrayList<Rescuer>(agentManager.getAllRescuers());
        List<Injured> victimsForRender = victimManager.getAll();

        gamePanel.updateData(map, rescuers, victimsForRender);
        miniMapPanel.updateMiniMap(map, rescuers, victimsForRender);
    }
}
