// ui/GamePanel.java
package ui;

import agent.Rescuer;
import map.Cell;
import map.CityMap;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * --------------------
 * لایه: UI Layer
 * --------------------
 * پنل رندرِ بازی: نقشه، نجات‌دهنده‌ها و مجروح‌ها + اورلی دیباگ و گرید.
 * مختصات، تایل‌محور است.
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
    private boolean debugWalkable = false; // اورلی دیباگ: سبز/قرمز (walkable/occupied)

    // ---- Viewport ----
    private int viewX = 0;                 // مختصات تایل بالا-چپ ویوپورت
    private int viewY = 0;
    private int viewWidth;                 // عرض ویوپورت بر حسب تعداد تایل
    private int viewHeight;                // ارتفاع ویوپورت بر حسب تعداد تایل

    // ---- سازنده ----
    public GamePanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        // اندازه اولیهٔ ویوپورت بر اساس اندازهٔ پنل در زمان اجرا تنظیم می‌شود
        this.viewWidth = 1;
        this.viewHeight = 1;

        if (cityMap != null) {
            this.viewWidth = Math.max(1, cityMap.getWidth() / 2);
            this.viewHeight = Math.max(1, cityMap.getHeight() / 2);
            setPreferredSize(new Dimension(viewWidth * tileSize,
                    viewHeight * tileSize));
        } else {
            this.viewWidth = 25; // مقادیر پیش‌فرض اگر نقشه‌ای موجود نباشد
            this.viewHeight = 19;
            setPreferredSize(new Dimension(viewWidth * tileSize,
                    viewHeight * tileSize));}



        setFocusable(true);
        requestFocusInWindow();
        setDoubleBuffered(true);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateViewportSize();
            }
        });
    }

    // ---------------------- رندر اصلی ----------------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // پس‌زمینه
        g.setColor(new Color(200, 200, 200));
        g.fillRect(0, 0, getWidth(), getHeight());

        if (cityMap == null) return;

        updateViewportSize();

        updateViewport();

        Graphics2D gWorld = (Graphics2D) g.create();
        gWorld.translate(-viewX * tileSize, -viewY * tileSize);

        drawMap(gWorld);

        if (victims != null) {
            drawVictims(gWorld);
        }

        if (rescuers != null) {
            Graphics2D g2 = (Graphics2D) gWorld.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            drawRescuers(g2);
            g2.dispose();
        }

        if (debugWalkable) {
            drawWalkableOverlay(gWorld);
        }

        if (drawGrid) {
            drawGridLines(gWorld);
        }

        gWorld.dispose();
    }

    // ---------------------- متدهای رندر ----------------------

    private void updateViewportSize() {
        if (cityMap == null) return;
        int tilesW = Math.max(1, getWidth() / tileSize);
        int tilesH = Math.max(1, getHeight() / tileSize);
        viewWidth = Math.min(cityMap.getWidth(), tilesW);
        viewHeight = Math.min(cityMap.getHeight(), tilesH);
    }


    private void updateViewport() {
        if (cityMap == null || rescuers == null || rescuers.isEmpty()) return;
        Rescuer r = rescuers.get(0);
        if (r == null || r.getPosition() == null) return;

        int centerX = r.getPosition().getX();
        int centerY = r.getPosition().getY();
        viewX = centerX - viewWidth / 2;
        viewY = centerY - viewHeight / 2;

        if (viewX < 0) viewX = 0;
        if (viewY < 0) viewY = 0;
        int maxX = cityMap.getWidth() - viewWidth;
        int maxY = cityMap.getHeight() - viewHeight;
        if (viewX > maxX) viewX = maxX;
        if (viewY > maxY) viewY = maxY;
    }

    private void drawMap(Graphics g) {
        int startY = viewY;
        int endY = Math.min(cityMap.getHeight(), viewY + viewHeight);
        int startX = viewX;
        int endX = Math.min(cityMap.getWidth(), viewX + viewWidth);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
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
            if (p.getX() < viewX || p.getX() >= viewX + viewWidth ||
                    p.getY() < viewY || p.getY() >= viewY + viewHeight) continue;

            Color col;
            InjurySeverity sev = inj.getSeverity();
            if (sev == InjurySeverity.LOW) col = Color.YELLOW;
            else if (sev == InjurySeverity.MEDIUM) col = Color.ORANGE;
            else col = Color.RED; // CRITICAL

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
            if (pos.getX() < viewX || pos.getX() >= viewX + viewWidth ||
                    pos.getY() < viewY || pos.getY() >= viewY + viewHeight) continue;

            int baseX = pos.getX() * tileSize;
            int baseY = pos.getY() * tileSize;
            int size = (int) Math.round(tileSize * rescuerScale);

            Image sprite = r.getSpriteScaled(size);

            int drawX = baseX + (tileSize - size) / 2; // وسط‌چین افقی
            int drawY = baseY + (tileSize - size);     // تکیه به کف سلول (پا روی زمین)

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
        int startY = viewY;
        int endY = Math.min(cityMap.getHeight(), viewY + viewHeight);
        int startX = viewX;
        int endX = Math.min(cityMap.getWidth(), viewX + viewWidth);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                Cell c = cityMap.getCell(x, y);
                if (c == null) continue;
                int px = x * tileSize;
                int py = y * tileSize;
                if (c.isWalkable() && !c.isOccupied()) gg.setColor(Color.GREEN);
                else gg.setColor(Color.RED);
                gg.fillRect(px, py, tileSize, tileSize);
            }
        }
        gg.dispose();
    }

    private void drawGridLines(Graphics g) {
        g.setColor(new Color(0, 0, 0, 40));
        int startX = viewX * tileSize;
        int endX = (viewX + viewWidth) * tileSize;
        int startY = viewY * tileSize;
        int endY = (viewY + viewHeight) * tileSize;

        for (int x = startX; x <= endX; x += tileSize) g.drawLine(x, startY, x, endY);
        for (int y = startY; y <= endY; y += tileSize) g.drawLine(startX, y, endX, y);
    }

    // ---------------------- Setter / Update ----------------------
    public void updateData(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        if (cityMap != null) {
            this.viewWidth = Math.max(1, cityMap.getWidth() / 2);
            this.viewHeight = Math.max(1, cityMap.getHeight() / 2);
            setPreferredSize(new Dimension(viewWidth * tileSize,
                    viewHeight * tileSize));
        }
        updateViewport();
        revalidate();
        repaint();
    }

    public void setTileSize(int tileSize) {
        if (tileSize <= 0) return;
        this.tileSize = tileSize;
        if (cityMap != null) {
            setPreferredSize(new Dimension(viewWidth * tileSize,
                    viewHeight * tileSize));
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
            this.viewWidth = Math.max(1, cityMap.getWidth() / 2);
            this.viewHeight = Math.max(1, cityMap.getHeight() / 2);
            setPreferredSize(new Dimension(viewWidth * tileSize,
                    viewHeight * tileSize));
        }
        updateViewport();
        revalidate();
        repaint();
    }

    public void setRescuers(List<Rescuer> rescuers) {
        this.rescuers = rescuers;
        updateViewport();
        repaint();
    }

    public void setVictims(List<Injured> victims) {
        this.victims = victims;
        repaint();
    }
}
