package ru.nsu.yousightseeing.features.route;

import android.content.Intent;
import android.util.Log;

import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.runtime.image.ImageProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.PlacesApi;
import ru.nsu.yousightseeing.api.RouteApi;
import ru.nsu.yousightseeing.features.MainActivity;
import ru.nsu.yousightseeing.features.main.MainUIManager;
import ru.nsu.yousightseeing.model.Route;
import ru.nsu.yousightseeing.utils.DistanceHelper;
import ru.nsu.yousightseeing.utils.MapPointHelper;
import ru.nsu.yousightseeing.utils.MapRouteHelper;
import ru.nsu.yousightseeing.utils.RouteOptimizer;

public class RouteController {

    private final MainActivity mainActivity;
    private final MainUIManager uiManager;
    private final RouteControllerCallback callback;
    private final MapRouteHelper mapRouteHelper;
    private final MapPointHelper mapPointHelper;

    private final List<Point> selectedPoints = new ArrayList<>();
    private final Set<PlacemarkMapObject> selectedMarkers = new HashSet<>();
    private final List<PlacemarkMapObject> customMarkers = new ArrayList<>();

    public interface RouteControllerCallback {
        void onRouteStateChanged();
        void showToast(String message);
        Point getStartPoint();
        Route getCurrentRoute();
        void setCurrentRoute(Route route);
    }

    public RouteController(MainActivity mainActivity, MainUIManager uiManager, MapRouteHelper mapRouteHelper, MapPointHelper mapPointHelper, RouteControllerCallback callback) {
        this.mainActivity = mainActivity;
        this.uiManager = uiManager;
        this.mapRouteHelper = mapRouteHelper;
        this.mapPointHelper = mapPointHelper;
        this.callback = callback;
    }

    public void togglePlaceInRoute(PlacemarkMapObject marker) {
        if (!(marker.getUserData() instanceof PlacesApi.Place)) return;
        PlacesApi.Place place = (PlacesApi.Place) marker.getUserData();
        Point placeLocation = new Point(place.lat, place.lon);

        if (selectedMarkers.contains(marker)) {
            // Remove from route
            selectedMarkers.remove(marker);
            selectedPoints.removeIf(p ->
                    Math.abs(p.getLatitude() - placeLocation.getLatitude()) < 1e-6 &&
                            Math.abs(p.getLongitude() - placeLocation.getLongitude()) < 1e-6
            );

            if (customMarkers.contains(marker)) {
                // For custom points, remove them completely
                uiManager.mapView.getMapWindow().getMap().getMapObjects().remove(marker);
                customMarkers.remove(marker);
            } else {
                // For POI, just reset the icon
                marker.setIcon(ImageProvider.fromResource(mainActivity, R.drawable.pinm));
            }

            String placeName = (place.name != null && !place.name.isEmpty()) ? place.name : String.format("%.5f, %.5f", place.lat, place.lon);
            callback.showToast(placeName + " убрано из маршрута");
        } else {
            // Add to route
            selectedMarkers.add(marker);
            selectedPoints.add(placeLocation);

            marker.setIcon(ImageProvider.fromResource(mainActivity, android.R.drawable.btn_star_big_on));

            String placeName = (place.name != null && !place.name.isEmpty()) ? place.name : String.format("%.5f, %.5f", place.lat, place.lon);
            callback.showToast(placeName + " добавлено (" + getManualSelectedPlacesCount() + ")");
        }

        callback.onRouteStateChanged();

        updateOptimalRoute();
    }

