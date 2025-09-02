// util/AssetLoader.java
package util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * Loader ساده و سریع برای تصاویر و شیت‌های تایل.
 * - اگر مسیر با "/" شروع شود از classpath خوانده می‌شود.
 * - در غیر این صورت از فایل‌سیستم خوانده می‌شود.
 * - از cache استفاده می‌شود تا لود تکراری نشود.
 *
 * افزونه‌ها:
 * - ابزارهای گرافیکی عمومی (شفاف‌سازی رنگ، کراپ حاشیه، وارونه‌سازی، اسکیل Nearest/Bilinear)
 * - بهبود exists و چندین مسیر جایگزین
 * - متدهای کمکی slice بیشتر
 */
public final class AssetLoader {

    /** Cache با محدودیت اندازه برای جلوگیری از مصرف بیش‌ازحد حافظه. LRU ساده. */
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Map<String, BufferedImage> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    private AssetLoader() { /* no-op */ }

    // =========================================================
    // لود تصویر (classpath یا فایل‌سیستم) + کش کردن
    // =========================================================
    /**
     * تلاش به ترتیب:
     * 1) classpath با مسیر داده‌شده (با/بدون اسلش آغازین)
     * 2) فایل‌سیستم نسبت به working dir
     */
    public static BufferedImage loadImage(String path) {
        if (path == null || path.length() == 0) {
            System.err.println("[AssetLoader] مسیر خالی است.");
            return null;
        }

        BufferedImage hit = CACHE.get(path);
        if (hit != null) return hit;

        BufferedImage img = null;

        // 1) classpath (با / و بدون /)
        try {
            URL url = AssetLoader.class.getResource(path.startsWith("/") ? path : "/" + path);
            if (url == null) {
                url = AssetLoader.class.getClassLoader().getResource(path);
            }
            if (url != null) {
                img = ImageIO.read(url);
            }
        } catch (IOException e) {
            System.err.println("[AssetLoader] خطا در classpath: " + path + " | " + e.getMessage());
        }

        // 2) فایل‌سیستم
        if (img == null) {
            try {
                File f = new File(path);
                if (f.exists()) img = ImageIO.read(f);
            } catch (IOException e) {
                System.err.println("[AssetLoader] خطا در فایل‌سیستم: " + path + " | " + e.getMessage());
            }
        }

        if (img == null) {
            System.err.println("[AssetLoader] تصویر پیدا نشد: " + path);
            return null;
        }

        // اطمینان از فرمت ARGB برای کار راحت با آلفا
        BufferedImage argb = ensureARGB(img);
        CACHE.put(path, argb);
        return argb;
    }

    /** همان loadImage اما اگر پیدا نشود، Exception می‌دهد. */
    public static BufferedImage requireImage(String path) {
        BufferedImage img = loadImage(path);
        if (img == null) throw new IllegalArgumentException("Asset not found: " + path);
        return img;
    }

    /** تلاش با چند مسیر؛ اگر همه شکست خورد، fallbackPath را لود می‌کند (می‌تواند null باشد). */
    public static BufferedImage loadOrDefault(String fallbackPath, String... candidates) {
        if (candidates != null) {
            for (int i = 0; i < candidates.length; i++) {
                BufferedImage img = loadImage(candidates[i]);
                if (img != null) return img;
            }
        }
        if (fallbackPath == null) return null;
        return loadImage(fallbackPath);
    }

    // =========================================================
    // Scale با حفظ شفافیت + کش
    // =========================================================
    public static BufferedImage loadScaled(String path, int width, int height) {
        String key = path + "#NN#" + width + "x" + height;
        BufferedImage hit = CACHE.get(key);
        if (hit != null) return hit;

        BufferedImage src = requireImage(path);
        BufferedImage dst = scaleNearest(src, width, height);
        CACHE.put(key, dst);
        return dst;
    }

