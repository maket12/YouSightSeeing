package com.example.yandexmapcitysearch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

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
    private Button btnBuildRoute;
    private Button btnZoomIn;
    private Button btnZoomOut;

    private Button btnProfile;

    // Yandex Search
    private SearchManager searchManager;
    private Session searchSession;

    // Маршрут
    private OpenRouteServiceClient orsClient;
    private Route currentRoute;
    private Point startPoint;
    private int currentPointIndex = 0;
    private boolean routeMode = false;
    private boolean poiMode = false;
    private List<Point> selectedPoints = new ArrayList<>();
    private List<PlacemarkMapObject> pointMarkers = new ArrayList<>();
    private List<PlacemarkMapObject> poiMarkers = new ArrayList<>();
    private PolylineMapObject routeLine;

    // Map input
    private InputListener mapInputListener;

    // Геопозиция
    private static final int REQ_LOCATION = 1001;
    private FusedLocationProviderClient fusedClient;
    private Point userLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermissionAndLoad();

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
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);
        btnProfile = findViewById(R.id.btnProfile);
        btnBuildRoute = findViewById(R.id.btnBuildRoute);

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

        if (btnProfile != null) {
            btnProfile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        }

        if (btnBuildRoute != null) {
            btnBuildRoute.setOnClickListener(v -> {
                if (!routeMode) {
                    // ПЕРВЫЙ КЛИК: показать POI
                    showCategoriesDialog();
                } else if (currentRoute == null) {
                    // РЕЖИМ POI: строим маршрут
                    if (selectedPoints.size() < 2) {
                        Toast.makeText(this, "Выберите минимум 2 места", Toast.LENGTH_SHORT).show();
                    } else {
                        buildOptimalRoute();
                    }
                } else {
                    // МАРШРУТ ПОСТРОЕН: сбрасываем
                    resetRoute();
                    Toast.makeText(this, "Маршрут сброшен", Toast.LENGTH_SHORT).show();
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
     * Показывает диалог выбора категорий и запускает поиск POI.
     */
    private void showCategoriesDialog() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> userCategories = prefs.getStringSet("categories", new HashSet<>());

        if (userCategories.isEmpty()) {
            Toast.makeText(this, "Выберите категории в профиле", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userLocation != null) {
            // Ищем вокруг пользователя
            startPoint = userLocation;
            Toast.makeText(this, "Ищем POI вокруг вас", Toast.LENGTH_SHORT).show();
            searchNearbyPlaces(startPoint.getLatitude(), startPoint.getLongitude(), userCategories);
            routeMode = true;
            poiMode = true;
            updateBuildRouteButton();
        } else {
            Toast.makeText(this, "Сначала выберите точку на карте", Toast.LENGTH_LONG).show();
            poiMode = false;
            routeMode = false;
        }
    }


    /**
     * Обновляет текст и состояние кнопки "Построить маршрут" в зависимости от текущего режима.
     *
     * В режиме поиска POI: показывает "Построить маршрут" (всегда активна).
     * В режиме выбора точек: показывает счётчик выбранных ("Построить маршрут (3)")
     * и активирует только при ≥2 точках.
     */
    private void updateBuildRouteButton() {
        if (btnBuildRoute != null) {
            if (routeMode) {
                btnBuildRoute.setText("Построить маршрут (" + selectedPoints.size() + ")");
                btnBuildRoute.setEnabled(selectedPoints.size() >= 2);
            } else {
                btnBuildRoute.setText("Построить маршрут");
                btnBuildRoute.setEnabled(true);
            }
        }
    }

    /**
     * Ищет POI вокруг указанной точки по категориям пользователя.
     */
    private void searchNearbyPlaces(double lat, double lon, Set<String> categories) {
        Toast.makeText(this, "Поиск достопримечательностей в радиусе 5км...", Toast.LENGTH_SHORT).show();

        GeoapifyClient geoClient = new GeoapifyClient(this);
        geoClient.getNearbyPlaces(lat, lon, categories, new GeoapifyClient.GeoapifyCallback() {
            @Override
            public void onSuccess(List<GeoapifyClient.Place> places) {
                runOnUiThread(() -> displayNearbyPlaces(places));
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Не найдено POI: " + errorMessage, Toast.LENGTH_LONG).show()
                );
            }
        });
    }


    /**
     * Отображает найденные POI на карте как кликабельные маркеры.
     */
    private void displayNearbyPlaces(List<GeoapifyClient.Place> places) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
        clearNearbyPlaces();

        for (GeoapifyClient.Place place : places) {
            if (place.location == null) continue;

            PlacemarkMapObject marker = mapObjects.addPlacemark(place.location);
            marker.setUserData(place);
            poiMarkers.add(marker);

            marker.setIcon(ImageProvider.fromResource(this, android.R.drawable.ic_menu_mylocation));
        }

        Toast.makeText(this, "Найдено " + places.size() + " мест. Тапните для добавления", Toast.LENGTH_LONG).show();
        if (!places.isEmpty()) adjustCameraToPlaces(places);

        poiMode = true;
        routeMode = true;
        updateBuildRouteButton();
    }


    /**
     * Очищает только маркеры POI, оставляя маршрут и пользовательские точки.
     */
    private void clearNearbyPlaces() {
        if (poiMarkers.isEmpty()) return;

        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
        for (PlacemarkMapObject marker : poiMarkers) {
            mapObjects.remove(marker);
        }
        poiMarkers.clear();
    }


    /**
     * Добавляет POI в список точек маршрута.
     */
    private void addPlaceToRoute(GeoapifyClient.Place place) {
        if (place.location != null) {
            selectedPoints.add(place.location);

            // Меняем иконку маркера на "выбранную"
            highlightSelectedMarker(place);

            // Обновляем кнопку с новым счётчиком
            updateBuildRouteButton();

            Toast.makeText(this, (place.name != null ? place.name : "Точка") +
                            " добавлено (" + selectedPoints.size() + ")",
                    Toast.LENGTH_SHORT).show();

        }
    }

    /**
     * Подсвечивает маркер выбранного POI (меняет иконку).
     */
    private void highlightSelectedMarker(GeoapifyClient.Place place) {
        for (PlacemarkMapObject marker : poiMarkers) { // ← ИЗМЕНИ НА ЭТО
            GeoapifyClient.Place markerPlace = (GeoapifyClient.Place) marker.getUserData();
            if (markerPlace != null && markerPlace.location.equals(place.location)) {
                marker.setIcon(ImageProvider.fromResource(this, android.R.drawable.btn_star));
                return;
            }
        }
    }

    /**
     * Подгоняет камеру под все найденные POI.
     */
    private void adjustCameraToPlaces(List<GeoapifyClient.Place> places) {
        if (places.isEmpty() || mapView == null) return;

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (GeoapifyClient.Place place : places) {
            if (place.location != null) {
                minLat = Math.min(minLat, place.location.getLatitude());
                maxLat = Math.max(maxLat, place.location.getLatitude());
                minLon = Math.min(minLon, place.location.getLongitude());
                maxLon = Math.max(maxLon, place.location.getLongitude());
            }
        }

        double centerLat = (minLat + maxLat) / 2;
        double centerLon = (minLon + maxLon) / 2;

        mapView.getMapWindow().getMap().move(
                new CameraPosition(new Point(centerLat, centerLon), 15f, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1f),
                null
        );
    }

    /**
     * Обработка нажатия на карту
     */
    private void handleMapTap(com.yandex.mapkit.map.Map map, Point point) {
        if (!routeMode) return;

        // Проверяем - попали ли в POI маркер? (в радиусе 50м)
        PlacemarkMapObject tappedMarker = null;
        GeoapifyClient.Place tappedPlace = null;

        for (PlacemarkMapObject marker : poiMarkers) {
            GeoapifyClient.Place markerPlace = (GeoapifyClient.Place) marker.getUserData();
            if (markerPlace != null && markerPlace.location != null) {
                double distance = distanceBetween(point, markerPlace.location);
                if (distance < 0.0005) { // ~50 метров
                    tappedMarker = marker;
                    tappedPlace = markerPlace;
                    break;
                }
            }
        }

        showAddPointDialog(tappedPlace, point);
    }

    /** Расстояние между двумя точками в градусах (~111м на градус) */
    private double distanceBetween(Point p1, Point p2) {
        double latDiff = Math.abs(p1.getLatitude() - p2.getLatitude());
        double lonDiff = Math.abs(p1.getLongitude() - p2.getLongitude());
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }


    /**
     * Показывает диалог добавления точки в маршрут для ЛЮБОГО тапа на карте.
     *
     * @param place POI объект (может быть null для обычной точки на карте)
     * @param point координаты тапа на карте
     */
    private void showAddPointDialog(GeoapifyClient.Place place, Point point) {
        String title = place != null ? (place.name != null ? place.name : "POI") : "Точка на карте";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Добавить в маршрут?")
                .setPositiveButton("Добавить", (dialog, which) -> {
                    if (place != null) {
                        addPlaceToRoute(place);
                    } else {
                        GeoapifyClient.Place pointPlace = new GeoapifyClient.Place(
                                "Точка " + (selectedPoints.size() + 1), point
                        );
                        addPlaceToRoute(pointPlace);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
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

        // Запрос маршрута через backend API
        orsClient.getMultiPointRoute(
                MainActivity.this,
                optimizedPoints,
                new OpenRouteServiceClient.ORSCallback() {
                    @Override
                    public void onSuccess(List<Point> routeCoordinates) {
                        runOnUiThread(() -> {
                            displayRoute(routeCoordinates);

                            // ИЗМЕНЕНИЕ КНОПКИ НА "Сбросить маршрут"
                            if (btnBuildRoute != null) {
                                btnBuildRoute.setText("Сбросить маршрут");
                                btnBuildRoute.setEnabled(true);
                            }
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() ->
                                Toast.makeText(
                                        MainActivity.this,
                                        "Ошибка построения маршрута: " + errorMessage,
                                        Toast.LENGTH_LONG
                                ).show()
                        );
                    }
                }
        );
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
            for (PlacemarkMapObject marker : pointMarkers) mapObjects.remove(marker);
            for (PlacemarkMapObject marker : poiMarkers) mapObjects.remove(marker);
            if (routeLine != null) {
                mapObjects.remove(routeLine);
                routeLine = null;
            }
        }

        selectedPoints.clear();
        pointMarkers.clear();
        poiMarkers.clear();
        poiMode = false;
        routeMode = false;
        currentRoute = null;
        currentPointIndex = 0;

        updateBuildRouteButton();
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

    /**
     * Запрашивает разрешение на геопозицию и загружает текущую локацию пользователя.
     * Если разрешения нет — запрашивает их, иначе сразу получает координаты.
     */
    private void requestLocationPermissionAndLoad() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Нет разрешения — запрашиваем у пользователя
            requestPermissions(
                    new String[] {
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_LOCATION
            );
        } else {
            // Разрешение есть — сразу получаем локацию
            requestUserLocation();
        }
    }

    /**
     * Получает последнюю известную геопозицию пользователя и центрирует карту.
     */
    private void requestUserLocation() {
        try {
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lon = location.getLongitude();
                            userLocation = new Point(lat, lon);

                            if (mapView != null && mapView.getMapWindow() != null) {
                                mapView.getMapWindow().getMap().move(
                                        new CameraPosition(userLocation, 14f, 0.0f, 0.0f),
                                        new Animation(Animation.Type.SMOOTH, 1f),
                                        null
                                );
                            }
                            Log.d("MainActivity", "User location: " + lat + "," + lon);
                        } else {
                            Log.d("MainActivity", "Last location is null, using default camera");
                        }
                    });
        } catch (SecurityException e) {
            // Разрешение отозвано или недоступно
            Log.w("MainActivity", "Location permission denied", e);
            Toast.makeText(this, "Геопозиция недоступна", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Обработчик результата запроса разрешений на геопозицию.
     * При согласии — загружает локацию, при отказе — fallback на выбор точки тапом.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Пользователь дал разрешение — загружаем локацию
                requestUserLocation();
            } else {
                // Отказ — пользователь сам выберет стартовую точку на карте
                Toast.makeText(this, "Геопозиция отключена, выберите точку на карте", Toast.LENGTH_LONG).show();
            }
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