    public void buildOptimalRoute() {
        Point startPoint = callback.getStartPoint();
        if (getManualSelectedPlacesCount() < 2) {
            callback.showToast("Недостаточно точек для построения маршрута");
            return;
        }

        if (startPoint == null) {
            callback.showToast("Не выбрана стартовая точка маршрута");
            return;
        }

        List<Point> pointsToOptimize = new ArrayList<>();
        pointsToOptimize.add(startPoint);

        for (Point p : selectedPoints) {
            if (DistanceHelper.distanceInMeters(startPoint, p) > 5.0) {
                pointsToOptimize.add(p);
            }
        }

        Set<String> unique = new HashSet<>();
        List<Point> cleanedPoints = new ArrayList<>();
        for (Point p : pointsToOptimize) {
            String key = p.getLatitude() + "," + p.getLongitude();
            if (!unique.contains(key)) {
                unique.add(key);
                cleanedPoints.add(p);
            }
        }

        if (cleanedPoints.size() < 2) {
            callback.showToast("Недостаточно уникальных точек для построения маршрута");
            return;
        }

        callback.showToast("Построение оптимального маршрута...");

        List<Point> optimizedPoints = RouteOptimizer.optimize(cleanedPoints);

        RouteApi.calculateRoute(
                mainActivity,
                optimizedPoints,
                true,
                new RouteApi.RouteCallback() {
                    @Override
                    public void onSuccess(List<Point> routeCoordinates, double distance, double duration) {
                        mainActivity.runOnUiThread(() -> {
                            Intent intent = new Intent(mainActivity, RouteConfirmationActivity.class);

                            JSONArray routePointsJson = new JSONArray();
                            for (Point p : routeCoordinates) {
                                JSONArray pair = new JSONArray();
                                try {
                                    pair.put(p.getLongitude());
                                    pair.put(p.getLatitude());
                                    routePointsJson.put(pair);
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                            intent.putExtra(RouteConfirmationActivity.EXTRA_ROUTE_POINTS_JSON, routePointsJson.toString());

                            JSONArray placesJson = new JSONArray();
                            for (PlacemarkMapObject marker : selectedMarkers) {
                                if (marker.getUserData() instanceof PlacesApi.Place) {
                                    PlacesApi.Place place = (PlacesApi.Place) marker.getUserData();
                                    try {
                                        JSONObject placeObj = new JSONObject();
                                        placeObj.put("name", place.name);
                                        placeObj.put("lat", place.lat);
                                        placeObj.put("lon", place.lon);
                                        placesJson.put(placeObj);
                                    } catch (Exception e) {
                                        Log.e("RouteController", "Error creating place JSON", e);
                                    }
                                }
                            }
                            intent.putExtra(RouteConfirmationActivity.EXTRA_PLACES_JSON, placesJson.toString());

                            intent.putExtra(RouteConfirmationActivity.EXTRA_DISTANCE, distance);
                            intent.putExtra(RouteConfirmationActivity.EXTRA_DURATION, duration);

                            mainActivity.startActivity(intent);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        mainActivity.runOnUiThread(() ->
                                callback.showToast("Ошибка маршрута: " + message)
                        );
                    }
                }
        );
    }

    private void updateOptimalRoute() {
        Point startPoint = callback.getStartPoint();
        if (getManualSelectedPlacesCount() < 2) {
            mapRouteHelper.clearCurrentRouteOnly();
            callback.setCurrentRoute(null);
            callback.onRouteStateChanged();
            return;
        }

        if (startPoint == null) {
            return;
        }

        List<Point> pointsToOptimize = new ArrayList<>();
        pointsToOptimize.add(startPoint);
        for (Point p : selectedPoints) {
            if (DistanceHelper.distanceInMeters(startPoint, p) > 5.0) {
                pointsToOptimize.add(p);
            }
        }

        Set<String> unique = new HashSet<>();
        List<Point> cleanedPoints = new ArrayList<>();
        for (Point p : pointsToOptimize) {
            String key = p.getLatitude() + "," + p.getLongitude();
            if (!unique.contains(key)) {
                unique.add(key);
                cleanedPoints.add(p);
            }
        }

        if (cleanedPoints.size() < 2) {
            mapRouteHelper.clearCurrentRouteOnly();
            callback.setCurrentRoute(null);
            callback.onRouteStateChanged();
            return;
        }

        List<Point> optimizedPoints = RouteOptimizer.optimize(cleanedPoints);

        RouteApi.calculateRoute(
                mainActivity,
                optimizedPoints,
                true,
                new RouteApi.RouteCallback() {
                    @Override
                    public void onSuccess(List<Point> routeCoordinates, double distance, double duration) {
                        mainActivity.runOnUiThread(() -> {
                            Route newRoute = new Route(routeCoordinates, distance, duration);
                            callback.setCurrentRoute(newRoute);
                            mapRouteHelper.drawRoute(routeCoordinates);
                            callback.onRouteStateChanged();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        mainActivity.runOnUiThread(() ->
                                callback.showToast("Ошибка обновления маршрута: " + message)
                        );
                    }
                }
        );
    }


    public void addCustomPoint(Point point, String name) {
        MapObjectCollection mapObjects = uiManager.mapView.getMapWindow().getMap().getMapObjects();
        PlacemarkMapObject newMarker = mapObjects.addPlacemark(point);

        PlacesApi.Place newPlace = new PlacesApi.Place(name, point.getLatitude(), point.getLongitude());
        newMarker.setUserData(newPlace);

        customMarkers.add(newMarker);
        togglePlaceInRoute(newMarker);
    }

    public void reset() {
        selectedPoints.clear();
        selectedMarkers.clear();
        customMarkers.clear();
        callback.onRouteStateChanged();
    }

    public int getManualSelectedPlacesCount() {
        Point startPoint = callback.getStartPoint();
        int count = 0;
        for (Point p : selectedPoints) {
            if (startPoint == null || DistanceHelper.distanceInMeters(startPoint, p) >= 5.0) {
                count++;
            }
        }
        return count;
    }

    public List<Point> getSelectedPoints() {
        return selectedPoints;
    }

    public Set<PlacemarkMapObject> getSelectedMarkers() {
        return selectedMarkers;
    }

    public List<PlacemarkMapObject> getCustomMarkers() {
        return customMarkers;
    }
}
