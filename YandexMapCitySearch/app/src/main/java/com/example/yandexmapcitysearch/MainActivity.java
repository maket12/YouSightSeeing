package com.example.yandexmapcitysearch;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Geometry;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.InputListener;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.PolylineMapObject;
import com.yandex.mapkit.map.VisibleRegionUtils;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.search.Response;
import com.yandex.mapkit.search.SearchFactory;
import com.yandex.mapkit.search.SearchManager;
import com.yandex.mapkit.search.SearchManagerType;
import com.yandex.mapkit.search.SearchOptions;
import com.yandex.mapkit.search.Session;
import com.yandex.runtime.Error;
import com.yandex.runtime.image.ImageProvider;
import com.yandex.mapkit.GeoObjectCollection;

import java.util.List;

public class MainActivity extends AppCompatActivity implements Session.SearchListener {

    private MapView mapView;
    private EditText editCity;
    private Button btnSearch;
    private SearchManager searchManager;
    private Session searchSession;
    private Point startPoint = null;
    private Point endPoint = null;
    private PlacemarkMapObject startMarker;
    private PlacemarkMapObject endMarker;
    private PolylineMapObject routeLine;
    private OpenRouteServiceClient orsClient;
    private InputListener mapInputListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        orsClient = new OpenRouteServiceClient();