    /** اسکیل نرم (Bilinear)؛ برای آیکون‌های غیرپیکسلی مناسب‌تر از nearest است. */
    public static BufferedImage loadScaledSmooth(String path, int width, int height) {
        String key = path + "#BL#" + width + "x" + height;
        BufferedImage hit = CACHE.get(key);
        if (hit != null) return hit;

        BufferedImage src = requireImage(path);
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();
        CACHE.put(key, dst);
        return dst;
    }

    // =========================================================
    // برش شیت به گرید/ردیف
    // =========================================================
    public static BufferedImage[][] sliceGrid(BufferedImage sheet, int frameW, int frameH) {
        if (sheet == null) throw new IllegalArgumentException("sheet is null");
        int cols = sheet.getWidth() / frameW;
        int rows = sheet.getHeight() / frameH;
        BufferedImage[][] grid = new BufferedImage[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = sheet.getSubimage(c * frameW, r * frameH, frameW, frameH);
            }
        }
        return grid;
    }

    public static BufferedImage[] sliceRow(BufferedImage sheet, int rowIndex, int frameW, int frameH, int frames) {
        if (sheet == null) throw new IllegalArgumentException("sheet is null");
        BufferedImage[] arr = new BufferedImage[frames];
        for (int c = 0; c < frames; c++) {
            arr[c] = sheet.getSubimage(c * frameW, rowIndex * frameH, frameW, frameH);
        }
        return arr;
    }

    /** برش نواری (Strip) از ستون/ردیف مشخص با شروع و طول مشخص. */
    public static BufferedImage[] sliceStrip(BufferedImage sheet, int startX, int startY, int frameW, int frameH, int count, boolean horizontal) {
        if (sheet == null) throw new IllegalArgumentException("sheet is null");
        BufferedImage[] out = new BufferedImage[count];
        for (int i = 0; i < count; i++) {
            int x = startX + (horizontal ? i * frameW : 0);
            int y = startY + (horizontal ? 0 : i * frameH);
            out[i] = sheet.getSubimage(x, y, frameW, frameH);
        }
        return out;
    }

    // -----------------------------------------------------------
    // ابزارهای مخصوص Tiled: margin/spacing و گرفتن تایل بر اساس GID
    // -----------------------------------------------------------
    public static BufferedImage[][] sliceGridSpaced(
            BufferedImage sheet,
            int tileW, int tileH,
            int margin, int spacing,
            int rows, int cols
    ) {
        if (sheet == null) throw new IllegalArgumentException("sheet is null");

        if (cols <= 0) {
            cols = (sheet.getWidth() - 2 * margin + spacing) / (tileW + spacing);
        }
        if (rows <= 0) {
            rows = (sheet.getHeight() - 2 * margin + spacing) / (tileH + spacing);
        }

        BufferedImage[][] grid = new BufferedImage[rows][cols];
        int y = margin;
        for (int r = 0; r < rows; r++) {
            int x = margin;
            for (int c = 0; c < cols; c++) {
                grid[r][c] = sheet.getSubimage(x, y, tileW, tileH);
                x += tileW + spacing;
            }
            y += tileH + spacing;
        }
        return grid;
    }

    public static BufferedImage getTileFromSheetByGid(
            BufferedImage sheet,
            int tileW, int tileH,
            int margin, int spacing,
            int firstGid, int gid
    ) {
        if (sheet == null) throw new IllegalArgumentException("sheet is null");
        if (gid < firstGid) return null; // مربوط به این tileset نیست
        int localId = gid - firstGid;     // از 0 شروع می‌شود

        int cols = (sheet.getWidth() - 2 * margin + spacing) / (tileW + spacing);
        int row = localId / cols;
        int col = localId % cols;

        int x = margin + col * (tileW + spacing);
        int y = margin + row * (tileH + spacing);

        if (x + tileW > sheet.getWidth() || y + tileH > sheet.getHeight()) {
            return null;
        }
        return sheet.getSubimage(x, y, tileW, tileH);
    }

    public static Map<Integer, BufferedImage> buildTilesetIndex(
            BufferedImage sheet,
            int tileW, int tileH,
            int margin, int spacing
    ) {
        BufferedImage[][] grid = sliceGridSpaced(sheet, tileW, tileH, margin, spacing, -1, -1);
        Map<Integer, BufferedImage> map = new LinkedHashMap<Integer, BufferedImage>();
        int id = 0;
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[0].length; c++) {
                map.put(id++, grid[r][c]);
            }
        }
        return map;
    }

    // =========================================================
    // ابزارهای عمومی گرافیک (برای استفاده مجدد در Rescuer/Vehicle/...)
    // =========================================================
    /** اسکیل Nearest-Neighbor به ARGB. */
    public static BufferedImage scaleNearest(BufferedImage src, int width, int height) {
        if (src == null) return null;
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();
        return dst;
    }

    /** حذف رنگ پس‌زمینه با تلورانس (alpha=0). */
    public static BufferedImage makeColorTransparent(BufferedImage src, int rgb, int tolerance) {
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

    /** کراپ حاشیهٔ یک‌دستِ پس‌زمینه دور کل تصویر. */
    public static BufferedImage cropToContent(BufferedImage img, int bg, int tol) {
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
        return img;
    }

    /** وارونه‌سازی افقی. */
    public static BufferedImage flipHorizontal(BufferedImage src) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, src.getWidth(), src.getHeight(),
                src.getWidth(), 0, 0, src.getHeight(), null);
        g.dispose();
        return out;
    }

    /** وارونه‌سازی عمودی. */
    public static BufferedImage flipVertical(BufferedImage src) {
        if (src == null) return null;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, src.getWidth(), src.getHeight(),
                0, src.getHeight(), src.getWidth(), 0, null);
        g.dispose();
        return out;
    }

    /** اطمینان از اینکه تصویر ARGB است. */
    public static BufferedImage ensureARGB(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return out;
    }

    // =========================================================
    // ابزارهای عمومی
    // =========================================================
    public static void preload(String... paths) {
        if (paths == null) return;
        for (int i = 0; i < paths.length; i++) {
            loadImage(paths[i]);
        }
    }

    public static boolean exists(String path) {
        if (path == null || path.length() == 0) return false;
        if (CACHE.containsKey(path)) return true;

        URL url = AssetLoader.class.getResource(path.startsWith("/") ? path : "/" + path);
        if (url != null) return true;

        URL url2 = AssetLoader.class.getClassLoader().getResource(path);
        if (url2 != null) return true;

        return new File(path).exists();
    }

    public static BufferedImage get(String path) {
        return CACHE.get(path);
    }

    /** پاک‌کردن همهٔ کش. */
    public static void clear() {
        CACHE.clear();
    }

    /** پاک‌کردن ورودی‌هایی که با prefix کلیدشان شروع می‌شود (مثلاً مسیر یک دایرکتوری خاص). */
    public static void clearByPrefix(String prefix) {
        if (prefix == null) return;
        synchronized (CACHE) {
            Map<String, BufferedImage> copy = new LinkedHashMap<String, BufferedImage>(CACHE);
            for (Map.Entry<String, BufferedImage> e : copy.entrySet()) {
                if (e.getKey() != null && e.getKey().startsWith(prefix)) {
                    CACHE.remove(e.getKey());
                }
            }
        }
    }

    // -------------------- هِلپرهای داخلی --------------------
    private static boolean isClose(int c1, int c2, int tol) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = (c1) & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = (c2) & 0xFF;
        return Math.abs(r1 - r2) <= tol && Math.abs(g1 - g2) <= tol && Math.abs(b1 - b2) <= tol;
    }

    private static boolean isNearlyWhite(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = (c) & 0xFF;
        return r >= 245 && g >= 245 && b >= 245;
    }
}
