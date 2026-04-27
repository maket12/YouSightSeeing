package ru.nsu.yousightseeing.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.geometry.BoundingBox;
import com.yandex.mapkit.geometry.Geometry;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.VisibleRegion;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.search.Response;
import com.yandex.mapkit.search.SearchFactory;
import com.yandex.mapkit.search.SearchManager;
import com.yandex.mapkit.search.SearchOptions;
import com.yandex.mapkit.search.Session;
import com.yandex.runtime.Error;
import com.yandex.runtime.image.ImageProvider;
import com.yandex.mapkit.GeoObjectCollection;

import ru.nsu.yousightseeing.R;

public class SearchHelper implements Session.SearchListener {

    private final Context context;
    private final MapView mapView;
    private SearchManager searchManager;
    private Session searchSession;

    public SearchHelper(Context context, MapView mapView) {
        this.context = context;
        this.mapView = mapView;
        this.searchManager = SearchFactory.getInstance()
                .createSearchManager(com.yandex.mapkit.search.SearchManagerType.COMBINED);
    }

    public void submitQuery(String query) {
        if (searchSession != null) {
            searchSession.cancel();
        }

        if (mapView == null || mapView.getMapWindow() == null) {
            Log.e("SearchHelper", "MapView or MapWindow is null");
            Toast.makeText(context, "Ошибка карты", Toast.LENGTH_SHORT).show();
            return;
        }

        com.yandex.mapkit.map.Map map = mapView.getMapWindow().getMap();

        if (map == null) {
            Log.e("SearchHelper", "Map is null");
            return;
        }

        VisibleRegion visibleRegion = map.getVisibleRegion();

        if (visibleRegion == null) {
            Log.e("SearchHelper", "Visible region is null");
            Toast.makeText(context, "Ошибка: область карты недоступна", Toast.LENGTH_SHORT).show();
            return;
        }

        Geometry geometry = Geometry.fromBoundingBox(
                new BoundingBox(
                        visibleRegion.getBottomLeft(),
                        visibleRegion.getTopRight()
                )
        );

        searchSession = searchManager.submit(
                query,
                geometry,
                new SearchOptions(),
                this
        );

        Log.d("SearchHelper", "Search query submitted: " + query);
    }
    
    public SearchManager getSearchManager() {
        return searchManager;
    }

    public void cancelSearch() {
        if (searchSession != null) {
            searchSession.cancel();
        }
    }

    @Override
    public void onSearchResponse(Response response) {
        if (mapView == null || mapView.getMapWindow() == null || mapView.getMapWindow().getMap() == null) {
            Log.e("SearchHelper", "MapWindow or Map is null during onSearchResponse");
            Toast.makeText(context, "Ошибка отображения карты", Toast.LENGTH_LONG).show();
            return;
        }

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        GeoObjectCollection.Item firstResult = null;
        if (!response.getCollection().getChildren().isEmpty()) {
            firstResult = response.getCollection().getChildren().get(0);
        }

        if (firstResult != null && firstResult.getObj() != null && !firstResult.getObj().getGeometry().isEmpty()) {
            Point resultLocation = firstResult.getObj().getGeometry().get(0).getPoint();
            if (resultLocation != null) {
                ImageProvider searchResultImageProvider = null;
                try {
                    searchResultImageProvider = ImageProvider.fromResource(context, R.drawable.search_result);
                } catch (Exception e) {
                    Log.e("SearchHelper", "Ошибка загрузки R.drawable.search_result: " + e.getMessage());
                    Toast.makeText(context, "Ошибка загрузки иконки метки", Toast.LENGTH_SHORT).show();
                }

                PlacemarkMapObject placemark = mapObjects.addPlacemark(resultLocation);
                if (searchResultImageProvider != null) {
                    try {
                        placemark.setIcon(searchResultImageProvider);
                    } catch (Exception e) {
                        Log.e("SearchHelper", "Ошибка установки иконки: " + e.getMessage());
                        Toast.makeText(context, "Не удалось установить иконку метки", Toast.LENGTH_SHORT).show();
                    }
                }

                mapView.getMapWindow().getMap().move(
                        new CameraPosition(resultLocation, 10.0f, 0.0f, 0.0f),
                        new Animation(Animation.Type.SMOOTH, 1),
                        null
                );
                Toast.makeText(context, "Найдено: " + firstResult.getObj().getName(), Toast.LENGTH_SHORT).show();
                Log.d("SearchHelper", "Moved camera to: " + resultLocation.getLatitude() + ", " + resultLocation.getLongitude());
            } else {
                Toast.makeText(context, "Местоположение не найдено", Toast.LENGTH_SHORT).show();
                Log.e("SearchHelper", "Result location is null");
            }
        } else {
            Toast.makeText(context, "Результаты поиска не найдены", Toast.LENGTH_SHORT).show();
            Log.e("SearchHelper", "Search response is empty");
        }
    }

    @Override
    public void onSearchError(Error error) {
        String errorMessage = "Неизвестная ошибка";
        if (error instanceof com.yandex.runtime.network.RemoteError) {
            errorMessage = "Ошибка сервера: " + error.toString();
        } else if (error instanceof com.yandex.runtime.network.NetworkError) {
            errorMessage = "Ошибка сети: проверьте подключение к интернету";
        }

        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
        Log.e("SearchHelper", "Search Error: " + errorMessage);
    }
}