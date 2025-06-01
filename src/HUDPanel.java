import javax.swing.*;
import java.awt.*;

// --------------------
// لایه: UI Layer
// --------------------
// این پنل برای نمایش اطلاعات HUD بازیه:
// مثل امتیاز، نجات‌یافته‌ها، مرده‌ها و...
public class HUDPanel extends JPanel {

    private int score;
    private int rescuedCount;
    private int deadCount;

    public HUDPanel() {
        setPreferredSize(new Dimension(200, 100));
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        g.setColor(getForeground());
        g.setFont(new Font("Arial", Font.BOLD, 16));
        g.drawString("امتیاز: " + score, 10, 25);
        g.drawString("نجات‌یافته‌ها: " + rescuedCount, 10, 50);
        g.drawString("مرده‌ها: " + deadCount, 10, 75);
    }

    public void updateHUD(int score, int rescued, int dead) {
        this.score = score;
        this.rescuedCount = rescued;
        this.deadCount = dead;
        repaint();
    }
}
