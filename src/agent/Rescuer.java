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
 * - حالت عادی: اسپرایت ریسکیور
 * - نزدیک مجروح: enterAmbulanceModeWith(v) → مجروح ضمیمه می‌شود (beingRescued=true)
 * - حرکت در حالت آمبولانس: باید فقط روی جاده حرکت کند (isRoadOnlyMode() == true)
 * - نزدیک بیمارستان: deliverVictimAtHospital() → مجروح rescued + امتیاز 2×زمان اولیه
 *
 * مختصات position تایل‌محور است (x,y به واحد تایل).
 */
public class Rescuer {

    // ====== وضعیت منطقی عامل ======
    private final int id;                 // شناسه یکتا
    private Position position;            // موقعیت به واحد تایل
    private boolean isBusy;               // آیا درگیر عملیات است؟
    private Injured carryingVictim;       // مجروحِ در حال حمل (در صورت وجود)

    // حالت آمبولانس (وقتی true → فقط روی جاده باید حرکت کند)
    private boolean ambulanceMode;

    // ====== گرافیک/انیمیشن – ریسکیور ======
    // frames[direction][frame]  →  direction: 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
    private BufferedImage[][] rescuerFrames;
    private int direction = 0;            // 0=پایین، 1=چپ، 2=راست، 3=بالا
    private int currentFrame = 0;         // ستون فریم جاری

    // کش فریم‌های اسکیل‌شده برحسب tileSize (برای ریسکیور)
    private final Map<Integer, BufferedImage[][]> rescuerScaledCache = new HashMap<Integer, BufferedImage[][]>();

    // پیکربندی شیت (ستون/ردیف) برای ریسکیور
    private static final int RESCUER_COLS = 4;    // تعداد ستون‌ها
    private static final int RESCUER_ROWS = 3;    // down/left/right ؛ up از down کپی می‌شود

    // ابعاد واقعی هر فریم ریسکیور (پس از کراپ)
    private int RESCUER_FRAME_W = 64;
    private int RESCUER_FRAME_H = 64;

    // مسیر اسپرایت‌شیت‌ ریسکیور و آمبولانس
    private static final String RESCUER_SPRITE_PATH   = "assets/characters/rescuer.png";
    private static final String AMBULANCE_SPRITE_PATH = "assets/characters/Ambulance.png";

    // ====== گرافیک/انیمیشن – آمبولانس ======
    // در فایل PNG تو 4 نما داریم (front/right/back/left). این‌جا به صورت grid 2×2 برش می‌زنیم:
    // map: [DOWN(front)=0], [RIGHT=2], [UP(back)=3], [LEFT=1]
    private BufferedImage[][] ambulanceFrames; // [dir][0] فقط یک فریم برای هر جهت
    private final Map<Integer, BufferedImage[][]> ambulanceScaledCache = new HashMap<Integer, BufferedImage[][]>();
    private int AMB_FRAME_W = 64;
    private int AMB_FRAME_H = 64;

    // -------------------- سازنده --------------------
    public Rescuer(int id, Position startPos) {
        this.id = id;
        this.position = (startPos != null) ? startPos : new Position(0, 0);
        this.isBusy = false;
        this.carryingVictim = null;
        this.ambulanceMode = false;
        loadRescuerSpriteSheet();
        loadAmbulanceSpriteSheet();
    }

    // -------------------- بارگذاری اسپرایت‌شیت ریسکیور --------------------
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
        int r, c, sx, sy;
        for (r = 0; r < RESCUER_ROWS; r++) {
            for (c = 0; c < RESCUER_COLS; c++) {
                sx = c * RESCUER_FRAME_W;
                sy = r * RESCUER_FRAME_H;
                if (sx + RESCUER_FRAME_W <= cropped.getWidth() && sy + RESCUER_FRAME_H <= cropped.getHeight()) {
                    BufferedImage raw = cropped.getSubimage(sx, sy, RESCUER_FRAME_W, RESCUER_FRAME_H);
                    BufferedImage clear = AssetLoader.makeColorTransparent(raw, bg, tolerance);
                    rescuerFrames[r][c] = clear; // r: 0=DOWN,1=LEFT,2=RIGHT
                }
            }
        }

        // ساخت ردیف LEFT با وارونه‌سازی RIGHT اگر لازم شد
        if ((rescuerFrames[1] == null || rescuerFrames[1][0] == null) && rescuerFrames[2][0] != null) {
            BufferedImage[] leftRow = new BufferedImage[RESCUER_COLS];
            for (c = 0; c < RESCUER_COLS; c++) {
                if (rescuerFrames[2][c] != null) {
                    leftRow[c] = AssetLoader.flipHorizontal(rescuerFrames[2][c]);
                }
            }
            rescuerFrames[1] = leftRow;
        }

