import agent.Rescuer;
import file.GameState;
import map.*;
import playercontrol.PlayerInputHandler;
import strategy.InjuryPrioritySelector;
import strategy.AStarPathFinder;
import ui.*;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        // ساخت نقشه اولیه
        CityMap map = new CityMap(20, 15);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Cell.Type type = (x + y) % 7 == 0 ? Cell.Type.RUBBLE : Cell.Type.ROAD;
                if (x == 10 && y == 7) type = Cell.Type.HOSPITAL;
                map.setCell(x, y, new Cell(new Position(x, y), type));
            }
        }

        // ساخت بیمارستان
        List<Hospital> hospitals = new ArrayList<>();
        hospitals.add(new Hospital(new Position(10, 7)));

        // ساخت مجروح‌ها
        List<Injured> victims = new ArrayList<>();
        victims.add(new Injured(1, new Position(2, 3), InjurySeverity.CRITICAL, 20));
        victims.add(new Injured(2, new Position(8, 5), InjurySeverity.MEDIUM, 30));

        // ساخت نجات‌دهنده‌ها
        List<Rescuer> rescuers = new ArrayList<>();
        rescuers.add(new Rescuer(1, new Position(0, 0)));

        // ساخت پنل‌ها
        GamePanel gamePanel = new GamePanel(map, rescuers, victims);
        HUDPanel hudPanel = new HUDPanel();
        MiniMapPanel miniMapPanel = new MiniMapPanel(map, rescuers, victims);

        // کنترل ورودی کاربر
        PlayerInputHandler inputHandler = new PlayerInputHandler();
        KeyHandler keyHandler = new KeyHandler(rescuers, rescuers.get(0), inputHandler);
        gamePanel.addKeyListener(keyHandler);
        gamePanel.setFocusable(true);

        // ساخت پنجره اصلی
        JFrame frame = new JFrame("City Rescue Ops");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        frame.add(gamePanel, BorderLayout.CENTER);
        frame.add(hudPanel, BorderLayout.SOUTH);
        frame.add(miniMapPanel, BorderLayout.EAST);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // مقداردهی HUD تستی
        hudPanel.updateHUD(500, 0, 0);
    }
}
