package ru.nsu.yousightseeing.model;

import com.yandex.mapkit.geometry.Point;

public class RoutePoint {
    public Point location;
    public String name;

    public RoutePoint(Point location, String name) {
        this.location = location;
        this.name = name;
    }
}
