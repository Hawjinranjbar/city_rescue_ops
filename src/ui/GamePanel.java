package ui;

import agent.Rescuer;
import map.CityMap;
import util.Position;
import victim.Injured;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GamePanel extends JPanel {

    private CityMap cityMap;
    private List<Rescuer> rescuers;
    private List<Injured> victims;

        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int y = 0; y < cityMap.getHeight(); y++) {
            for (int x = 0; x < cityMap.getWidth(); x++) {
                Cell cell = cityMap.getCell(x, y);
                if (cell == null) continue;

                    g.fillRect(x * tileSize, y * tileSize, tileSize, tileSize);
                }
            }

        }
    }

                }
    }

    public void updateData(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.cityMap = cityMap;
        this.rescuers = rescuers;
        this.victims = victims;
        repaint();
    }
}
