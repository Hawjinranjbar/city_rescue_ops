package util;

import org.w3c.dom.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * نگاشت برخورد تایل‌محور.
 *
 * قراردادها:
 *  - در لایه‌های باینری CSV (مثلاً CollisionLayer_Vehicle): مقدار 0 = قابل عبور، مقدار 1 = مسدود.
 *  - isWalkable(x,y) => true اگر تایل قابل عبور باشد.
 *  - isBlocked(x,y)  => true اگر تایل خارج از نقشه یا مسدود باشد.
 */
public final class CollisionMap {

    /** حالت ساخت خودکار از TMX */
    public enum Mode { PEDESTRIAN, VEHICLE }

    private final int width;
    private final int height;
    // walkable[y][x] = true => عبوری
    private final boolean[][] walkable;

    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    private CollisionMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.walkable = new boolean[height][width];
    }

    /** کپی عمیق */
    public CollisionMap copy() {
        CollisionMap c = new CollisionMap(width, height);
        for (int y = 0; y < height; y++) {
            System.arraycopy(this.walkable[y], 0, c.walkable[y], 0, width);
        }
        return c;
    }

    /** پرکردن کل نقشه با مقدار عبوری/مسدود */
    public void fill(boolean canPass) {
        for (int y = 0; y < height; y++) {
            Arrays.fill(walkable[y], canPass);
        }
    }

    /** آیا مختصات تایل‌محور (x,y) قابل عبور است؟  خارج مرز => false */
    public boolean isWalkable(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        return walkable[y][x];
    }

    /** آیا مختصات تایل‌محور (x,y) مسدود است؟  خارج مرز => true */
    public boolean isBlocked(int x, int y) {
        return !isWalkable(x, y);
    }

    /** تنظیم عبوری/مسدود بودن برحسب "blocked". */
    public void set(int x, int y, boolean blocked) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        walkable[y][x] = !blocked;
    }

    /** صراحتاً ستِ عبوری. */
    public void setWalkable(int x, int y, boolean canPass) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        walkable[y][x] = canPass;
    }

    /* =========================
       0) ساخت خودکار از TMX بر اساس Mode
       ========================= */

    /**
     * تلاش می‌کند بر اساس نام‌های رایج لایه‌ها CollisionMap بسازد.
     * اگر لایهٔ مناسب پیدا نشود، به متد legacy {@link #fromTMX(String)} برمی‌گردد.
     */
    public static CollisionMap autoFromTMX(String tmxPath, Mode mode) {
        try {
            Document doc = parseXML(tmxPath);
            String[] veh = new String[] {
                    "CollisionLayer_Vehicle", "VehicleCollision", "Collision_Vehicle",
                    "vehicle", "VEHICLE"
            };
            String[] ped = new String[] {
                    "CollisionLayer_Pedestrian", "Walkable", "Collision_Pedestrian",
                    "walkable", "PEDESTRIAN"
            };
            String[] names = mode == Mode.VEHICLE ? veh : ped;

            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                Element layer = findLayerByName(doc, name);
                if (layer != null) {
                    // این یک لایهٔ باینری CSV است: 0=عبوری, غیرصفر=مسدود
                    return fromBinaryLayerParsed(doc, name);
                }
            }
        } catch (Exception ignored) { }

        // fallback
        try {
            return fromTMX(tmxPath);
        } catch (Exception e) {
            throw new RuntimeException("autoFromTMX failed: " + e.getMessage(), e);
        }
    }

    /* =========================
       1) ساخت از لایه‌ی باینری CSV در TMX
       ========================= */

    /**
     * یک لایه‌ی CSV با name مشخص را از TMX می‌خواند.
     * قرارداد: 0 = عبوری، غیر صفر = مسدود.
     */
    public static CollisionMap fromBinaryLayer(String tmxPath, String layerName) {
        try {
            Document doc = parseXML(tmxPath);
            return fromBinaryLayerParsed(doc, layerName);
        } catch (Exception ex) {
            throw new RuntimeException("fromBinaryLayer failed: " + ex.getMessage(), ex);
        }
    }

    /** نسخه‌ای که Document آماده دارد (برای autoFromTMX) */
    private static CollisionMap fromBinaryLayerParsed(Document doc, String layerName) throws Exception {
        Element map = (Element) doc.getElementsByTagName("map").item(0);
        int w = Integer.parseInt(map.getAttribute("width"));
        int h = Integer.parseInt(map.getAttribute("height"));
        CollisionMap cm = new CollisionMap(w, h);

        Element layer = findLayerByName(doc, layerName);
        if (layer == null) {
            throw new IllegalStateException("Layer not found: " + layerName);
        }
        Element data = (Element) layer.getElementsByTagName("data").item(0);
        String encoding = data.getAttribute("encoding");
        if (!"csv".equalsIgnoreCase(encoding)) {
            throw new IllegalStateException("Only CSV encoding is supported for layer: " + layerName);
        }

        int[][] vals = readCsvGrid(data.getTextContent(), w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = vals[y][x];
                cm.set(x, y, v != 0); // 0 => عبوری, غیر صفر => مسدود
            }
        }
        return cm;
    }

    /* =========================
       2) ساخت از چند لایه‌ی تایل در TMX (غیر باینری)
       ========================= */

    /**
     * لایه‌هایی که باید "عبوری" باشند و لایه‌هایی که باید "مسدود" باشند را ترکیب می‌کند.
     * هرجایی که در blockedLayers GID!=0 باشد، خروجی را مسدود می‌کند (ارجحیت با مسدود است).
     * هرجایی که در walkableLayers GID!=0 باشد، خروجی را عبوری می‌کند (اگر در blocked هم بود، مسدود می‌ماند).
     * اگر هر دو لیست خالی باشند، خروجی همه‌جا "مسدود" خواهد بود.
     */
    public static CollisionMap fromTMX(String tmxPath,
                                       List<String> walkableLayers,
                                       List<String> blockedLayers) {
        try {
            Document doc = parseXML(tmxPath);
            Element map = (Element) doc.getElementsByTagName("map").item(0);
            int w = Integer.parseInt(map.getAttribute("width"));
            int h = Integer.parseInt(map.getAttribute("height"));
            CollisionMap cm = new CollisionMap(w, h);

            // پیش‌فرض: همه غیرعبوری
            for (int y = 0; y < h; y++) Arrays.fill(cm.walkable[y], false);

            // 2-1) عبوری‌ها
            List<String> wl = (walkableLayers == null) ? Collections.<String>emptyList() : walkableLayers;
            for (int i = 0; i < wl.size(); i++) {
                String ln = wl.get(i);
                Element layer = findLayerByName(doc, ln);
                if (layer == null) continue;
                Element data = (Element) layer.getElementsByTagName("data").item(0);
                if (!"csv".equalsIgnoreCase(data.getAttribute("encoding")))
                    throw new IllegalStateException("Only CSV encoding supported for: " + ln);
                int[][] vals = readCsvGrid(data.getTextContent(), w, h);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (vals[y][x] != 0) cm.setWalkable(x, y, true);
                    }
                }
            }

            // 2-2) مسدودی‌ها (ارجحیت)
            List<String> bl = (blockedLayers == null) ? Collections.<String>emptyList() : blockedLayers;
            for (int i = 0; i < bl.size(); i++) {
                String ln = bl.get(i);
                Element layer = findLayerByName(doc, ln);
                if (layer == null) continue;
                Element data = (Element) layer.getElementsByTagName("data").item(0);
                if (!"csv".equalsIgnoreCase(data.getAttribute("encoding")))
                    throw new IllegalStateException("Only CSV encoding supported for: " + ln);
                int[][] vals = readCsvGrid(data.getTextContent(), w, h);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (vals[y][x] != 0) cm.set(x, y, true); // true => مسدود
                    }
                }
            }

            return cm;
        } catch (Exception ex) {
            throw new RuntimeException("fromTMX(walkable/blocked) failed: " + ex.getMessage(), ex);
        }
    }

    /* =========================
       3) نسخه‌ی قدیمی: از tileset با property "walkable"
       ========================= */

    /**
     * نسخه‌ی قدیمی (Legacy): تشخیص عبوری‌بودن با property تایل‌ها (walkable=true).
     * در پروژه‌ی فعلی توصیه می‌شود از fromBinaryLayer یا fromTMX(walkable,blocked) استفاده کنید.
     */
    public static CollisionMap fromTMX(String tmxPath) throws Exception {
        File tmxFile = new File(tmxPath);
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(tmxFile);

        Element mapElem = doc.getDocumentElement();
        int width = Integer.parseInt(mapElem.getAttribute("width"));
        int height = Integer.parseInt(mapElem.getAttribute("height"));

        CollisionMap cm = new CollisionMap(width, height);

        // --- نگاشت GID به walkable ---
        NodeList tilesetNodes = mapElem.getElementsByTagName("tileset");

        class TilesetInfo {
            int firstGid;
            Set<Integer> walkableIds = new HashSet<Integer>();
            boolean isWalkableGid(int gid) {
                int local = gid - firstGid;
                return walkableIds.contains(local);
            }
        }

        List<TilesetInfo> tilesets = new ArrayList<TilesetInfo>();
        for (int i = 0; i < tilesetNodes.getLength(); i++) {
            Element ts = (Element) tilesetNodes.item(i);
            TilesetInfo info = new TilesetInfo();
            info.firstGid = Integer.parseInt(ts.getAttribute("firstgid"));

            NodeList tileNodes = ts.getElementsByTagName("tile");
            for (int t = 0; t < tileNodes.getLength(); t++) {
                Element tileElem = (Element) tileNodes.item(t);
                int id = Integer.parseInt(tileElem.getAttribute("id"));
                NodeList propNodes = tileElem.getElementsByTagName("property");
                for (int p = 0; p < propNodes.getLength(); p++) {
                    Element prop = (Element) propNodes.item(p);
                    if ("walkable".equalsIgnoreCase(prop.getAttribute("name")) &&
                            "true".equalsIgnoreCase(prop.getAttribute("value"))) {
                        info.walkableIds.add(id);
                    }
                }
            }
            tilesets.add(info);
        }

        // اولین لایه‌ی تایل
        Element layer = (Element) mapElem.getElementsByTagName("layer").item(0);
        Element data = (Element) layer.getElementsByTagName("data").item(0);
        String encoding = data.getAttribute("encoding");
        if (!"csv".equalsIgnoreCase(encoding))
            throw new IllegalStateException("Only CSV encoding supported in legacy fromTMX");

        String[] rawLines = data.getTextContent().trim().split("\\R+");
        for (int y = 0; y < height; y++) {
            String[] toks = rawLines[y].trim().split(",");
            for (int x = 0; x < width; x++) {
                int gid = Integer.parseInt(toks[x].trim());
                boolean walk = false;
                if (gid != 0) {
                    for (int j = 0; j < tilesets.size(); j++) {
                        TilesetInfo ts = tilesets.get(j);
                        if (gid >= ts.firstGid && ts.isWalkableGid(gid)) {
                            walk = true;
                            break;
                        }
                    }
                }
                cm.walkable[y][x] = walk;
            }
        }
        return cm;
    }

    /* =========================
       4) ساخت از ماسک تصویری (PNG)
       ========================= */

    /**
     * ساخت CollisionMap از ماسک پیکسلی: پیکسل سفید ⇒ قابل عبور، سیاه ⇒ غیرقابل عبور.
     * ابعاد خروجی برابر با ابعاد تصویر است (تایل=۱ پیکسل).
     */
    public static CollisionMap fromMask(String maskPath) throws IOException {
        BufferedImage img = ImageIO.read(new File(maskPath));
        int w = img.getWidth();
        int h = img.getHeight();
        CollisionMap cm = new CollisionMap(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                cm.setWalkable(x, y, rgb != 0x000000);
            }
        }
        return cm;
    }

    /**
     * ساخت CollisionMap از ماسک کاشی‌محور: هر بلوک tileWidth×tileHeight یک تایل.
     * معیار: میانگین روشنایی بلوک؛ اگر >=127 ⇒ عبوری (سفید غالب)، در غیر اینصورت مسدود.
     */
    public static CollisionMap fromMask(String maskPath, int tileWidth, int tileHeight) throws IOException {
        BufferedImage img = ImageIO.read(new File(maskPath));
        int tilesX = img.getWidth() / tileWidth;
        int tilesY = img.getHeight() / tileHeight;
        CollisionMap cm = new CollisionMap(tilesX, tilesY);

        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                int px0 = tx * tileWidth;
                int py0 = ty * tileHeight;

                long sum = 0;
                for (int oy = 0; oy < tileHeight; oy++) {
                    for (int ox = 0; ox < tileWidth; ox++) {
                        int rgb = img.getRGB(px0 + ox, py0 + oy) & 0xFFFFFF;
                        int r = (rgb >> 16) & 0xFF;
                        int g = (rgb >> 8)  & 0xFF;
                        int b = (rgb)       & 0xFF;
                        int lum = (r + g + b) / 3;
                        sum += lum;
                    }
                }
                int count = tileWidth * tileHeight;
                int avg = (int)(sum / count);
                boolean canPass = avg >= 127; // سفیدتر ⇒ عبوری
                cm.setWalkable(tx, ty, canPass);
            }
        }
        return cm;
    }

    /* =========================
       5) ادغام چند نقشه برخورد
       ========================= */

    /**
     * ادغام چند CollisionMap هم‌اندازه: اگر هرکدام مسدود کند، خروجی هم مسدود می‌شود.
     * (Intersection روی walkable: خروجی فقط وقتی عبوری است که همه عبوری باشند)
     */
    public static CollisionMap merge(List<CollisionMap> maps) {
        if (maps == null || maps.isEmpty())
            throw new IllegalArgumentException("maps is empty");
        CollisionMap base = maps.get(0);
        int w = base.width, h = base.height;
        CollisionMap out = new CollisionMap(w, h);

        // شروع: همه عبوری
        for (int y = 0; y < h; y++) Arrays.fill(out.walkable[y], true);

        for (int i = 0; i < maps.size(); i++) {
            CollisionMap m = maps.get(i);
            if (m.width != w || m.height != h)
                throw new IllegalArgumentException("All maps must have the same size");
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // اگر هر کدام مسدود کند => خروجی مسدود
                    if (!m.walkable[y][x]) out.walkable[y][x] = false;
                }
            }
        }
        return out;
    }

    /* =========================
       6) خروجی گرفتن برای دیباگ
       ========================= */

    /**
     * خروجی ماسک PNG برای دیباگ: سفید=عبوری، سیاه=مسدود.
     * هر تایل به اندازهٔ tileW×tileH ترسیم می‌شود.
     */
    public void exportMaskPNG(String outPath, int tileW, int tileH) throws IOException {
        if (tileW <= 0) tileW = 1;
        if (tileH <= 0) tileH = 1;
        BufferedImage img = new BufferedImage(width * tileW, height * tileH, BufferedImage.TYPE_INT_RGB);
        int white = 0xFFFFFF;
        int black = 0x000000;

        for (int ty = 0; ty < height; ty++) {
            for (int tx = 0; tx < width; tx++) {
                int color = walkable[ty][tx] ? white : black;
                for (int oy = 0; oy < tileH; oy++) {
                    for (int ox = 0; ox < tileW; ox++) {
                        img.setRGB(tx * tileW + ox, ty * tileH + oy, color);
                    }
                }
            }
        }
        ImageIO.write(img, "png", new File(outPath));
    }

    /* =========================
       کمک‌متدهای داخلی
       ========================= */

    private static Document parseXML(String path) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new File(path));
    }

    private static Element findLayerByName(Document doc, String name) {
        NodeList layers = doc.getElementsByTagName("layer");
        for (int i = 0; i < layers.getLength(); i++) {
            Element e = (Element) layers.item(i);
            if (name.equals(e.getAttribute("name")) || name.equalsIgnoreCase(e.getAttribute("name"))) return e;
        }
        return null;
    }

    /** خواندن CSV به ماتریس [y][x] با حذف فضا و خطوط خالی. */
    private static int[][] readCsvGrid(String csvText, int w, int h) {
        String trimmed = csvText == null ? "" : csvText.trim();
        String[] rawLines = trimmed.split("\\R+");

        // مسیر امن در صورت وجود خطوط خالی/اضافی
        List<Integer> flat = new ArrayList<Integer>(w * h);
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i];
            if (line == null) continue;
            String t = line.trim();
            if (t.length() == 0) continue;
            String[] parts = t.split(",");
            for (int j = 0; j < parts.length; j++) {
                String ss = parts[j].trim();
                if (ss.length() == 0) continue;
                flat.add(Integer.valueOf(Integer.parseInt(ss)));
            }
        }
        if (flat.size() != w * h) {
            throw new IllegalStateException("CSV size mismatch: expected " + (w * h) + " got " + flat.size());
        }

        int[][] out = new int[h][w];
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y][x] = flat.get(idx++).intValue();
            }
        }
        return out;
    }
}
