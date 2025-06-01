package file;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

// --------------------
// لایه: File I/O Layer
// --------------------
// این کلاس وضعیت بازی (GameState) رو در فایل ذخیره می‌کنه
public class SaveManager {

    private final String filePath;

    public SaveManager(String filePath) {
        this.filePath = filePath;
    }

    // ذخیره وضعیت فعلی بازی در فایل
    public void saveGame(GameState gameState) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filePath))) {
            oos.writeObject(gameState);
            System.out.println("بازی با موفقیت ذخیره شد.");
        } catch (IOException e) {
            System.err.println("خطا در ذخیره بازی: " + e.getMessage());
        }
    }
}
