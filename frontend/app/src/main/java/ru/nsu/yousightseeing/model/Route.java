package ru.nsu.yousightseeing.model;

import com.yandex.mapkit.geometry.Point;
import java.util.List;

/**
 * Модель данных для маршрута
 */
public class Route {
    private List<Point> points;
    private String name;
    private double distance;
    private double duration;
    private long createdAt;

    public Route(List<Point> points, double distance, double duration) {
        this.points = points;
        this.distance = distance;
        this.duration = duration;
        this.createdAt = System.currentTimeMillis();
    }

    public Route(List<Point> points, String name, double distance, double duration) {
        this.points = points;
        this.name = name;
        this.distance = distance;
        this.duration = duration;
        this.createdAt = System.currentTimeMillis();
    }

    public List<Point> getPoints() {
        return points;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getDuration() {
        return duration;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
