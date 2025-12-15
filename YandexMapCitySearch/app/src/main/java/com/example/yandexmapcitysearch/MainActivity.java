package com.example.yandexmapcitysearch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import model.Route;
import utils.RouteOptimizer;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements Session.SearchListener {

    // UI компоненты
    private MapView mapView;
    private EditText editCity;
    private Button btnSearch;
    private EditText editPointsCount;
    private Button btnSetPoints;
    private Button btnZoomIn;
    private Button btnZoomOut;

    // Yandex Search
    private SearchManager searchManager;
    private Session searchSession;

    // Маршрут
    private OpenRouteServiceClient orsClient;
    private Route currentRoute;
    private int maxPoints = 2;
    private int currentPointIndex = 0;
    private List<Point> selectedPoints = new ArrayList<>();
    private List<PlacemarkMapObject> pointMarkers = new ArrayList<>();
    private PolylineMapObject routeLine;

    // Map input
    private InputListener mapInputListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeMapKit();
        initializeUI();
        initializeSearch();
    }


    /**
     * Инициализация MapKit
     */
    private void initializeMapKit() {
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
        }
    }

    /**
     * Инициализация UI элементов
     */
    private void initializeUI() {
        mapView = findViewById(R.id.mapView);
        editCity = findViewById(R.id.editCity);
        btnSearch = findViewById(R.id.btnSearch);
        editPointsCount = findViewById(R.id.editPointsCount);
        btnSetPoints = findViewById(R.id.btnSetPoints);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);

        orsClient = new OpenRouteServiceClient();

        // Инициализация карты
        if (mapView != null) {
            initializeMap();
        } else {
            Toast.makeText(this, "Ошибка инициализации MapView", Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "MapView is null");
        }

        // Обработчики кнопок
        setupButtonListeners();
    }

    /**
     * Инициализация карты и слушателей
     */
    private void initializeMap() {
        MapWindow mapWindow = mapView.getMapWindow();
        if (mapWindow != null) {
            mapInputListener = new InputListener() {
                @Override
                public void onMapTap(com.yandex.mapkit.map.Map map, Point point) {
                    handleMapTap(map, point);
                }

                @Override
                public void onMapLongTap(com.yandex.mapkit.map.Map map, Point point) {
                    // Сброс маршрута по долгому нажатию
                    resetRoute();
                    Toast.makeText(MainActivity.this, "Маршрут сброшен", Toast.LENGTH_SHORT).show();
                }
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
    }

    /**
     * Настройка обработчиков кнопок
     */
    private void setupButtonListeners() {
        if (btnSetPoints != null) {
            btnSetPoints.setOnClickListener(v -> setPointsCount());
        }

        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> zoomIn());
        }

        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> zoomOut());
        }

        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> {
                String city = editCity != null ? editCity.getText().toString().trim() : "";
                if (!city.isEmpty()) {
                    submitQuery(city);
                } else {
                    Toast.makeText(this, "Введите название города", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Инициализация поиска городов
     */
    private void initializeSearch() {
        searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.ONLINE);
    }

    /**
     * Установка количества точек маршрута
     */
    private void setPointsCount() {
        String input = editPointsCount.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Введите количество точек", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            int points = Integer.parseInt(input);
            if (points < 2 || points > 10) {
                Toast.makeText(this, "Число должно быть от 2 до 10", Toast.LENGTH_SHORT).show();
                return;
            }

            maxPoints = points;
            resetRoute();
            Toast.makeText(this, "Выберите " + maxPoints + " точек на карте", Toast.LENGTH_LONG).show();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Некорректное число", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Обработка нажатия на карту
     * ВАШ КОД: интеграция с пользовательскими предпочтениями
     */
    private void handleMapTap(com.yandex.mapkit.map.Map map, Point point) {
        MapObjectCollection mapObjects = map.getMapObjects();

        if (currentPointIndex < maxPoints) {
            selectedPoints.add(point);
            PlacemarkMapObject marker = mapObjects.addPlacemark(point);
            pointMarkers.add(marker);
            currentPointIndex++;

            Toast.makeText(this, "Точка " + currentPointIndex + "/" + maxPoints + " добавлена", Toast.LENGTH_SHORT).show();

            if (currentPointIndex == maxPoints) {
                buildOptimalRoute();
            }
        } else {
            // После добавления всех точек, ищем достопримечательности (ВАШ КОД)
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> userCategories = prefs.getStringSet("categories", new HashSet<>());

            GeoapifyClient geoClient = new GeoapifyClient();
            Log.d("PreferencesCheck", "Выбранные категории: " + userCategories.toString());

            geoClient.getNearbyPlaces(
                    point.getLatitude(),
                    point.getLongitude(),
                    userCategories,
                    new GeoapifyClient.GeoapifyCallback() {
                        @Override
                        public void onSuccess(List<GeoapifyClient.Place> places) {
                            runOnUiThread(() -> {
                                if (!places.isEmpty()) {
                                    Log.d("Geoapify", "Найдено " + places.size() + " достопримечательностей");
                                }
                            });
                        }

                        @Override
                        public void onError(String errorMessage) {
                            runOnUiThread(() ->
                                    Log.e("Geoapify", "Ошибка поиска: " + errorMessage)
                            );
                        }
                    });

            resetRoute();
            Toast.makeText(this, "Маршрут сброшен. Выберите " + maxPoints + " точек заново", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Построение оптимального маршрута
     */
    private void buildOptimalRoute() {
        if (selectedPoints.size() < 2) {
            Toast.makeText(this, "Недостаточно точек для построения маршрута", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Построение оптимального маршрута...", Toast.LENGTH_LONG).show();

        // Оптимизация порядка точек
        List<Point> optimizedPoints = RouteOptimizer.optimize(selectedPoints);

        // Запрос маршрута через API
        orsClient.getMultiPointRoute(optimizedPoints, new OpenRouteServiceClient.ORSCallback() {
            @Override
            public void onSuccess(List<Point> routeCoordinates) {
                runOnUiThread(() -> displayRoute(routeCoordinates));
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Ошибка построения маршрута: " + errorMessage, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    /**
     * Отображение маршрута на карте
     */
    private void displayRoute(List<Point> routeCoordinates) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        if (routeLine != null) {
            mapObjects.remove(routeLine);
        }

        Polyline poly = new Polyline(routeCoordinates);
        routeLine = mapObjects.addPolyline(poly);

        adjustCameraToRoute(routeCoordinates);

        // Создаем объект маршрута
        currentRoute = new Route(routeCoordinates, "Маршрут " + System.currentTimeMillis());

        Toast.makeText(MainActivity.this, "Маршрут построен!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Сброс маршрута
     */
    private void resetRoute() {
        if (mapView != null && mapView.getMapWindow() != null) {
            MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
            mapObjects.clear();
        }
        selectedPoints.clear();
        pointMarkers.clear();
        currentPointIndex = 0;
        routeLine = null;
        currentRoute = null;
    }

    /**
     * Приближение карты
     */
    private void zoomIn() {
        if (mapView == null || mapView.getMapWindow() == null) return;

        com.yandex.mapkit.map.Map map = mapView.getMapWindow().getMap();
        CameraPosition currentPosition = map.getCameraPosition();
        float newZoom = currentPosition.getZoom() + 1.0f;

        map.move(
                new CameraPosition(
                        currentPosition.getTarget(),
                        newZoom,
                        currentPosition.getAzimuth(),
                        currentPosition.getTilt()
                ),
                new Animation(Animation.Type.SMOOTH, 0.5f),
                null
        );
    }

    /**
     * Отдаление карты
     */
    private void zoomOut() {
        if (mapView == null || mapView.getMapWindow() == null) return;

        com.yandex.mapkit.map.Map map = mapView.getMapWindow().getMap();
        CameraPosition currentPosition = map.getCameraPosition();
        float newZoom = Math.max(currentPosition.getZoom() - 1.0f, 0.0f);

        map.move(
                new CameraPosition(
                        currentPosition.getTarget(),
                        newZoom,
                        currentPosition.getAzimuth(),
                        currentPosition.getTilt()
                ),
                new Animation(Animation.Type.SMOOTH, 0.5f),
                null
        );
    }

    /**
     * Подгонка камеры под маршрут
     */
    private void adjustCameraToRoute(List<Point> routeCoordinates) {
        if (routeCoordinates.isEmpty() || mapView == null) return;

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (Point p : routeCoordinates) {
            if (p.getLatitude() < minLat) minLat = p.getLatitude();
            if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
            if (p.getLongitude() < minLon) minLon = p.getLongitude();
            if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
        }

        double centerLat = (minLat + maxLat) / 2;
        double centerLon = (minLon + maxLon) / 2;

        float zoom;
        double latDiff = maxLat - minLat;
        double lonDiff = maxLon - minLon;
        double maxDiff = Math.max(latDiff, lonDiff);

        if (maxDiff < 0.005) zoom = 17f;
        else if (maxDiff < 0.02) zoom = 15f;
        else if (maxDiff < 0.05) zoom = 14f;
        else if (maxDiff < 0.1) zoom = 13f;
        else zoom = 12f;

        mapView.getMapWindow().getMap().move(
                new CameraPosition(new Point(centerLat, centerLon), zoom, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1f),
                null
        );
    }

    /**
     * Поиск города по названию
     */
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