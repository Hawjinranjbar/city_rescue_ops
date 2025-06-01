package util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// --------------------
// لایه: Utility Layer
// --------------------
// این کلاس برای ثبت لاگ‌های مهم بازی طراحی شده
// مثل نجات موفق، مرگ مجروح، زمان شروع، یا هر عملیات خاص
public class Logger {

    private final String logFilePath;
    private final boolean toConsole;

    // سازنده: مسیر فایل لاگ و اینکه خروجی روی کنسول هم چاپ بشه یا نه
    public Logger(String filePath, boolean toConsole) {
        this.logFilePath = filePath;
        this.toConsole = toConsole;
    }

    // متد اصلی برای نوشتن لاگ با زمان دقیق
    public void log(String message) {
        String timestampedMessage = formatWithTimestamp(message);

        if (toConsole) {
            System.out.println(timestampedMessage);
        }

        try (FileWriter fw = new FileWriter(logFilePath, true);
             PrintWriter out = new PrintWriter(fw)) {
            out.println(timestampedMessage);
        } catch (IOException e) {
            System.err.println("خطا در نوشتن لاگ: " + e.getMessage());
        }
    }

    // اضافه کردن زمان به پیام
    private String formatWithTimestamp(String msg) {
        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return "[" + time + "] " + msg;
    }
}
