package strategy;

import util.Position;

import java.util.List;

// --------------------
// لایه: strategy Layer
// --------------------
// اینترفیس برای الگوریتم‌های مسیر‌یابی مثل A* یا Dijkstra
public interface IPathFinder {

    /**
     * پیدا کردن مسیر بین دو موقعیت.
     * @param from موقعیت شروع
     * @param to موقعیت مقصد
     * @return لیست موقعیت‌هایی که نجات‌دهنده باید طی کنه (شامل نقطهٔ مقصد)
     */
    List<Position> findPath(Position from, Position to);
}
