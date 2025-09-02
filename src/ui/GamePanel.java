package ui;

import agent.Rescuer;
import agent.Vehicle;
import map.Cell;
import map.CityMap;
import util.AssetLoader;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * پنل رندر: نقشه، ریسکیورها، مجروح‌ها. Vehicle این‌جا رندر نمی‌شود.
 */
public class GamePanel extends JPanel {

    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;

    // فقط برای سازگاری با KeyHandler نگه می‌داریم؛ رندر نمی‌کنیم
    private Vehicle vehicle;

    private final Map<InjurySeverity, BufferedImage> victimSprites =
            new EnumMap<InjurySeverity, BufferedImage>(InjurySeverity.class);

    private int tileSize = 32;
    private boolean drawGrid = false;

    // --- اندازه‌ها ---
    private double rescuerScale = 2.0;   // اسکیل پیاده
    private double ambulanceScale = 4.0; // ✅ اسکیل آمبولانس (۳ برابر)
    private double victimScale  = 3.0;

    private boolean debugWalkable = false;
    private boolean showVictimTimers = true;

    private int victimXOffset = 0;
    private int victimYOffset = 0;

    private int viewX = 0, viewY = 0;
    private int viewWidth = 1, viewHeight = 1;

    public GamePanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        if (cityMap != null) {
            this.viewWidth  = Math.max(1, cityMap.getWidth() / 2);
            this.viewHeight = Math.max(1, cityMap.getHeight() / 2);
            setPreferredSize(new Dimension(viewWidth * tileSize, viewHeight * tileSize));
        } else {
            this.viewWidth = 25;
            this.viewHeight = 19;
            setPreferredSize(new Dimension(viewWidth * tileSize, viewHeight * tileSize));
        }

        setFocusable(true);
        requestFocusInWindow();
        setDoubleBuffered(true);

