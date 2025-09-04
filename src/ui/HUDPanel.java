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
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * HUDPanel — MiniMap (اختیاری) + نوار کنترل + اطلاعات
 * - آیکن‌ها 100% برداری با Java2D (بدون فایل تصویری)
 * - تایمر بزرگ با سایه و چشمک‌زن زیر 30 ثانیه
 * - بدون Lambda/Stream
 */
public class HUDPanel extends JPanel {

    // ---------- Layout / Theme ----------
    private static final int HUD_GUTTER_PX = 8;
    private static final Color BG = new Color(12, 12, 12);
    private static final Color FG = new Color(235, 235, 235);
    private static final Color BAR_BG = new Color(22, 22, 22);
    private static final Color CARD_BG = new Color(18, 18, 18);
    private static final Color ACCENT = new Color(60, 170, 250);
    private static final Color OK = new Color(80, 200, 120);
    private static final Color WARN = new Color(235, 90, 80);

    // ---------- State ----------
    private int score;
    private int rescuedCount;
    private int deadCount;
    private int timeLeft; // seconds

    private boolean paused = false;
    private boolean blinkOn = true; // برای چشمک‌زدن تایمر

    // ---------- Subcomponents ----------
    private MiniMapPanel miniMapPanel;         // ممکن است null باشد
    private final JPanel controlBar;
    private final InfoPanel infoPanel;

    // Engine
    private GameEngine gameEngine;

    // Buttons (vector icons)
    private final JButton btnSave;
    private final JButton btnLoad;
    private final JButton btnRestart;
    private final JButton btnAddAI;

    // Timer for blinking (no lambda)
    private final javax.swing.Timer hudBlinkTimer;

    // ---------- Constructors ----------
    public HUDPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setForeground(FG);
        // فاصله‌ی کناری چپ را صفر می‌کنیم تا پنل بازی به HUD بچسبد
        setBorder(new EmptyBorder(HUD_GUTTER_PX, 0, HUD_GUTTER_PX, HUD_GUTTER_PX));

        score = rescuedCount = deadCount = 0;
        timeLeft = 0;

        controlBar = buildControlBar();
        infoPanel = new InfoPanel();

        add(controlBar, BorderLayout.NORTH);
        add(infoPanel, BorderLayout.SOUTH);

        setPreferredSize(new Dimension(300, 220));

        btnSave    = createVectorButton(Glyph.SAVE,    "Quick Save",     "Save");
        btnLoad    = createVectorButton(Glyph.LOAD,    "Quick Load",     "Load");
        btnRestart = createVectorButton(Glyph.RESTART, "Restart",        "Restart");
        btnAddAI   = createVectorButton(Glyph.PLUS,    "Add AI Rescuer", "AI");

        wireButtons();

        controlBar.add(btnSave);
        controlBar.add(btnLoad);
        controlBar.add(btnRestart);
        controlBar.add(btnAddAI);

