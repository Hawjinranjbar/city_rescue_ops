package util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
 * نکات:
 *  - اگر مسیر با "/" شروع شود از classpath خوانده می‌شود.
 *  - در غیر این صورت از فایل‌سیستم خوانده می‌شود.
 *  - از cache استفاده می‌شود تا لود تکراری نشود.
 */
public final class AssetLoader {

    /** Cache با محدودیت اندازه برای جلوگیری از مصرف بیش‌ازحد حافظه. */
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Map<String, BufferedImage> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    private AssetLoader() { /* no-op */ }

    // ---------------------------------------------
    // لود تصویر (classpath یا فایل‌سیستم) + کش کردن
    // ---------------------------------------------
    public static BufferedImage loadImage(String path) {
        if (path == null || path.isEmpty()) {
            System.err.println("[AssetLoader] مسیر خالی است.");
            return null;
        }
        BufferedImage hit = CACHE.get(path);
        if (hit != null) return hit;

        BufferedImage img = null;
        // 1) classpath
        try {
            URL url = AssetLoader.class.getResource(path.startsWith("/") ? path : "/" + path);
            if (url != null) img = ImageIO.read(url);
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
        CACHE.put(path, img);
        return img;
    }

    /** همان loadImage اما اگر پیدا نشود، Exception می‌دهد. */
    public static BufferedImage requireImage(String path) {
        BufferedImage img = loadImage(path);
        if (img == null) throw new IllegalArgumentException("Asset not found: " + path);
        return img;
    }

    // -------------------------
    // Scale با حفظ کش
    // -------------------------
    public static BufferedImage loadScaled(String path, int width, int height) {
        String key = path + "#" + width + "x" + height;
        BufferedImage hit = CACHE.get(key);
        if (hit != null) return hit;

        BufferedImage src = requireImage(path);
        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = dst.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.drawImage(src, 0, 0, width, height, null);
        g2.dispose();

        CACHE.put(key, dst);
        return dst;
    }

    // -------------------------
    // برش شیت به گرید ساده
    // -------------------------
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

    /** برش یک ردیف مشخص از شیت (برای انیمیشن یک جهت). */
    public static BufferedImage[] sliceRow(BufferedImage sheet, int rowIndex, int frameW, int frameH, int frames) {
        if (sheet == null) throw new IllegalArgumentException("sheet is null");
        BufferedImage[] arr = new BufferedImage[frames];
        for (int c = 0; c < frames; c++) {
            arr[c] = sheet.getSubimage(c * frameW, rowIndex * frameH, frameW, frameH);
        }
        return arr;
    }

    // -----------------------------------------------------------
    // ابزارهای مخصوص Tiled: margin/spacing و گرفتن تایل بر اساس GID
    // -----------------------------------------------------------

    /**
     * برش گرید با درنظر گرفتن margin و spacing مطابق فرمت Tiled.
     * اگر rows/cols منفی باشند، از روی اندازهٔ تصویر محاسبه می‌شوند.
     */
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

    /**
     * گرفتن یک تایل از شیت بر اساس GID (مثل Tiled).
     * @param firstGid  اولین شناسهٔ این tileset در TMX
     * @param gid       شناسهٔ سراسری تایل در لایه
     */
    public static BufferedImage getTileFromSheetByGid(
            BufferedImage sheet,
            int tileW, int tileH,
            int margin, int spacing,
            int firstGid, int gid
    ) {
        if (gid < firstGid) return null; // مربوط به این tileset نیست
        int localId = gid - firstGid;     // از 0 شروع می‌شود

        int cols = (sheet.getWidth() - 2 * margin + spacing) / (tileW + spacing);
        int row = localId / cols;
        int col = localId % cols;

        int x = margin + col * (tileW + spacing);
        int y = margin + row * (tileH + spacing);

        if (x + tileW > sheet.getWidth() || y + tileH > sheet.getHeight()) {
            // خارج از محدوده (مثلاً gid نامعتبر)
            return null;
        }
        return sheet.getSubimage(x, y, tileW, tileH);
    }

    /**
     * ساخت ایندکس از همهٔ تایل‌های یک شیت (کلید: localId از 0).
     * برای سرعت در رندر لایه‌های TMX.
     */
    public static Map<Integer, BufferedImage> buildTilesetIndex(
            BufferedImage sheet,
            int tileW, int tileH,
            int margin, int spacing
    ) {
        BufferedImage[][] grid = sliceGridSpaced(sheet, tileW, tileH, margin, spacing, -1, -1);
        Map<Integer, BufferedImage> map = new LinkedHashMap<>();
        int id = 0;
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid[0].length; c++) {
                map.put(id++, grid[r][c]);
            }
        }
        return map;
    }

    // -------------------------
    // ابزارهای عمومی
    // -------------------------
    public static void preload(String... paths) {
        if (paths == null) return;
        for (String p : paths) loadImage(p);
    }

    public static boolean exists(String path) {
        if (CACHE.containsKey(path)) return true;
        URL url = AssetLoader.class.getResource(path.startsWith("/") ? path : "/" + path);
        if (url != null) return true;
        return new File(path).exists();
    }

    public static BufferedImage get(String path) {
        return CACHE.get(path);
    }

    public static void clear() {
        CACHE.clear();
    }
}
