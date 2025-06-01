package map;

import util.Position;
import java.awt.image.BufferedImage;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نمایندهٔ یک خانه (Tile) روی نقشه.
 * می‌تونه جاده، مانع، بیمارستان یا فضای خالی باشه.
 */
public class Cell {

    public enum Type {
        ROAD,       // قابل عبور
        RUBBLE,     // آوار / غیرقابل عبور
        HOSPITAL,   // نقطه تحویل مجروح
        EMPTY       // سلول خالی یا تعریف‌نشده
    }

    private final Position position;     // موقعیت سلول در نقشه
    private final Type type;             // نوع سلول
    private boolean occupied;            // آیا کسی روی این سلول قرار دارد

    // ---- بخش گرافیک ----
    private BufferedImage image;         // تصویر تایل
    private int tileId;                  // شمارهٔ تایل در TMX (برای دیباگ)

    // سازندهٔ پایه (نوع مشخص + موقعیت)
    public Cell(Position position, Type type) {
        this(position, type, null, -1);
    }

    // سازندهٔ کامل
    public Cell(Position position, Type type, BufferedImage image, int tileId) {
        this.position = position;
        this.type = type != null ? type : Type.EMPTY;
        this.occupied = false;
        this.image = image;
        this.tileId = tileId;
    }

    // سازندهٔ ساده برای وقتی فقط تصویر داری
    public Cell(Position position, BufferedImage image, int tileId) {
        this(position, Type.EMPTY, image, tileId);
    }

    // ---- منطق ----
    public Position getPosition() { return position; }
    public Type getType() { return type; }

    public boolean isWalkable() {
        return type == Type.ROAD || type == Type.HOSPITAL;
    }


    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }

    // ---- گرافیک ----
    public BufferedImage getImage() { return image; }
    public void setImage(BufferedImage image) { this.image = image; }

    public int getTileId() { return tileId; }
    public void setTileId(int tileId) { this.tileId = tileId; }

    // ---- ابزار ----
    public Cell cloneShallow() {
        return new Cell(position, type, image, tileId);
    }

    @Override
    public String toString() {
        return "Cell{" +
                "pos=" + position +
                ", type=" + type +
                ", tileId=" + tileId +
                ", occupied=" + occupied +
                '}';
    }
}
