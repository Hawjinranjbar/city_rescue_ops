// ui/MapRenderer.java
package ui;

import map.CityMap;
import map.Cell;
import util.AssetLoader;
import util.Position;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MapRenderer: خواندن یک TMX و رندر روی JPanel
 * - همهٔ tileset ها و همهٔ layer های نوع "tilelayer" با encoding=CSV را می‌خواند (به‌ترتیب).
 * - TSX خارجی، firstgid، margin/spacing، columns و tilecount پشتیبانی می‌شود.
 * - فلگ‌های Flip افقی/عمودی هندل می‌شوند (diagonal اگر وجود داشت، اخطار چاپ می‌شود).
 *
 * نکته: برای سازگاری، کانستراکتورهای قدیمی (با tilesetPath) هم نگه داشته شده‌اند،
 *       اما اگر TMX خودش image دارد، از همان استفاده می‌شود.
 */
public class MapRenderer extends JPanel {

    /** برای نگاشت GID به نوع سلول (اختیاری) */
    @FunctionalInterface
    public interface TileTypeResolver {
        Cell.Type resolve(int gid);
    }

    // ----- دادهٔ نقشه -----
    private final CityMap map;                  // صرفاً برای ابعاد و سایز تایل
    private final List<TileLayer> layers;       // لایه‌های رندرشدنی
    private final List<TilesetInfo> tilesets;   // اطلاعات هر tileset (کافی برای برش subimage)

    // ===== سازنده‌ها =====

    // ساده‌ترین سازنده: از TMX همه‌چیز خوانده می‌شود (بدون تعیین type)
    public MapRenderer(String tmxPath) throws Exception {
        this(tmxPath, (String) null, gid -> Cell.Type.EMPTY);
    }

    public MapRenderer(String tmxPath, TileTypeResolver resolver) throws Exception {
        this(tmxPath, (String) null, resolver);
    }

    // سازندهٔ قدیمی: tilesetPath اگر داده شود و TMX تصویر نداشته باشد، استفاده می‌شود.
    public MapRenderer(String tmxPath, String tilesetPath) throws Exception {
        this(tmxPath, tilesetPath, gid -> Cell.Type.EMPTY);
    }

    // سازندهٔ کامل با تعیین نوع سلول از روی GID (برای پر کردن لایه پایه در CityMap)
    public MapRenderer(String tmxPath, String tilesetPath, TileTypeResolver resolver) throws Exception {
        LoaderResult lr = loadTMXAll(tmxPath, tilesetPath, resolver);
        this.map = lr.cityMap;
        this.layers = lr.layers;
        this.tilesets = lr.tilesets;
        setPreferredSize(new Dimension(map.getWidth() * map.getTileWidth(),
                map.getHeight() * map.getTileHeight()));
        setDoubleBuffered(true);
    }

    public CityMap getMap() { return map; }

    // ===== رندر =====
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (map == null) return;

        Graphics2D g2 = (Graphics2D) g;
        final int tw = map.getTileWidth();
        final int th = map.getTileHeight();

