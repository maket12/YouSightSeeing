package ru.nsu.yousightseeing.utils;

import android.content.Context;

import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.runtime.image.ImageProvider;

import ru.nsu.yousightseeing.R;

public class MapPointHelper {
    private final Context context;
    private final MapView mapView;
    private PlacemarkMapObject startPointMarker;

    public MapPointHelper(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
    }

    /**
     * Отображает маркер стартовой точки на карте.
     */
    public void showStartPoint(Point startPoint) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        // Удаляем старый маркер старта, если он есть
        if (startPointMarker != null) {
            mapObjects.remove(startPointMarker);
            startPointMarker = null;
        }

        if (startPoint != null) {
            startPointMarker = mapObjects.addPlacemark(startPoint);
            ImageProvider.fromResource(context, R.drawable.pinm); // Замените my_position на нужную иконку
        }
    }

    /**
     * Удаляет маркер стартовой точки с карты.
     */
    public void removeStartMarker() {
        if (mapView != null && mapView.getMapWindow() != null && startPointMarker != null) {
            mapView.getMapWindow().getMap().getMapObjects().remove(startPointMarker);
            startPointMarker = null;
        }
    }
}