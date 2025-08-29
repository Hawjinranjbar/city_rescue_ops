package map;

import util.AssetLoader;
import util.Position;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * --------------------
 * لایه: Domain Layer
 * --------------------
 * Loader برای فایل‌های TMX (نقشه‌های Tiled).
 * - خواندن tileset
 * - خواندن لایه‌ها
 * - تعیین نوع Cell بر اساس property یا fallback ساده
 */
public class MapLoader {

    private static final long GID_MASK = 0x0FFFFFFFL;

    /** اطلاعات tileset */
    private static class TilesetInfo {
        int firstGid;
        int tileWidth;
        int tileHeight;
        int tileCount;
        int columns;
        BufferedImage image;
        int margin = 0, spacing = 0;
        Element tilesetElement; // برای خواندن propertyهای tile

        BufferedImage getSubImage(int gid) {
            int localId = gid - firstGid;
            if (localId < 0 || localId >= tileCount) return null;

            int col = localId % columns;
            int row = localId / columns;
            int x = margin + col * (tileWidth + spacing);
            int y = margin + row * (tileHeight + spacing);

            return image.getSubimage(x, y, tileWidth, tileHeight);
        }

        boolean owns(int gid) {
            return gid >= firstGid && gid < firstGid + tileCount;
        }
    }

    public static CityMap loadTMX(String tmxPath) throws Exception {
        File tmxFile = new File(tmxPath);
        File baseDir = tmxFile.getParentFile();

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        DocumentBuilder b = f.newDocumentBuilder();
        Document doc = b.parse(tmxFile);

        Element mapElem = doc.getDocumentElement();
        int tileWidth  = Integer.parseInt(mapElem.getAttribute("tilewidth"));
        int tileHeight = Integer.parseInt(mapElem.getAttribute("tileheight"));

        // --- tileset ها ---
        NodeList tsNodes = mapElem.getElementsByTagName("tileset");
        List<TilesetInfo> tilesets = new ArrayList<>();
        for (int i = 0; i < tsNodes.getLength(); i++) {
            Element ts = (Element) tsNodes.item(i);
            TilesetInfo info = new TilesetInfo();
            info.firstGid  = Integer.parseInt(ts.getAttribute("firstgid"));
            info.tileWidth = tileWidth;
            info.tileHeight= tileHeight;
            info.tileCount = Integer.parseInt(ts.getAttribute("tilecount"));
            info.columns   = Integer.parseInt(ts.getAttribute("columns"));
            info.tilesetElement = ts;

            // تصویر tileset
            Element img = (Element) ts.getElementsByTagName("image").item(0);
            String src = img.getAttribute("source");
            File imgFile = new File(baseDir, src);
            info.image = AssetLoader.requireImage(imgFile.getPath());

            tilesets.add(info);
        }

        // --- فقط لایه‌ی اول (فعلاً) ---
        Element layer = (Element) mapElem.getElementsByTagName("layer").item(0);
        int width  = Integer.parseInt(layer.getAttribute("width"));
        int height = Integer.parseInt(layer.getAttribute("height"));
        String data = layer.getElementsByTagName("data").item(0).getTextContent().trim();
        String[] tokens = data.split(",");

        CityMap cityMap = new CityMap(width, height, tileWidth, tileHeight);

        // --- پرکردن نقشه ---
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String s = tokens[y * width + x].trim();
                if (s.isEmpty()) continue;

                long raw = Long.parseUnsignedLong(s);
                int gid = (int) (raw & GID_MASK);
                if (gid == 0) continue;

                BufferedImage tileImage = null;
                TilesetInfo owner = null;
                for (TilesetInfo ts : tilesets) {
                    if (ts.owns(gid)) {
                        tileImage = ts.getSubImage(gid);
                        owner = ts;
                        break;
                    }
                }
                if (tileImage == null) continue;

                // --- تعیین نوع سلول ---
                Cell.Type type = Cell.Type.ROAD; // حالت پیش‌فرض

                // اگر property داشت، از آن بخوان
                if (owner != null) {
                    int localId = gid - owner.firstGid;
                    NodeList tileNodes = owner.tilesetElement.getElementsByTagName("tile");
                    for (int ti = 0; ti < tileNodes.getLength(); ti++) {
                        Element tileElem = (Element) tileNodes.item(ti);
                        int id = Integer.parseInt(tileElem.getAttribute("id"));
                        if (id == localId) {
                            NodeList propList = tileElem.getElementsByTagName("property");
                            for (int pi = 0; pi < propList.getLength(); pi++) {
                                Element prop = (Element) propList.item(pi);
                                String name = prop.getAttribute("name");
                                String value = prop.getAttribute("value");
                                if (name.equalsIgnoreCase("type")) {
                                    if ("road".equalsIgnoreCase(value)) type = Cell.Type.ROAD;
                                    else if ("hospital".equalsIgnoreCase(value)) type = Cell.Type.HOSPITAL;
                                    else if ("building".equalsIgnoreCase(value)) type = Cell.Type.BUILDING;
                                    else if ("car".equalsIgnoreCase(value) || "obstacle".equalsIgnoreCase(value))
                                        type = Cell.Type.OBSTACLE;
                                    else if ("rubble".equalsIgnoreCase(value)) type = Cell.Type.OBSTACLE;
                                    else type = Cell.Type.EMPTY;
                                }
                            }
                        }
                    }
                }

                // اگر property نداشت، می‌تونی با GID نوع را تخمین بزنی
                if (gid == 25) type = Cell.Type.HOSPITAL;
                else if (gid >= 50 && gid <= 70) type = Cell.Type.BUILDING;
                else if (gid >= 71 && gid <= 80) type = Cell.Type.OBSTACLE;

                Cell cell = new Cell(new Position(x, y), type, tileImage, gid);
                cityMap.setCell(x, y, cell);
            }
        }

        return cityMap;
    }
}
