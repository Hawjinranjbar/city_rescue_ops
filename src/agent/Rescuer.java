// src/agent/Rescuer.java
package agent;

import controller.ScoreManager;
import util.AssetLoader;
import util.Position;
import victim.Injured;
import victim.InjurySeverity;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * --------------------
 * لایه: Domain Layer — package agent
 * --------------------
 * مدل نجات‌دهنده + مدیریت اسپرایت/انیمیشن + حالت آمبولانس.
 * جهت‌ها: 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
 *
 * شیت 3×4 (۳ ردیف، ۴ ستون): به ترتیب ردیف‌ها:
 *   r=0 → DOWN ، r=1 → LEFT ، r=2 → RIGHT   (UP نداریم، از DOWN کپی می‌شود)
 */
public class Rescuer {

    // ====== ثابت‌های جهت ======
    public static final int DIR_DOWN  = 0;
    public static final int DIR_LEFT  = 1;
    public static final int DIR_RIGHT = 2;
    public static final int DIR_UP    = 3;

    // ====== وضعیت منطقی عامل ======
    private final int id;
    private Position position;
    private boolean isBusy;
    private Injured carryingVictim;
    private boolean ambulanceMode;
    /** اگر true باشد، این نجات‌دهنده توسط هوش مصنوعی کنترل می‌شود */
    private boolean aiControlled;

    /** اگر true باشد، نجات‌دهنده می‌تواند روی هر سلولی حرکت کند (نادیده گرفتن collision/occupied/hospital) */
    private boolean noClip = true; // فقط روی Rescuer اثر دارد؛ آمبولانس را تغییر نمی‌دهد

    /** فریز سراسری برای Pause/Resume (حین Save/Load) */
    private boolean paused = false;

    // ====== گرافیک/انیمیشن – ریسکیور ======
    private BufferedImage[][] rescuerFrames;                // [dir][frame]
    private int direction = DIR_DOWN;                      // 0=DOWN,1=LEFT,2=RIGHT,3=UP
    private int currentFrame = 0;
    private final Map<Integer, BufferedImage[][]> rescuerScaledCache = new HashMap<Integer, BufferedImage[][]>();
    private static final int RESCUER_COLS = 4;
    private static final int RESCUER_ROWS = 3;
    private int RESCUER_FRAME_W = 64;
    private int RESCUER_FRAME_H = 64;
    private static final String RESCUER_SPRITE_PATH   = "assets/characters/rescuer.png";
    private static final String AMBULANCE_SPRITE_PATH = "assets/characters/Ambulance.png";

    // ====== گرافیک/انیمیشن – آمبولانس ======
    private BufferedImage[][] ambulanceFrames;             // [dir][0]
    private final Map<Integer, BufferedImage[][]> ambulanceScaledCache = new HashMap<Integer, BufferedImage[][]>();
    private int AMB_FRAME_W = 64;
    private int AMB_FRAME_H = 64;

    // ====== سازنده ======
    public Rescuer(int id, Position startPos) {
        this.id = id;
        this.position = (startPos != null) ? startPos : new Position(0, 0);
        this.isBusy = false;
        this.carryingVictim = null;
        this.ambulanceMode = false;
        this.aiControlled = false;
        loadRescuerSpriteSheet();
        loadAmbulanceSpriteSheet();
    }

