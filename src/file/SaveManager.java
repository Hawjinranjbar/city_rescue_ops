package file;

import java.io.*;

/**
 * --------------------
 * لایه: File I/O Layer
 * --------------------
 * ذخیره/بارگذاری GameState + Quick Save/Load + Restart با اسنپ‌شات عمیق.
 * - بدون لامبدا
 * - سازگار با ThreadFreezeHook (اختیاری)
 *
 * نکته‌ی مهم: برای Restart، حتماً از deep-copy استفاده می‌کنیم
 * تا تغییرات حین بازی روی initialState اثر نگذارد.
 */
public class SaveManager {

    private final String filePath;

    /** مسیر پیش‌فرض quick save */
    public static final String QUICK_SAVE_PATH = "saves/quick.sav";

    /** اسنپ‌شات اولیه برای ری‌استارت (به‌صورت کپی عمیق نگهداری می‌شود) */
    private static GameState initialState;

    /** قلاب‌های pause/resume برای Threadها (اختیاری) */
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

    /**
     * ثبت وضعیت اولیه برای Restart.
     * ⚠️ با کپیِ عمیق ذخیره می‌شود تا بعداً آلوده نشود.
     */
    public static void setInitialState(GameState state) {
        initialState = deepCopy(state);
    }

    /**
     * دریافت اسنپ‌شات اولیه.
     * ⚠️ یک کپیِ عمیق برمی‌گردانیم تا دست‌کاری نشود.
     */
    public static GameState getInitialState() {
        return deepCopy(initialState);
    }

    // ------------------------
    // ذخیره در مسیر پیش‌فرض شیء
    // ------------------------
    public boolean saveGame(GameState gameState) {
        return saveGameToPath(gameState, this.filePath);
    }

    // ------------------------
    // ذخیره در مسیر مشخص
    // ------------------------
    public static boolean saveGameToPath(GameState gameState, String path) {
        ensureDir(path);
        if (freezeHook != null) {
            try { freezeHook.pauseAll(); } catch (Throwable ignored) {}
        }

        ObjectOutputStream oos = null;
        try {
            FileOutputStream fos = new FileOutputStream(path);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(gameState);
            oos.flush();
            System.out.println("بازی با موفقیت ذخیره شد: " + path);
            return true;
        } catch (IOException e) {
            System.err.println("خطا در ذخیره بازی: " + e.getMessage());
            return false;
        } finally {
            if (oos != null) { try { oos.close(); } catch (Exception ignore) {} }
            if (freezeHook != null) {
                try { freezeHook.resumeAll(); } catch (Throwable ignored) {}
            }
        }
    }

    // ------------------------
    // بارگذاری از مسیر مشخص
    // ------------------------
    public static GameState loadGame(String path) {
        // وجود فایل را چک کن
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            System.err.println("فایل ذخیره یافت نشد: " + path);
            return null;
        }

        if (freezeHook != null) {
            try { freezeHook.pauseAll(); } catch (Throwable ignored) {}
        }

        ObjectInputStream ois = null;
        try {
            FileInputStream fis = new FileInputStream(path);
            ois = new ObjectInputStream(fis);
            Object obj = ois.readObject();
            if (obj instanceof GameState) {
                System.out.println("بازی با موفقیت بارگذاری شد: " + path);
                return (GameState) obj;
            } else {
                System.err.println("فایل معتبر نیست (نوع داده نادرست).");
                return null;
            }
        } catch (Exception e) {
            System.err.println("خطا در بارگذاری بازی: " + e.getMessage());
            return null;
        } finally {
            if (ois != null) { try { ois.close(); } catch (Exception ignore) {} }
            if (freezeHook != null) {
                try { freezeHook.resumeAll(); } catch (Throwable ignored) {}
            }
        }
    }

    // ------------------------
    // Quick Save / Load
    // ------------------------
    public static boolean quickSave(GameState state) {
        return saveGameToPath(state, QUICK_SAVE_PATH);
    }

    public static GameState quickLoad() {
        return loadGame(QUICK_SAVE_PATH);
    }

    // ------------------------
    // ری‌استارت بازی (برگرداندن اسنپ‌شات اولیه)
    // ------------------------
    public static GameState restartGame() {
        return getInitialState(); // کپیِ عمیق
    }

    // ------------------------
    // ابزارها
    // ------------------------
    /** ساخت پوشه‌ی مقصد در صورت نبودن */
    private static void ensureDir(String path) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean ok = parent.mkdirs();
                if (!ok) {
                    System.err.println("عدم موفقیت در ساخت پوشه: " + parent.getAbsolutePath());
                }
            }
        } catch (Throwable ignored) {}
    }

    /**
     * کپیِ عمیق با Serialization (ساده، بدون وابستگی خارجی).
     * برای کارکرد درست: GameState و تمام اشیای درونش باید Serializable باشند.
     */
    private static GameState deepCopy(GameState original) {
        if (original == null) return null;
        ByteArrayOutputStream bos = null;
        ObjectOutputStream oos = null;
        ByteArrayInputStream bis = null;
        ObjectInputStream ois = null;
        try {
            bos = new ByteArrayOutputStream();
            oos = new ObjectOutputStream(bos);
            oos.writeObject(original);
            oos.flush();

            bis = new ByteArrayInputStream(bos.toByteArray());
            ois = new ObjectInputStream(bis);
            Object obj = ois.readObject();
            if (obj instanceof GameState) {
                return (GameState) obj;
            }
        } catch (Exception e) {
            System.err.println("deepCopy خطا: " + e.getMessage());
        } finally {
            if (oos != null) { try { oos.close(); } catch (Exception ignore) {} }
            if (bos != null) { try { bos.close(); } catch (Exception ignore) {} }
            if (ois != null) { try { ois.close(); } catch (Exception ignore) {} }
            if (bis != null) { try { bis.close(); } catch (Exception ignore) {} }
        }
        // اگر کپی عمیق نشد، حداقل ارجاع را برگردان—but discouraged
        return original;
    }
}
