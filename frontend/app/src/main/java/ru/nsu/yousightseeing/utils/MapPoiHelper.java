package ru.nsu.yousightseeing.utils;

import android.content.Context;

import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.runtime.image.ImageProvider;

import java.util.List;
import java.util.Set;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.PlacesApi;

public class MapPoiHelper {

    private final Context context;
    private final MapView mapView;

    public MapPoiHelper(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
    }

    /**
     * Очищает список POI и снимает выделение с маркеров.
     */
    public void clearNearbyPlaces(List<PlacemarkMapObject> poiMarkers, Set<PlacemarkMapObject> selectedMarkers) {
        if (mapView == null || mapView.getMapWindow() == null) return;
        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
        for (PlacemarkMapObject marker : poiMarkers) {
            mapObjects.remove(marker);
        }
        poiMarkers.clear();
        selectedMarkers.clear();
    }

    /**
     * Отображает места на карте из списка PlacesApi.Place.
     */
    public void displayNearbyPlaces(List<PlacesApi.Place> places, List<PlacemarkMapObject> poiMarkers, Set<PlacemarkMapObject> selectedMarkers, List<Point> selectedPoints) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        clearNearbyPlaces(poiMarkers, selectedMarkers);

        ImageProvider pinProvider = ImageProvider.fromResource(context, R.drawable.pinm);

        for (PlacesApi.Place place : places) {
            if (place.lat != 0 && place.lon != 0) {
                Point point = new Point(place.lat, place.lon);
                PlacemarkMapObject marker = mapObjects.addPlacemark(point);
                marker.setIcon(pinProvider);
                marker.setUserData(place);
                poiMarkers.add(marker);
            }
        }
    }

    public void displayGeneratedPlaces(List<PlacesApi.Place> places, List<PlacemarkMapObject> poiMarkers, Set<PlacemarkMapObject> selectedMarkers, List<Point> selectedPoints) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        clearNearbyPlaces(poiMarkers, selectedMarkers);
        selectedPoints.clear();

        ImageProvider pinProvider = ImageProvider.fromResource(context, android.R.drawable.btn_star_big_on);

        for (PlacesApi.Place place : places) {
            if (place.lat != 0 && place.lon != 0) {
                Point point = new Point(place.lat, place.lon);
                PlacemarkMapObject marker = mapObjects.addPlacemark(point);
                marker.setIcon(pinProvider);
                marker.setUserData(place);
                poiMarkers.add(marker);
                selectedMarkers.add(marker);
                selectedPoints.add(point);
            }
        }
    }

}