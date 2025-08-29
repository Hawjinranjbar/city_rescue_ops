package map;

import util.AssetLoader;
import util.Position;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Loader ساده برای TMX (نقشه‌های Tiled).
 * - فقط لایه‌ی اول را می‌خواند (CSV)
 * - tileset داخلی (inline) با یک <image>
 * - تعیین نوع Cell بر اساس property تایل‌ها:
 *     property name="type"  → road/hospital/building/obstacle/empty
 *     property name="walkable" → true/false
 * - اگر walkable=true و type نامشخص بود، ROAD درنظر می‌گیرد.
 */
public final class MapLoader {

    private MapLoader() { }

    /** ماسک بیت‌های flip در TMX (30 بیت پایین = GID واقعی) */
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
        Element tilesetElement; // برای properties تایل‌ها

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

        /** پیدا کردن عنصر <tile id="..."> برای localId */
        Element findTileElement(int localId) {
            NodeList tileNodes = tilesetElement.getElementsByTagName("tile");
            for (int ti = 0; ti < tileNodes.getLength(); ti++) {
                Element tileElem = (Element) tileNodes.item(ti);
                String idAttr = tileElem.getAttribute("id");
                if (idAttr == null || idAttr.isEmpty()) continue;
                int id = Integer.parseInt(idAttr);
                if (id == localId) return tileElem;
            }
            return null;
        }
    }

    public static CityMap loadTMX(String tmxPath) throws Exception {
        File tmxFile = new File(tmxPath);
        File baseDir = tmxFile.getParentFile();

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(tmxFile);
        Element mapElem = doc.getDocumentElement();

        final int tileWidth  = Integer.parseInt(mapElem.getAttribute("tilewidth"));
        final int tileHeight = Integer.parseInt(mapElem.getAttribute("tileheight"));

        // ---------- خواندن tileset ها ----------
        NodeList tsNodes = mapElem.getElementsByTagName("tileset");
        List<TilesetInfo> tilesets = new ArrayList<>();

        for (int i = 0; i < tsNodes.getLength(); i++) {
            Element ts = (Element) tsNodes.item(i);

            TilesetInfo info = new TilesetInfo();
            info.tilesetElement = ts;

            // firstgid الزاماً روی عنصر <tileset> است
            info.firstGid = Integer.parseInt(ts.getAttribute("firstgid"));

            // اگر tilecount/columns داخل tileset تعریف نشده بودند، از image اندازه می‌گیریم
            Element img = (Element) ts.getElementsByTagName("image").item(0);
            String src = img.getAttribute("source");
            File imgFile = new File(baseDir, src);
            info.image = AssetLoader.requireImage(imgFile.getPath());

            // اگر margin/spacing در تایل‌ست تعریف شده:
            if (ts.hasAttribute("margin"))  info.margin  = parseIntOr(ts.getAttribute("margin"), 0);
            if (ts.hasAttribute("spacing")) info.spacing = parseIntOr(ts.getAttribute("spacing"), 0);

            // اندازه پیکسل هر تایل (اگر در TS تعریف نشده باشد، از map می‌گیریم)
            info.tileWidth  = ts.hasAttribute("tilewidth")  ? Integer.parseInt(ts.getAttribute("tilewidth"))  : tileWidth;
            info.tileHeight = ts.hasAttribute("tileheight") ? Integer.parseInt(ts.getAttribute("tileheight")) : tileHeight;

            // محاسبه columns/tilecount اگر نبود
            if (ts.hasAttribute("columns")) {
                info.columns = Integer.parseInt(ts.getAttribute("columns"));
            } else {
                int imgW = info.image.getWidth();
                info.columns = Math.max(1, (imgW - info.margin + info.spacing) / (info.tileWidth + info.spacing));
            }
            if (ts.hasAttribute("tilecount")) {
                info.tileCount = Integer.parseInt(ts.getAttribute("tilecount"));
            } else {
                int imgH = info.image.getHeight();
                int rows = Math.max(1, (imgH - info.margin + info.spacing) / (info.tileHeight + info.spacing));
                info.tileCount = info.columns * rows;
            }

            tilesets.add(info);
        }

        // ---------- فقط لایهٔ اول ----------
        Element layer = (Element) mapElem.getElementsByTagName("layer").item(0);
        int width  = Integer.parseInt(layer.getAttribute("width"));
        int height = Integer.parseInt(layer.getAttribute("height"));

        String data = layer.getElementsByTagName("data").item(0).getTextContent().trim();
        String[] tokens = data.split(",");

        CityMap cityMap = new CityMap(width, height, tileWidth, tileHeight);

        // ---------- پر کردن نقشه ----------
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String s = tokens[y * width + x].trim();
                if (s.isEmpty()) continue;

                long raw = Long.parseUnsignedLong(s);
                int gid = (int) (raw & GID_MASK);
                if (gid == 0) continue; // خانه خالی

                // پیدا کردن tileset مالک این GID
                TilesetInfo owner = null;
                for (TilesetInfo ts : tilesets) {
                    if (ts.owns(gid)) { owner = ts; break; }
                }
                if (owner == null) continue;

                BufferedImage tileImage = owner.getSubImage(gid);

                // --- تعیین نوع سلول از روی properties ---
                Cell.Type type = Cell.Type.OBSTACLE; // پیش‌فرض: غیرقابل عبور
                boolean walkable = false;

                int localId = gid - owner.firstGid;
                Element tileElem = owner.findTileElement(localId);
                if (tileElem != null) {
                    NodeList props = tileElem.getElementsByTagName("property");
                    for (int pi = 0; pi < props.getLength(); pi++) {
                        Element prop = (Element) props.item(pi);
                        String name = prop.getAttribute("name");
                        String value = prop.getAttribute("value");
                        if (name == null) continue;

                        if (name.equalsIgnoreCase("type")) {
                            if ("road".equalsIgnoreCase(value))       type = Cell.Type.ROAD;
                            else if ("hospital".equalsIgnoreCase(value)) type = Cell.Type.HOSPITAL;
                            else if ("building".equalsIgnoreCase(value)) type = Cell.Type.BUILDING;
                            else if ("obstacle".equalsIgnoreCase(value) || "car".equalsIgnoreCase(value) || "rubble".equalsIgnoreCase(value))
                                type = Cell.Type.OBSTACLE;
                            else if ("empty".equalsIgnoreCase(value))
                                type = Cell.Type.EMPTY;
                        } else if (name.equalsIgnoreCase("walkable")) {
                            walkable = Boolean.parseBoolean(value);
                        }
                    }
                }

                // اگر walkable=true بود اما type هنوز مانع است → ROAD
                if (walkable && type == Cell.Type.OBSTACLE) {
                    type = Cell.Type.ROAD;
                }

                // fallback خیلی ساده (اختیاری – بسته به GID):
                // if (type == Cell.Type.OBSTACLE) {
                //     if (gid == 25) type = Cell.Type.HOSPITAL;
                // }

                Cell cell = new Cell(new Position(x, y), type, tileImage, gid);
                cityMap.setCell(x, y, cell);
            }
        }

        return cityMap;
    }

    private static int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}
