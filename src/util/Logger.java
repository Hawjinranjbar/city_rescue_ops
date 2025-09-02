package util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * Logger نخ‌امن برای ثبت رویدادهای بازی.
 * ویژگی‌ها:
 *  - زمان‌زدن و نام‌تاپیک (Thread) در هر لاگ
 *  - سطح لاگ: INFO/WARN/ERROR
 *  - ساخت خودکار پوشهٔ مقصد فایل
 *  - هِلپرهای اختصاصی برای مکانیک آمبولانس/نجات/جریمه/پاداش
 */
public class Logger {

    // ---- سطح لاگ ----
    public static final String INFO  = "INFO";
    public static final String WARN  = "WARN";
    public static final String ERROR = "ERROR";

    private final String logFilePath;
    private boolean toConsole;
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // سازنده: مسیر فایل لاگ و اینکه خروجی روی کنسول هم چاپ بشه یا نه
    public Logger(String filePath, boolean toConsole) {
        if (filePath == null || filePath.trim().length() == 0) {
            filePath = "logs/game.log";
        }
        this.logFilePath = filePath.replace('\\', '/');
        this.toConsole = toConsole;
        ensureParentDirExists(this.logFilePath);
    }

    /** تغییر آنی نمایش روی کنسول */
    public synchronized void setConsoleEnabled(boolean enabled) {
        this.toConsole = enabled;
    }

    /** متد سادهٔ قبلی: سطح پیش‌فرض INFO */
    public synchronized void log(String message) {
        write(INFO, message);
    }

    /** متد با سطح دلخواه */
    public synchronized void log(String level, String message) {
        if (level == null) level = INFO;
        write(level, message);
    }

    /** ثبت خطا با استثناء */
    public synchronized void logError(String where, Exception e) {
        String msg = "[EXCEPTION] at " + safe(where) + " → " + (e != null ? e.getClass().getSimpleName() + ": " + safe(e.getMessage()) : "null");
        write(ERROR, msg);
    }

    /** شروع بازی/ماموریت */
    public synchronized void logGameStart(int mapWidth, int mapHeight, int hospitalsCount, int initialScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Game Start → Map=").append(mapWidth).append("x").append(mapHeight)
                .append(", Hospitals=").append(hospitalsCount)
                .append(", Score=").append(initialScore);
        write(INFO, sb.toString());
    }

    /** وقتی نجات‌دهنده به قربانی نزدیک شد و وارد حالت آمبولانس شد */
    public synchronized void logAmbulancePickup(int rescuerId, Position at, int victimId, String victimSeverity, int victimInitialTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ambulance PICKUP → rescuer#").append(rescuerId)
                .append(" at ").append(pos(at))
                .append(", victim#").append(victimId)
                .append(" sev=").append(safe(victimSeverity))
                .append(" t0=").append(victimInitialTime);
        write(INFO, sb.toString());
    }

    /** تحویل کنار بیمارستان و دریافت پاداش 2×t0 */
    public synchronized void logAmbulanceDeliver(int rescuerId, Position at, int victimId, int rewardAdded, int newScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Ambulance DELIVER → rescuer#").append(rescuerId)
                .append(" at ").append(pos(at))
                .append(", victim#").append(victimId)
                .append(", reward=+").append(rewardAdded)
                .append(", score=").append(newScore);
        write(INFO, sb.toString());
    }

    /** مرگ مجروح (اتمام تایمر) و اعمال جریمه 2×t0 */
    public synchronized void logVictimDeath(int victimId, String victimSeverity, int initialTime, int penaltyApplied, int newScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("Victim DEATH → victim#").append(victimId)
                .append(" sev=").append(safe(victimSeverity))
                .append(" t0=").append(initialTime)
                .append(", penalty=-").append(penaltyApplied)
                .append(", score=").append(newScore);
        write(WARN, sb.toString());
    }

    /** تغییر امتیاز با دلیل */
    public synchronized void logScoreChange(int before, int after, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("ScoreChange ").append(before).append(" → ").append(after)
                .append(" (").append(safe(reason)).append(")");
        write(INFO, sb.toString());
    }

    // ==================== پیاده‌سازی اصلی نوشتن ====================
    private synchronized void write(String level, String message) {
        String ts = LocalDateTime.now().format(fmt);
        String threadName = Thread.currentThread().getName();
        String line = "[" + ts + "][" + level + "][T:" + threadName + "] " + safe(message);

        if (toConsole) {
            System.out.println(line);
        }

        PrintWriter out = null;
        FileWriter fw = null;
        try {
            fw = new FileWriter(logFilePath, true);
            out = new PrintWriter(fw);
            out.println(line);
            out.flush();
        } catch (IOException e) {
            System.err.println("خطا در نوشتن لاگ: " + e.getMessage());
        } finally {
            if (out != null) try { out.close(); } catch (Throwable ignored) { }
            if (fw  != null) try { fw.close();  } catch (Throwable ignored) { }
        }
    }

    // ==================== هِلپرها ====================
    private static String safe(String s) {
        return s == null ? "null" : s;
    }

    private static String pos(Position p) {
        return p == null ? "(null)" : p.toString();
    }

    private static void ensureParentDirExists(String path) {
        try {
            File f = new File(path);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean ok = parent.mkdirs();
                if (!ok) {
                    // اگر ساخت پوشه موفق نشد، لااقل تلاش نکنیم برنامه کرش کند
                    System.err.println("[Logger] Cannot create log directory: " + parent.getAbsolutePath());
                }
            }
        } catch (Throwable ignored) { }
    }
}
