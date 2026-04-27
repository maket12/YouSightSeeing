package ru.nsu.yousightseeing.utils;

import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MapResetHelper {

    public static void fullReset(
            MapView mapView,
            List<PlacemarkMapObject> poiMarkers,
            List<PlacemarkMapObject> customMarkers,
            Set<PlacemarkMapObject> selectedMarkers,
            MapRouteHelper mapRouteHelper,
            MapPointHelper mapPointHelper
    ) {
        if (mapView != null && mapView.getMapWindow() != null) {
            MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

            List<PlacemarkMapObject> markersToRemove = new ArrayList<>(poiMarkers);
            for (PlacemarkMapObject marker : markersToRemove) {
                mapObjects.remove(marker);
                poiMarkers.remove(marker);
            }

            for (PlacemarkMapObject marker : customMarkers) {
                mapObjects.remove(marker);
            }
            customMarkers.clear();

            mapRouteHelper.clearCurrentRouteOnly();
            mapPointHelper.removeStartMarker();
        }

        selectedMarkers.clear();
        poiMarkers.clear();
    }
}