        loadVictimSprites();

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                updateViewportSize();
            }
        });
    }

    private void loadVictimSprites() {
        int w = (int) Math.round(tileSize * victimScale);
        int h = w;
        victimSprites.clear();
        victimSprites.put(InjurySeverity.LOW,
                AssetLoader.loadScaled("assets/characters/LOW.png", w, h));
        victimSprites.put(InjurySeverity.MEDIUM,
                AssetLoader.loadScaled("assets/characters/MEDIUM.png", w, h));
        victimSprites.put(InjurySeverity.CRITICAL,
                AssetLoader.loadScaled("assets/characters/CRITICAL.png", w, h));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(new Color(200, 200, 200));
        g.fillRect(0, 0, getWidth(), getHeight());

        if (cityMap == null) return;

        updateViewportSize();
        updateViewport();

        Graphics2D gWorld = (Graphics2D) g.create();
        gWorld.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        gWorld.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        gWorld.translate(-viewX * tileSize, -viewY * tileSize);

        drawMap(gWorld);
        if (debugWalkable) drawWalkableOverlay(gWorld);
        drawVictims(gWorld);
        drawRescuers(gWorld);
        // Vehicle را اینجا نمی‌کشیم
        if (drawGrid) drawGridLines(gWorld);

        gWorld.dispose();
    }

    private void updateViewportSize() {
        if (cityMap == null) return;
        int tilesW = Math.max(1, getWidth() / tileSize);
        int tilesH = Math.max(1, getHeight() / tileSize);
        viewWidth  = Math.min(cityMap.getWidth(), tilesW);
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
        int startY = viewY, endY = Math.min(cityMap.getHeight(), viewY + viewHeight);
        int startX = viewX, endX = Math.min(cityMap.getWidth(), viewX + viewWidth);
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
        if (victims == null) return;
        for (int i = 0; i < victims.size(); i++) {
            Injured inj = victims.get(i);
            if (inj == null || inj.isDead() || inj.isRescued() || inj.isBeingRescued()) continue;
            Position p = inj.getPosition();
            if (p == null) continue;

            if (p.getX() < viewX || p.getX() >= viewX + viewWidth ||
                    p.getY() < viewY || p.getY() >= viewY + viewHeight) continue;

            BufferedImage sprite = victimSprites.get(inj.getSeverity());
            int baseX = p.getX() * tileSize;
            int baseY = p.getY() * tileSize;

            if (sprite != null) {
                int drawX = baseX + (tileSize - sprite.getWidth()) / 2 + victimXOffset;
                int drawY = baseY + (tileSize - sprite.getHeight()) + victimYOffset;
                g.drawImage(sprite, drawX, drawY, null);
                if (showVictimTimers) drawVictimTimerHUD((Graphics2D) g, inj, baseX, baseY, sprite.getWidth());
            } else {
                Color col = Color.RED;
                switch (inj.getSeverity()) {
                    case LOW: col = Color.YELLOW; break;
                    case MEDIUM: col = Color.ORANGE; break;
                    case CRITICAL: col = Color.RED; break;
                    default: break;
                }
                g.setColor(col);
                int r = tileSize / 2;
                int cx = baseX + (tileSize - r) / 2 + victimXOffset;
                int cy = baseY + (tileSize - r) / 2 + victimYOffset;
                g.fillOval(cx, cy, r, r);
                if (showVictimTimers) drawVictimTimerHUD((Graphics2D) g, inj, baseX, baseY, r);
            }
        }
    }

    private void drawVictimTimerHUD(Graphics2D g2, Injured inj, int baseX, int baseY, int spriteW) {
        int barWidth = (int) (tileSize * 0.9);
        int barHeight = Math.max(6, (int) (tileSize * 0.18));
        int x = baseX + (tileSize - barWidth) / 2;
        int y = baseY - 6;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(x, y - barHeight, barWidth, barHeight, 6, 6);

        float pct = inj.getTimePercent();
        pct = Math.max(0f, Math.min(1f, pct));
        int filled = (int) (barWidth * pct);

        Color fill;
        if (pct > 0.6f) fill = new Color(50, 205, 50);
        else if (pct > 0.3f) fill = new Color(255, 193, 7);
        else fill = new Color(220, 53, 69);

        g2.setColor(fill);
        g2.fillRoundRect(x+1, y - barHeight + 1, Math.max(0, filled - 2), barHeight - 2, 6, 6);

        String txt = String.valueOf(Math.max(0, inj.getRemainingTime()));
        Font old = g2.getFont();
        Font f = old.deriveFont(Font.BOLD, Math.max(14f, tileSize * 0.6f));
        g2.setFont(f);

        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(txt);
        int th = fm.getAscent();
        int tx = x + (barWidth - tw) / 2;
        int ty = y - barHeight/2 + th/2;

        g2.setColor(new Color(0, 0, 0, 200));
        g2.drawString(txt, tx + 1, ty + 1);
        g2.setColor(fill);
        g2.drawString(txt, tx, ty);

        g2.setFont(old);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void drawRescuers(Graphics2D g2) {
        if (rescuers == null) return;
        for (int i = 0; i < rescuers.size(); i++) {
            Rescuer r = rescuers.get(i);
            if (r == null || r.getPosition() == null) continue;

            // همیشه بکش؛ در حالت آمبولانس از اسپرایت آمبولانس استفاده می‌شود
            Position pos = r.getPosition();
            if (pos.getX() < viewX || pos.getX() >= viewX + viewWidth ||
                    pos.getY() < viewY || pos.getY() >= viewY + viewHeight) continue;

            int baseX = pos.getX() * tileSize;
            int baseY = pos.getY() * tileSize;

            // ✅ اندازهٔ مخصوص آمبولانس
            boolean isAmb = false;
            try { isAmb = r.isAmbulanceMode(); } catch (Throwable ignored) {}
            double scale = isAmb ? ambulanceScale : rescuerScale;

            int size = (int) Math.round(tileSize * scale);
            BufferedImage sprite = r.getSpriteScaled(size);

            // پای تایل را لنگر کن (برای اسپرایت‌های بزرگ‌تر از تایل)
            int drawX = baseX + (tileSize - size) / 2;
            int drawY = baseY + (tileSize - size);

            if (sprite != null) {
                g2.drawImage(sprite, drawX, drawY, size, size, null);
            } else {
                // فالی‌بک: اگر به هر دلیل null بود، دیده شود
                g2.setColor(isAmb ? new Color(200, 0, 0) : new Color(0, 70, 200));
                g2.fillRect(drawX, drawY, size, size);
            }
        }
    }

    private void drawWalkableOverlay(Graphics g) {
        Graphics2D gg = (Graphics2D) g.create();
        gg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
        int startY = viewY, endY = Math.min(cityMap.getHeight(), viewY + viewHeight);
        int startX = viewX, endX = Math.min(cityMap.getWidth(), viewX + viewWidth);
        for (int y = startY; y < endY; y++) {
            for (int x = startX; x < endX; x++) {
                Cell c = cityMap.getCell(x, y);
                if (c == null) continue;
                int px = x * tileSize, py = y * tileSize;
                gg.setColor((c.isWalkable() && !c.isOccupied()) ? Color.GREEN : Color.RED);
                gg.fillRect(px, py, tileSize, tileSize);
            }
        }
        gg.dispose();
    }

    private void drawGridLines(Graphics g) {
        g.setColor(new Color(0, 0, 0, 40));
        int startX = viewX * tileSize, endX = (viewX + viewWidth) * tileSize;
        int startY = viewY * tileSize, endY = (viewY + viewHeight) * tileSize;
        for (int x = startX; x <= endX; x += tileSize) g.drawLine(x, startY, x, endY);
        for (int y = startY; y <= endY; y += tileSize) g.drawLine(startX, y, endX, y);
    }

    // ---------- Setter / Update ----------
    public void updateData(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        if (cityMap != null) {
            this.viewWidth  = Math.max(1, cityMap.getWidth() / 2);
            this.viewHeight = Math.max(1, cityMap.getHeight() / 2);
            setPreferredSize(new Dimension(viewWidth * tileSize, viewHeight * tileSize));
        }
        updateViewport();
        revalidate();
        repaint();
    }

    public void setVehicle(Vehicle v) { this.vehicle = v; repaint(); } // رندرش نمی‌کنیم

    public void setTileSize(int tileSize) {
        if (tileSize <= 0) return;
        this.tileSize = tileSize;
        if (cityMap != null) setPreferredSize(new Dimension(viewWidth * tileSize, viewHeight * tileSize));
        loadVictimSprites();
        revalidate();
        repaint();
    }

    // 🔧 در صورت نیاز قابل تغییر از بیرون:
    public void setRescuerScale(double scale) { if (scale > 0) { this.rescuerScale = scale; repaint(); } }
    public void setAmbulanceScale(double scale) { if (scale > 0) { this.ambulanceScale = scale; repaint(); } }
    public void setVictimScale(double scale)  { if (scale > 0) { this.victimScale  = scale; loadVictimSprites(); repaint(); } }
    public void setVictimOffset(int xOffset, int yOffset) { this.victimXOffset = xOffset; this.victimYOffset = yOffset; repaint(); }
    public void setDebugWalkable(boolean on) { this.debugWalkable = on; repaint(); }
    public void setDrawGrid(boolean drawGrid) { this.drawGrid = drawGrid; repaint(); }
    public void setShowVictimTimers(boolean on) { this.showVictimTimers = on; repaint(); }
    public void setMap(CityMap cityMap) { this.cityMap = cityMap; updateViewport(); revalidate(); repaint(); }
    public void setRescuers(List<Rescuer> rescuers) { this.rescuers = rescuers; updateViewport(); repaint(); }
    public void setVictims(List<Injured> victims) { this.victims = victims; repaint(); }
}