        // به ترتیب لایه‌ها رندر کن
        for (TileLayer layer : layers) {
            if (!layer.visible) continue;

            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    long raw = layer.gid[y][x];
                    if (raw == 0) continue;

                    // فلگ‌های flip را جدا کن
                    final long FLIP_H = 0x80000000L;
                    final long FLIP_V = 0x40000000L;
                    final long FLIP_D = 0x20000000L;
                    boolean fh = (raw & FLIP_H) != 0;
                    boolean fv = (raw & FLIP_V) != 0;
                    boolean fd = (raw & FLIP_D) != 0;

                    int gid = (int) (raw & 0x0FFFFFFFL);
                    BufferedImage img = getTileImage(gid);
                    if (img == null) continue;

                    int dx = x * tw;
                    int dy = y * th;

                    if (!fh && !fv && !fd) {
                        g2.drawImage(img, dx, dy, tw, th, null);
                    } else {
                        // فقط H و V را کامل پشتیبانی می‌کنیم؛ Diagonal هشدار می‌دهد
                        if (fd) {
                            // برای سادگی: فعلاً بدون چرخش خاص رسم می‌کنیم
                            // (اگر نیاز شد می‌توانیم مپ‌کردن کامل rotation 90/180/270 را اضافه کنیم)
                            System.err.println("[MapRenderer] Diagonal flip flag detected on ("+x+","+y+") - drawing without diagonal transform.");
                        }
                        AffineTransform at = new AffineTransform();
                        // مقصد: dx,dy به‌عنوان مبنا
                        at.translate(dx, dy);

                        double sx = fh ? -1.0 : 1.0;
                        double sy = fv ? -1.0 : 1.0;
                        // برای scale منفی، باید جابجا کنیم تا تصویر در جای درست بیفتد
                        if (fh) at.translate(tw, 0);
                        if (fv) at.translate(0, th);

                        at.scale(sx * (tw / (double) img.getWidth()),
                                sy * (th / (double) img.getHeight()));

                        g2.drawImage(img, at, null);
                    }
                }
            }
        }
    }

    // ====== مدل داده‌های داخلی ======

    private static class TilesetInfo {
        int firstGid;
        int tileWidth;
        int tileHeight;
        int columns;
        int tileCount;
        int margin;
        int spacing;
        BufferedImage image;
        File baseDir; // برای TSX های جداگانه

        BufferedImage getSubImage(int gid) {
            int localId = gid - firstGid;
            if (localId < 0 || localId >= tileCount) return null;
            int col = localId % columns;
            int row = localId / columns;
            int sx = margin + col * (tileWidth + spacing);
            int sy = margin + row * (tileHeight + spacing);
            if (sx + tileWidth > image.getWidth() || sy + tileHeight > image.getHeight()) return null;
            return image.getSubimage(sx, sy, tileWidth, tileHeight);
        }

        boolean owns(int gid) {
            return gid >= firstGid && gid < firstGid + tileCount;
        }
    }

    private static class TileLayer {
        String name;
        boolean visible = true;
        long[][] gid; // [y][x] شامل فلگ‌های flip
    }

    private static class LoaderResult {
        CityMap cityMap;
        List<TilesetInfo> tilesets;
        List<TileLayer> layers;
    }

    // ====== Load TMX (همهٔ tileset/layer) ======
    private LoaderResult loadTMXAll(String tmxPath, String tilesetOverride, TileTypeResolver resolver) throws Exception {
        Document doc = parseXML(new File(tmxPath));
        Element mapElem = doc.getDocumentElement();
        File baseDir = new File(tmxPath).getParentFile();

        int tileWidth  = parseInt(mapElem.getAttribute("tilewidth"));
        int tileHeight = parseInt(mapElem.getAttribute("tileheight"));

        // ---- tilesets ----
        NodeList tsNodes = mapElem.getElementsByTagName("tileset");
        List<TilesetInfo> tilesets = new ArrayList<>();

        for (int i = 0; i < tsNodes.getLength(); i++) {
            Element ts = (Element) tsNodes.item(i);

            TilesetInfo info = new TilesetInfo();
            info.firstGid   = parseInt(ts.getAttribute("firstgid"));
            info.margin     = ts.hasAttribute("margin")  ? parseInt(ts.getAttribute("margin"))  : 0;
            info.spacing    = ts.hasAttribute("spacing") ? parseInt(ts.getAttribute("spacing")) : 0;

            // TSX خارجی؟
            String tsxSource = ts.getAttribute("source");
            if (tsxSource != null && !tsxSource.isEmpty()) {
                File tsxFile = new File(baseDir, tsxSource);
                parseTSX(tsxFile, info);
            } else {
                // از خود TMX بخوان
                info.tileWidth  = ts.hasAttribute("tilewidth")  ? parseInt(ts.getAttribute("tilewidth"))  : tileWidth;
                info.tileHeight = ts.hasAttribute("tileheight") ? parseInt(ts.getAttribute("tileheight")) : tileHeight;
                Element img = (Element) ts.getElementsByTagName("image").item(0);
                if (img == null) continue;

                String src = img.getAttribute("source");
                BufferedImage tilesetImage;
                if (src != null && !src.isEmpty()) {
                    tilesetImage = AssetLoader.requireImage(new File(baseDir, src).getPath());
                } else if (tilesetOverride != null) {
                    tilesetImage = AssetLoader.requireImage(tilesetOverride);
                } else {
                    throw new IllegalStateException("Tileset image source missing");
                }
                info.image = tilesetImage;

                // columns/tilecount
                if (ts.hasAttribute("columns")) {
                    info.columns = parseInt(ts.getAttribute("columns"));
                } else {
                    info.columns = Math.max(1, (tilesetImage.getWidth() - info.margin + info.spacing) / (info.tileWidth + info.spacing));
                }

                if (ts.hasAttribute("tilecount")) {
                    info.tileCount = parseInt(ts.getAttribute("tilecount"));
                } else {
                    int rows = Math.max(1, (tilesetImage.getHeight() - info.margin + info.spacing) / (info.tileHeight + info.spacing));
                    info.tileCount = info.columns * rows;
                }
            }
            tilesets.add(info);
        }

        // ---- tile layers ----
        List<TileLayer> layers = new ArrayList<>();
        NodeList layerNodes = mapElem.getElementsByTagName("layer");
        int mapW = -1, mapH = -1;

        for (int i = 0; i < layerNodes.getLength(); i++) {
            Element layer = (Element) layerNodes.item(i);
            String name = layer.getAttribute("name");
            int w = parseInt(layer.getAttribute("width"));
            int h = parseInt(layer.getAttribute("height"));
            if (mapW < 0) { mapW = w; mapH = h; }

            boolean visible = !layer.hasAttribute("visible") || !"0".equals(layer.getAttribute("visible"));

            Element data = (Element) layer.getElementsByTagName("data").item(0);
            if (data == null) continue;
            String enc = data.getAttribute("encoding");
            if (!"csv".equalsIgnoreCase(enc)) {
                throw new IllegalStateException("Only CSV-encoded tile layers are supported (layer: " + name + ")");
            }

            long[][] grid = readCsvToGridLong(data.getTextContent(), w, h);

            TileLayer tl = new TileLayer();
            tl.name = (name == null || name.isEmpty()) ? ("Layer" + i) : name;
            tl.visible = visible;
            tl.gid = grid;
            layers.add(tl);
        }

        if (mapW < 0 || mapH < 0) {
            throw new IllegalStateException("No tile layer found in TMX");
        }

        CityMap cityMap = new CityMap(mapW, mapH, tileWidth, tileHeight);

        // برای سازگاری، لایهٔ اول را به‌صورت «تصویر پایه» وارد CityMap می‌کنیم
        if (!layers.isEmpty()) {
            TileLayer base = layers.get(0);
            for (int y = 0; y < mapH; y++) {
                for (int x = 0; x < mapW; x++) {
                    int gid = (int) (base.gid[y][x] & 0x0FFFFFFFL);
                    if (gid == 0) continue;
                    BufferedImage tileImg = getTileImage(tilesets, gid);
                    if (tileImg == null) continue;
                    Cell.Type type = (resolver != null) ? resolver.resolve(gid) : Cell.Type.EMPTY;
                    cityMap.setCell(x, y, new Cell(new Position(x, y), type, tileImg, gid));
                }
            }
        }

        LoaderResult out = new LoaderResult();
        out.cityMap = cityMap;
        out.tilesets = tilesets;
        out.layers = layers;
        return out;
    }

    private BufferedImage getTileImage(int gid) {
        return getTileImage(this.tilesets, gid);
    }
    private static BufferedImage getTileImage(List<TilesetInfo> tilesets, int gid) {
        for (int i = tilesets.size() - 1; i >= 0; i--) {
            TilesetInfo ts = tilesets.get(i);
            if (ts.owns(gid)) return ts.getSubImage(gid);
        }
        return null;
    }

    // ====== TSX Parser (کمینه) ======
    private static void parseTSX(File tsxFile, TilesetInfo out) throws Exception {
        Document doc = parseXML(tsxFile);
        Element ts = (Element) doc.getElementsByTagName("tileset").item(0);
        if (ts == null) throw new IllegalStateException("Invalid TSX: no <tileset>");

        out.tileWidth  = parseInt(ts.getAttribute("tilewidth"));
        out.tileHeight = parseInt(ts.getAttribute("tileheight"));
        out.margin     = ts.hasAttribute("margin")  ? parseInt(ts.getAttribute("margin"))  : 0;
        out.spacing    = ts.hasAttribute("spacing") ? parseInt(ts.getAttribute("spacing")) : 0;
        out.columns    = parseInt(ts.getAttribute("columns"));
        out.tileCount  = parseInt(ts.getAttribute("tilecount"));

        Element img = (Element) ts.getElementsByTagName("image").item(0);
        if (img == null) throw new IllegalStateException("Invalid TSX: no <image>");
        String src = img.getAttribute("source");
        out.image = AssetLoader.requireImage(new File(tsxFile.getParentFile(), src).getPath());
    }

    // ====== CSV و XML Helpers ======
    private static long[][] readCsvToGridLong(String csv, int w, int h) {
        String[] lines = csv.trim().split("\\R+");
        List<Long> flat = new ArrayList<>(w * h);
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] toks = t.split(",");
            for (String s : toks) {
                String ss = s.trim();
                if (!ss.isEmpty()) {
                    // مقدار می‌تواند از نوع long باشد (به‌خاطر بیت‌های flip)
                    long v = Long.decode(ss);
                    flat.add(v);
                }
            }
        }
        if (flat.size() != w * h)
            throw new IllegalStateException("CSV size mismatch: expected " + (w * h) + " got " + flat.size());
        long[][] out = new long[h][w];
        int idx = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                out[y][x] = flat.get(idx++);
        return out;
    }

    private static Document parseXML(File f) throws Exception {
        DocumentBuilderFactory fac = DocumentBuilderFactory.newInstance();
        fac.setNamespaceAware(false);
        DocumentBuilder b = fac.newDocumentBuilder();
        return b.parse(f);
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    // نمونهٔ ساده از resolver برای نگاشت GID به نوع سلول (دلخواه)
    public static TileTypeResolver sampleResolver(Map<Integer, Cell.Type> gidToType) {
        Map<Integer, Cell.Type> mapCopy = new LinkedHashMap<>(gidToType);
        return gid -> mapCopy.getOrDefault(gid, Cell.Type.EMPTY);
    }
}
