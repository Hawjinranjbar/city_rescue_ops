package agent;

import util.AssetLoader;
import util.Position;
import victim.Injured;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;

/**
 * --------------------
 * لایه: Domain Layer — package agent
 * --------------------
 * مدل نجات‌دهنده + مدیریت اسپرایت/انیمیشن.
 * مختصات ریسکیور تایل‌محور است (x,y به واحد تایل).
 */
public class Rescuer {

    // ====== وضعیت منطقی عامل ======
    private final int id;                 // شناسه یکتا
    private Position position;            // موقعیت به واحد تایل
    private boolean isBusy;               // آیا درگیر عملیات است؟
    private Injured carryingVictim;       // مجروحِ در حال حمل (در صورت وجود)

    // ====== گرافیک/انیمیشن ======
    // frames[direction][frame]  →  direction: 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
    private BufferedImage[][] frames;
    private int direction = 0;            // 0=پایین، 1=چپ، 2=راست، 3=بالا
    private int currentFrame = 0;         // ستون فریم جاری

    // کش فریم‌های اسکیل‌شده برحسب tileSize
    private final Map<Integer, BufferedImage[][]> scaledCache = new HashMap<>();

    // پیکربندی شیت (ستون/ردیف)
    private static final int COLS = 4;    // تعداد ستون‌ها
    private static final int ROWS = 3;    // تعداد ردیف‌های واقعی شیت (down/left/right) ؛ up نداریم

    // ابعاد واقعی هر فریم (پس از کراپ)
    private int FRAME_W = 64;
    private int FRAME_H = 64;

    // مسیر اسپرایت‌شیت (مطابق پوشه assets پروژه)
    private static final String SPRITE_PATH = "assets/characters/rescuer.png";

    // -------------------- سازنده --------------------
    public Rescuer(int id, Position startPos) {
        this.id = id;
        this.position = (startPos != null) ? startPos : new Position(0, 0);
        this.isBusy = false;
        this.carryingVictim = null;
        loadSpriteSheet();
    }

