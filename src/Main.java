import agent.Rescuer;
import map.CityMap;
import map.Cell;
import map.MapLoader;
import ui.GamePanel;
import util.MoveGuard;
import util.Position;

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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override public void run() {
                try {
                    // 1) لود نقشه از TMX
                    map = MapLoader.loadTMX("assets/maps/rescue_city.tmx");

                    // 2) ساخت نجات‌دهنده
                    List<Rescuer> rescuers = new ArrayList<>();
                    r = new Rescuer(1, new Position(5, 5));
                    // اگر خانه‌ی شروع غیرقابل عبور بود، اولین خانه‌ی مجاز را پیدا کن
                    if (!isWalkable(r.getPosition())) {
                        Position start = findFirstWalkable();
                        r.setPosition(start);
                    }
                    // اشغال‌کردن خانه‌ی شروع
                    Cell startCell = map.getCell(r.getPosition().getX(), r.getPosition().getY());
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
                                    moved = MoveGuard.tryMoveTo(map, r, x, y - 1, 3);
                                    break;

                                case KeyEvent.VK_DOWN:
                                case KeyEvent.VK_S:
                                    moved = MoveGuard.tryMoveTo(map, r, x, y + 1, 0);
                                    break;

                                case KeyEvent.VK_LEFT:
                                case KeyEvent.VK_A:
                                    moved = MoveGuard.tryMoveTo(map, r, x - 1, y, 1);
                                    break;

                                case KeyEvent.VK_RIGHT:
                                case KeyEvent.VK_D:
                                    moved = MoveGuard.tryMoveTo(map, r, x + 1, y, 2);
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

    // --- ابزار عبورپذیری برای خانه‌ی شروع ---
    private static boolean isWalkable(Position p) {
        if (p == null || !map.isValid(p.getX(), p.getY())) return false;
        Cell c = map.getCell(p.getX(), p.getY());
        return c != null && c.isWalkable() && !c.isOccupied();
    }

    private static Position findFirstWalkable() {
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Cell c = map.getCell(x, y);
                if (c != null && c.isWalkable() && !c.isOccupied()) {
                    return new Position(x, y);
                }
            }
        }
        return new Position(0, 0);
    }
}
