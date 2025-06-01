// map/MapInitializer.java
package map;

import util.Position;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class MapInitializer {

    public static class Result {
        public final CityMap map;
        public final int hospitalEntranceX, hospitalEntranceY;
        public Result(CityMap m, int hx, int hy) {
            this.map = m;
            this.hospitalEntranceX = hx;
            this.hospitalEntranceY = hy;
        }
    }

    // پیکربندی
    private static final double ROAD_WHITE_RATIO = 0.55; // اگر ≥55% پیکسل‌های بلاک سفید بود → ROAD
    private static final int WHITE_SUM_THRESHOLD = 700;  // آستانه‌ی «تقریباً سفید»: r+g+b>=700 (~233*3)

    /** فقط با ماسک جاده؛ بیمارستان را دستی می‌گذاری یا بعداً ماسک جدا اضافه می‌کنی */
    public static Result createLogicMap(String roadMaskPath, int tileSize) throws IOException {
        return createLogicMap(roadMaskPath, null, tileSize);
    }

    /** با ماسک جاده و (اختیاری) ماسک بیمارستان */
    public static Result createLogicMap(String roadMaskPath, String hospitalMaskPath, int tileSize) throws IOException {
        BufferedImage roadMask = ImageIO.read(new File(roadMaskPath));
        if (roadMask == null) throw new IOException("Cannot load road mask: " + roadMaskPath);

        BufferedImage hospMask = null;
        if (hospitalMaskPath != null) {
            hospMask = ImageIO.read(new File(hospitalMaskPath));
            if (hospMask == null) throw new IOException("Cannot load hospital mask: " + hospitalMaskPath);
            if (hospMask.getWidth() != roadMask.getWidth() || hospMask.getHeight() != roadMask.getHeight())
                throw new IOException("Road/Hospital masks size mismatch.");
        }

        int pxW = roadMask.getWidth();
        int pxH = roadMask.getHeight();
        if (pxW % tileSize != 0 || pxH % tileSize != 0)
            throw new IOException("Mask size not divisible by tileSize=" + tileSize + " ("
                    + pxW + "x" + pxH + ").");

        int gridW = pxW / tileSize;
        int gridH = pxH / tileSize;

        CityMap map = new CityMap(gridW, gridH, tileSize, tileSize);

        // 1) پیش‌فرض: همه RUBBLE
        int y, x;
        for (y = 0; y < gridH; y++) {
            for (x = 0; x < gridW; x++) {
                map.setCell(x, y, new Cell(new Position(x, y), Cell.Type.RUBBLE));
            }
        }

        // 2) تعیین ROAD/HOSPITAL به ازای هر بلاک سلولی
        for (y = 0; y < gridH; y++) {
            for (x = 0; x < gridW; x++) {
                int sx = x * tileSize;
                int sy = y * tileSize;

                // شمارش پیکسل‌های سفید در بلاک
                int white = 0;
                int total = 0;

                // برای سرعت هر 2 پیکسل یکی نمونه بگیر (می‌تونی 1 کنی تا دقیق‌تر شود)
                int step = 2;
                int yy, xx;
                for (yy = sy; yy < sy + tileSize; yy += step) {
                    for (xx = sx; xx < sx + tileSize; xx += step) {
                        int rgb = roadMask.getRGB(xx, yy);
                        int a = (rgb >>> 24) & 0xFF;
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8)  & 0xFF;
                        int b = (rgb)       & 0xFF;

                        // شفاف = حساب نشود
                        if (a < 16) continue;

                        if ((r + g + b) >= WHITE_SUM_THRESHOLD) white++;
                        total++;
                    }
                }

                boolean isRoad = (total > 0) && ((white / (double) total) >= ROAD_WHITE_RATIO);

                // اگر ماسک بیمارستان داری، اولویت با HOSPITAL است
                boolean isHospital = false;
                if (hospMask != null) {
                    int cx = sx + tileSize / 2;
                    int cy = sy + tileSize / 2;
                    int hrgb = hospMask.getRGB(cx, cy);
                    int ha = (hrgb >>> 24) & 0xFF;
                    int hr = (hrgb >> 16) & 0xFF;
                    int hg = (hrgb >> 8)  & 0xFF;
                    int hb = (hrgb)       & 0xFF;
                    // هر پیکسلی که هم آلفا داشته باشد و هم روشن باشد، فعال تلقی می‌کنیم
                    isHospital = ha >= 16 && (hr + hg + hb) >= 96;
                }

                if (isHospital) {
                    map.setCell(x, y, new Cell(new Position(x, y), Cell.Type.HOSPITAL));
                } else if (isRoad) {
                    map.setCell(x, y, new Cell(new Position(x, y), Cell.Type.ROAD));
                }
                // else همان RUBBLE می‌ماند
            }
        }

        // 3) تعیین ورودی بیمارستان (اولین خانه‌ی HOSPITAL که به ROAD چسبیده)
        int hx = -1, hy = -1;
        outer:
        for (y = 0; y < gridH; y++) {
            for (x = 0; x < gridW; x++) {
                Cell c = map.getCell(x, y);
                if (c != null && c.getType() == Cell.Type.HOSPITAL) {
                    if (hasRoadNeighbor(map, x, y)) {
                        hx = x; hy = y;
                        break outer;
                    }
                }
            }
        }
        // اگر ماسک بیمارستان نداشتی و ورودی پیدا نشد، اصلاً ورودی تعیین نکن (اختیاری)
        return new Result(map, hx, hy);
    }

    private static boolean hasRoadNeighbor(CityMap map, int x, int y) {
        return isRoad(map, x+1, y) || isRoad(map, x-1, y) || isRoad(map, x, y+1) || isRoad(map, x, y-1);
    }

    private static boolean isRoad(CityMap map, int x, int y) {
        if (!map.isValid(x, y)) return false;
        Cell c = map.getCell(x, y);
        return c != null && c.getType() == Cell.Type.ROAD;
    }
}
