package ui;

import agent.Rescuer;
import controller.GameEngine;
import map.CityMap;
import victim.Injured;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.URL;
import java.util.List;

/**
 * HUDPanel — MiniMap on top, info below.
 * نوار کنترل بالا: Pause/Resume, Save, Load, Restart
 * آیکن‌ها اگر پیدا نشدن متن fallback نمایش داده می‌شود.
 * هیچ لامبدایی استفاده نشده.
 */
public class HUDPanel extends JPanel {

    // فاصله‌های ظاهری
    private static final int HUD_GUTTER_PX = 8;

    // وضعیت‌های HUD
    private int score;
    private int rescuedCount;
    private int deadCount;
    private int timeLeft; // seconds

    // زیرکامپوننت‌ها
    private MiniMapPanel miniMapPanel;   // اختیاری
    private final InfoPanel infoPanel;   // بخش پایینی نوشته‌ها
    private final JPanel controlBar;     // نوار دکمه‌ها

    // اتصال به موتور
    private GameEngine gameEngine;       // باید از بیرون تزریق شود
    private boolean paused = false;

    // دکمه‌ها
    private final JButton btnPause;
    private final JButton btnSave;
    private final JButton btnLoad;
    private final JButton btnRestart;

    // ------------------- سازنده‌ها -------------------
    public HUDPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setBorder(new EmptyBorder(HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX));

        // مقادیر اولیه (Engine بعداً آپدیت می‌کند)
        this.score = 0;
        this.rescuedCount = 0;
        this.deadCount = 0;
        this.timeLeft = 0;

        controlBar = buildControlBar();
        infoPanel  = new InfoPanel();

        add(controlBar, BorderLayout.NORTH);
        add(infoPanel,  BorderLayout.SOUTH);

        // حداقل اندازه
        setPreferredSize(new Dimension(260, 240));

