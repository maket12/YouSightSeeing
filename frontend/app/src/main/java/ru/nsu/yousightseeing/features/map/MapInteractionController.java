package ru.nsu.yousightseeing.features.map;

import android.app.AlertDialog;
import android.util.Log;

import androidx.annotation.Nullable;

import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.search.Response;
import com.yandex.mapkit.search.SearchOptions;
import com.yandex.mapkit.search.Session;
import com.yandex.runtime.Error;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.nsu.yousightseeing.api.PlacesApi;
import ru.nsu.yousightseeing.features.MainActivity;
import ru.nsu.yousightseeing.utils.DistanceHelper;
import ru.nsu.yousightseeing.utils.SearchHelper;

public class MapInteractionController {

    private final MainActivity mainActivity;
    private final MapInteractionCallback callback;
    private final SearchHelper searchHelper;
    private Session searchSession;

    public interface MapInteractionCallback {
        void onPoiTapped(PlacemarkMapObject marker);
        void onCustomPointTapped(Point point, @Nullable String name);
        List<PlacemarkMapObject> getPoiMarkers();
        List<PlacemarkMapObject> getCustomMarkers();
        Set<PlacemarkMapObject> getSelectedMarkers();
    }

    public MapInteractionController(MainActivity mainActivity, SearchHelper searchHelper, MapInteractionCallback callback) {
        this.mainActivity = mainActivity;
        this.searchHelper = searchHelper;
        this.callback = callback;
    }

    /**
     * Handles a tap on the map, deciding whether it hit a POI or empty space.
     */
    public void handleMapTap(Point point) {
        // Check if tap hit an existing POI or custom marker
        PlacemarkMapObject tappedMarker = findTappedMarker(point);

        if (tappedMarker != null) {
            // If a marker is tapped, show info to add/remove it
            showPoiInfoDialog(tappedMarker);
        } else {
            // If empty map space is tapped, add a new custom point
            addCustomPointFromTap(point);
        }
    }

    private PlacemarkMapObject findTappedMarker(Point tapPoint) {
        List<PlacemarkMapObject> allMarkers = new ArrayList<>(callback.getPoiMarkers());
        allMarkers.addAll(callback.getCustomMarkers());
        allMarkers.addAll(callback.getSelectedMarkers()); // Make sure to check selected markers too

        Set<PlacemarkMapObject> uniqueMarkers = new HashSet<>(allMarkers);

        for (PlacemarkMapObject marker : uniqueMarkers) {
            if (marker.getUserData() instanceof PlacesApi.Place) {
                PlacesApi.Place markerPlace = (PlacesApi.Place) marker.getUserData();
                if (markerPlace.lat != 0) {
                    double distance = DistanceHelper.distanceBetween(tapPoint, new Point(markerPlace.lat, markerPlace.lon));
                    // A reasonable tap radius on the map (approx. 20-30 meters on a mid-zoom level)
                    if (distance < 0.0005) {
                        return marker;
                    }
                }
            }
        }
        return null;
    }

    private void addCustomPointFromTap(final Point point) {
        mainActivity.showToast("Определение точки...");

        SearchOptions options = new SearchOptions();
        options.setResultPageSize(1);

        searchSession = searchHelper.getSearchManager().submit(
                point,
                16,
                options,
                new Session.SearchListener() {
                    @Override
                    public void onSearchResponse(Response response) {
                        String title = String.format(
                                "%.5f, %.5f",
                                point.getLatitude(),
                                point.getLongitude()
                        );
                        // In a real app, you might try to get a toponym from the response
                        mainActivity.runOnUiThread(() ->
                                showConfirmationForCustomPoint(point, title)
                        );
                    }

                    @Override
                    public void onSearchError(Error error) {
                        Log.e("MapInteractionController", "Reverse geocoding error: " + error.toString());
                        mainActivity.runOnUiThread(() ->
                                showConfirmationForCustomPoint(
                                        point,
                                        String.format("%.5f, %.5f",
                                                point.getLatitude(),
                                                point.getLongitude())
                                )
                        );
                    }
                }
        );
    }

    private void showConfirmationForCustomPoint(final Point point, @Nullable String name) {
        final String title = (name != null && !name.isEmpty())
                ? name
                : String.format("%.5f, %.5f", point.getLatitude(), point.getLongitude());

        new AlertDialog.Builder(mainActivity)
                .setTitle(title)
                .setMessage("Добавить в маршрут?")
                .setPositiveButton("Добавить", (dialog, which) -> {
                    callback.onCustomPointTapped(point, title);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    public void showPoiInfoDialog(PlacemarkMapObject marker) {
        if (!(marker.getUserData() instanceof PlacesApi.Place)) return;
        PlacesApi.Place place = (PlacesApi.Place) marker.getUserData();

        boolean alreadySelected = callback.getSelectedMarkers().contains(marker);

        String title;
        if (place.name != null && !place.name.isEmpty()) {
            title = place.name;
        } else {
            title = String.format("%.5f, %.5f", place.lat, place.lon);
        }

        new AlertDialog.Builder(mainActivity)
                .setTitle(title)
                .setMessage(
                        alreadySelected
                                ? "Убрать эту точку из маршрута?"
                                : "Добавить эту точку в маршрут?"
                )
                .setPositiveButton(
                        alreadySelected ? "Убрать" : "Добавить",
                        (d, w) -> callback.onPoiTapped(marker)
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    public void cancel() {
        if (searchSession != null) {
            searchSession.cancel();
        }
    }
}
