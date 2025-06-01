package ui;

import agent.Rescuer;
import map.CityMap;
import map.Cell;
import util.Position;
import victim.Injured;

import javax.swing.*;
import java.awt.*;
import java.util.List;

// --------------------
// لایه: ui Layer
// --------------------
// این کلاس نقشه کوچک (MiniMap) رو نمایش می‌ده
// شامل مجروح‌ها، نجات‌دهنده‌ها و موقعیت کلی بیمارستان‌ها
public class MiniMapPanel extends JPanel {

    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;

    private final int tileSize = 6; // اندازه کوچک‌تر برای نمای مینی‌مپ

    public MiniMapPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        setPreferredSize(new Dimension(cityMap.getWidth() * tileSize,
                cityMap.getHeight() * tileSize));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // رسم نقشه
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
                g.fillRect(pos.getX() * tileSize, pos.getY() * tileSize, tileSize, tileSize);
            }
        }

        // رسم نجات‌دهنده‌ها
        for (Rescuer rescuer : rescuers) {
            Position pos = rescuer.getPosition();
            g.setColor(Color.BLUE);
            g.fillRect(pos.getX() * tileSize, pos.getY() * tileSize, tileSize, tileSize);
        }
    }

    public void updateMiniMap(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        repaint();
    }
}
