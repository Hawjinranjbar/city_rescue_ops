import agent.Rescuer;
import map.Cell;
import map.CityMap;
import map.MapLoader;
import playercontrol.DecisionInterface;
import ui.GamePanel;
import ui.KeyHandler;
import util.Position;
import victim.Injured;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String TMX_PATH = "assets/maps/rescue_city.tmx";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    // 1) لود نقشه از TMX
                    CityMap cityMap = MapLoader.loadTMX(TMX_PATH);

                    // 2) ساخت یک Rescuer روی اولین تایل جاده
                    List<Rescuer> rescuers = new ArrayList<Rescuer>();
                    Position spawn = findFirstOfType(cityMap, Cell.Type.ROAD);
                    if (spawn == null) spawn = new Position(1, 1);
                    Rescuer r1 = new Rescuer(1, spawn);
                    rescuers.add(r1);
                    cityMap.setOccupied(spawn.getX(), spawn.getY(), true);

                    // 3) لیست مجروح‌ها (فعلاً خالی)
                    List<Injured> victims = new ArrayList<Injured>();

                    // 4) پنل رندر
                    final GamePanel panel = new GamePanel(cityMap, rescuers, victims);
                    panel.setDrawGrid(false);
                    panel.setDebugWalkable(false);

                    // 5) کنترل کیبورد برای Rescuer (بدون لامبدا)
                    DecisionInterface decision = new DecisionInterface() {
                        @Override
                        public Rescuer switchToNextRescuer(Rescuer current, List<Rescuer> all) {
                            if (all == null || all.isEmpty() || current == null) return current;
                            int idx = all.indexOf(current);
                            if (idx < 0) return all.get(0);
                            return all.get((idx + 1) % all.size());
                        }
                        @Override
                        public Injured chooseVictim(Rescuer current, List<Injured> candidates) {
                            return (candidates == null || candidates.isEmpty()) ? null : candidates.get(0);
                        }
                    };
                    KeyHandler kh = new KeyHandler(rescuers, r1, decision, cityMap, /*collision*/ null, panel);
                    panel.addKeyListener(kh);

                    // 6) فریم و نمایش
                    JFrame f = new JFrame("City Rescue Ops — Simulation");
                    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
                    f.setLayout(new BorderLayout());
                    f.add(panel, BorderLayout.CENTER);
                    f.pack();
                    f.setLocationRelativeTo(null);
                    f.setVisible(true);
                    panel.requestFocusInWindow();

                    // 7) ریپینت سبک (بدون لامبدا)
                    javax.swing.Timer repaintTimer = new javax.swing.Timer(80, new ActionListener() {
                        @Override public void actionPerformed(ActionEvent e) {
                            panel.repaint();
                        }
                    });
                    repaintTimer.start();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "خطا: " + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private static Position findFirstOfType(CityMap map, Cell.Type type) {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Cell c = map.getCell(x, y);
                if (c != null && c.getType() == type) return new Position(x, y);
            }
        }
        return null;
    }
}
