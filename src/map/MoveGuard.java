package map;

import util.Position;

/**
 * بررسی سادهٔ امکان حرکت روی نقشه بر اساس property های تایل.
 */
public class MoveGuard {

    private final CityMap map;

    public MoveGuard(CityMap map) {
        this.map = map;
    }

    /**
     * آیا می‌توان به موقعیت موردنظر حرکت کرد؟
     * شرط: وجود تایل، walkable=true و type یکی از road/sidewalk.
     */
    public boolean canMoveTo(Position pos) {
        if (map == null || pos == null) return false;
        int x = pos.getX();
        int y = pos.getY();
        if (!map.isValid(x, y)) return false;

        int gid = map.getTileId(x, y);
        if (gid <= 0) return false;

        String walk = map.getTileProperty(gid, "walkable");
        if (!("true".equalsIgnoreCase(walk) || "1".equals(walk))) return false;

        String type = map.getTileProperty(gid, "type");
        return "road".equalsIgnoreCase(type) || "sidewalk".equalsIgnoreCase(type);
    }
}
