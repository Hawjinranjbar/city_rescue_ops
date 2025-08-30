package util;

import org.w3c.dom.*;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * --------------------
 * لایه: Utility Layer
 * --------------------
 * نگاشت برخورد تایل‌محور که می‌تواند از فایل TMX (property‌ی به نام
 * <code>walkable=true</code>) یا از ماسک سیاه/سفید (PNG) ساخته شود.
 * پیکسل‌های سفید یا tileهایی که property <code>walkable</code> دارند قابل عبور فرض می‌شوند.
 */
public class CollisionMap {

    private final int width;
    private final int height;
    private final boolean[][] walkable; // walkable[y][x]

    private CollisionMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.walkable = new boolean[height][width];
    }

    /** آیا مختصات تایل‌محور (x,y) قابل عبور است؟ */
    public boolean isWalkable(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return false;
        return walkable[y][x];
    }

    /**
     * ساخت CollisionMap از فایل TMX. برای هر تایل، property ای با نام
     * "walkable" اگر مقدار "true" داشته باشد آن سلول قابل عبور است.
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
            Set<Integer> walkableIds = new HashSet<>(); // local tile ids
            boolean isWalkable(int gid) {
                return walkableIds.contains(gid - firstGid);
            }
        }
        java.util.List<TilesetInfo> tilesets = new java.util.ArrayList<>();
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

        // --- لایه اول ---
        Element layer = (Element) mapElem.getElementsByTagName("layer").item(0);
        String data = layer.getElementsByTagName("data").item(0).getTextContent().trim();
        String[] tokens = data.split(",");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String s = tokens[y * width + x].trim();
                if (s.isEmpty()) continue;
                int gid = Integer.parseInt(s);
                boolean walk = false;
                for (TilesetInfo ts : tilesets) {
                    if (gid >= ts.firstGid && ts.isWalkable(gid)) {
                        walk = true;
                        break;
                    }
                }
                cm.walkable[y][x] = walk;
            }
        }
        return cm;
    }

    /**
     * ساخت CollisionMap از ماسک PNG سیاه/سفید.
     * پیکسل سفید ⇒ قابل عبور، سیاه ⇒ غیرقابل عبور.
     */
    public static CollisionMap fromMask(String maskPath) throws IOException {
        BufferedImage img = ImageIO.read(new File(maskPath));
        int w = img.getWidth();
        int h = img.getHeight();
        CollisionMap cm = new CollisionMap(w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y) & 0xFFFFFF;
                cm.walkable[y][x] = rgb != 0x000000; // غیر سیاه
            }
        }
        return cm;
    }

    /**
     * ساخت CollisionMap از ماسک PNG که در آن هر بلوک tileWidth×tileHeight معرف یک تایل است.
     * پیکسل سفید ⇒ قابل عبور، سیاه ⇒ غیرقابل عبور.
     */
    public static CollisionMap fromMask(String maskPath, int tileWidth, int tileHeight) throws IOException {
        BufferedImage img = ImageIO.read(new File(maskPath));
        int w = img.getWidth() / tileWidth;
        int h = img.getHeight() / tileHeight;
        CollisionMap cm = new CollisionMap(w, h);

        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                int px = tx * tileWidth;
                int py = ty * tileHeight;

                boolean walk = false;
                for (int oy = 0; oy < tileHeight && !walk; oy++) {
                    for (int ox = 0; ox < tileWidth; ox++) {
                        int rgb = img.getRGB(px + ox, py + oy) & 0xFFFFFF;
                        if (rgb != 0x000000) { walk = true; break; }
                    }
                }
                cm.walkable[ty][tx] = walk;

                int rgb = img.getRGB(px, py) & 0xFFFFFF;
                cm.walkable[ty][tx] = rgb != 0x000000;

            }
        }
        return cm;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
