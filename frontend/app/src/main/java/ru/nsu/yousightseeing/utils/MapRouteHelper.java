package ru.nsu.yousightseeing.utils;

import android.content.Context;
import android.graphics.Color;

import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PolylineMapObject;
import com.yandex.mapkit.mapview.MapView;

import java.util.List;

import ru.nsu.yousightseeing.model.Route;

public class MapRouteHelper {
    private final Context context;
    private final MapView mapView;
    private PolylineMapObject currentRouteLine;

    public MapRouteHelper(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
    }

    public void drawRoute(List<Point> routeCoordinates) {
        if (mapView == null || mapView.getMapWindow() == null || routeCoordinates == null || routeCoordinates.isEmpty()) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        clearCurrentRouteOnly();

        Polyline polyline = new Polyline(routeCoordinates);
        currentRouteLine = mapObjects.addPolyline(polyline);
        currentRouteLine.setStrokeColor(Color.parseColor("#4A90E2"));
        currentRouteLine.setStrokeWidth(5f);
    }

    public void drawRoute(Route route) {
        if (route != null) {
            drawRoute(route.getPoints());
        }
    }

    public void clearCurrentRouteOnly() {
        if (mapView != null && mapView.getMapWindow() != null && currentRouteLine != null) {
            mapView.getMapWindow().getMap().getMapObjects().remove(currentRouteLine);
            currentRouteLine = null;
        }
    }
}