    // -------------------- بارگذاری اسپرایت‌شیت و شفاف‌سازی --------------------
    private void loadSpriteSheet() {
        BufferedImage sheet = AssetLoader.loadImage(SPRITE_PATH);
        if (sheet == null) {
            frames = new BufferedImage[4][COLS];
            System.err.println("[Rescuer] Sprite sheet NOT found: " + SPRITE_PATH);
            return;
        }

        // 1) رنگ پس‌زمینه و کراپ حاشیه‌های یکدست
        int bg = sheet.getRGB(0, 0);
        BufferedImage cropped = cropToContent(sheet, bg, 20);
        if (cropped == null) cropped = sheet;

        // 2) محاسبهٔ اندازهٔ فریم بر اساس تعداد ستون/ردیف تعریف‌شده
        FRAME_W = Math.max(1, cropped.getWidth() / COLS);
        FRAME_H = Math.max(1, cropped.getHeight() / ROWS);
        // System.out.println("[Rescuer] frame size: " + FRAME_W + "x" + FRAME_H);

        // 3) برش و شفاف‌سازی هر فریم
        frames = new BufferedImage[4][COLS]; // 4 جهت (UP بعداً پر می‌شود)
        int tolerance = 36; // تلورانس برای حذف هاله سفید

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int sx = c * FRAME_W;
                int sy = r * FRAME_H;
                if (sx + FRAME_W <= cropped.getWidth() && sy + FRAME_H <= cropped.getHeight()) {
                    BufferedImage raw = cropped.getSubimage(sx, sy, FRAME_W, FRAME_H);
                    BufferedImage clear = makeColorTransparent(raw, bg, tolerance);
                    frames[r][c] = clear; // r: 0=DOWN,1=LEFT,2=RIGHT
                }
            }
        }

        // ساخت فریم‌های جهت چپ با وارونه‌سازی افقی فریم‌های راست
        BufferedImage[] leftRow = new BufferedImage[COLS];
        for (int c = 0; c < COLS; c++) {
            if (frames[2][c] != null) {
                leftRow[c] = flipHorizontal(frames[2][c]);
            }
        }
        frames[1] = leftRow;

        // اگر ردیف UP در شیت وجود ندارد، از ردیف DOWN کپی کن
        if (frames[3] == null || frames[3][0] == null) {
            BufferedImage[] upRow = new BufferedImage[COLS];
            for (int c = 0; c < COLS; c++) {
                upRow[c] = frames[0][c]; // UP = DOWN
            }
            frames[3] = upRow;
        }

        // هر بار که فریم‌ها عوض می‌شوند، کش اسکیل را پاک کن
        scaledCache.clear();
    }

    // پیکسل‌های نزدیک به رنگِ پس‌زمینه یا سفید را شفاف می‌کند (alpha = 0)
    private static BufferedImage makeColorTransparent(BufferedImage src, int rgb, int tolerance) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int p = src.getRGB(x, y);
                if (isClose(p, rgb, tolerance) || isNearlyWhite(p)) {
                    out.setRGB(x, y, 0x00000000); // شفاف کامل
                } else {
                    out.setRGB(x, y, p);
                }
            }
        }
        return out;
    }

    private static boolean isClose(int c1, int c2, int tol) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = (c1) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = (c2) & 0xFF;
        return Math.abs(r1 - r2) <= tol && Math.abs(g1 - g2) <= tol && Math.abs(b1 - b2) <= tol;
    }

    private static boolean isNearlyWhite(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = (c) & 0xFF;
        return r >= 245 && g >= 245 && b >= 245; // سفید یا بسیار نزدیک به سفید
    }

    // کراپ‌کردن حاشیهٔ یکپارچهٔ پس‌زمینه دور کل شیت
    private static BufferedImage cropToContent(BufferedImage img, int bg, int tol) {
        int minX = img.getWidth(), minY = img.getHeight();
        int maxX = -1, maxY = -1;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if (!(isClose(p, bg, tol) || isNearlyWhite(p))) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX >= minX && maxY >= minY) {
            return img.getSubimage(minX, minY, (maxX - minX + 1), (maxY - minY + 1));
        }
        return null; // یعنی کل تصویر پس‌زمینه بوده
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

    public boolean isBusy() { return isBusy; }

    public void pickUp(Injured victim) {
        if (!isBusy && victim != null) {
            this.carryingVictim = victim;
            this.isBusy = true;
        }
    }

    public void dropVictim() {
        if (carryingVictim != null) {
            carryingVictim.markAsRescued();
            carryingVictim = null;
            isBusy = false;
        }
    }

    public Injured getCarryingVictim() { return carryingVictim; }
    public boolean isCarryingVictim() { return carryingVictim != null; }

    // -------------------- API گرافیک/انیمیشن --------------------
    public BufferedImage getSprite() {
        if (frames == null) return null;
        int dir = clamp(direction, 0, 3);
        int colCount = (frames[dir] != null) ? frames[dir].length : 0;
        int cf = (colCount > 0) ? (currentFrame % colCount) : 0;
        return frames[dir][cf];
    }

    /** فریم جاری را به اندازهٔ tileSize اسکیل می‌کند (با کش داخلی). */
    public BufferedImage getSpriteScaled(int tileSize) {
        if (frames == null) return null;
        if (tileSize <= 0) return getSprite();

        BufferedImage[][] grid = scaledCache.get(tileSize);
        if (grid == null) {
            int rows = frames.length;
            int cols = frames[0].length;
            grid = new BufferedImage[rows][cols];
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (frames[r] != null && frames[r][c] != null) {
                        grid[r][c] = scaleNearest(frames[r][c], tileSize, tileSize);
                    }
                }
            }
            scaledCache.put(tileSize, grid);
        }

        int dir = clamp(direction, 0, 3);
        int colCount = (grid[dir] != null) ? grid[dir].length : 0;
        int cf = (colCount > 0) ? (currentFrame % colCount) : 0;
        return grid[dir][cf];
    }

    private static BufferedImage scaleNearest(BufferedImage src, int w, int h) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.drawImage(src, 0, 0, w, h, null);
        g2.dispose();
        return dst;
    }

    /** وارونه‌سازی افقی تصویر برای ساخت فریم‌های جهت چپ از راست */
    private static BufferedImage flipHorizontal(BufferedImage src) {
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, w, h, w, 0, 0, h, null);
        g.dispose();
        return out;
    }

    public void setDirection(int dir) { this.direction = clamp(dir, 0, 3); }
    public int getDirection() { return direction; }

    public void nextFrame() {
        if (frames == null) return;
        int dir = clamp(direction, 0, 3);
        int colCount = (frames[dir] != null) ? frames[dir].length : 0;
        if (colCount > 0) currentFrame = (currentFrame + 1) % colCount;
    }

    /** ریست کردن انیمیشن (مثلاً هنگام تعویض جهت یا شروع/پایان حرکت) */
    public void resetAnim() { currentFrame = 0; }

    private static int clamp(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
