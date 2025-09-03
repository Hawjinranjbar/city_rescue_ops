package ui;

import agent.Rescuer;
import map.CityMap;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * MiniMapPanel — black map box + legend, with optional grid/hospital/viewport overlay.
 * - Map box: 220 x 220 px (fixed)
 * - Victims = circles, Rescuer = cyan square, Hospital = white square with red cross
 * - Optional faint grid and camera viewport rectangle
 * - No tile rendering (flat black background)
 * - بدون استفاده از لامبدا
 */
public class MiniMapPanel extends JPanel {

    // --- Fixed sizes for the minimap area ---
    private static final int MINIMAP_W = 220;
    private static final int MINIMAP_H = 220;
    private static final int INNER_PADDING = 8;
    private static final int LEGEND_H = 70;

    // --- Theme ---
    private static final Color BG_PANEL   = Color.BLACK;
    private static final Color MAP_BG     = Color.BLACK;
    private static final Color MAP_BORDER = new Color(255, 255, 255, 80);
    private static final Color GRID_COLOR = new Color(255, 255, 255, 20);
    private static final Color LEG_BG     = new Color(0, 0, 0, 190);
    private static final Color LEG_BR     = new Color(255, 255, 255, 70);
    private static final Color TEXT       = Color.WHITE;
    private static final Color RESCUER_C  = Color.CYAN;
    private static final Color HOSPITAL_BR= new Color(255, 255, 255, 220);
    private static final Color VIEWPORT_C = new Color(255, 255, 0, 140);

    // Legend styling
    private static final Font LEGEND_FONT  = new Font("Arial", Font.BOLD, 11);
    private static final int  LEGEND_DOT_R = 5;
    private static final int  LEGEND_SQ    = 11;
    private static final int  LEGEND_ITEM_GAP = 14;
    private static final int  LEGEND_ROW_GAP  = 8;

    // Markers
    private static final int VICTIM_R = 4;  // victim circle radius
    private static final int RESC_S   = 10; // rescuer square side
    private static final int HOSP_S   = 12; // hospital square side (icon background)

    // Data
    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;
    private List<map.Hospital> hospitals; // اختیاری

    // Scaling (tile coords -> minimap pixels)
    private float scaleX = 1f;
    private float scaleY = 1f;

    // Options
    private boolean showGrid = true;
    private boolean showHospitals = true;

    // Optional camera viewport (in tile units)
    private Rectangle viewportTiles; // x,y,w,h in tile coords; may be null

