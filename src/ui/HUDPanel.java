package ui;

import agent.Rescuer;
import map.CityMap;
import victim.Injured;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * لایه: UI Layer
 * پنل HUD: شامل
 * - تایمر
 * - امتیاز
 * - وضعیت مجروحان
 * - مینی‌مپ (MiniMapPanel)
 */
public class HUDPanel extends JPanel {

    private int score;
    private int rescuedCount;
    private int deadCount;
    private int timeLeft;

    private MiniMapPanel miniMapPanel;  // اضافه کردن مینی‌مپ

    public HUDPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);

        // داده‌های اولیه
        this.score = 500;
        this.rescuedCount = 0;
        this.deadCount = 0;
        this.timeLeft = 300;

        // بخش متن سمت چپ
        JPanel infoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.WHITE);

                // --- تایمر (بزرگ)
                g.setFont(new Font("Arial", Font.BOLD, 28));
                g.drawString("⏱ " + formatTime(timeLeft), 10, 35);

                // --- امتیاز
                g.setFont(new Font("Arial", Font.BOLD, 20));
                g.drawString("امتیاز: " + score, 10, 70);

                // --- وضعیت مجروحان
                g.setFont(new Font("Arial", Font.PLAIN, 16));
                g.drawString("نجات‌یافته‌ها: " + rescuedCount, 10, 100);
                g.drawString("مرده‌ها: " + deadCount, 10, 125);
            }
        };
        infoPanel.setPreferredSize(new Dimension(240, 140));
        infoPanel.setBackground(Color.BLACK);

        // ساخت مینی‌مپ
        this.miniMapPanel = new MiniMapPanel(cityMap, rescuers, victims);

        // اضافه کردن بخش‌ها
        add(infoPanel, BorderLayout.WEST);
        add(miniMapPanel, BorderLayout.EAST);
    }

    public void updateHUD(int score, int rescued, int dead, int timeLeft,
                          CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.score = score;
        this.rescuedCount = rescued;
        this.deadCount = dead;
        this.timeLeft = timeLeft;

        // آپدیت مینی‌مپ
        this.miniMapPanel.updateMiniMap(cityMap, rescuers, victims);

        repaint();
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
