package map;

import util.Position;
import java.awt.image.BufferedImage;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * نمایندهٔ یک خانه (Tile) روی نقشه.
 * می‌تواند جاده، مانع، بیمارستان یا فضای خالی باشد.
 */
public class Cell {

    public enum Type {
        ROAD,       // جاده قابل عبور
        SIDEWALK,   // پیاده‌رو
        GROUND,     // زمین خنثی
        OBSTACLE,   // مانع مانند خودرو یا آوار
        BUILDING,   // ساختمان‌ها / غیرقابل عبور
        HOSPITAL,   // نقطه تحویل مجروح
        EMPTY;      // سلول خالی یا تعریف‌نشده

        /**
         * آیا این نوع سلول قابل عبور است؟
         * برای سازگاری به عقب، هر دو نام isWalkable() و walkable() در دسترس هستند.
         */
        public boolean isWalkable() {
            // عبوری‌ها: جاده، پیاده‌رو، ورودی/ناحیهٔ بیمارستان
            return this == ROAD || this == SIDEWALK || this == HOSPITAL;
        }

        /** سازگاری با کد قدیمی که به جای isWalkable از walkable استفاده می‌کرد. */
        public boolean walkable() { return isWalkable(); }

        /** true اگر این نوع مانع/غیرقابل عبور باشد. */
        public boolean isBlocked() { return !isWalkable(); }

        /** true اگر این تایل بیمارستان باشد. */
        public boolean isHospital() { return this == HOSPITAL; }
    }

    private final Position position;  // موقعیت تایل در شبکه
    private final Type type;          // نوع سلول (immutable)
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
    public int getX() { return position != null ? position.getX() : 0; }
    public int getY() { return position != null ? position.getY() : 0; }

    public Type getType() { return type; }

    /** عبوری بودن بر اساس نوع سلول. */
    public boolean isWalkable() { return type.isWalkable(); }

    /** بلاک بودن با درنظرگرفتن اشغال‌بودن تایل. */
    public boolean isBlocked() { return type.isBlocked() || occupied; }

    public boolean isOccupied() { return occupied; }
    public void setOccupied(boolean occupied) { this.occupied = occupied; }

    // ---- هِلپرهای نوع ----
    public boolean isRoad()      { return type == Type.ROAD; }
    public boolean isSidewalk()  { return type == Type.SIDEWALK; }
    public boolean isGround()    { return type == Type.GROUND; }
    public boolean isObstacle()  { return type == Type.OBSTACLE || type == Type.BUILDING; }
    public boolean isHospital()  { return type == Type.HOSPITAL; }
    public boolean isEmpty()     { return type == Type.EMPTY; }

    // ---- گرافیک ----
    public BufferedImage getImage() { return image; }
    public void setImage(BufferedImage image) { this.image = image; }

    public int getTileId() { return tileId; }
    public void setTileId(int tileId) { this.tileId = tileId; }

    // ---- ابزار ----
    public Cell cloneShallow() { return new Cell(position, type, image, tileId); }

    /** کپی با تصویر جدید (برای تغییر تم/ری‌اسکین بدون دست‌زدن به type). */
    public Cell copyWithImage(BufferedImage newImage) {
        return new Cell(position, type, newImage, tileId);
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
