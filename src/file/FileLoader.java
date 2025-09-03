package file;

import java.io.Closeable;

/**
 * --------------------
 * لایه: File I/O Layer
 * --------------------
 * Loader سبک که بارگذاری را به SaveManager می‌سپارد.
 * نه به فیلدهای private دست می‌زند و نه IO را دوباره تکرار می‌کند.
 */
public class FileLoader implements Closeable {

    private final String filePath;

    public FileLoader(String filePath) {
        this.filePath = filePath;
    }

    /** بارگذاری وضعیت بازی از مسیر سازنده. */
    public GameState loadGame() {
        return SaveManager.loadGame(this.filePath);
    }

    /** بارگذاری از مسیر دلخواه (استاتیک). */
    public static GameState load(String path) {
        return SaveManager.loadGame(path);
    }

    /** بارگذاری سریع از مسیر پیش‌فرض SaveManager. */
    public static GameState quickLoad() {
        return SaveManager.quickLoad();
    }

    /** بازگرداندن وضعیت اولیه برای ریستارت. */
    public static GameState restartLoad() {
        return SaveManager.getInitialState();
    }

    @Override
    public void close() {
        // منبعی برای بستن نداریم؛ فقط برای سازگاری با try-with-resources
    }
}
