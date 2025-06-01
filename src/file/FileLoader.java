package file;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

// --------------------
// لایه: File I/O Layer
// --------------------
// این کلاس وظیفه داره فایل ذخیره‌شدهٔ بازی رو بخونه
// و وضعیت کامل بازی (GameState) رو بازسازی کنه
public class FileLoader {

    private final String filePath;

    public FileLoader(String filePath) {
        this.filePath = filePath;
    }

    // بارگذاری وضعیت بازی از فایل
    public GameState loadGame() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(filePath))) {
            return (GameState) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("خطا در بارگذاری بازی: " + e.getMessage());
            return null;
        }
    }
}
