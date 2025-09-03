package file;

import java.io.*;

/**
 * --------------------
 * لایه: File I/O Layer
 * --------------------
 * این کلاس وضعیت بازی (GameState) رو در فایل ذخیره/لود می‌کنه
 * و قابلیت quick save/load و restart رو هم فراهم می‌کنه.
 */
public class SaveManager {

    private final String filePath;

    // مسیر پیش‌فرض quick save
    public static final String QUICK_SAVE_PATH = "saves/quick.sav";

    // نگهداری state اولیه برای ری‌استارت
    private static GameState initialState;

    // قلاب‌های pause/resume برای threadها
    public interface ThreadFreezeHook {
        void pauseAll();
        void resumeAll();
    }

    private static ThreadFreezeHook freezeHook;

    public SaveManager(String filePath) {
        this.filePath = filePath;
    }

    // ------------------------
    // ثبت قلاب‌ها
    // ------------------------
    public static void setThreadFreezeHook(ThreadFreezeHook hook) {
        freezeHook = hook;
    }

    public static void setInitialState(GameState state) {
        initialState = state;
    }

    public static GameState getInitialState() {
        return initialState;
    }

    // ------------------------
    // ذخیره در مسیر پیش‌فرضِ این شیء
    // ------------------------
    public void saveGame(GameState gameState) {
        saveGameToPath(gameState, this.filePath);
    }

    // ------------------------
    // ذخیره در مسیر مشخص
    // ------------------------
    public static void saveGameToPath(GameState gameState, String path) {
        ensureDir(path);

        if (freezeHook != null) freezeHook.pauseAll();
        ObjectOutputStream oos = null;
        try {
            FileOutputStream fos = new FileOutputStream(path);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(gameState);
            oos.flush();
            System.out.println("بازی با موفقیت ذخیره شد: " + path);
        } catch (IOException e) {
            System.err.println("خطا در ذخیره بازی: " + e.getMessage());
        } finally {
            if (oos != null) {
                try { oos.close(); } catch (Exception ignore) {}
            }
            if (freezeHook != null) freezeHook.resumeAll();
        }
    }

    // ------------------------
    // بارگذاری از مسیر مشخص
    // ------------------------
    public static GameState loadGame(String path) {
        if (freezeHook != null) freezeHook.pauseAll();
        ObjectInputStream ois = null;
        try {
            FileInputStream fis = new FileInputStream(path);
            ois = new ObjectInputStream(fis);
            Object obj = ois.readObject();
            if (obj instanceof GameState) {
                System.out.println("بازی با موفقیت بارگذاری شد: " + path);
                return (GameState) obj;
            } else {
                System.err.println("فایل معتبر نیست.");
                return null;
            }
        } catch (Exception e) {
            System.err.println("خطا در بارگذاری بازی: " + e.getMessage());
            return null;
        } finally {
            if (ois != null) {
                try { ois.close(); } catch (Exception ignore) {}
            }
            if (freezeHook != null) freezeHook.resumeAll();
        }
    }

    // ------------------------
    // Quick Save / Load
    // ------------------------
    public static void quickSave(GameState state) {
        saveGameToPath(state, QUICK_SAVE_PATH);
    }

    public static GameState quickLoad() {
        return loadGame(QUICK_SAVE_PATH);
    }

    // ------------------------
    // ری‌استارت بازی
    // ------------------------
    public static GameState restartGame() {
        return initialState;
    }

    // ------------------------
    // ابزار: ساخت پوشه اگر نبود
    // ------------------------
    private static void ensureDir(String path) {
        File f = new File(path).getParentFile();
        if (f != null && !f.exists()) {
            f.mkdirs();
        }
    }
}
