package ui;

import agent.Rescuer;
import map.CityMap;
import map.Cell;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * --------------------
 * لایه: UI Layer — MiniMapPanel
 * --------------------
 * مینی‌مپ ساده با رسم تایل‌ها و نمایش:
 * - مجروح‌ها: دایره‌ی کوچک
 *      CRITICAL = قرمز
 *      MEDIUM   = آبی
 *      LOW      = سفید
 * - نجات‌دهنده‌ها: دایره‌ی فیروزه‌ای
 *
 * نکته: همه‌ی switchها به سبک قدیمی نوشته شده‌اند (بدون ->).
 */
public class MiniMapPanel extends JPanel {

    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;

    // اندازه هر تایل روی مینی‌مپ
    private final int tileSize = 6;

    // رنگ‌های پایه‌ی نقشه (دلخواه و قابل تغییر)
    private static final Color ROAD_COLOR      = Color.LIGHT_GRAY;
    private static final Color OBSTACLE_COLOR  = Color.DARK_GRAY;
    private static final Color BUILDING_COLOR  = new Color(120, 81, 57);
    private static final Color HOSPITAL_COLOR  = new Color(30, 200, 30);
    private static final Color EMPTY_COLOR     = Color.BLACK;

    // رنگ مارکرها
    private static final Color RESCUER_COLOR   = Color.CYAN;
    private static final Color VICTIM_CRIT     = Color.RED;
    private static final Color VICTIM_MED      = Color.BLUE;
    private static final Color VICTIM_LOW      = Color.WHITE;
    private static final Color MARKER_OUTLINE  = Color.BLACK;

    public MiniMapPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        setOpaque(true);
        setBackground(Color.BLACK);

        int w = cityMap != null ? cityMap.getWidth() * tileSize : 200;
        int h = cityMap != null ? cityMap.getHeight() * tileSize : 150;
        setPreferredSize(new Dimension(w, h));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);

        if (cityMap == null) return;

        Graphics2D g = (Graphics2D) g0.create();
        // برای دایره‌های نرم‌تر
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // --- رسم بکگراند نقشه بر اساس نوع Cell
        for (int y = 0; y < cityMap.getHeight(); y++) {
            for (int x = 0; x < cityMap.getWidth(); x++) {
                Cell cell = cityMap.getCell(x, y);
                if (cell == null) continue;

                // switch کلاسیک
                switch (cell.getType()) {
                    case ROAD:
                        g.setColor(ROAD_COLOR);
                        break;
                    case OBSTACLE:
                        g.setColor(OBSTACLE_COLOR);
                        break;
                    case BUILDING:
                        g.setColor(BUILDING_COLOR);
                        break;
                    case HOSPITAL:
                        g.setColor(HOSPITAL_COLOR);
                        break;
                    case EMPTY:
                    default:
                        g.setColor(EMPTY_COLOR);
                        break;
                }

                g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
            }
        }

        // شعاع مارکر (دایره‌ی کوچک داخل هر تایل)
        int r = Math.max(2, (tileSize / 2) - 1);
        int d = r * 2;

        // --- رسم مجروح‌ها (دایره‌های کوچک رنگی)
        if (victims != null) {
            for (int i = 0; i < victims.size(); i++) {
                Injured injured = victims.get(i);
                if (injured == null) continue;
                if (injured.isDead() || injured.isRescued()) continue;

                Position pos = injured.getPosition();
                if (pos == null) continue;

                int cx = pos.getX() * tileSize + (tileSize / 2) - r;
                int cy = pos.getY() * tileSize + (tileSize / 2) - r;

                Color color;
                InjurySeverity sev = injured.getSeverity();
                if (sev == InjurySeverity.CRITICAL) {
                    color = VICTIM_CRIT;
                } else if (sev == InjurySeverity.MEDIUM) {
                    color = VICTIM_MED;
                } else {
                    color = VICTIM_LOW; // LOW
                }

                g.setColor(color);
                g.fillOval(cx, cy, d, d);

                // حاشیه برای خوانایی روی پس‌زمینه‌های روشن یا تیره
                g.setColor(MARKER_OUTLINE);
                g.drawOval(cx, cy, d, d);
            }
        }

        // --- رسم نجات‌دهنده‌ها (دایره‌ی فیروزه‌ای)
        if (rescuers != null) {
            for (int i = 0; i < rescuers.size(); i++) {
                Rescuer rescuer = rescuers.get(i);
                if (rescuer == null) continue;

                Position pos = rescuer.getPosition();
                if (pos == null) continue;

                int cx = pos.getX() * tileSize + (tileSize / 2) - r;
                int cy = pos.getY() * tileSize + (tileSize / 2) - r;

                g.setColor(RESCUER_COLOR);
                g.fillOval(cx, cy, d, d);

                g.setColor(MARKER_OUTLINE);
                g.drawOval(cx, cy, d, d);
            }
        }

        g.dispose();
    }

    // به‌روزرسانی داده‌های مینی‌مپ
    public void updateMiniMap(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;

        // اطمینان از اندازه‌ی درست پنل در صورت تغییر اندازه‌ی نقشه
        if (cityMap != null) {
            setPreferredSize(new Dimension(cityMap.getWidth() * tileSize, cityMap.getHeight() * tileSize));
            revalidate();
        }
        repaint();
    }
}
