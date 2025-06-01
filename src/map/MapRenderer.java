package ui;

import map.CityMap;
import map.Cell;
import util.AssetLoader;
import util.Position;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MapRenderer: خواندن یک TMX (CSV) و رندر روی JPanel
 * - فعلاً اولین tileset و اولین layer را می‌خواند (کافی برای شروع).
 * - margin/spacing و firstgid را از خود TMX استخراج می‌کند.
 * - اگر نیاز داشتی نوع سلول را از روی GID تعیین کنی، یک resolver بده (سازندهٔ دوم).
 */
public class MapRenderer extends JPanel {

    /** برای نگاشت GID به نوع سلول (اختیاری) */
    @FunctionalInterface
    public interface TileTypeResolver {
        Cell.Type resolve(int gid);
    }

    private final CityMap map;

    // سازندهٔ ساده: همهٔ سلول‌ها نوع EMPTY خواهند بود (فقط تصویر دارند).
    public MapRenderer(String tmxPath, String tilesetPath) throws Exception {
        this(tmxPath, tilesetPath, gid -> Cell.Type.EMPTY);
    }

    // سازندهٔ کامل با تعیین نوع سلول از روی GID
    public MapRenderer(String tmxPath, String tilesetPath, TileTypeResolver resolver) throws Exception {
        this.map = loadTMX(tmxPath, tilesetPath, resolver);
        setPreferredSize(new Dimension(map.getWidth() * map.getTileWidth(),
                map.getHeight() * map.getTileHeight()));
    }

    public CityMap getMap() { return map; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        for (int y = 0; y < map.getHeight(); y++) {
            for (int x = 0; x < map.getWidth(); x++) {
                Cell cell = map.getCell(x, y);
                if (cell != null && cell.getImage() != null) {
                    g.drawImage(cell.getImage(),
                            x * map.getTileWidth(),
                            y * map.getTileHeight(),
                            null);
                }
            }
        }
    }

    // --------------------------------
    // خواندن و ساخت CityMap از TMX
    // --------------------------------
    private CityMap loadTMX(String tmxPath, String tilesetPath, TileTypeResolver resolver) throws Exception {
        // XML parse
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(tmxPath));
        Element mapElem = doc.getDocumentElement();

        // ابعاد تایل و نقشه
        int tileWidth  = Integer.parseInt(mapElem.getAttribute("tilewidth"));
        int tileHeight = Integer.parseInt(mapElem.getAttribute("tileheight"));

        // --- Tileset (فقط اولین tileset) ---
        Element ts = (Element) mapElem.getElementsByTagName("tileset").item(0);
        int firstGid = Integer.parseInt(ts.getAttribute("firstgid"));
        int margin   = ts.hasAttribute("margin")   ? Integer.parseInt(ts.getAttribute("margin"))   : 0;
        int spacing  = ts.hasAttribute("spacing")  ? Integer.parseInt(ts.getAttribute("spacing"))  : 0;

        // تصویر tileset
        BufferedImage tileset = AssetLoader.requireImage(tilesetPath);

        // ایندکس همهٔ تایل‌ها (localId -> image)
        Map<Integer, BufferedImage> tileIndex =
                AssetLoader.buildTilesetIndex(tileset, tileWidth, tileHeight, margin, spacing);

        // --- Layer (فقط اولین layer) ---
        Element layer = (Element) mapElem.getElementsByTagName("layer").item(0);
        int width  = Integer.parseInt(layer.getAttribute("width"));
        int height = Integer.parseInt(layer.getAttribute("height"));

        // داده‌ها به صورت CSV
        String data = layer.getElementsByTagName("data").item(0).getTextContent().trim();
        String[] gids = data.split(",");

        CityMap cityMap = new CityMap(width, height, tileWidth, tileHeight);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                String s = gids[y * width + x].trim();
                if (s.isEmpty()) continue;

                int gid = Integer.parseInt(s);
                if (gid <= 0) continue; // خانهٔ خالی در Tiled

                // image برای این gid
                BufferedImage tileImage = tileIndex.get(gid - firstGid);
                if (tileImage == null) continue;

                // نوع سلول از روی resolver (اگر ندی، EMPTY)
                Cell.Type type = resolver != null ? resolver.resolve(gid) : Cell.Type.EMPTY;

                // ساخت Cell
                Cell cell = new Cell(new Position(x, y), type, tileImage, gid);
                cityMap.setCell(x, y, cell);
            }
        }
        return cityMap;
    }

    // نمونهٔ ساده از resolver برای نگاشت GID به نوع سلول (دلخواه)
    // می‌تونی این متد رو حذف کنی؛ فقط مثال است.
    public static TileTypeResolver sampleResolver(Map<Integer, Cell.Type> gidToType) {
        // کَپی ایمن از نگاشت ورودی
        Map<Integer, Cell.Type> mapCopy = new LinkedHashMap<>(gidToType);
        return gid -> mapCopy.getOrDefault(gid, Cell.Type.EMPTY);
    }
}
