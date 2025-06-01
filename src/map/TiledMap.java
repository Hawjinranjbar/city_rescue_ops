package map;

public class TiledMap {
    public final int width, height;         // تعداد خانه‌ها
    public final int tileWidth, tileHeight; // اندازه هر تایل در TMX
    public final int[][] gids;              // [y][x] شناسه‌ی تایل‌ها

    // مشخصات tileset
    public final String tilesetImagePath;   // مسیر عکس tileset (resolve‌شده)
    public final int firstGid;
    public final int margin;
    public final int spacing;
    public final int columns;               // تعداد ستون‌های tileset
    public final int imageWidth, imageHeight;

    public TiledMap(int width, int height, int tileWidth, int tileHeight,
                    int[][] gids,
                    String tilesetImagePath, int firstGid,
                    int margin, int spacing, int columns, int imageWidth, int imageHeight) {
        this.width = width;
        this.height = height;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.gids = gids;
        this.tilesetImagePath = tilesetImagePath;
        this.firstGid = firstGid;
        this.margin = margin;
        this.spacing = spacing;
        this.columns = columns;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }
}