    // ====== بارگذاری شیتِ ریسکیور ======
    private void loadRescuerSpriteSheet() {
        BufferedImage sheet = AssetLoader.loadImage(RESCUER_SPRITE_PATH);
        if (sheet == null) {
            rescuerFrames = new BufferedImage[4][RESCUER_COLS];
            System.err.println("[Rescuer] Sprite sheet NOT found: " + RESCUER_SPRITE_PATH);
            return;
        }

        int bg = sheet.getRGB(0, 0);
        BufferedImage cropped = AssetLoader.cropToContent(sheet, bg, 20);
        if (cropped == null) cropped = sheet;

        RESCUER_FRAME_W = Math.max(1, cropped.getWidth() / RESCUER_COLS);
        RESCUER_FRAME_H = Math.max(1, cropped.getHeight() / RESCUER_ROWS);
        rescuerFrames = new BufferedImage[4][RESCUER_COLS];

        int tolerance = 36;
        for (int r = 0; r < RESCUER_ROWS; r++) {
            for (int c = 0; c < RESCUER_COLS; c++) {
                int sx = c * RESCUER_FRAME_W;
                int sy = r * RESCUER_FRAME_H;
                if (sx + RESCUER_FRAME_W <= cropped.getWidth() && sy + RESCUER_FRAME_H <= cropped.getHeight()) {
                    BufferedImage raw = cropped.getSubimage(sx, sy, RESCUER_FRAME_W, RESCUER_FRAME_H);
                    BufferedImage clear = AssetLoader.makeColorTransparent(raw, bg, tolerance);

                    // نگاشت استاندارد: r=0(DOWN), r=1(LEFT), r=2(RIGHT)
                    if (r == 0) {
                        rescuerFrames[DIR_DOWN][c] = clear;
                    } else if (r == 1) {
                        rescuerFrames[DIR_LEFT][c] = clear;
                    } else {
                        rescuerFrames[DIR_RIGHT][c] = clear;
                    }
                }
            }
        }

        // اگر ردیف LEFT موجود نبود، از RIGHT وارونه بساز
        if ((rescuerFrames[DIR_LEFT] == null || rescuerFrames[DIR_LEFT][0] == null) && rescuerFrames[DIR_RIGHT] != null) {
            BufferedImage[] leftRow = new BufferedImage[RESCUER_COLS];
            for (int c = 0; c < RESCUER_COLS; c++) {
                if (rescuerFrames[DIR_RIGHT][c] != null) {
                    leftRow[c] = AssetLoader.flipHorizontal(rescuerFrames[DIR_RIGHT][c]);
                }
            }
            rescuerFrames[DIR_LEFT] = leftRow;
        }

        // اگر ردیف UP نبود، از DOWN کپی کن
        if (rescuerFrames[DIR_UP] == null || rescuerFrames[DIR_UP][0] == null) {
            BufferedImage[] upRow = new BufferedImage[RESCUER_COLS];
            for (int c = 0; c < RESCUER_COLS; c++) upRow[c] = rescuerFrames[DIR_DOWN][c];
            rescuerFrames[DIR_UP] = upRow;
        }

        rescuerScaledCache.clear();
    }

    // ====== بارگذاری شیتِ آمبولانس ======
    private void loadAmbulanceSpriteSheet() {
        BufferedImage sheet = AssetLoader.loadImage(AMBULANCE_SPRITE_PATH);
        if (sheet == null) {
            ambulanceFrames = new BufferedImage[4][1];
            System.err.println("[Rescuer] Ambulance sprite NOT found: " + AMBULANCE_SPRITE_PATH);
            return;
        }

        int bg = sheet.getRGB(0, 0);
        BufferedImage cropped = AssetLoader.cropToContent(sheet, bg, 20);
        if (cropped == null) cropped = sheet;

        int cols = 2, rows = 2;
        AMB_FRAME_W = Math.max(1, cropped.getWidth() / cols);
        AMB_FRAME_H = Math.max(1, cropped.getHeight() / rows);

        BufferedImage front = cropSafe(cropped, 0,            0,            AMB_FRAME_W, AMB_FRAME_H); // بالا-چپ
        BufferedImage right = cropSafe(cropped, AMB_FRAME_W,  0,            AMB_FRAME_W, AMB_FRAME_H); // بالا-راست
        BufferedImage back  = cropSafe(cropped, 0,            AMB_FRAME_H,  AMB_FRAME_W, AMB_FRAME_H); // پایین-چپ
        BufferedImage left  = cropSafe(cropped, AMB_FRAME_W,  AMB_FRAME_H,  AMB_FRAME_W, AMB_FRAME_H); // پایین-راست

        int tolerance = 36;
        front = AssetLoader.makeColorTransparent(front, bg, tolerance);
        right = AssetLoader.makeColorTransparent(right, bg, tolerance);
        back  = AssetLoader.makeColorTransparent(back,  bg, tolerance);
        left  = AssetLoader.makeColorTransparent(left,  bg, tolerance);

        ambulanceFrames = new BufferedImage[4][1];

        // اگر چپ/راست برعکس دیده می‌شود این را true بگذار
        final boolean SWAP_LR = true;

        ambulanceFrames[DIR_DOWN][0] = front; // DOWN
        if (SWAP_LR) {
            ambulanceFrames[DIR_LEFT][0]  = right; // LEFT  ← جابجا
            ambulanceFrames[DIR_RIGHT][0] = left;  // RIGHT ← جابجا
        } else {
            ambulanceFrames[DIR_LEFT][0]  = left;
            ambulanceFrames[DIR_RIGHT][0] = right;
        }
        ambulanceFrames[DIR_UP][0] = back;  // UP

        ambulanceScaledCache.clear();
    }