    // -------------------- Constructors --------------------
    public MiniMapPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this(cityMap, rescuers, victims, null);
    }

    public MiniMapPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims,
                        List<map.Hospital> hospitals) {
        setOpaque(true);
        setBackground(BG_PANEL);
        setBorder(new EmptyBorder(INNER_PADDING, INNER_PADDING, INNER_PADDING, INNER_PADDING));

        int w = MINIMAP_W + INNER_PADDING * 2;
        int h = MINIMAP_H + LEGEND_H + INNER_PADDING * 2;
        setPreferredSize(new Dimension(w, h));

        this.cityMap   = cityMap;
        this.rescuers  = rescuers;
        this.victims   = victims;
        this.hospitals = hospitals;

        recalcScale();
    }

    // -------------------- Public API --------------------
    /** بروزرسانی کامل مینی‌مپ (بدون بیمارستان‌ها) */
    public void updateMiniMap(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap  = cityMap;
        this.rescuers = rescuers;
        this.victims  = victims;
        recalcScale();
        repaint();
    }

    /** بروزرسانی کامل مینی‌مپ (با بیمارستان‌ها) */
    public void updateMiniMap(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims,
                              List<map.Hospital> hospitals) {
        this.cityMap   = cityMap;
        this.rescuers  = rescuers;
        this.victims   = victims;
        this.hospitals = hospitals;
        recalcScale();
        repaint();
    }

    /** نمایش/عدم نمایش خطوط Grid کم‌رنگ */
    public void setShowGrid(boolean show) {
        this.showGrid = show;
        repaint();
    }

    /** نمایش/عدم نمایش بیمارستان‌ها */
    public void setShowHospitals(boolean show) {
        this.showHospitals = show;
        repaint();
    }

    /** تنظیم مستطیل Viewport دوربین (مختصات تایل؛ null = عدم نمایش) */
    public void setViewportTiles(int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            this.viewportTiles = null;
        } else {
            this.viewportTiles = new Rectangle(x, y, w, h);
        }
        repaint();
    }

    // -------------------- Paint --------------------
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int ox = INNER_PADDING;
        int oy = INNER_PADDING;

        // --- BACKGROUND: flat black rectangle (no tiles)
        g.setColor(MAP_BG);
        g.fillRect(ox, oy, MINIMAP_W, MINIMAP_H);

        // Optional grid (faint)
        if (showGrid && cityMap != null) {
            drawGrid(g, ox, oy);
        }

        // Border around map
        g.setColor(MAP_BORDER);
        g.drawRect(ox - 1, oy - 1, MINIMAP_W + 1, MINIMAP_H + 1);

        // Hospitals (optional)
        if (showHospitals && hospitals != null) {
            for (int i = 0; i < hospitals.size(); i++) {
                map.Hospital h = hospitals.get(i);
                if (h == null || h.getPosition() == null) continue;
                Position p = h.getPosition();
                int cx = ox + Math.round(p.getX() * scaleX);
                int cy = oy + Math.round(p.getY() * scaleY);
                drawHospitalIcon(g, cx, cy);
            }
        }

        // Victims (circles)
        int dVictim = VICTIM_R * 2;
        if (victims != null) {
            for (int i = 0; i < victims.size(); i++) {
                Injured v = victims.get(i);
                if (v == null || v.isDead() || v.isRescued()) continue;
                Position p = v.getPosition();
                if (p == null) continue;

                int cx = ox + Math.round(p.getX() * scaleX);
                int cy = oy + Math.round(p.getY() * scaleY);

                Color body, outline;
                InjurySeverity sev = v.getSeverity();
                if (sev == InjurySeverity.CRITICAL) { body = Color.RED;   outline = Color.WHITE; }
                else if (sev == InjurySeverity.MEDIUM) { body = Color.BLUE;  outline = Color.WHITE; }
                else { body = Color.WHITE; outline = Color.BLACK; } // LOW

                g.setColor(body);
                g.fillOval(cx - VICTIM_R, cy - VICTIM_R, dVictim, dVictim);
                g.setColor(outline);
                g.drawOval(cx - VICTIM_R, cy - VICTIM_R, dVictim, dVictim);
            }
        }

        // Rescuers (squares)
        if (rescuers != null) {
            for (int i = 0; i < rescuers.size(); i++) {
                Rescuer r = rescuers.get(i);
                if (r == null || r.getPosition() == null) continue;

                int cx = ox + Math.round(r.getPosition().getX() * scaleX);
                int cy = oy + Math.round(r.getPosition().getY() * scaleY);

                int half = RESC_S / 2;
                g.setColor(RESCUER_C);
                g.fillRect(cx - half, cy - half, RESC_S, RESC_S);
                g.setColor(Color.BLACK);
                g.drawRect(cx - half, cy - half, RESC_S, RESC_S);
            }
        }

        // Camera viewport (tile-based rectangle)
        if (viewportTiles != null) {
            int vx = ox + Math.round(viewportTiles.x * scaleX);
            int vy = oy + Math.round(viewportTiles.y * scaleY);
            int vw = Math.round(viewportTiles.width  * scaleX);
            int vh = Math.round(viewportTiles.height * scaleY);

            // clamp to minimap box
            if (vw < 1) vw = 1;
            if (vh < 1) vh = 1;
            if (vx < ox) vx = ox;
            if (vy < oy) vy = oy;
            if (vx + vw > ox + MINIMAP_W) vw = (ox + MINIMAP_W) - vx;
            if (vy + vh > oy + MINIMAP_H) vh = (oy + MINIMAP_H) - vy;

            Stroke old = g.getStroke();
            g.setColor(VIEWPORT_C);
            g.setStroke(new BasicStroke(2f));
            g.drawRect(vx, vy, vw, vh);
            g.setStroke(old);
        }

        // Title
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(TEXT);
        g.drawString("MiniMap", ox + (MINIMAP_W / 2) - 30, oy - 10);

        // Legend
        drawLegendResponsive(g, ox, oy + MINIMAP_H + 8, MINIMAP_W, LEGEND_H - 12);

        g.dispose();
    }

    // -------------------- Helpers --------------------
    private void recalcScale() {
        if (cityMap != null && cityMap.getWidth() > 0 && cityMap.getHeight() > 0) {
            scaleX = (float) MINIMAP_W / (float) cityMap.getWidth();
            scaleY = (float) MINIMAP_H / (float) cityMap.getHeight();
        } else {
            scaleX = 1f;
            scaleY = 1f;
        }
    }

    private void drawGrid(Graphics2D g, int ox, int oy) {
        if (cityMap == null) return;
        int w = cityMap.getWidth();
        int h = cityMap.getHeight();
        if (w <= 0 || h <= 0) return;

        g.setColor(GRID_COLOR);
        // عمودی‌ها
        for (int x = 1; x < w; x++) {
            int px = ox + Math.round(x * scaleX);
            g.drawLine(px, oy, px, oy + MINIMAP_H);
        }
        // افقی‌ها
        for (int y = 1; y < h; y++) {
            int py = oy + Math.round(y * scaleY);
            g.drawLine(ox, py, ox + MINIMAP_W, py);
        }
    }

    private void drawHospitalIcon(Graphics2D g, int cx, int cy) {
        int half = HOSP_S / 2;
        // زمینه سفید
        g.setColor(Color.WHITE);
        g.fillRect(cx - half, cy - half, HOSP_S, HOSP_S);
        g.setColor(HOSPITAL_BR);
        g.drawRect(cx - half, cy - half, HOSP_S, HOSP_S);

        // صلیب قرمز
        int arm = Math.max(3, HOSP_S / 5);
        int bar = Math.max(3, HOSP_S / 5);
        g.setColor(Color.RED);
        // عمودی
        g.fillRect(cx - arm / 2, cy - half + 2, arm, HOSP_S - 4);
        // افقی
        g.fillRect(cx - half + 2, cy - bar / 2, HOSP_S - 4, bar);
    }

    private void drawLegendResponsive(Graphics2D g, int x, int y, int w, int h) {
        // کارت نیمه‌شفاف
        g.setColor(LEG_BG);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(LEG_BR);
        g.drawRoundRect(x, y, w, h, 10, 10);

        g.setFont(LEGEND_FONT);
        int rowBase = y + h / 2 - 8;
        int cursor = x + 12;
        int maxX = x + w - 12;

        // LOW
        cursor = placeLegendItem(g, cursor, rowBase, "Low", Color.WHITE, Color.BLACK, false, maxX);
        // MEDIUM
        cursor = placeLegendItem(g, cursor, rowBase, "Medium", Color.BLUE, Color.WHITE, false, maxX);
        // CRITICAL
        cursor = placeLegendItem(g, cursor, rowBase, "Critical", Color.RED, Color.WHITE, false, maxX);

        // رفتن به خط بعد اگر جا نشد
        int estResc = estimateItemWidth(g, "Rescuer");
        if (cursor + estResc > maxX) {
            rowBase += LEGEND_ROW_GAP + 14;
            cursor = x + 12;
        }
        cursor = placeLegendItem(g, cursor, rowBase, "Rescuer", RESCUER_C, Color.BLACK, true, maxX);

        // Hospital (اگر جا شد)
        int estHosp = estimateItemWidth(g, "Hospital");
        if (cursor + estHosp > maxX) {
            rowBase += LEGEND_ROW_GAP + 14;
            cursor = x + 12;
        }
        placeLegendItemHospital(g, cursor, rowBase, "Hospital", maxX);
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

        g.setColor(TEXT);
        g.drawString(label, cx, cy + 5);

        int textW = g.getFontMetrics().stringWidth(label);
        int itemW = (cx - start) + textW + LEGEND_ITEM_GAP;
        if (start + itemW > maxX) itemW = maxX - start;

        return start + itemW;
    }

    private void placeLegendItemHospitalIcon(Graphics2D g, int cx, int cy) {
        // یک نسخه کوچک از آیکن بیمارستان
        int s = LEGEND_SQ + 2;
        int half = s / 2;

        g.setColor(Color.WHITE);
        g.fillRect(cx, cy - half, s, s);
        g.setColor(HOSPITAL_BR);
        g.drawRect(cx, cy - half, s, s);

        int arm = Math.max(2, s / 5);
        int bar = Math.max(2, s / 5);
        g.setColor(Color.RED);
        g.fillRect(cx + (s / 2) - (arm / 2), cy - half + 2, arm, s - 4);
        g.fillRect(cx + 2, cy - (bar / 2), s - 4, bar);
    }

    private int placeLegendItemHospital(Graphics2D g, int cx, int cy, String label, int maxX) {
        int start = cx;
        placeLegendItemHospitalIcon(g, cx, cy);
        cx += (LEGEND_SQ + 2) + 6;

        g.setColor(TEXT);
        g.drawString(label, cx, cy + 5);

        int textW = g.getFontMetrics().stringWidth(label);
        int itemW = (cx - start) + textW + LEGEND_ITEM_GAP;
        if (start + itemW > maxX) itemW = maxX - start;
        return start + itemW;
    }

    private int estimateItemWidth(Graphics2D g, String label) {
        FontMetrics fm = g.getFontMetrics(LEGEND_FONT);
        int textW = fm.stringWidth(label);
        int iconW = Math.max(LEGEND_SQ, LEGEND_DOT_R * 2);
        return iconW + 6 + textW + LEGEND_ITEM_GAP;
    }
}