        // اگر ردیف UP در شیت وجود ندارد، از ردیف DOWN کپی کن
        if (rescuerFrames[3] == null || rescuerFrames[3][0] == null) {
            BufferedImage[] upRow = new BufferedImage[RESCUER_COLS];
            for (c = 0; c < RESCUER_COLS; c++) {
                upRow[c] = rescuerFrames[0][c]; // UP = DOWN
            }
            rescuerFrames[3] = upRow;
        }

        rescuerScaledCache.clear();
    }

    // -------------------- بارگذاری اسپرایت‌شیت آمبولانس --------------------
    private void loadAmbulanceSpriteSheet() {
        BufferedImage sheet = AssetLoader.loadImage(AMBULANCE_SPRITE_PATH);
        if (sheet == null) {
            ambulanceFrames = new BufferedImage[4][1]; // فقط 1 فریم برای هر جهت
            System.err.println("[Rescuer] Ambulance sprite NOT found: " + AMBULANCE_SPRITE_PATH);
            return;
        }

        int bg = sheet.getRGB(0, 0);
        BufferedImage cropped = AssetLoader.cropToContent(sheet, bg, 20);
        if (cropped == null) cropped = sheet;

        // فایل 2×2 (front, right, back, left)
        int cols = 2;
        int rows = 2;
        AMB_FRAME_W = Math.max(1, cropped.getWidth() / cols);
        AMB_FRAME_H = Math.max(1, cropped.getHeight() / rows);

        // بریده‌ها
        BufferedImage front = cropSafe(cropped, 0, 0, AMB_FRAME_W, AMB_FRAME_H);                         // بالا-چپ
        BufferedImage right = cropSafe(cropped, AMB_FRAME_W, 0, AMB_FRAME_W, AMB_FRAME_H);               // بالا-راست
        BufferedImage back  = cropSafe(cropped, 0, AMB_FRAME_H, AMB_FRAME_W, AMB_FRAME_H);               // پایین-چپ
        BufferedImage left  = cropSafe(cropped, AMB_FRAME_W, AMB_FRAME_H, AMB_FRAME_W, AMB_FRAME_H);     // پایین-راست

        // شفاف‌سازی هاله‌ها
        int tolerance = 36;
        front = AssetLoader.makeColorTransparent(front, bg, tolerance);
        right = AssetLoader.makeColorTransparent(right, bg, tolerance);
        back  = AssetLoader.makeColorTransparent(back,  bg, tolerance);
        left  = AssetLoader.makeColorTransparent(left,  bg, tolerance);

        // map به آرایه [dir][frameIndex]؛ فقط 1 فریم برای هر جهت
        ambulanceFrames = new BufferedImage[4][1];
        // 0=DOWN(front)، 1=LEFT، 2=RIGHT، 3=UP(back)
        ambulanceFrames[0][0] = front;
        ambulanceFrames[2][0] = right;
        ambulanceFrames[3][0] = back;
        ambulanceFrames[1][0] = left;

        ambulanceScaledCache.clear();
    }

    // -------------------- ابزار برش امن --------------------
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

    // -------------------- API منطق بازی --------------------
    public int getId() { return id; }
    public Position getPosition() { return position; }

    /** ست موقعیت (تایل‌محور). در صورت نیاز از MoveGuard برای چک عبور استفاده کن. */
    public void setPosition(Position newPos) {
        if (newPos != null) this.position = newPos;
    }

    /** تغییر سریع مختصات بدون ساخت آبجکت جدید (برای MoveGuard/انیمیشن مفید است). */
    public void setPositionXY(int x, int y) {
        if (this.position == null) {
            this.position = new Position(x, y);
        } else {
            this.position.setX(x);
            this.position.setY(y);
        }
    }

    /** فراخوانی استاندارد بعد از یک حرکت موفق: موقعیت + جهت + انیمیشن */
    public void onMoveStep(int newX, int newY, int dir) {
        setPositionXY(newX, newY);
        setDirection(dir);
        nextFrame();
    }

    public boolean isBusy() { return isBusy; }
    public boolean isCarryingVictim() { return carryingVictim != null; }
    public Injured getCarryingVictim() { return carryingVictim; }

    /** آیا در حالت آمبولانس هستیم؟ (برای enforce حرکت فقط روی جاده) */
    public boolean isAmbulanceMode() { return ambulanceMode; }

    /** وقتی به مجروح نزدیک شد: وارد حالت آمبولانس می‌شویم و مجروح ضمیمه می‌شود. */
    public void enterAmbulanceModeWith(Injured victim) {
        if (victim == null) return;
        if (ambulanceMode) return;

        this.carryingVictim = victim;
        this.isBusy = true;
        this.ambulanceMode = true;

        // مجروح در حال نجات است؛ از صحنه مخفی/عدم رندر (بسته به GamePanel)
        try { victim.setBeingRescued(true); } catch (Throwable ignored) {}

        // انیمیشن را ریست کن تا فریم اول آمبولانس نمایش داده شود
        resetAnim();
    }

    /**
     * وقتی به بیمارستان رسیدیم:
     * - وضعیت مجروح: rescued
     * - امتیاز: + 2 × زمان اولیه (با fallback اگر getInitialTimeLimit نباشد)
     * - خروج از حالت آمبولانس و برگشت به ریسکیور
     */
    public void deliverVictimAtHospital() {
        if (carryingVictim == null) return;

        // ثبت نجات
        carryingVictim.markAsRescued();

        // پاداش 2× زمان اولیه
        int initial = safeInitialTime(carryingVictim);
        if (initial < 0) initial = 0;
        ScoreManager.add(2 * initial);

        // آزادسازی
        carryingVictim = null;
        isBusy = false;
        ambulanceMode = false;

        // اطمینان از خروج از حالت در GamePanel (نمایش ریسکیور)
        resetAnim();
    }

    /** اگر در میانهٔ راه منصرف شدیم یا سناریو ریست شد */
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

    // -------------------- API گرافیک/انیمیشن --------------------
    public void setDirection(int dir) { this.direction = clamp(dir, 0, 3); }
    public void faceTo(int dir) { setDirection(dir); } // نام مستعار
    public int getDirection() { return direction; }

    /** گرفتن فریم جاری (با توجه به حالت) */
    public BufferedImage getSprite() {
        if (ambulanceMode) {
            if (ambulanceFrames == null) return null;
            int dir = clamp(direction, 0, 3);
            return ambulanceFrames[dir][0]; // یک فریم برای هر جهت
        } else {
            if (rescuerFrames == null) return null;
            int dir = clamp(direction, 0, 3);
            int colCount = (rescuerFrames[dir] != null) ? rescuerFrames[dir].length : 0;
            int cf = (colCount > 0) ? (currentFrame % colCount) : 0;
            return rescuerFrames[dir][cf];
        }
    }

    /** فریم جاری اسکیل‌شده به اندازهٔ tileSize (با کش داخلی و تفکیک حالت) */
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
        if (ambulanceMode) {
            // آمبولانس انیمیشن قدم‌زدن ندارد؛ یک فریم ثابت در هر جهت
            return;
        }
        if (rescuerFrames == null) return;
        int dir = clamp(direction, 0, 3);
        int colCount = (rescuerFrames[dir] != null) ? rescuerFrames[dir].length : 0;
        if (colCount > 0) currentFrame = (currentFrame + 1) % colCount;
    }

    /** ریست کردن انیمیشن (مثلاً هنگام تعویض حالت یا شروع/پایان حرکت) */
    public void resetAnim() { currentFrame = 0; }

    // -------------------- قلاب برای محدودسازی حرکت روی جاده --------------------
    /**
     * اگر true باشد، سیستم حرکت باید فقط روی سلول‌های «جاده» اجازه حرکت بدهد.
     * این متد را در MoveGuard/AgentController چک کن.
     */
    public boolean isRoadOnlyMode() {
        return ambulanceMode;
    }

    // -------------------- محاسبهٔ امن زمان اولیه --------------------
    /** تلاش می‌کند getInitialTimeLimit() را بخواند؛ اگر نبود، با شدت fallback می‌کند. */
    private int safeInitialTime(Injured v) {
        if (v == null) return 0;
        try {
            Method m = v.getClass().getMethod("getInitialTimeLimit");
            Object r = m.invoke(v);
            if (r instanceof Integer) return ((Integer) r).intValue();
        } catch (Throwable ignored) { }
        // fallback: بر اساس شدت
        try {
            InjurySeverity sev = v.getSeverity();
            if (sev == InjurySeverity.CRITICAL) return 60;
            if (sev == InjurySeverity.MEDIUM)   return 120;
            if (sev == InjurySeverity.LOW)      return 180;
        } catch (Throwable ignored2) { }
        return 0;
    }
}
