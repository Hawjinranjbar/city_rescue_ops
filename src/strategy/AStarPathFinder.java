package strategy;

import map.CityMap;
import map.Cell;
import util.Position;

import java.util.*;

// --------------------
// لایه: strategy Layer
// --------------------
// این کلاس الگوریتم A* رو برای مسیر‌یابی بین دو نقطه پیاده‌سازی می‌کنه
// از CityMap برای بررسی همسایه‌ها استفاده می‌کنه
public class AStarPathFinder implements IPathFinder {

    private final CityMap cityMap;

    public AStarPathFinder(CityMap cityMap) {
        this.cityMap = cityMap;
    }

    @Override
    public List<Position> findPath(Position start, Position goal) {
        Map<Position, Position> cameFrom = new HashMap<>();
        Map<Position, Integer> gScore = new HashMap<>();
        Map<Position, Integer> fScore = new HashMap<>();

        PriorityQueue<Position> openSet = new PriorityQueue<>(Comparator.comparingInt(fScore::get));
        gScore.put(start, 0);
        fScore.put(start, heuristic(start, goal));
        openSet.add(start);

        while (!openSet.isEmpty()) {
            Position current = openSet.poll();
            if (current.equals(goal)) {
                return reconstructPath(cameFrom, current);
            }

            for (Position neighbor : cityMap.getWalkableNeighbors(current)) {
                int tentativeG = gScore.get(current) + 1; // فرض می‌کنیم هزینه هر قدم ۱ باشه
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    fScore.put(neighbor, tentativeG + heuristic(neighbor, goal));
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        return Collections.emptyList(); // مسیر پیدا نشد
    }

    // تابع تخمینی فاصله (heuristic) با Manhattan distance
    private int heuristic(Position a, Position b) {
        return a.distanceTo(b);
    }

    // بازسازی مسیر از انتها به ابتدا
    private List<Position> reconstructPath(Map<Position, Position> cameFrom, Position current) {
        List<Position> path = new ArrayList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }
}
