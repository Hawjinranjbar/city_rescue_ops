import agent.Rescuer;
import map.CityMap;
import map.Cell;
import util.Position;
import victim.Injured;

import javax.swing.*;
import java.awt.*;
import java.util.List;

// --------------------
// لایه: UI Layer
// --------------------
// این کلاس پنل گرافیکی اصلی بازیه
// نقشه، مجروح‌ها، نجات‌دهنده‌ها و مسیرها اینجا نمایش داده می‌شن
public class GamePanel extends JPanel {

    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;
    private int tileSize = 32;

    public GamePanel(CityMap cityMap,
                     List<Rescuer> rescuers,
                     List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        setPreferredSize(new Dimension(cityMap.getWidth() * tileSize,
                cityMap.getHeight() * tileSize));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // رسم نقشه (سلول‌ها)
        for (int y = 0; y < cityMap.getHeight(); y++) {
            for (int x = 0; x < cityMap.getWidth(); x++) {
                Cell cell = cityMap.getCell(x, y);
                if (cell == null) continue;

                switch (cell.getType()) {
                    case ROAD -> g.setColor(Color.LIGHT_GRAY);
                    case RUBBLE -> g.setColor(Color.DARK_GRAY);
                    case HOSPITAL -> g.setColor(Color.GREEN);
                }

                g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
            }
        }

        // رسم مجروح‌ها
        for (Injured injured : victims) {
            if (!injured.isDead() && !injured.isRescued()) {
                Position pos = injured.getPosition();
                g.setColor(switch (injured.getSeverity()) {
                    case LOW -> Color.YELLOW;
                    case MEDIUM -> Color.ORANGE;
                    case CRITICAL -> Color.RED;
                });
                g.fillOval(pos.getX() * tileSize + 8, pos.getY() * tileSize + 8, 16, 16);
            }
        }

        // رسم نجات‌دهنده‌ها
        for (Rescuer rescuer : rescuers) {
            Position pos = rescuer.getPosition();
            g.setColor(Color.BLUE);
            g.fillRect(pos.getX() * tileSize + 4, pos.getY() * tileSize + 4, 24, 24);
        }
    }

    // آپدیت داده‌ها و درخواست رسم مجدد
    public void updateData(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        repaint();
    }
}
