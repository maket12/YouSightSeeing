package ru.nsu.yousightseeing.utils;

import com.yandex.mapkit.geometry.Point;

public class DistanceHelper {

    /**
     * Расстояние между двумя точками в градусах (~111м на градус)
     */
    public static double distanceBetween(Point p1, Point p2) {
        double latDiff = Math.abs(p1.getLatitude() - p2.getLatitude());
        double lonDiff = Math.abs(p1.getLongitude() - p2.getLongitude());
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }

    /**
     * Расстояние между двумя точками в метрах по формуле Haversine
     */
    public static double distanceInMeters(Point p1, Point p2) {
        double R = 6371000; // радиус Земли в метрах
        double lat1 = Math.toRadians(p1.getLatitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double dLat = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double dLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}