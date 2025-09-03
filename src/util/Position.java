package util;

import java.io.Serializable;
import java.util.Objects;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * نگه‌دارندهٔ موقعیت روی شبکهٔ تایل + هِلپرهای جهت/مجاورت.
 * جهت‌ها (چهارضلعی): 0=DOWN, 1=LEFT, 2=RIGHT, 3=UP
 *
 * نکته: Serializable برای سازگاری با Save/Load/Restart
 */
public class Position implements Serializable {

    private static final long serialVersionUID = 1L;

    // ----- جهت‌ها مطابق کل پروژه -----
    public static final int DIR_DOWN  = 0;
    public static final int DIR_LEFT  = 1;
    public static final int DIR_RIGHT = 2;
    public static final int DIR_UP    = 3;

    private int x;
    private int y;

    // سازنده اصلی
    public Position(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // کپی‌کننده
    public Position(Position other) {
        if (other == null) {
            this.x = 0;
            this.y = 0;
        } else {
            this.x = other.x;
            this.y = other.y;
        }
    }

    // ---------- دسترسی ----------
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    /** ست‌کردن هر دو مختصات از روی یک پوزیشن دیگر */
    public void set(Position p) {
        if (p != null) {
            this.x = p.x;
            this.y = p.y;
        }
    }

    /** ست‌کردن هر دو مختصات به‌صورت مستقیم */
    public void setXY(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** برگرداندن یک کپی امن (بدون تغییر شیء فعلی) */
    public Position copy() {
        return new Position(this);
    }

    // ---------- فاصله/مجاورت ----------
    /** فاصلهٔ منهتنی (|dx|+|dy|) – سازگاری با کد قبلی */
    public int distanceTo(Position other) {
        if (other == null) return Integer.MAX_VALUE;
        return Math.abs(this.x - other.x) + Math.abs(this.y - other.y);
    }

    /** نام مستعارِ خواناتر برای فاصلهٔ منهتنی */
    public int manhattanDistanceTo(Position other) {
        return distanceTo(other);
    }

    /** آیا روی خانهٔ مجاور چهارضلعی هستیم؟ (بالا/پایین/چپ/راست) */
    public boolean isAdjacent4(Position other) {
        return other != null && distanceTo(other) == 1;
    }

    // ---------- حرکت/جهت ----------
    /** یک پوزیشن جدید که یک گام به بالا می‌رود */
    public Position up() { return new Position(x, y - 1); }

    /** یک پوزیشن جدید که یک گام به پایین می‌رود */
    public Position down() { return new Position(x, y + 1); }

    /** یک پوزیشن جدید که یک گام به چپ می‌رود */
    public Position left() { return new Position(x - 1, y); }

    /** یک پوزیشن جدید که یک گام به راست می‌رود */
    public Position right() { return new Position(x + 1, y); }

    /** آرایهٔ چهار همسایهٔ چهارضلعی: [DOWN, LEFT, RIGHT, UP] */
    public Position[] neighbors4() {
        Position[] out = new Position[4];
        out[0] = down();
        out[1] = left();
        out[2] = right();
        out[3] = up();
        return out;
    }

    /** جابه‌جایی نسبی (تغییردهنده) */
    public void moveBy(int dx, int dy) {
        this.x += dx;
        this.y += dy;
    }

    /** جابه‌جایی نسبی (غیرتغییردهنده) */
    public Position translated(int dx, int dy) {
        return new Position(this.x + dx, this.y + dy);
    }

    /** یک گام در جهت داده‌شده (غیرتغییردهنده) */
    public Position step(int dir) {
        if (dir == DIR_DOWN)  return down();
        if (dir == DIR_LEFT)  return left();
        if (dir == DIR_RIGHT) return right();
        if (dir == DIR_UP)    return up();
        return new Position(this); // اگر جهت نامعتبر بود، هم‌جای خود
    }

    /** نسخهٔ ایستای step */
    public static Position step(Position from, int dir) {
        if (from == null) return null;
        return from.step(dir);
    }

    /**
     * اگر دو موقعیت «مجاور چهارضلعی» باشند، جهت حرکت از a به b را برمی‌گرداند.
     * در غیر این صورت -1 برمی‌گرداند.
     */
    public static int dirFromAToB(Position a, Position b) {
        if (a == null || b == null) return -1;
        int dx = b.x - a.x;
        int dy = b.y - a.y;
        if (dx == 0 && dy == 1)  return DIR_DOWN;
        if (dx == -1 && dy == 0) return DIR_LEFT;
        if (dx == 1 && dy == 0)  return DIR_RIGHT;
        if (dx == 0 && dy == -1) return DIR_UP;
        return -1;
    }

    // ---------- محدوده/کنترل نقشه (اختیاری ولی مفید) ----------
    /** آیا داخل محدودهٔ [0..width-1]×[0..height-1] هست؟ */
    public boolean withinBounds(int width, int height) {
        if (width <= 0 || height <= 0) return false;
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** مختصات را داخل محدودهٔ نقشه می‌بُرد (Clamp) */
    public void clampToBounds(int width, int height) {
        if (width > 0) {
            if (x < 0) x = 0;
            if (x >= width) x = width - 1;
        }
        if (height > 0) {
            if (y < 0) y = 0;
            if (y >= height) y = height - 1;
        }
    }

    // ---------- equals/hash/toString ----------
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position other = (Position) o;
        return this.x == other.x && this.y == other.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + ")";
    }
}