        try {
            if (BuildConfig.MAPKIT_API_KEY == null || BuildConfig.MAPKIT_API_KEY.isEmpty()) {
                Log.e("MainActivity", "MAPKIT_API_KEY is not set in BuildConfig");
                Toast.makeText(this, "Ошибка: API-ключ не настроен", Toast.LENGTH_LONG).show();
                return;
            }
            MapKitFactory.initialize(this);
            Log.d("MainActivity", "MapKit initialized successfully");
        } catch (AssertionError e) {
            Log.e("MainActivity", "Ошибка инициализации MapKit: " + e.getMessage());
            Toast.makeText(this, "Ошибка инициализации карты: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        mapView = findViewById(R.id.mapView);
        editCity = findViewById(R.id.editCity);
        btnSearch = findViewById(R.id.btnSearch);

        if (mapView != null) {
            MapWindow mapWindow = mapView.getMapWindow();
            if (mapWindow != null) {

                // Слушатель нажатий по карте
                mapInputListener = new InputListener() {
                    @Override
                    public void onMapTap(com.yandex.mapkit.map.Map map, Point point) {
                        MapObjectCollection mapObjects = map.getMapObjects();

                        if (startPoint == null) {
                            startPoint = point;
                            startMarker = mapObjects.addPlacemark(point);
                            Toast.makeText(MainActivity.this, "Начало маршрута выбрано", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        if (endPoint == null) {
                            endPoint = point;
                            endMarker = mapObjects.addPlacemark(point);
                            Toast.makeText(MainActivity.this, "Конец маршрута выбран", Toast.LENGTH_SHORT).show();

                            orsClient.getRoute(startPoint, endPoint, new OpenRouteServiceClient.ORSCallback() {
                                @Override
                                public void onSuccess(List<Point> routePoints) {
                                    runOnUiThread(() -> {
                                        if (routeLine != null) mapObjects.remove(routeLine);
                                        Polyline poly = new Polyline(routePoints);
                                        routeLine = mapObjects.addPolyline(poly);

                                        // ==== Подгоняем камеру под весь маршрут ====
                                        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
                                        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

                                        for (Point p : routePoints) {
                                            if (p.getLatitude() < minLat) minLat = p.getLatitude();
                                            if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
                                            if (p.getLongitude() < minLon) minLon = p.getLongitude();
                                            if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
                                        }

                                        double centerLat = (minLat + maxLat) / 2;
                                        double centerLon = (minLon + maxLon) / 2;

// Примерный zoom под длину маршрута
                                        float zoom;
                                        double latDiff = maxLat - minLat;
                                        double lonDiff = maxLon - minLon;
                                        double maxDiff = Math.max(latDiff, lonDiff);
                                        if (maxDiff < 0.005) zoom = 17f;
                                        else if (maxDiff < 0.02) zoom = 15f;
                                        else if (maxDiff < 0.1) zoom = 13f;
                                        else zoom = 13f;

// Двигаем камеру
                                        mapView.getMapWindow().getMap().move(
                                                new CameraPosition(new Point(centerLat, centerLon), zoom, 0.0f, 0.0f),
                                                new Animation(Animation.Type.SMOOTH, 1f),
                                                null
                                        );

                                        Toast.makeText(MainActivity.this, "Маршрут построен", Toast.LENGTH_SHORT).show();
                                    });
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    runOnUiThread(() ->
                                            Toast.makeText(MainActivity.this, "Ошибка маршрута: " + errorMessage, Toast.LENGTH_LONG).show()
                                    );
                                }
                            });
                            return;
                        }

                        // третий тап — сброс маршрута
                        mapObjects.clear();
                        startPoint = null;
                        endPoint = null;
                        startMarker = null;
                        endMarker = null;
                        routeLine = null;
                        Toast.makeText(MainActivity.this, "Маршрут сброшен, выбери заново", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onMapLongTap(com.yandex.mapkit.map.Map map, Point point) { }
                };

                mapWindow.getMap().addInputListener(mapInputListener);

                mapWindow.getMap().move(
                        new CameraPosition(new Point(55.751225, 37.62954), 10.0f, 0.0f, 0.0f),
                        new Animation(Animation.Type.SMOOTH, 1),
                        null
                );
            } else {
                Toast.makeText(this, "Ошибка инициализации MapWindow", Toast.LENGTH_LONG).show();
                Log.e("MainActivity", "MapWindow is null");
            }
        } else {
            Toast.makeText(this, "Ошибка инициализации MapView", Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "MapView is null");
        }

        searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.ONLINE);

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                String city = editCity != null ? editCity.getText().toString().trim() : "";
                if (!city.isEmpty()) {
                    submitQuery(city);
                } else {
                    Toast.makeText(this, "Введите название города", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Log.e("MainActivity", "btnSearch is null");
        }
    }

    private void submitQuery(String query) {
        if (searchSession != null) {
            searchSession.cancel();
        }
        if (mapView != null) {
            MapWindow mapWindow = mapView.getMapWindow();
            if (mapWindow != null && mapWindow.getMap() != null) {
                Geometry visibleRegion = VisibleRegionUtils.toPolygon(mapWindow.getMap().getVisibleRegion());
                if (visibleRegion == null) {
                    Log.e("MainActivity", "Visible region is null");
                    Toast.makeText(this, "Ошибка: Видимая область карты недоступна", Toast.LENGTH_SHORT).show();
                    return;
                }
                searchSession = searchManager.submit(
                        query,
                        visibleRegion,
                        new SearchOptions(),
                        this
                );
                Log.d("MainActivity", "Search query submitted: " + query);
            } else {
                Log.e("MainActivity", "MapWindow or Map is null during submitQuery");
                Toast.makeText(this, "Ошибка карты: MapWindow недоступен", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.e("MainActivity", "MapView is null during submitQuery");
            Toast.makeText(this, "Ошибка карты: MapView недоступен", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSearchResponse(Response response) {
        if (mapView == null || mapView.getMapWindow() == null || mapView.getMapWindow().getMap() == null) {
            Log.e("MainActivity", "MapWindow or Map is null during onSearchResponse");
            Toast.makeText(this, "Ошибка отображения карты", Toast.LENGTH_LONG).show();
            return;
        }

        MapWindow mapWindow = mapView.getMapWindow();
        MapObjectCollection mapObjects = mapWindow.getMap().getMapObjects();
        mapObjects.clear();

        GeoObjectCollection.Item firstResult = null;
        if (!response.getCollection().getChildren().isEmpty()) {
            firstResult = response.getCollection().getChildren().get(0);
        }

        if (firstResult != null && firstResult.getObj() != null && !firstResult.getObj().getGeometry().isEmpty()) {
            Point resultLocation = firstResult.getObj().getGeometry().get(0).getPoint();
            if (resultLocation != null) {
                ImageProvider searchResultImageProvider = null;
                try {
                    searchResultImageProvider = ImageProvider.fromResource(this, R.drawable.search_result);
                } catch (Exception e) {
                    Log.e("MainActivity", "Ошибка загрузки R.drawable.search_result: " + e.getMessage());
                    Toast.makeText(this, "Ошибка загрузки иконки метки", Toast.LENGTH_SHORT).show();
                }

                PlacemarkMapObject placemark = mapObjects.addPlacemark(resultLocation);
                if (searchResultImageProvider != null) {
                    try {
                        placemark.setIcon(searchResultImageProvider);
                    } catch (Exception e) {
                        Log.e("MainActivity", "Ошибка установки иконки: " + e.getMessage());
                        Toast.makeText(this, "Не удалось установить иконку метки", Toast.LENGTH_SHORT).show();
                    }
                }

                mapWindow.getMap().move(
                        new CameraPosition(resultLocation, 10.0f, 0.0f, 0.0f),
                        new Animation(Animation.Type.SMOOTH, 1),
                        null
                );
                Toast.makeText(this, "Найдено: " + firstResult.getObj().getName(), Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "Moved camera to: " + resultLocation.getLatitude() + ", " + resultLocation.getLongitude());
            } else {
                Toast.makeText(this, "Местоположение не найдено", Toast.LENGTH_SHORT).show();
                Log.e("MainActivity", "Result location is null");
            }
        } else {
            Toast.makeText(this, "Результаты поиска не найдены", Toast.LENGTH_SHORT).show();
            Log.e("MainActivity", "Search response is empty");
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
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
        Log.e("MainActivity", "Search Error: " + errorMessage);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onStop() {
        if (mapView != null && mapView.getMapWindow() != null && mapInputListener != null) {
            mapView.getMapWindow().getMap().removeInputListener(mapInputListener);
            mapInputListener = null;
        }
        if (mapView != null) mapView.onStop();
        MapKitFactory.getInstance().onStop();
        if (searchSession != null) searchSession.cancel();
        super.onStop();
    }
}
