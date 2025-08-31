import agent.Rescuer;
import map.CityMap;
import map.Cell;
import map.MapLoader;
import ui.GamePanel;
import util.MoveGuard;
import util.Position;
import util.CollisionMap;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main {

    private static CityMap map;
    private static Rescuer r;
    private static GamePanel gamePanel;
    private static CollisionMap collisionMap;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    // 1) Load map + collision directly from TMX properties
                    map = MapLoader.loadTMX("assets/maps/rescue_city.tmx");
                    collisionMap = CollisionMap.fromTMX("assets/maps/rescue_city.tmx");
                    map.setCollisionMap(collisionMap);

                    // 2) Spawn rescuer at nearest road (bottom-right search)
                    List<Rescuer> rescuers = new ArrayList<>();
                    Position start = findBottomRightRoad();
                    r = new Rescuer(1, start);
                    Cell startCell = map.getCell(start.getX(), start.getY());
                    if (startCell != null) startCell.setOccupied(true);
                    rescuers.add(r);

                    // 3) Spawn victims at random walkable tiles
                    List<Injured> victims = spawnVictims(map, collisionMap, 5);

                    // 4) Game panel
                    gamePanel = new GamePanel(map, rescuers, victims);
                    gamePanel.setFocusable(true);

                    // 5) Keyboard control – movement only if MoveGuard allows (roads only)
                    gamePanel.addKeyListener(new KeyAdapter() {
                        @Override public void keyPressed(KeyEvent e) {
                            if (r == null || r.getPosition() == null) return;

                            int x = r.getPosition().getX();
                            int y = r.getPosition().getY();
                            boolean moved = false;

                            switch (e.getKeyCode()) {
                                case KeyEvent.VK_UP:
                                case KeyEvent.VK_W:
                                    moved = MoveGuard.tryMoveTo(map, collisionMap, r, x, y - 1, 3);
                                    break;
                                case KeyEvent.VK_DOWN:
                                case KeyEvent.VK_S:
                                    moved = MoveGuard.tryMoveTo(map, collisionMap, r, x, y + 1, 0);
                                    break;
                                case KeyEvent.VK_LEFT:
                                case KeyEvent.VK_A:
                                    moved = MoveGuard.tryMoveTo(map, collisionMap, r, x - 1, y, 1);
                                    break;
                                case KeyEvent.VK_RIGHT:
                                case KeyEvent.VK_D:
                                    moved = MoveGuard.tryMoveTo(map, collisionMap, r, x + 1, y, 2);
                                    break;
                                default:
                                    break;
                            }

                            if (moved) gamePanel.repaint();
                        }
                    });

                    // 6) Frame
                    JFrame frame = new JFrame("City Rescue Ops - Test");
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.setLayout(new BorderLayout());
                    frame.add(gamePanel, BorderLayout.CENTER);

                    int w = Math.min(1024, map.getWidth() * map.getTileWidth());
                    int h = Math.min(768,  map.getHeight() * map.getTileHeight());
                    frame.setSize(Math.max(w, 640), Math.max(h, 480));
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override public void run() { gamePanel.requestFocusInWindow(); }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "خطا در لود نقشه: " + ex.getMessage());
                }
            }
        });
    }

    /** تعدادی مجروح را روی تایل‌های قابل‌عبور و خالی به‌صورت تصادفی پخش می‌کند. */
    private static List<Injured> spawnVictims(CityMap map, CollisionMap cm, int count) {
        List<Injured> list = new ArrayList<>();
        if (map == null) return list;

        Random rnd = new Random();
        int w = map.getWidth();
        int h = map.getHeight();
        int id = 1;
        int attempts = 0;
        while (id <= count && attempts < count * 20) {
            int x = rnd.nextInt(w);
            int y = rnd.nextInt(h);
            Cell c = map.getCell(x, y);
            boolean walk = (cm != null) ? cm.isWalkable(x, y) : (c != null && c.isWalkable());
            boolean free = (c == null) || !c.isOccupied();
            if (walk && free) {
                InjurySeverity sev = InjurySeverity.values()[rnd.nextInt(InjurySeverity.values().length)];
                Injured inj = new Injured(id, new Position(x, y), sev, 300);
                list.add(inj);
                if (c != null) c.setOccupied(true);
                id++;
            }
            attempts++;
        }
        return list;
    }

    /** نزدیک‌ترین خانهٔ جاده از گوشهٔ پایین‌راست را برمی‌گرداند. */
    private static Position findBottomRightRoad() {
        if (map == null) return new Position(0, 0);

        for (int y = map.getHeight() - 1; y >= 0; y--) {
            for (int x = map.getWidth() - 1; x >= 0; x--) {
                Cell c = map.getCell(x, y);

                boolean walkable = (collisionMap != null)
                        ? collisionMap.isWalkable(x, y)
                        : (c != null && c.getType() == Cell.Type.ROAD);
                boolean free = (c == null) || !c.isOccupied();

                if (walkable && free) {
                    if (c == null || c.getType() != Cell.Type.ROAD) {
                        map.setCell(x, y, new Cell(new Position(x, y), Cell.Type.ROAD));
                    }
                    return new Position(x, y);
                }
            }
        }
        // اگر هیچ جاده‌ای پیدا نشد، نقطهٔ 0,0
        return new Position(12, 12);
    }
}
