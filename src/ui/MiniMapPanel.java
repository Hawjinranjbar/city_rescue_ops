package ui;

import agent.Rescuer;
// import map.Cell;  // دیگر نیازی نیست چون بک‌گراند نقشه را نمی‌کشیم
import map.CityMap;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * MiniMapPanel — enlarged map and legend with minimal padding.
 * - Map box: 220 x 220 px
 * - Legend is tall (70 px), shows Low/Medium/High/Rescuer
 * - Victims = circles, Rescuer = square
 * - INNER_PADDING reduced to 8 so panel hugs edges
 * - **Background of the map area is now flat BLACK (no tile rendering)**
 */
public class MiniMapPanel extends JPanel {

    // --- Sizes ---
    private static final int MINIMAP_W = 220;
    private static final int MINIMAP_H = 220;
    private static final int INNER_PADDING = 8;   // reduced inner padding
    private static final int LEGEND_H = 70;

    // Legend styling
    private static final Font LEGEND_FONT  = new Font("Arial", Font.BOLD, 11);
    private static final int  LEGEND_DOT_R = 5;
    private static final int  LEGEND_SQ    = 11;
    private static final int  LEGEND_ITEM_GAP = 14;
    private static final int  LEGEND_ROW_GAP  = 8;

    // Map markers
    private static final int VICTIM_R = 4;
    private static final int RESC_S   = 10;

    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;

    private float scaleX = 1f;
    private float scaleY = 1f;

    public MiniMapPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        setOpaque(true);
        setBackground(Color.BLACK);
        setBorder(new EmptyBorder(INNER_PADDING, INNER_PADDING, INNER_PADDING, INNER_PADDING));

        int w = MINIMAP_W + INNER_PADDING * 2;
        int h = MINIMAP_H + LEGEND_H + INNER_PADDING * 2;
        setPreferredSize(new Dimension(w, h));

        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        if (cityMap != null && cityMap.getWidth() > 0 && cityMap.getHeight() > 0) {
            scaleX = (float) MINIMAP_W / (float) cityMap.getWidth();
            scaleY = (float) MINIMAP_H / (float) cityMap.getHeight();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int ox = INNER_PADDING;
        int oy = INNER_PADDING;

        // --- BACKGROUND: flat black rectangle (no map/tiles)
        g.setColor(Color.BLACK);
        g.fillRect(ox, oy, MINIMAP_W, MINIMAP_H);

        // Border around map (می‌تونی اگر نخواستی حذفش کنی)
        g.setColor(new Color(255, 255, 255, 80));
        g.drawRect(ox - 1, oy - 1, MINIMAP_W + 1, MINIMAP_H + 1);

        // --- Victims (circles)
        int dVictim = VICTIM_R * 2;
        if (victims != null) {
            for (Injured v : victims) {
                if (v == null || v.isDead() || v.isRescued()) continue;
                Position p = v.getPosition();
                if (p == null) continue;

                // تبدیل مختصات گرید به پیکسل روی باکس مشکی
                int cx = ox + Math.round(p.getX() * scaleX);
                int cy = oy + Math.round(p.getY() * scaleY);

                Color body, outline;
                InjurySeverity sev = v.getSeverity();
                if (sev == InjurySeverity.CRITICAL) { body = Color.RED; outline = Color.WHITE; }
                else if (sev == InjurySeverity.MEDIUM) { body = Color.BLUE; outline = Color.WHITE; }
                else { body = Color.WHITE; outline = Color.BLACK; }

                g.setColor(body);
                g.fillOval(cx - VICTIM_R, cy - VICTIM_R, dVictim, dVictim);
                g.setColor(outline);
                g.drawOval(cx - VICTIM_R, cy - VICTIM_R, dVictim, dVictim);
            }
        }

        // --- Rescuers (squares)
        if (rescuers != null) {
            for (Rescuer r : rescuers) {
                if (r == null || r.getPosition() == null) continue;

                int cx = ox + Math.round(r.getPosition().getX() * scaleX);
                int cy = oy + Math.round(r.getPosition().getY() * scaleY);

                int half = RESC_S / 2;
                g.setColor(Color.CYAN);
                g.fillRect(cx - half, cy - half, RESC_S, RESC_S);
                g.setColor(Color.BLACK);
                g.drawRect(cx - half, cy - half, RESC_S, RESC_S);
            }
        }

        // Title (اگر نمی‌خوایش حذف کن)
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString("MiniMap", ox + (MINIMAP_W / 2) - 30, oy - 10);

        // Legend
        drawLegendResponsive(g, ox, oy + MINIMAP_H + 8, MINIMAP_W, LEGEND_H - 12);

        g.dispose();
    }

    private void drawLegendResponsive(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(255, 255, 255, 70));
        g.drawRoundRect(x, y, w, h, 10, 10);

        g.setFont(LEGEND_FONT);
        int rowBase = y + h / 2 - 8;
        int cursor = x + 12;
        int maxX = x + w - 12;

        cursor = placeLegendItem(g, cursor, rowBase, "Low", Color.WHITE, Color.BLACK, false, maxX);
        cursor = placeLegendItem(g, cursor, rowBase, "Medium", Color.BLUE, Color.WHITE, false, maxX);
        cursor = placeLegendItem(g, cursor, rowBase, "High", Color.RED, Color.WHITE, false, maxX);

        int estResc = estimateItemWidth(g, "Rescuer");
        if (cursor + estResc > maxX) {
            rowBase += LEGEND_ROW_GAP + 14;
            cursor = x + 12;
        }
        placeLegendItem(g, cursor, rowBase, "Rescuer", Color.CYAN, Color.BLACK, true, maxX);
    }

    private int placeLegendItem(Graphics2D g, int cx, int cy, String label,
                                Color body, Color outline, boolean square, int maxX) {
        int start = cx;

        if (square) {
            int half = LEGEND_SQ / 2;
            g.setColor(body);
            g.fillRect(cx, cy - half, LEGEND_SQ, LEGEND_SQ);
            g.setColor(outline);
            g.drawRect(cx, cy - half, LEGEND_SQ, LEGEND_SQ);
            cx += LEGEND_SQ + 6;
        } else {
            int r = LEGEND_DOT_R, d = r * 2;
            g.setColor(body);
            g.fillOval(cx, cy - r, d, d);
            g.setColor(outline);
            g.drawOval(cx, cy - r, d, d);
            cx += d + 6;
        }

        g.setColor(Color.WHITE);
        g.drawString(label, cx, cy + 5);

        int textW = g.getFontMetrics().stringWidth(label);
        int itemW = (cx - start) + textW + LEGEND_ITEM_GAP;
        if (start + itemW > maxX) itemW = maxX - start;

        return start + itemW;
    }

    private int estimateItemWidth(Graphics2D g, String label) {
        int textW = g.getFontMetrics(LEGEND_FONT).stringWidth(label);
        int iconW = Math.max(LEGEND_SQ, LEGEND_DOT_R * 2);
        return iconW + 6 + textW + LEGEND_ITEM_GAP;
    }

    public void updateMiniMap(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        if (cityMap != null && cityMap.getWidth() > 0 && cityMap.getHeight() > 0) {
            scaleX = (float) MINIMAP_W / (float) cityMap.getWidth();
            scaleY = (float) MINIMAP_H / (float) cityMap.getHeight();
        }
        repaint();
    }
}
