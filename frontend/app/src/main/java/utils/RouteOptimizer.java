package utils;

import com.yandex.mapkit.geometry.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Оптимизирует порядок точек маршрута используя алгоритм ближайшего соседа (Nearest Neighbor)
 */
public class RouteOptimizer {

    public static List<Point> optimize(List<Point> points) {
        if (points.size() <= 2) {
            return new ArrayList<>(points);
        }

        List<Point> optimized = new ArrayList<>();
        List<Point> remaining = new ArrayList<>(points);

        Point current = remaining.remove(0);
        optimized.add(current);

        while (!remaining.isEmpty()) {
            Point nearest = findNearest(current, remaining);
            remaining.remove(nearest);
            optimized.add(nearest);
            current = nearest;
        }

        return optimized;
    }

    private static Point findNearest(Point from, List<Point> points) {
        Point nearest = points.get(0);
        double minDistance = calculateDistance(from, nearest);

        for (Point candidate : points) {
            double distance = calculateDistance(from, candidate);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    private static double calculateDistance(Point p1, Point p2) {
        double dLat = p1.getLatitude() - p2.getLatitude();
        double dLon = p1.getLongitude() - p2.getLongitude();
        return Math.sqrt(dLat * dLat + dLon * dLon);
    }
}