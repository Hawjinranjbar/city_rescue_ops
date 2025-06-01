package util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// --------------------
// لایه: Utility Layer
// --------------------
// این کلاس مسئول بارگذاری و نگهداری تصاویر (Assetها) برای بازیه
// مثلاً تصویر نجات‌دهنده، بیمارستان، خیابون و...
public class AssetLoader {

    private static final Map<String, BufferedImage> assets = new HashMap<>();

    // بارگذاری تصویر از مسیر مشخص (فقط یک‌بار)
    public static BufferedImage loadImage(String path) {
        if (assets.containsKey(path)) {
            return assets.get(path);
        }

        try (InputStream is = AssetLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("تصویر پیدا نشد: " + path);
                return null;
            }
            BufferedImage img = ImageIO.read(is);
            assets.put(path, img);
            return img;
        } catch (IOException e) {
            System.err.println("خطا در لود تصویر: " + path + " | " + e.getMessage());
            return null;
        }
    }

    // گرفتن تصویر از cache اگه قبلاً لود شده باشه
    public static BufferedImage get(String path) {
        return assets.get(path);
    }

    // پاک کردن همه Assetها (مثلاً موقع بارگذاری مرحله جدید)
    public static void clear() {
        assets.clear();
    }
}
