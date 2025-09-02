package ui;

import javax.swing.*;
import java.awt.*;

/**
 * لایه: UI Layer
 * پنل HUD: نمایش امتیاز، نجات‌یافته‌ها، مرده‌ها
 */
public class HUDPanel extends JPanel {

    private int score;
    private int rescuedCount;
    private int deadCount;

    public HUDPanel() {
        setPreferredSize(new Dimension(240, 110));
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        // مقدار اولیه فقط برای نمای اولیه؛ بعداً از GameEngine سینک می‌شود
        this.score = 500;
        this.rescuedCount = 0;
        this.deadCount = 0;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(getForeground());

        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.drawString("امتیاز: " + score, 12, 30);

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.drawString("نجات‌یافته‌ها: " + rescuedCount, 12, 60);
        g.drawString("مرده‌ها: " + deadCount, 12, 85);
    }

    public void updateHUD(int score, int rescued, int dead) {
        this.score = score;
        this.rescuedCount = rescued;
        this.deadCount = dead;
        repaint();
    }
}