    private static BufferedImage cropSafe(BufferedImage src, int x, int y, int w, int h) {
        int maxW = src.getWidth();
        int maxH = src.getHeight();
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x + w > maxW) w = maxW - x;
        if (y + h > maxH) h = maxH - y;
        if (w <= 0 || h <= 0) return null;
        return src.getSubimage(x, y, w, h);
    }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    // ====== API منطقی ======
    public int getId() { return id; }
    public Position getPosition() { return position; }

    /** X/Y کاشی فعلی (برای ذخیره‌سازی سبک) */
    public int getTileX() { return position != null ? position.getX() : 0; }
    public int getTileY() { return position != null ? position.getY() : 0; }

    public void setPosition(Position newPos) {
        if (newPos != null) this.position = newPos;
    }

    /** تنظیم موقعیت به مختصات کاشی */
    public void setTile(int x, int y) {
        setPositionXY(x, y);
    }

    public void setPositionXY(int x, int y) {
        if (this.position == null) this.position = new Position(x, y);
        else { this.position.setX(x); this.position.setY(y); }
    }

    /**
     * تعیین جهت از روی دلتا حرکت برای اطمینان از رندر صحیح سمت چپ.
     * اگر حرکتی نبود، جهت ورودی حفظ می‌شود تا ایستاده‌بودن هم درست نمایش داده شود.
     */
    public void onMoveStep(int newX, int newY, int dir) {
        if (paused) return; // در حالت فریز حرکت/انیمیشن نکن
        int oldX = (this.position != null) ? this.position.getX() : newX;
        int oldY = (this.position != null) ? this.position.getY() : newY;
        int dx = newX - oldX;
        int dy = newY - oldY;

        setPositionXY(newX, newY);

        if (dx < 0) {
            this.direction = DIR_LEFT;
        } else if (dx > 0) {
            this.direction = DIR_RIGHT;
        } else if (dy < 0) {
            this.direction = DIR_UP;
        } else if (dy > 0) {
            this.direction = DIR_DOWN;
        } else {
            setDirection(dir);
        }

        nextFrame();
    }

    public boolean isBusy() { return isBusy; }
    public void setBusy(boolean busy) { this.isBusy = busy; }

    public boolean isCarryingVictim() { return carryingVictim != null; }
    public Injured getCarryingVictim() { return carryingVictim; }

    public boolean isAmbulanceMode() { return ambulanceMode; }
    /** تنظیم مستقیم حالت آمبولانس (برای Load). */
    public void setAmbulanceMode(boolean enabled) {
        if (this.ambulanceMode == enabled) return;
        this.ambulanceMode = enabled;
        if (!enabled) {
            // خروج از حالت: وضعیت‌ها ریست می‌شن ولی قربانی رها نمی‌شود مگر detach صدا زده شود
            resetAnim();
        } else {
            // ورود به حالت: انیمیشن صفر
            resetAnim();
        }
    }

    /** فلگ no-clip فقط برای حرکتِ خود Rescuer (نه Vehicle). */
    public boolean isNoClip() { return noClip; }
    public void setNoClip(boolean noClip) { this.noClip = noClip; }

    /** وضعیت کنترل هوش مصنوعی */
    public boolean isAIControlled() { return aiControlled; }
    public void setAIControlled(boolean aiControlled) { this.aiControlled = aiControlled; }

    // ====== عملیات حمل/تحویل ======
    public void enterAmbulanceModeWith(Injured victim) {
        if (victim == null || ambulanceMode) return;
        this.carryingVictim = victim;
        this.isBusy = true;
        this.ambulanceMode = true;
        try { victim.setBeingRescued(true); } catch (Throwable ignored) {}
        resetAnim();
    }

    /** معادل attachVictim برای سازگاری با Apply از روی DTO */
    public void attachVictim(Injured victim) {
        if (victim == null) return;
        this.carryingVictim = victim;
        this.isBusy = true;
        try { victim.setBeingRescued(true); } catch (Throwable ignored) {}
        // حالت آمبولانس را به caller وا می‌گذاریم؛ اگر لازم است:
        // this.ambulanceMode = true;
        resetAnim();
    }

    public void detachVictimIfAny() {
        if (this.carryingVictim != null) {
            try { this.carryingVictim.setBeingRescued(false); } catch (Throwable ignored) {}
        }
        this.carryingVictim = null;
        this.isBusy = false;
        // خروج از آمبولانس به عهده‌ی caller است؛ اینجا تغییر نمی‌دهیم
        resetAnim();
    }

    public void deliverVictimAtHospital() {
        if (carryingVictim == null) return;
        carryingVictim.markAsRescued();
        int initial = safeInitialTime(carryingVictim);
        if (initial < 0) initial = 0;
        ScoreManager.add(2 * initial);
        carryingVictim = null;
        isBusy = false;
        ambulanceMode = false;
        resetAnim();
    }

    public void cancelAmbulanceMode() {
        if (!ambulanceMode) return;
        if (carryingVictim != null) {
            try { carryingVictim.setBeingRescued(false); } catch (Throwable ignored) {}
        }
        carryingVictim = null;
        isBusy = false;
        ambulanceMode = false;
        resetAnim();
    }

    // ====== گرافیک/انیمیشن ======
    public void setDirection(int dir) {
        this.direction = clamp(dir, 0, 3);
    }
    public void faceTo(int dir) { setDirection(dir); }
    public int getDirection() { return direction; }

    public BufferedImage getSprite() {
        if (ambulanceMode) {
            if (ambulanceFrames == null) return null;
            int dir = clamp(direction, 0, 3);
            return ambulanceFrames[dir][0];
        } else {
            if (rescuerFrames == null) return null;
            int dir = clamp(direction, 0, 3);
            int colCount = (rescuerFrames[dir] != null) ? rescuerFrames[dir].length : 0;
            int cf = (colCount > 0) ? (currentFrame % colCount) : 0;
            return rescuerFrames[dir][cf];
        }
    }

    public BufferedImage getSpriteScaled(int tileSize) {
        if (tileSize <= 0) return getSprite();

        if (ambulanceMode) {
            if (ambulanceFrames == null) return null;
            BufferedImage[][] grid = ambulanceScaledCache.get(tileSize);
            if (grid == null) {
                int rows = ambulanceFrames.length; // 4
                int cols = 1;
                grid = new BufferedImage[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (ambulanceFrames[r] != null && ambulanceFrames[r][c] != null) {
                            grid[r][c] = AssetLoader.scaleNearest(ambulanceFrames[r][c], tileSize, tileSize);
                        }
                    }
                }
                ambulanceScaledCache.put(tileSize, grid);
            }
            int dir = clamp(direction, 0, 3);
            return gridSafe(grid, dir, 0);
        } else {
            if (rescuerFrames == null) return null;
            BufferedImage[][] grid = rescuerScaledCache.get(tileSize);
            if (grid == null) {
                int rows = rescuerFrames.length;
                int cols = RESCUER_COLS;
                grid = new BufferedImage[rows][cols];
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        if (rescuerFrames[r] != null && rescuerFrames[r][c] != null) {
                            grid[r][c] = AssetLoader.scaleNearest(rescuerFrames[r][c], tileSize, tileSize);
                        }
                    }
                }
                rescuerScaledCache.put(tileSize, grid);
            }
            int dir = clamp(direction, 0, 3);
            int colCount = (grid[dir] != null) ? grid[dir].length : 0;
            int cf = (colCount > 0) ? (currentFrame % colCount) : 0;
            return gridSafe(grid, dir, cf);
        }
    }

    private static BufferedImage gridSafe(BufferedImage[][] grid, int r, int c) {
        if (grid == null) return null;
        if (r < 0 || r >= grid.length) return null;
        if (grid[r] == null) return null;
        if (c < 0 || c >= grid[r].length) return null;
        return grid[r][c];
    }

    public void nextFrame() {
        if (paused) return;
        if (ambulanceMode) return; // آمبولانس فریم راه‌رفتن ندارد
        if (rescuerFrames == null) return;
        int dir = clamp(direction, 0, 3);
        int colCount = (rescuerFrames[dir] != null) ? rescuerFrames[dir].length : 0;
        if (colCount > 0) currentFrame = (currentFrame + 1) % colCount;
    }

    public void resetAnim() { currentFrame = 0; }

    /** محدودسازی حرکت روی جاده در حالت آمبولانس (این همان منطقِ Vehicle است و تغییری نکرده) */
    public boolean isRoadOnlyMode() { return ambulanceMode; }

    private int safeInitialTime(Injured v) {
        if (v == null) return 0;
        try {
            Method m = v.getClass().getMethod("getInitialTimeLimit");
            Object r = m.invoke(v);
            if (r instanceof Integer) return ((Integer) r).intValue();
        } catch (Throwable ignored) { }
        try {
            InjurySeverity sev = v.getSeverity();
            if (sev == InjurySeverity.CRITICAL) return 60;
            if (sev == InjurySeverity.MEDIUM)   return 120;
            if (sev == InjurySeverity.LOW)      return 180;
        } catch (Throwable ignored2) { }
        return 0;
    }

    // ====== سازگاری با Thread-base: Pause/Resume ======

    /** فریز منطق حرکت/انیمیشن این نجات‌دهنده (حین Save/Load) */
    public void pause() {
        this.paused = true;
    }

    /** ادامهٔ منطق پس از فریز */
    public void resume() {
        this.paused = false;
    }
}
