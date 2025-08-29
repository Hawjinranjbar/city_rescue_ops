import agent.Rescuer;
import map.CityMap;
import map.Cell;
import map.MapLoader;
import ui.GamePanel;
import util.MoveGuard;
import util.Position;
import util.CollisionMap;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static CityMap map;
    private static Rescuer r;
    private static GamePanel gamePanel;
    private static CollisionMap collisionMap;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    // 1) لود نقشه و CollisionMap از TMX
                    map = MapLoader.loadTMX("assets/maps/rescue_city.tmx");
                    collisionMap = CollisionMap.fromTMX("assets/maps/rescue_city.tmx");
                    map.setCollisionMap(collisionMap);

                    // 2) ساخت نجات‌دهنده و قرار دادن آن در پایین‌سمت راست جاده
                    List<Rescuer> rescuers = new ArrayList<>();
                    Position start = findBottomRightRoad();
                    r = new Rescuer(1, start);
                    Cell startCell = map.getCell(start.getX(), start.getY());
                    if (startCell != null) startCell.setOccupied(true);
                    rescuers.add(r);

                    // 3) ساخت پنل بازی
                    gamePanel = new GamePanel(map, rescuers, new ArrayList<>()); // فعلاً بدون Victim
                    gamePanel.setFocusable(true);

                    // 4) کنترل کیبورد (حرکت فقط از MoveGuard)
                    gamePanel.addKeyListener(new KeyAdapter() {
                        @Override public void keyPressed(KeyEvent e) {
                            if (r == null || r.getPosition() == null) return;

                            int x = r.getPosition().getX();
                            int y = r.getPosition().getY();
                            boolean moved = false;

                            int code = e.getKeyCode();
                            switch (code) {
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

                            if (moved) {
                                gamePanel.repaint();
                            }
                        }
                    });

                    // 5) فریم اصلی
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
                        @Override public void run() {
                            gamePanel.requestFocusInWindow();
                        }
                    });

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(null, "خطا در لود نقشه: " + ex.getMessage());
                }
            }
        });
    }

    // --- پیدا کردن نزدیک‌ترین جاده به گوشه‌ی پایین‌راست ---
    private static Position findBottomRightRoad() {
        for (int y = map.getHeight() - 1; y >= 0; y--) {
            for (int x = map.getWidth() - 1; x >= 0; x--) {
                Cell c = map.getCell(x, y);
                if (c != null && c.getType() == Cell.Type.ROAD && !c.isOccupied()) {
                    return new Position(x, y);
                }
                if (c == null && collisionMap != null && collisionMap.isWalkable(x, y)) {
                    // در صورتی که CollisionMap آن را قابل عبور بداند ولی سلول وجود ندارد
                    map.setCell(x, y, new Cell(new Position(x, y), Cell.Type.ROAD));
                    return new Position(x, y);
                }
            }
        }
        return new Position(0, 0);
    }
}
