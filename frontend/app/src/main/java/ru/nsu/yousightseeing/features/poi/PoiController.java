package ru.nsu.yousightseeing.features.poi;

import android.content.SharedPreferences;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.PlacemarkMapObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.nsu.yousightseeing.api.GeoapifyClient;
import ru.nsu.yousightseeing.api.PlacesApi;
import ru.nsu.yousightseeing.features.MainActivity;
import ru.nsu.yousightseeing.utils.MapPoiHelper;
import static android.content.Context.MODE_PRIVATE;

public class PoiController {

    private final MainActivity mainActivity;
    private final PoiControllerCallback callback;
    private final MapPoiHelper mapPoiHelper;
    private final List<PlacemarkMapObject> poiMarkers = new ArrayList<>();
    private Point lastPoiCenter = null;

    public interface PoiControllerCallback {
        void showToast(String message);
        void updateAllUI();
        Set<PlacemarkMapObject> getSelectedMarkers();
        List<Point> getSelectedPoints();
    }

    public PoiController(MainActivity mainActivity, MapPoiHelper mapPoiHelper, PoiControllerCallback callback) {
        this.mainActivity = mainActivity;
        this.mapPoiHelper = mapPoiHelper;
        this.callback = callback;
    }

    public void searchNearbyPlaces(double lat, double lon) {
        lastPoiCenter = new Point(lat, lon);
        callback.showToast("Поиск POI в радиусе 5км...");

        SharedPreferences prefs = mainActivity.getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
        if (categories.isEmpty()) {
            callback.showToast("Категории не выбраны");
            return;
        }

        GeoapifyClient geoClient = new GeoapifyClient(mainActivity);
        geoClient.getNearbyPlaces(lat, lon, categories, new GeoapifyClient.GeoapifyCallback() {
            @Override
            public void onSuccess(List<PlacesApi.Place> places) {
                mainActivity.runOnUiThread(() -> {
                    if (places.isEmpty()) {
                        callback.showToast("POI не найдены рядом");
                    } else {
                        mapPoiHelper.displayNearbyPlaces(places, poiMarkers, callback.getSelectedMarkers(), callback.getSelectedPoints());
                        callback.updateAllUI();
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                mainActivity.runOnUiThread(() -> callback.showToast("Ошибка POI: " + errorMessage));
            }
        });
    }

    public void reloadPoisWithNewCategories() {
        if (lastPoiCenter == null) {
            callback.showToast("Нет точки для обновления POI");
            return;
        }
        searchNearbyPlaces(lastPoiCenter.getLatitude(), lastPoiCenter.getLongitude());
        callback.showToast("Точки обновлены по новым категориям");
    }

    public List<PlacemarkMapObject> getPoiMarkers() {
        return poiMarkers;
    }

    public Point getLastPoiCenter() {
        return lastPoiCenter;
    }

    public void clear() {
        poiMarkers.clear();
        lastPoiCenter = null;
    }
}