        // ساخت دکمه‌ها
        btnPause   = createPauseButton();
        btnSave    = createIconButton("assets/ui/save.png",    "Quick Save", "Save");
        btnLoad    = createIconButton("assets/ui/load.png",    "Quick Load", "Load");
        btnRestart = createIconButton("assets/ui/restart.png", "Restart",    "Restart");
        wireNonPauseButtons();
        addButtonsToBar();
    }

    public HUDPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setBorder(new EmptyBorder(HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX));

        this.score = 0;
        this.rescuedCount = 0;
        this.deadCount = 0;
        this.timeLeft = 0;

        controlBar   = buildControlBar();
        miniMapPanel = new MiniMapPanel(cityMap, rescuers, victims);
        infoPanel    = new InfoPanel();

        add(controlBar,   BorderLayout.NORTH);
        add(miniMapPanel, BorderLayout.CENTER);
        add(infoPanel,    BorderLayout.SOUTH);

        int w = miniMapPanel.getPreferredSize().width + HUD_GUTTER_PX * 2;
        int h = miniMapPanel.getPreferredSize().height + 140 + HUD_GUTTER_PX * 2;
        setPreferredSize(new Dimension(w, h));

        btnPause   = createPauseButton();
        btnSave    = createIconButton("assets/ui/save.png",    "Quick Save", "Save");
        btnLoad    = createIconButton("assets/ui/load.png",    "Quick Load", "Load");
        btnRestart = createIconButton("assets/ui/restart.png", "Restart",    "Restart");
        wireNonPauseButtons();
        addButtonsToBar();
    }

    // ------------------- اتصال موتور -------------------
    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
        System.out.println("[HUDPanel] GameEngine injected: " + (engine != null));
    }

    /** دسترسی به MiniMap داخلی برای تزریق به GameEngine */
    public MiniMapPanel getMiniMapPanel() {
        return miniMapPanel;
    }

    // ------------------- API آپدیت -------------------
    public void updateHUD(int score, int rescued, int dead) {
        this.score = score;
        this.rescuedCount = rescued;
        this.deadCount = dead;
        repaint();
    }

    public void updateHUD(int score, int rescued, int dead, int timeLeft,
                          CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this.score = score;
        this.rescuedCount = rescued;
        this.deadCount = dead;
        this.timeLeft = (timeLeft < 0) ? 0 : timeLeft;

        if (miniMapPanel != null) {
            miniMapPanel.updateMiniMap(cityMap, rescuers, victims);
        }
        revalidate();
        repaint();
    }

    // ------------------- زیرکلاس: اطلاعات -------------------
    private class InfoPanel extends JPanel {
        InfoPanel() {
            setPreferredSize(new Dimension(240, 110));
            setBackground(Color.BLACK);
            setForeground(Color.WHITE);
            setBorder(new EmptyBorder(6, 6, 6, 6));
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(getForeground());
            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.drawString("⏱  " + formatTime(timeLeft), 10, 32);
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString("Score: " + score, 10, 58);
            g.setFont(new Font("Arial", Font.PLAIN, 15));
            g.drawString("Rescued: " + rescuedCount, 10, 82);
            g.drawString("Dead: " + deadCount, 10, 102);
        }
    }

    // ------------------- نوار کنترل -------------------
    private JPanel buildControlBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(true);
        bar.setBackground(new Color(20, 20, 20));
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBorder(new EmptyBorder(2, 2, 2, 2));
        return bar;
    }

    private void addButtonsToBar() {
        controlBar.removeAll();
        controlBar.add(btnPause);
        controlBar.add(btnSave);
        controlBar.add(btnLoad);
        controlBar.add(btnRestart);
        controlBar.revalidate();
        controlBar.repaint();
    }

    // ------------------- دکمه Pause/Resume -------------------
    private JButton createPauseButton() {
        final JButton b = createIconButton("assets/ui/pause.png", "Pause / Resume", "Pause");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) {
                    System.out.println("[HUDPanel] Pause clicked BUT gameEngine==null");
                    return;
                }
                if (!paused) {
                    System.out.println("[HUDPanel] Pause → stop()");
                    try { gameEngine.stop(); } catch (Throwable ignored) {}
                    paused = true;
                    setIconOrText(b, "assets/ui/resume.png", "Resume");
                } else {
                    System.out.println("[HUDPanel] Resume → start()");
                    try { gameEngine.start(); } catch (Throwable ignored) {}
                    paused = false;
                    setIconOrText(b, "assets/ui/pause.png", "Pause");
                }
            }
        });
        return b;
    }

    // دکمه‌های Save/Load/Restart
    private void wireNonPauseButtons() {
        btnSave.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] Save clicked BUT gameEngine==null"); return; }
                System.out.println("[HUDPanel] Save → saveQuick()");
                try { gameEngine.saveQuick(); } catch (Throwable t) { t.printStackTrace(); }
            }
        });
        btnLoad.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] Load clicked BUT gameEngine==null"); return; }
                System.out.println("[HUDPanel] Load → loadQuick()");
                try { gameEngine.loadQuick(); } catch (Throwable t) { t.printStackTrace(); }
            }
        });
        btnRestart.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] Restart clicked BUT gameEngine==null"); return; }
                System.out.println("[HUDPanel] Restart → restartGame()");
                try { gameEngine.restartGame(); } catch (Throwable t) { t.printStackTrace(); }
            }
        });
    }

    // ------------------- هِلپر ساخت دکمه آیکن‌دار -------------------
    private JButton createIconButton(String iconPath, String tooltip, String fallbackText) {
        JButton b = new JButton();
        b.setFocusable(false);
        b.setBackground(new Color(30, 30, 30));
        b.setForeground(new Color(230, 230, 230));
        b.setBorder(new EmptyBorder(2, 8, 2, 8));
        b.setToolTipText(tooltip);
        setIconOrText(b, iconPath, fallbackText);
        return b;
    }

    /** تلاش برای لود آیکن؛ اگر نبود متن نشان بده. */
    private void setIconOrText(AbstractButton b, String path, String fallback) {
        ImageIcon icon = loadIcon(path);
        if (icon != null && icon.getIconWidth() > 0) {
            b.setIcon(icon);
            b.setText("");
        } else {
            b.setIcon(null);
            b.setText(fallback);
        }
    }

    private ImageIcon loadIcon(String path) {
        // 1) تلاش از classpath
        try {
            String normalized = path.startsWith("/") ? path : "/" + path;
            URL url = HUDPanel.class.getResource(normalized);
            if (url == null) url = HUDPanel.class.getResource(path);
            if (url != null) return new ImageIcon(url);
        } catch (Throwable ignore) { }
        // 2) تلاش از فایل سیستم
        try {
            File f = new File(path);
            if (f.exists()) return new ImageIcon(path);
        } catch (Throwable ignore) { }
        return null;
    }

    // ------------------- هِلپرهای عمومی -------------------
    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }
}
