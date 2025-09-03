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
import java.util.List;

/**
 * HUDPanel — MiniMap on top, info below (English labels).
 * Very small outer margins so HUD hugs the window edges more closely.
 * Backward compatible: provides two updateHUD signatures.
 * + Control bar with Pause/Save/Load/Restart buttons.
 */
public class HUDPanel extends JPanel {

    // Smaller outer empty margin around the whole HUD (visual breathing room)
    private static final int HUD_GUTTER_PX = 8; // reduced from large values to keep spacing tight

    private int score;
    private int rescuedCount;
    private int deadCount;
    private int timeLeft; // seconds

    private MiniMapPanel miniMapPanel;  // optional (when constructed with map/rescuers/victims)
    private final InfoPanel infoPanel;  // bottom text area
    private final JPanel controlBar;    // top buttons row

    // --- Engine reference for buttons (optional) ---
    private GameEngine gameEngine;
    private boolean paused = false;     // local pause state for button toggle

    // --- Buttons ---
    private final JButton btnPause;
    private final JButton btnSave;
    private final JButton btnLoad;
    private final JButton btnRestart;

    // -------- Constructors --------
    public HUDPanel() {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setBorder(new EmptyBorder(HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX)); // small outer margin

        // initial preview values; game engine will overwrite via updateHUD(...)
        this.score = 500;
        this.rescuedCount = 0;
        this.deadCount = 0;
        this.timeLeft = 300;

        // control bar + buttons
        controlBar = buildControlBar();

        infoPanel = new InfoPanel();

        add(controlBar, BorderLayout.NORTH);
        // No minimap attached in this ctor
        add(infoPanel, BorderLayout.SOUTH);

        // minimal HUD size even if minimap is not attached
        setPreferredSize(new Dimension(260, 240));

        // initialize buttons after controlBar creation
        btnPause = createPauseButton();
        btnSave = createIconButton("assets/ui/save.png", "Quick Save (F5)", "Save");
        btnLoad = createIconButton("assets/ui/load.png", "Quick Load (F9)", "Load");
        btnRestart = createIconButton("assets/ui/restart.png", "Restart (R)", "Restart");
        addButtonsToBar();
    }

    public HUDPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        setForeground(Color.WHITE);
        setBorder(new EmptyBorder(HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX, HUD_GUTTER_PX)); // small outer margin

        this.score = 500;
        this.rescuedCount = 0;
        this.deadCount = 0;
        this.timeLeft = 300;

        controlBar = buildControlBar();
        miniMapPanel = new MiniMapPanel(cityMap, rescuers, victims);
        infoPanel = new InfoPanel();

        add(controlBar, BorderLayout.NORTH);
        add(miniMapPanel, BorderLayout.CENTER); // minimap on top/center
        add(infoPanel, BorderLayout.SOUTH);     // texts below

        int w = miniMapPanel.getPreferredSize().width + HUD_GUTTER_PX * 2;
        int h = miniMapPanel.getPreferredSize().height + 140 + HUD_GUTTER_PX * 2;
        setPreferredSize(new Dimension(w, h));

        // initialize buttons after controlBar creation
        btnPause = createPauseButton();
        btnSave = createIconButton("assets/ui/save.png", "Quick Save (F5)", "Save");
        btnLoad = createIconButton("assets/ui/load.png", "Quick Load (F9)", "Load");
        btnRestart = createIconButton("assets/ui/restart.png", "Restart (R)", "Restart");
        addButtonsToBar();
    }

    // -------- Engine hookup --------
    /** Inject the GameEngine so buttons can work. */
    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
    }

    // -------- Update APIs --------

    /** Backward-compatible signature (existing engine calls). */
    public void updateHUD(int score, int rescued, int dead) {
        this.score = score;
        this.rescuedCount = rescued;
        this.deadCount = dead;
        repaint();
    }

    /** Full signature when HUD owns the minimap. */
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

    // -------- Inner info panel (texts) --------
    private class InfoPanel extends JPanel {
        InfoPanel() {
            setPreferredSize(new Dimension(240, 110));
            setBackground(Color.BLACK);
            setForeground(Color.WHITE);
            // small inner padding to keep text slightly away from edges
            setBorder(new EmptyBorder(6, 6, 6, 6));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(getForeground());

            // Timer (large)
            g.setFont(new Font("Arial", Font.BOLD, 26));
            g.drawString("⏱  " + formatTime(timeLeft), 10, 32);

            // Score
            g.setFont(new Font("Arial", Font.BOLD, 18));
            g.drawString("Score: " + score, 10, 58);

            // Victim stats
            g.setFont(new Font("Arial", Font.PLAIN, 15));
            g.drawString("Rescued: " + rescuedCount, 10, 82);
            g.drawString("Dead: " + deadCount, 10, 102);
        }
    }

    // -------- Control bar (buttons) --------
    private JPanel buildControlBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(true);
        bar.setBackground(new Color(20, 20, 20));
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBorder(new EmptyBorder(2, 2, 2, 2));
        return bar;
    }

    private JButton createPauseButton() {
        final JButton b = new JButton();
        b.setFocusable(false);
        b.setBackground(new Color(30, 30, 30));
        b.setForeground(new Color(230, 230, 230));
        b.setBorder(new EmptyBorder(2, 8, 2, 8));
        setIconOrText(b, "assets/ui/pause.png", "Pause");

        b.setToolTipText("Pause / Resume");
        b.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) return;
                if (!paused) {
                    try { gameEngine.stop(); } catch (Throwable ignored) {}
                    paused = true;
                    setIconOrText(b, "assets/ui/resume.png", "Resume");
                } else {
                    try { gameEngine.start(); } catch (Throwable ignored) {}
                    paused = false;
                    setIconOrText(b, "assets/ui/pause.png", "Pause");
                }
            }
        });
        return b;
    }

    private JButton createIconButton(final String iconPath, final String tooltip, final String fallbackText) {
        final JButton b = new JButton();
        b.setFocusable(false);
        b.setBackground(new Color(30, 30, 30));
        b.setForeground(new Color(230, 230, 230));
        b.setBorder(new EmptyBorder(2, 8, 2, 8));
        b.setToolTipText(tooltip);
        setIconOrText(b, iconPath, fallbackText);
        return b;
    }

    private void addButtonsToBar() {
        // Wire actions for save/load/restart
        btnSave.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) return;
                try { gameEngine.saveQuick(); } catch (Throwable ignored) {}
            }
        });
        btnLoad.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) return;
                try { gameEngine.loadQuick(); } catch (Throwable ignored) {}
            }
        });
        btnRestart.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) return;
                try { gameEngine.restartGame(); } catch (Throwable ignored) {}
            }
        });

        controlBar.removeAll();
        controlBar.add(btnPause);
        controlBar.add(btnSave);
        controlBar.add(btnLoad);
        controlBar.add(btnRestart);
        controlBar.revalidate();
        controlBar.repaint();
    }

    // -------- Helpers --------
    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%02d:%02d", m, s);
    }

    /** Sets icon if file exists; otherwise shows text fallback. */
    private void setIconOrText(JButton b, String path, String fallback) {
        try {
            if (path != null && new File(path).exists()) {
                b.setIcon(new ImageIcon(path));
                b.setText("");
            } else {
                b.setIcon(null);
                b.setText(fallback);
            }
        } catch (Throwable t) {
            b.setIcon(null);
            b.setText(fallback);
        }
    }
}