        // Blink timer (هر 500ms)
        hudBlinkTimer = new javax.swing.Timer(500, new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                // اگر زمان کم باشد، چشمک بزن
                if (timeLeft <= 30) {
                    blinkOn = !blinkOn;
                    infoPanel.repaint();
                } else if (!blinkOn) {
                    // اگر از محدوده خطر خارج شد، روشن نگه داریم
                    blinkOn = true;
                    infoPanel.repaint();
                }
            }
        });
        hudBlinkTimer.setRepeats(true);
        hudBlinkTimer.start();
    }

    public HUDPanel(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        this();
        bindMiniMap(cityMap, rescuers, victims);
    }

    // ---------- Engine wiring ----------
    public void setGameEngine(GameEngine engine) {
        this.gameEngine = engine;
        System.out.println("[HUDPanel] GameEngine injected: " + (engine != null));
    }

    public MiniMapPanel getMiniMapPanel() {
        return miniMapPanel;
    }

    public void bindMiniMap(CityMap cityMap, List<Rescuer> rescuers, List<Injured> victims) {
        if (miniMapPanel == null) {
            miniMapPanel = new MiniMapPanel(cityMap, rescuers, victims);
            add(miniMapPanelCard(miniMapPanel), BorderLayout.CENTER);
            revalidate();
        } else {
            miniMapPanel.updateMiniMap(cityMap, rescuers, victims);
        }
        repaint();
    }

    // ---------- Public API ----------
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

        if (miniMapPanel != null) miniMapPanel.updateMiniMap(cityMap, rescuers, victims);
        revalidate();
        repaint();
    }

    /** فقط زمان را بروزرسانی کن (برای تیک هر ثانیه) */
    public void setTimeLeft(int seconds) {
        this.timeLeft = seconds < 0 ? 0 : seconds;
        repaint();
    }

    /**
     * مقدار فعلی زمان باقی‌مانده را برمی‌گرداند (بر حسب ثانیه).
     */
    public int getTimeLeft() {
        return timeLeft;
    }

    /** سنکرون‌سازی Pause از بیرون (مثلاً بعد از Resume/Restart) */
    public void setPaused(boolean paused) {
        this.paused = paused;
        repaint();
    }

    // ---------- UI building ----------
    private JPanel buildControlBar() {
        JPanel bar = new JPanel();
        bar.setOpaque(true);
        bar.setBackground(BAR_BG);
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBorder(new EmptyBorder(4, 6, 4, 6));
        return bar;
    }

    private JComponent miniMapPanelCard(JComponent inner) {
        JPanel card = new JPanel(new BorderLayout());
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(new EmptyBorder(6, 6, 6, 6));
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    // ---------- Buttons wiring ----------
    private void wireButtons() {
        // Save
        btnSave.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] Save clicked BUT gameEngine==null"); return; }
                try { gameEngine.saveQuick(); } catch (Throwable t) { t.printStackTrace(); }
            }
        });

        // Load
        btnLoad.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] Load clicked BUT gameEngine==null"); return; }
                try { gameEngine.loadQuick(); } catch (Throwable t) { t.printStackTrace(); }
            }
        });

        // Restart
        btnRestart.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] Restart clicked BUT gameEngine==null"); return; }
                try { gameEngine.restartGame(); } catch (Throwable t) { t.printStackTrace(); }
                setPaused(false);
            }
        });

        // Add AI
        btnAddAI.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                if (gameEngine == null) { System.out.println("[HUDPanel] AddAI clicked BUT gameEngine==null"); return; }
                try { gameEngine.spawnAIRescuer(); } catch (Throwable t) { t.printStackTrace(); }
            }
        });
    }

    // ---------- Panels ----------
    private class InfoPanel extends JPanel {
        InfoPanel() {
            setOpaque(true);
            setBackground(CARD_BG);
            setForeground(FG);
            setBorder(new EmptyBorder(8, 10, 10, 10));
            setPreferredSize(new Dimension(260, 120));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Timer
            String t = "⏱  " + formatTime(timeLeft);
            g2.setFont(new Font("Arial", Font.BOLD, 36));
            FontMetrics timerFm = g2.getFontMetrics();
            int x = (getWidth() - timerFm.stringWidth(t)) / 2;
            int y = 40;

            // Shadow
            if (timeLeft <= 30) {
                // چشمک‌زن: وقتی blinkOn=false، فقط سایه را می‌بینی (افکت چشمک)
                if (!blinkOn) {
                    g2.setColor(new Color(0, 0, 0, 100));
                    g2.drawString(t, x + 2, y + 2);
                } else {
                    g2.setColor(new Color(0, 0, 0, 120));
                    g2.drawString(t, x + 2, y + 2);
                    g2.setColor(WARN);
                    g2.drawString(t, x, y);
                }
            } else {
                g2.setColor(new Color(0, 0, 0, 120));
                g2.drawString(t, x + 2, y + 2);
                g2.setColor(FG);
                g2.drawString(t, x, y);
            }

            // Score
            g2.setFont(new Font("Arial", Font.BOLD, 18));
            g2.setColor(FG);
            String scoreText = "Score: " + score;
            int scoreX = (getWidth() - g2.getFontMetrics().stringWidth(scoreText)) / 2;
            g2.drawString(scoreText, scoreX, 68);

            // Stats row
            g2.setFont(new Font("Arial", Font.PLAIN, 15));
            // Badge Rescued (سبز)
            drawBadge(g2, "Rescued: " + rescuedCount, 10, 94, OK);
            // Badge Dead (قرمز)
            drawBadge(g2, "Dead: " + deadCount, 160, 94, WARN);
        }

        private void drawBadge(Graphics2D g2, String text, int x, int y, Color color) {
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 16;
            int h = 20;
            Shape round = new RoundRectangle2D.Float(x - 8, y - h + 4, w, h, 12, 12);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g2.fill(round);
            g2.setColor(color);
            g2.draw(round);
            g2.drawString(text, x, y);
        }
    }

    // ---------- Vector Icons ----------
    private enum Glyph { SAVE, LOAD, RESTART, PLUS }

    private JButton createVectorButton(Glyph glyph, String tooltip, String fallbackText) {
        JButton b = new JButton();
        b.setFocusable(false);
        b.setBackground(new Color(30, 30, 30));
        b.setForeground(FG);
        b.setBorder(new EmptyBorder(4, 10, 4, 10));
        b.setToolTipText(tooltip);
        setGlyph(b, glyph, tooltip, fallbackText);
        return b;
    }

    private void setGlyph(AbstractButton b, Glyph glyph, String tooltip, String fallbackText) {
        b.setToolTipText(tooltip);
        b.setIcon(new VectorIcon(glyph, 22, FG));
        b.setText("");
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(6);
        // اگر آیکن بنا به هر دلیلی نتوانست render شود، متن fallback را بگذاریم:
        Icon ic = b.getIcon();
        if (ic == null || ic.getIconWidth() <= 0) {
            b.setIcon(null);
            b.setText(fallbackText);
        }
    }

    /** آیکن برداری ساده با Java2D */
    private static class VectorIcon implements Icon {
        private final Glyph glyph;
        private final int size;
        private final Color color;

        VectorIcon(Glyph glyph, int size, Color color) {
            this.glyph = glyph;
            this.size = size;
            this.color = color;
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(x, y);
            g2.setColor(color);
            float s = (float) size;

            switch (glyph) {
                case SAVE: {
                    // فلoppy: بدنه + برچسب
                    g2.drawRoundRect((int)(s*0.12f), (int)(s*0.12f), (int)(s*0.76f), (int)(s*0.76f), 6, 6);
                    g2.fillRect((int)(s*0.24f), (int)(s*0.18f), (int)(s*0.44f), (int)(s*0.16f));
                    g2.drawRect((int)(s*0.20f), (int)(s*0.46f), (int)(s*0.60f), (int)(s*0.30f));
                    break;
                }
                case LOAD: {
                    // پوشه + پیکان
                    g2.drawRoundRect((int)(s*0.14f), (int)(s*0.22f), (int)(s*0.72f), (int)(s*0.54f), 6, 6);
                    Polygon a = new Polygon();
                    a.addPoint((int)(s*0.50f), (int)(s*0.24f));
                    a.addPoint((int)(s*0.70f), (int)(s*0.44f));
                    a.addPoint((int)(s*0.58f), (int)(s*0.44f));
                    a.addPoint((int)(s*0.58f), (int)(s*0.68f));
                    a.addPoint((int)(s*0.42f), (int)(s*0.68f));
                    a.addPoint((int)(s*0.42f), (int)(s*0.44f));
                    a.addPoint((int)(s*0.30f), (int)(s*0.44f));
                    g2.fillPolygon(a);
                    break;
                }
                case RESTART: {
                    // فلش چرخان
                    int r = (int)(s*0.28f);
                    int cx = (int)(s*0.50f);
                    int cy = (int)(s*0.55f);
                    g2.drawArc(cx - r, cy - r, r*2, r*2, 40, 260);
                    Polygon head = new Polygon();
                    head.addPoint((int)(s*0.68f), (int)(s*0.24f));
                    head.addPoint((int)(s*0.86f), (int)(s*0.26f));
                    head.addPoint((int)(s*0.74f), (int)(s*0.38f));
                    g2.fillPolygon(head);
                    break;
                }
                case PLUS: {
                    Stroke old = g2.getStroke();
                    g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine((int)(s*0.50f), (int)(s*0.20f), (int)(s*0.50f), (int)(s*0.80f));
                    g2.drawLine((int)(s*0.20f), (int)(s*0.50f), (int)(s*0.80f), (int)(s*0.50f));
                    g2.setStroke(old);
                    break;
                }
                default: break;
            }
            g2.dispose();
        }
    }

    // ---------- Helpers ----------
    private String formatTime(int seconds) {
        if (seconds < 0) seconds = 0;
        int m = seconds / 60;
        int s = seconds % 60;
        String mm = (m < 10 ? "0" + m : String.valueOf(m));
        String ss = (s < 10 ? "0" + s : String.valueOf(s));
        return mm + ":" + ss;
    }
}
