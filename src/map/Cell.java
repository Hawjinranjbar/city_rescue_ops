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
        // قابل عبور
        ROAD,        // جاده/پیاده‌رو
        HOSPITAL,    // نقطهٔ تحویل

        // غیرقابل عبور
        RUBBLE,      // آوار/خاک/سنگ (برای سازگاری با MapLoader)
        OBSTACLE,    // مانع (ماشین/دیوار و ...)
        BUILDING,    // ساختمان
        EMPTY;       // سلول خالی/نامشخص

        /** آیا این نوع سلول قابل عبور است؟ */
        public boolean isWalkable() {
            return this == ROAD || this == HOSPITAL;
        }

        /** سازگاری با کد قدیمی که به جای isWalkable از walkable استفاده می‌کرد. */
        public boolean walkable() {
            return isWalkable();
        }

        /** true اگر این نوع مانع/غیرقابل عبور باشد. */
        public boolean isBlocked() {
            return !isWalkable();
        }

        /** true اگر این تایل بیمارستان باشد. */
        public boolean isHospital() {
            return this == HOSPITAL;
        }
    }

    private final Position position;  // موقعیت تایل در شبکه
    private final Type type;          // نوع سلول
    private boolean occupied;         // آیا عامل روی این تایل ایستاده است؟

    // ---- بخش گرافیک ----
    private BufferedImage image;      // تصویر تایل
    private int tileId;               // GID/شناسه تایل در TMX (برای دیباگ)

    // سازندهٔ پایه
    public Cell(Position position, Type type) {
        this(position, type, null, -1);
    }

    // سازندهٔ کامل
    public Cell(Position position, Type type, BufferedImage image, int tileId) {
        this.position = position;
        this.type = (type != null) ? type : Type.EMPTY;
        this.image = image;
        this.tileId = tileId;
        this.occupied = false;
    }

    // سازندهٔ ساده وقتی فقط تصویر داری
    public Cell(Position position, BufferedImage image, int tileId) {
        this(position, Type.EMPTY, image, tileId);
    }

    // ---- منطق ----
    public Position getPosition() { return position; }
    public Type getType() { return type; }

    /** فقط ROAD و HOSPITAL قابل عبورند. */
    public boolean isWalkable() { return type.isWalkable(); }

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

