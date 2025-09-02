package map;

import util.AssetLoader;
import util.Position;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MapLoader {

    private MapLoader() {}

    /** 30 بیت پایین GID واقعی است (پرچم‌های flip حذف می‌شود). */
    private static final long GID_MASK = 0x0FFFFFFFL;

    /** اطلاعات tileset */
    private static class TilesetInfo {
        int firstGid;
        int tileWidth;
        int tileHeight;
        int tileCount;
        int columns;
        int margin = 0;
        int spacing = 0;
        BufferedImage image;
        Element tilesetElement;

        boolean owns(int gid) {
            return gid >= firstGid && gid < firstGid + tileCount;
        }

        BufferedImage getSubImage(int gid) {
            int localId = gid - firstGid;
            if (localId < 0 || localId >= tileCount) return null;

            int col = localId % columns;
            int row = localId / columns;
            int x = margin + col * (tileWidth + spacing);
            int y = margin + row * (tileHeight + spacing);
            return image.getSubimage(x, y, tileWidth, tileHeight);
        }

        Element findTileElement(int localId) {
            NodeList tileNodes = tilesetElement.getElementsByTagName("tile");
            for (int i = 0; i < tileNodes.getLength(); i++) {
                Element t = (Element) tileNodes.item(i);
                String idAttr = t.getAttribute("id");
                if (idAttr == null || idAttr.isEmpty()) continue;
                if (Integer.parseInt(idAttr) == localId) return t;
            }
            return null;
        }
    }

    /** لود نقشهٔ اصلی (فقط لایهٔ تصویری اول) برای رندر پایه */
    public static CityMap loadTMX(String tmxPath) throws Exception {
        File tmxFile = new File(tmxPath);
        File baseDir = tmxFile.getParentFile();

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(tmxFile);
        Element mapElem = doc.getDocumentElement();

        final int tileWidth  = Integer.parseInt(mapElem.getAttribute("tilewidth"));
        final int tileHeight = Integer.parseInt(mapElem.getAttribute("tileheight"));

        // ---- tileset ها ----
        NodeList tsNodes = mapElem.getElementsByTagName("tileset");
        List<TilesetInfo> tilesets = new ArrayList<>();

        for (int i = 0; i < tsNodes.getLength(); i++) {
            Element ts = (Element) tsNodes.item(i);

            TilesetInfo info = new TilesetInfo();
            info.tilesetElement = ts;
            info.firstGid = Integer.parseInt(ts.getAttribute("firstgid"));

            Element img = (Element) ts.getElementsByTagName("image").item(0);
            if (img == null) continue;
            String src = img.getAttribute("source");
            File imgFile = new File(baseDir, src);
            info.image = AssetLoader.requireImage(imgFile.getPath());

            if (ts.hasAttribute("margin"))  info.margin  = parseIntOr(ts.getAttribute("margin"), 0);
            if (ts.hasAttribute("spacing")) info.spacing = parseIntOr(ts.getAttribute("spacing"), 0);

            info.tileWidth  = ts.hasAttribute("tilewidth")  ? parseIntOr(ts.getAttribute("tilewidth"),  tileWidth)  : tileWidth;
            info.tileHeight = ts.hasAttribute("tileheight") ? parseIntOr(ts.getAttribute("tileheight"), tileHeight) : tileHeight;

            if (ts.hasAttribute("columns")) {
                info.columns = parseIntOr(ts.getAttribute("columns"), 1);
            } else {
                int imgW = info.image.getWidth();
                info.columns = Math.max(1, (imgW - info.margin + info.spacing) / (info.tileWidth + info.spacing));
            }

            if (ts.hasAttribute("tilecount")) {
                info.tileCount = parseIntOr(ts.getAttribute("tilecount"), 0);
            } else {
                int imgH = info.image.getHeight();
                int rows = Math.max(1, (imgH - info.margin + info.spacing) / (info.tileHeight + info.spacing));
                info.tileCount = info.columns * rows;
            }

            tilesets.add(info);
        }

        // ---- فقط لایهٔ اول تصویری ----
        Element layer = (Element) mapElem.getElementsByTagName("layer").item(0);
        int width  = Integer.parseInt(layer.getAttribute("width"));
        int height = Integer.parseInt(layer.getAttribute("height"));

        String data = layer.getElementsByTagName("data").item(0).getTextContent().trim();
        String[] tokens = data.split(",");

        CityMap cityMap = new CityMap(width, height, tileWidth, tileHeight);

        // ---- پر کردن نقشه ----
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String tok = tokens[y * width + x].trim();
                if (tok.isEmpty()) continue;

                long raw = Long.parseUnsignedLong(tok);
                int gid = (int) (raw & GID_MASK);
                if (gid == 0) continue; // خانه خالی

                // tileset مالک
                TilesetInfo owner = null;
                for (TilesetInfo ts : tilesets) {
                    if (ts.owns(gid)) { owner = ts; break; }
                }
                if (owner == null) continue;

                BufferedImage tileImage = owner.getSubImage(gid);

                // --- خواندن property ها ---
                Cell.Type type = Cell.Type.EMPTY;       // پیش‌فرض: غیرقابل عبور
                boolean walkable = false;
                Map<String, String> propMap = new HashMap<>();

                int localId = gid - owner.firstGid;
                Element tileElem = owner.findTileElement(localId);
                if (tileElem != null) {
                    NodeList props = tileElem.getElementsByTagName("property");
                    for (int pi = 0; pi < props.getLength(); pi++) {
                        Element prop = (Element) props.item(pi);
                        String name = prop.getAttribute("name");
                        String value = prop.getAttribute("value");
                        if (name == null) continue;

                        propMap.put(name, value);

                        if (name.equalsIgnoreCase("type")) {
                            if ("road".equalsIgnoreCase(value) ||
                                    "sidewalk".equalsIgnoreCase(value) ||
                                    "ground".equalsIgnoreCase(value)) {
                                type = Cell.Type.ROAD;
                            } else if ("hospital".equalsIgnoreCase(value) ||
                                    "clinic".equalsIgnoreCase(value)) {
                                type = Cell.Type.HOSPITAL;
                            } else if ("building".equalsIgnoreCase(value)) {
                                type = Cell.Type.BUILDING;
                            } else if ("obstacle".equalsIgnoreCase(value) ||
                                    "car".equalsIgnoreCase(value) ||
                                    "wall".equalsIgnoreCase(value) ||
                                    "rubble".equalsIgnoreCase(value) ||
                                    "debris".equalsIgnoreCase(value)) {
                                type = Cell.Type.OBSTACLE;
                            } else if ("empty".equalsIgnoreCase(value)) {
                                type = Cell.Type.EMPTY;
                            }
                        } else if (name.equalsIgnoreCase("walkable")) {
                            walkable = "true".equalsIgnoreCase(value) || "1".equals(value);
                        }
                    }
                }

                // اگر CityMap متد ثبت پراپرتی‌ها را داشت، ذخیره کن (اختیاری)
                try {
                    cityMap.getClass()
                            .getMethod("registerTileProperties", int.class, Map.class)
                            .invoke(cityMap, gid, propMap);
                } catch (Throwable ignored) {}

                // --- هماهنگ‌سازی نهایی با walkable ---
                if (walkable && type == Cell.Type.EMPTY) {
                    type = Cell.Type.ROAD;
                } else if (!walkable && (type == Cell.Type.ROAD || type == Cell.Type.EMPTY)) {
                    type = Cell.Type.OBSTACLE;
                }

                Cell cell = new Cell(new Position(x, y), type, tileImage, gid);
                cityMap.setCell(x, y, cell);
            }
        }

        return cityMap;
    }

    /** سازگاری با نسخه‌های قدیمی‌تر. */
    public static CityMap loadMap(String tmxPath) throws Exception {
        return loadTMX(tmxPath);
    }

    /* =========================
       متدهای کمکی خواندن ObjectGroup برای KeyPoints/Spawns
       ========================= */

    /** برگرداندن اولین آبجکت با name دقیق (به مختصات تایل). */
    public static Position findObject(String tmxPath, String groupName, String objectName) {
        try {
            Document doc = parseXML(tmxPath);
            Element map = (Element) doc.getElementsByTagName("map").item(0);
            int tileW = Integer.parseInt(map.getAttribute("tilewidth"));
            int tileH = Integer.parseInt(map.getAttribute("tileheight"));

            NodeList groups = doc.getElementsByTagName("objectgroup");
            for (int gi = 0; gi < groups.getLength(); gi++) {
                Element g = (Element) groups.item(gi);
                if (!groupName.equals(g.getAttribute("name"))) continue;

                NodeList objs = g.getElementsByTagName("object");
                for (int oi = 0; oi < objs.getLength(); oi++) {
                    Element o = (Element) objs.item(oi);
                    if (objectName.equals(o.getAttribute("name"))) {
                        int x = (int)Math.round(Double.parseDouble(o.getAttribute("x")) / tileW);
                        int y = (int)Math.round(Double.parseDouble(o.getAttribute("y")) / tileH);
                        return new Position(x, y);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** اگر نبود، مقدار پیش‌فرض بده. */
    public static Position findObjectOrDefault(String tmxPath, String group, String name, Position def) {
        Position p = findObject(tmxPath, group, name);
        return (p != null) ? p : def;
    }

    /** همهٔ آبجکت‌هایی که type مشخص دارند را (در یک objectgroup خاص) برمی‌گرداند. */
    public static List<Position> findObjectsByType(String tmxPath, String groupName, String type) {
        List<Position> out = new ArrayList<>();
        try {
            Document doc = parseXML(tmxPath);
            Element map = (Element) doc.getElementsByTagName("map").item(0);
            int tileW = Integer.parseInt(map.getAttribute("tilewidth"));
            int tileH = Integer.parseInt(map.getAttribute("tileheight"));

            NodeList groups = doc.getElementsByTagName("objectgroup");
            for (int gi = 0; gi < groups.getLength(); gi++) {
                Element g = (Element) groups.item(gi);
                if (!groupName.equals(g.getAttribute("name"))) continue;

                NodeList objs = g.getElementsByTagName("object");
                for (int oi = 0; oi < objs.getLength(); oi++) {
                    Element o = (Element) objs.item(oi);
                    String t = o.getAttribute("type");
                    if (type.equals(t)) {
                        int x = (int)Math.round(Double.parseDouble(o.getAttribute("x")) / tileW);
                        int y = (int)Math.round(Double.parseDouble(o.getAttribute("y")) / tileH);
                        out.add(new Position(x, y));
                    }
                }
            }
        } catch (Exception ignored) {}
        return out;
    }

    /* =========================
       Helpers
       ========================= */

    private static Document parseXML(String path) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(false);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new File(path));
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (Exception e) { return def; }
    }
}
