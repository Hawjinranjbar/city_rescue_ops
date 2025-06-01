package ui;

import agent.Rescuer;
import map.Cell;
import map.CityMap;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * --------------------
 * لایه: UI Layer
 * --------------------
 * نمایش نقشه، نجات‌دهنده‌ها و مجروح‌ها.
 * شامل اورلی‌های دیباگ برای walkable/occupied و خطوط شبکه.
 */
public class GamePanel extends JPanel {

    // ---- داده‌ها ----
    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;

    // ---- تنظیمات نمایش ----
    private int tileSize = 32;             // اندازه‌ی رسم هر تایل روی صفحه
    private boolean drawGrid = false;      // نمایش خطوط شبکه
    private double rescuerScale = 2.0;     // بزرگ‌نمایی فقط برای Rescuer
    private boolean debugWalkable = false; // اورلی دیباگ: سبز/قرمز روی نقشه

    // ---- سازنده ----
    public GamePanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        if (cityMap != null) {
            setPreferredSize(new Dimension(cityMap.getWidth() * tileSize, cityMap.getHeight() * tileSize));
        } else {
            setPreferredSize(new Dimension(800, 600));
        }

        setFocusable(true);
        requestFocusInWindow();
        setDoubleBuffered(true);
    }

    // ---------------------- رندر اصلی ----------------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // پس‌زمینه
        g.setColor(new Color(200, 200, 200));
        g.fillRect(0, 0, getWidth(), getHeight());

        if (cityMap != null) {
            drawMap(g);
        }

        if (victims != null) {
            drawVictims(g);
        }

        if (rescuers != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            drawRescuers(g2);
            g2.dispose();
        }

        if (debugWalkable && cityMap != null) {
            drawWalkableOverlay(g);
        }

        if (drawGrid && cityMap != null) {
            drawGridLines(g);
        }
    }

    // ---------------------- متدهای رندر ----------------------
    private void drawMap(Graphics g) {
        for (int y = 0; y < cityMap.getHeight(); y++) {
            for (int x = 0; x < cityMap.getWidth(); x++) {
                Cell cell = cityMap.getCell(x, y);
                if (cell == null) continue;

                BufferedImage tileImg = cell.getImage();
                if (tileImg != null) {
                    g.drawImage(tileImg, x * tileSize, y * tileSize, tileSize, tileSize, null);
                } else {
                    g.setColor(Color.GRAY);
                    g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
                }
            }
        }
    }

    private void drawVictims(Graphics g) {
        for (Injured inj : victims) {
            if (inj == null || inj.isDead() || inj.isRescued()) continue;

            Position p = inj.getPosition();
            if (p == null) continue;

            Color col;
            InjurySeverity sev = inj.getSeverity();
            if (sev == InjurySeverity.LOW) col = Color.YELLOW;
            else if (sev == InjurySeverity.MEDIUM) col = Color.ORANGE;
            else col = Color.RED;

            g.setColor(col);
            int r = tileSize / 2;
            int cx = p.getX() * tileSize + (tileSize - r) / 2;
            int cy = p.getY() * tileSize + (tileSize - r) / 2;
            g.fillOval(cx, cy, r, r);
        }
    }

    private void drawRescuers(Graphics2D g2) {
        for (Rescuer r : rescuers) {
            if (r == null || r.getPosition() == null) continue;

            Position pos = r.getPosition();
            int baseX = pos.getX() * tileSize;
            int baseY = pos.getY() * tileSize;
            int size = (int) Math.round(tileSize * rescuerScale);

            Image sprite = r.getSpriteScaled(size);

            int drawX = baseX + (tileSize - size) / 2;
            int drawY = baseY + (tileSize - size);

            if (sprite != null) {
                g2.drawImage(sprite, drawX, drawY, size, size, null);
            } else {
                g2.setColor(new Color(0, 70, 200));
                g2.fillRect(drawX, drawY, size, size);
            }
        }
    }

    private void drawWalkableOverlay(Graphics g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
        for (int y = 0; y < cityMap.getHeight(); y++) {
            for (int x = 0; x < cityMap.getWidth(); x++) {
                Cell c = cityMap.getCell(x, y);
                if (c == null) continue;
                int px = x * tileSize;
                int py = y * tileSize;
                if (c.isWalkable() && !c.isOccupied()) {
                    gg.setColor(Color.GREEN);
                } else {
                    gg.setColor(Color.RED);
                }
                gg.fillRect(px, py, tileSize, tileSize);
            }
        }
        gg.dispose();
    }

    private void drawGridLines(Graphics g) {
        g.setColor(new Color(0, 0, 0, 40));
        int w = (cityMap != null ? cityMap.getWidth() * tileSize : getWidth());
        int h = (cityMap != null ? cityMap.getHeight() * tileSize : getHeight());

        for (int x = 0; x <= w; x += tileSize) g.drawLine(x, 0, x, h);
        for (int y = 0; y <= h; y += tileSize) g.drawLine(0, y, w, y);
    }

    // ---------------------- Setter / Update ----------------------
    public void updateData(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        if (cityMap != null) {
            setPreferredSize(new Dimension(cityMap.getWidth() * tileSize, cityMap.getHeight() * tileSize));
        }
        revalidate();
        repaint();
    }

    public void setTileSize(int tileSize) {
        if (tileSize <= 0) return;
        this.tileSize = tileSize;
        if (cityMap != null) {
            setPreferredSize(new Dimension(cityMap.getWidth() * tileSize, cityMap.getHeight() * tileSize));
        }
        revalidate();
        repaint();
    }

    public void setRescuerScale(double scale) {
        if (scale > 0) {
            this.rescuerScale = scale;
            repaint();
        }
    }

    public void setDebugWalkable(boolean on) {
        this.debugWalkable = on;
        repaint();
    }

    public void setDrawGrid(boolean drawGrid) {
        this.drawGrid = drawGrid;
        repaint();
    }

    public void setMap(CityMap cityMap) {
        this.cityMap = cityMap;
        if (cityMap != null) {
            setPreferredSize(new Dimension(cityMap.getWidth() * tileSize, cityMap.getHeight() * tileSize));
        }
        revalidate();
        repaint();
    }

    public void setRescuers(List<Rescuer> rescuers) {
        this.rescuers = rescuers;
        repaint();
    }

    public void setVictims(List<Injured> victims) {
        this.victims = victims;
        repaint();
    }
}

