package ru.nsu.yousightseeing.features;

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

import ru.nsu.yousightseeing.BuildConfig;
import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.model.Route;
import ru.nsu.yousightseeing.utils.RouteOptimizer;
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
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.mapkit.layers.ObjectEvent;
import android.graphics.PointF;


public class MainActivity extends AppCompatActivity implements Session.SearchListener {

    // UI компоненты
    private MapView mapView;
    private EditText editCity;
    private Button btnSearch;
    private Button btnBuildRoute;
    private Button btnZoomIn;
    private Button btnZoomOut;

    private Button btnProfile;

    private Point manualStartPoint;

    private Button btnSelectStartPoint;
    private boolean manualStartPointMode = false; // режим выбора стартовой точки

    private final Set<PlacemarkMapObject> selectedMarkers = new HashSet<>();


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
    private List<PlacemarkMapObject> poiMarkers = new ArrayList<>();
    private PolylineMapObject routeLine;

    // Map input
    private InputListener mapInputListener;

    // Геопозиция
    private static final int REQ_LOCATION = 1001;
    private FusedLocationProviderClient fusedClient;
    private Point userLocation;

    // Геолокация MapKit
    private boolean allowGeo = false;
    private UserLocationLayer userLocationLayer;

    private Button btnEditCategories;

    private static final String[] ALL_CATEGORIES = {
            "Природа и свежий воздух",
            "Активные приключения",
            "Курорты и здоровый отдых",
            "Досуг и развлечения",
            "История, культура",
            "Места для шопинга",
            "Необычные и скрытые уголки города"
    };

    // Последняя точка, вокруг которой загружались POI
    private Point lastPoiCenter = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ← СВЯЗЬ С PermissionActivity
        allowGeo = getIntent().getBooleanExtra("ALLOW_GEO", false);
        Log.d("MainActivity", "ALLOW_GEO = " + allowGeo);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

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
        btnEditCategories = findViewById(R.id.btnEditCategories);


        orsClient = new OpenRouteServiceClient();

        // Инициализация карты ПЕРЕД GPS!
        if (mapView != null) {
            initializeMap();  // MapKit + InputListener готов
            checkAndRequestLocation();  // GPS + fallbackToMoscow()
        } else {
            Toast.makeText(this, "Ошибка инициализации MapView", Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "MapView is null");
        }

        // Обработчики кнопок ПОСЛЕ всего
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

        if (btnEditCategories != null) {
            btnEditCategories.setOnClickListener(v -> showEditCategoriesDialog());
        }

        if (btnSelectStartPoint != null) {
            btnSelectStartPoint.setOnClickListener(v -> {
                manualStartPointMode = true;
                routeMode = true; // включаем режим выбора POI
                Toast.makeText(this, "👆 Тапните на карте, чтобы выбрать стартовую точку", Toast.LENGTH_LONG).show();
            });
        }


        if (btnBuildRoute != null) {
            btnBuildRoute.setOnClickListener(v -> {
                SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
                if (categories.isEmpty()) {
                    Toast.makeText(this, "Выберите категории в профиле", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (currentRoute != null) {
                    // Если маршрут уже построен → сброс
                    resetRoute();
                    Toast.makeText(this, "Маршрут сброшен", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!routeMode) {
                    // Включаем режим выбора маршрута
                    routeMode = true;

                    if (userLocation != null) {
                        // Геопозиция доступна → спрашиваем у пользователя
                        showRouteStartDialog();
                    } else {
                        // GPS недоступен → сразу ждём тап по карте
                        Toast.makeText(this, "👆 Выберите точку на карте для начала маршрута", Toast.LENGTH_LONG).show();
                    }

                } else {
                    // routeMode включен → строим маршрут если точки есть
                    if (selectedPoints.size() >= 2) {
                        buildOptimalRoute();
                    } else {
                        Toast.makeText(this, "Недостаточно точек для маршрута", Toast.LENGTH_SHORT).show();
                    }
                }

                updateBuildRouteButton();
            });
        }
    }

    private void showRouteStartDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Начало маршрута")
                .setMessage("Строить маршрут от вашей текущей геопозиции или выбрать точку на карте?")
                .setPositiveButton("От моей позиции", (dialog, which) -> {
                    buildRouteAroundUser(); // метод у тебя уже есть
                    dialog.dismiss();
                })
                .setNegativeButton("Выбрать точку на карте", (dialog, which) -> {
                    Toast.makeText(this, "👆 Тапните на карте для выбора точки начала маршрута", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                })
                .show();
    }

    private void showEditCategoriesDialog() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet("categories", new HashSet<>());

        boolean[] checked = new boolean[ALL_CATEGORIES.length];
        for (int i = 0; i < ALL_CATEGORIES.length; i++) {
            checked[i] = saved.contains(ALL_CATEGORIES[i]);
        }

        new AlertDialog.Builder(this)
                .setTitle("Выбранные категории")
                .setMultiChoiceItems(
                        ALL_CATEGORIES,
                        checked,
                        (dialog, which, isChecked) -> checked[which] = isChecked
                )
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    Set<String> newSelection = new HashSet<>();
                    for (int i = 0; i < ALL_CATEGORIES.length; i++) {
                        if (checked[i]) {
                            newSelection.add(ALL_CATEGORIES[i]);
                        }
                    }

                    if (newSelection.isEmpty()) {
                        Toast.makeText(this,
                                "Нужно выбрать хотя бы одну категорию",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    prefs.edit()
                            .putStringSet("categories", newSelection)
                            .apply();

                    Toast.makeText(this,
                            "Категории обновлены",
                            Toast.LENGTH_SHORT).show();
                    // 🔁 Обновляем точки на карте
                    reloadPoisWithNewCategories();
                })
                .setNegativeButton("Отмена", null)
                .show();

    }

    private void updateEditCategoriesButton() {
        if (btnEditCategories != null) {
            btnEditCategories.setEnabled(selectedPoints.isEmpty());
        }
    }


    private void reloadPoisWithNewCategories() {
        if (lastPoiCenter == null) {
            Log.d("MainActivity", "Нет точки для обновления POI");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> categories = prefs.getStringSet("categories", new HashSet<>());

        if (categories.isEmpty()) {
            Toast.makeText(this, "Категории не выбраны", Toast.LENGTH_SHORT).show();
            return;
        }

        // ❌ Убираем старые точки маршрута
        selectedMarkers.clear();
        selectedPoints.clear();
        currentRoute = null;

        // 🔄 Загружаем новые POI
        searchNearbyPlaces(
                lastPoiCenter.getLatitude(),
                lastPoiCenter.getLongitude(),
                categories
        );

        Toast.makeText(this, "Точки обновлены по новым категориям", Toast.LENGTH_SHORT).show();
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

        // ВСЕГДА: routeMode ON + ждём тап!
        routeMode = true;
        poiMode = false;
        updateBuildRouteButton();

        Toast.makeText(this, "👆 Тапните на карте → POI вокруг точки", Toast.LENGTH_LONG).show();
    }

    /**
     * Обновляет текст и состояние кнопки "Построить маршрут" в зависимости от текущего режима.
     *
     * В режиме поиска POI: показывает "Построить маршрут" (всегда активна).
     * В режиме выбора точек: показывает счётчик выбранных ("Построить маршрут (3)")
     * и активирует только при ≥2 точках.
     */
    private void updateBuildRouteButton() {
        if (btnBuildRoute == null) return;

        if (currentRoute != null) {
            btnBuildRoute.setText("Сбросить маршрут");
            btnBuildRoute.setEnabled(true);
            return;
        }

        if (!routeMode) {
            btnBuildRoute.setText("Построить маршрут");
            btnBuildRoute.setEnabled(true);
        } else {
            if (poiMarkers.isEmpty()) {
                btnBuildRoute.setText("Ждём тап...");
                btnBuildRoute.setEnabled(false);
            } else {
                btnBuildRoute.setText("Построить маршрут (" + selectedPoints.size() + ")");
                btnBuildRoute.setEnabled(selectedPoints.size() >= 2);
            }
        }
    }



    /**
     * Ищет POI вокруг указанной точки по категориям пользователя.
     */
    private void searchNearbyPlaces(double lat, double lon, Set<String> categories) {
        lastPoiCenter = new Point(lat, lon); // ← ВАЖНО
        Toast.makeText(this, "Поиск POI в радиусе 5км...", Toast.LENGTH_SHORT).show();

        GeoapifyClient geoClient = new GeoapifyClient(this);
        geoClient.getNearbyPlaces(lat, lon, categories, new GeoapifyClient.GeoapifyCallback() {
            @Override
            public void onSuccess(List<GeoapifyClient.Place> places) {
                runOnUiThread(() -> {
                    if (places.isEmpty()) {
                        Toast.makeText(MainActivity.this, "POI не найдены рядом", Toast.LENGTH_SHORT).show();
                    } else {
                        displayNearbyPlaces(places);
                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() ->
                        Toast.makeText(MainActivity.this, "Ошибка POI: " + errorMessage, Toast.LENGTH_LONG).show()
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

            // 🔹 Здесь используем иконку pinm
            marker.setIcon(ImageProvider.fromResource(this, R.drawable.pinm));
        }

        Toast.makeText(this, "⭐ " + places.size() + " POI. Тапните для маршрута", Toast.LENGTH_LONG).show();
        if (!places.isEmpty()) adjustCameraToPlaces(places);

        poiMode = true;
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

    private void buildRouteAroundUser() {
        if (userLocation == null) {
            Toast.makeText(this, "Геопозиция пользователя недоступна", Toast.LENGTH_SHORT).show();
            return; // больше ничего не делаем
        }

        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
        if (categories.isEmpty()) {
            Toast.makeText(this, "Выберите категории в профиле", Toast.LENGTH_SHORT).show();
            return;
        }

        GeoapifyClient geoClient = new GeoapifyClient(this);
        geoClient.getNearbyPlaces(userLocation.getLatitude(), userLocation.getLongitude(), categories,
                new GeoapifyClient.GeoapifyCallback() {
                    @Override
                    public void onSuccess(List<GeoapifyClient.Place> places) {
                        runOnUiThread(() -> {
                            clearNearbyPlaces();
                            selectedPoints.clear();

                            // ✅ Добавляем только если геопозиция есть
                            startPoint = userLocation;
                            lastPoiCenter = userLocation;
                            selectedPoints.add(userLocation);

                            for (GeoapifyClient.Place place : places) {
                                if (place.location != null && distanceInMeters(userLocation, place.location) <= 5000) {
                                    PlacemarkMapObject marker = mapView.getMapWindow()
                                            .getMap().getMapObjects().addPlacemark(place.location);
                                    marker.setIcon(ImageProvider.fromResource(MainActivity.this, R.drawable.pinm));
                                    poiMarkers.add(marker);
                                }
                            }

                            updateBuildRouteButton();
                            Toast.makeText(MainActivity.this,
                                    "Точки вокруг геопозиции загружены. Стартовая точка добавлена.",
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this, "Ошибка POI: " + errorMessage, Toast.LENGTH_LONG).show()
                        );
                    }
                });
    }
    private void togglePlaceInRoute(PlacemarkMapObject marker) {
        GeoapifyClient.Place place = (GeoapifyClient.Place) marker.getUserData();
        if (place == null || place.location == null) return;

        if (selectedMarkers.contains(marker)) {
            // ❌ УБРАТЬ из маршрута
            selectedMarkers.remove(marker);
            selectedPoints.remove(place.location);

            marker.setIcon(ImageProvider.fromResource(this, R.drawable.pinm));

            Toast.makeText(this,
                    (place.name != null ? place.name : "Точка") + " убрано из маршрута",
                    Toast.LENGTH_SHORT).show();
        } else {
            // ✅ ДОБАВИТЬ в маршрут
            selectedMarkers.add(marker);
            selectedPoints.add(place.location);

            marker.setIcon(ImageProvider.fromResource(this, android.R.drawable.btn_star_big_on));

            Toast.makeText(this,
                    (place.name != null ? place.name : "Точка") + " добавлено (" + selectedPoints.size() + ")",
                    Toast.LENGTH_SHORT).show();
        }

        updateBuildRouteButton();
        updateEditCategoriesButton();  // ← добавлено

        if (currentRoute != null) {
            buildOptimalRoute();
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

        // ПЕРВЫЙ ТАП = центр POI
        if (poiMarkers.isEmpty()) {
            startPoint = point;
            lastPoiCenter = point;
            Toast.makeText(this, "🔍 Ищем POI вокруг точки...", Toast.LENGTH_SHORT).show();
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
            searchNearbyPlaces(point.getLatitude(), point.getLongitude(), categories);
            return;
        }

        if (manualStartPointMode) {
            manualStartPoint = point; // ← сохраняем выбранную точку
            startPoint = point;       // временно для визуализации
            manualStartPointMode = false;
            Toast.makeText(this, "Стартовая точка выбрана", Toast.LENGTH_SHORT).show();

            // Запускаем поиск POI вокруг этой точки
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
            searchNearbyPlaces(point.getLatitude(), point.getLongitude(), categories);
        }



        // POI есть → ищем тап по POI ИЛИ своя точка
        PlacemarkMapObject tappedMarker = null;
        GeoapifyClient.Place tappedPlace = null;

        for (PlacemarkMapObject marker : poiMarkers) {
            GeoapifyClient.Place markerPlace = (GeoapifyClient.Place) marker.getUserData();
            if (markerPlace != null && markerPlace.location != null) {
                double distance = distanceBetween(point, markerPlace.location);
                if (distance < 0.0005) { // ~50м
                    tappedMarker = marker;
                    tappedPlace = markerPlace;
                    break;
                }
            }
        }

        if (tappedMarker != null) {
            showPoiInfoDialog(tappedMarker);
        } else {
            showAddPointDialog(null, point);
        }


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
                    if (!selectedPoints.contains(point)) {
                        selectedPoints.add(point);
                        Toast.makeText(this,
                                "Точка добавлена (" + selectedPoints.size() + ")",
                                Toast.LENGTH_SHORT).show();
                        updateBuildRouteButton();
                        updateEditCategoriesButton();
                    } else {
                        Toast.makeText(this,
                                "Эта точка уже в маршруте",
                                Toast.LENGTH_SHORT).show();
                    }
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

        if (startPoint == null) {
            Toast.makeText(this, "Не выбрана стартовая точка маршрута", Toast.LENGTH_SHORT).show();
            return;
        }

        final double RADIUS_METERS = 5000;
        List<Point> filteredPoints = new ArrayList<>();

        for (Point p : selectedPoints) {
            double distance = distanceInMeters(startPoint, p);
            if (distance <= RADIUS_METERS) {
                filteredPoints.add(p);
            }
        }

        if (filteredPoints.size() < 2) {
            Toast.makeText(this, "Слишком мало точек в радиусе " + (int)(RADIUS_METERS/1000) + " км", Toast.LENGTH_LONG).show();
            return;
        }

        // Добавляем стартовую точку в начало маршрута
        List<Point> pointsToOptimize = new ArrayList<>(filteredPoints);
        pointsToOptimize.add(0, startPoint);

        Toast.makeText(this, "Построение оптимального маршрута...", Toast.LENGTH_LONG).show();

        List<Point> optimizedPoints = RouteOptimizer.optimize(pointsToOptimize);

        orsClient.getMultiPointRoute(
                MainActivity.this,
                optimizedPoints,
                new OpenRouteServiceClient.ORSCallback() {
                    @Override
                    public void onSuccess(List<Point> routeCoordinates) {
                        runOnUiThread(() -> {
                            displayRoute(routeCoordinates);
                            btnBuildRoute.setText("Сбросить маршрут");
                            btnBuildRoute.setEnabled(true);
                        });
                    }

                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() ->
                                Toast.makeText(MainActivity.this,
                                        "Ошибка построения маршрута: " + errorMessage,
                                        Toast.LENGTH_LONG).show()
                        );
                    }
                }
        );
    }

    /**
     * Расстояние между двумя точками в метрах по формуле Haversine
     */
    private double distanceInMeters(Point p1, Point p2) {
        double R = 6371000; // радиус Земли в метрах
        double lat1 = Math.toRadians(p1.getLatitude());
        double lat2 = Math.toRadians(p2.getLatitude());
        double dLat = Math.toRadians(p2.getLatitude() - p1.getLatitude());
        double dLon = Math.toRadians(p2.getLongitude() - p1.getLongitude());

        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(lat1) * Math.cos(lat2) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }

    private void showPoiInfoDialog(PlacemarkMapObject marker) {
        GeoapifyClient.Place place = (GeoapifyClient.Place) marker.getUserData();
        if (place == null) return;

        boolean alreadySelected = selectedMarkers.contains(marker);

        String title = place.name != null ? place.name : "Точка на карте";

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(
                        alreadySelected
                                ? "Убрать эту точку из маршрута?"
                                : "Добавить эту точку в маршрут?"
                )
                .setPositiveButton(
                        alreadySelected ? "Убрать" : "Добавить",
                        (d, w) -> togglePlaceInRoute(marker)
                )
                .setNegativeButton("Отмена", null)
                .show();
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
            for (PlacemarkMapObject marker : poiMarkers) mapObjects.remove(marker);
            if (routeLine != null) {
                mapObjects.remove(routeLine);
                routeLine = null;
            }
        }

        startPoint = null;
        selectedMarkers.clear();
        selectedPoints.clear();
        poiMarkers.clear();
        poiMode = false;
        routeMode = false;
        currentRoute = null;
        currentPointIndex = 0;

        updateBuildRouteButton();
        updateEditCategoriesButton(); // ← добавлено
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
     * Получает последнюю известную геопозицию пользователя и центрирует карту.
     */
    private void requestUserLocation() {
        try {
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            userLocation = new Point(location.getLatitude(), location.getLongitude());
                            centerMapOnLocation(userLocation);
                        } else {
                            fallbackToMoscow();
                        }
                    })
                    .addOnFailureListener(e -> fallbackToMoscow());
        } catch (SecurityException e) {
            fallbackToMoscow();
        }
    }


    private void checkAndRequestLocation() {
        if (!allowGeo) {
            Log.d("MainActivity", "Геолокация отключена пользователем");
            fallbackToMoscow();
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);

        } else {
            requestUserLocation();     // Google Fused (центр)
            initUserLocationLayer();   // Yandex MapKit (иконка)
        }
    }

    private void initUserLocationLayer() {
        if (mapView == null) return;

        userLocationLayer =
                MapKitFactory.getInstance().createUserLocationLayer(mapView.getMapWindow());

        userLocationLayer.setVisible(true);
        userLocationLayer.setAutoZoomEnabled(false);

        // Якорь иконки
        mapView.post(() -> {
            userLocationLayer.setAnchor(
                    new PointF(mapView.getWidth() / 2f, mapView.getHeight() / 2f),
                    new PointF(mapView.getWidth() / 2f, mapView.getHeight() * 0.75f)
            );
        });

        userLocationLayer.setObjectListener(new UserLocationObjectListener() {
            @Override
            public void onObjectAdded(UserLocationView view) {
                Log.d("MainActivity", "✅ User location added");

                try {
                    view.getPin().setIcon(
                            ImageProvider.fromResource(MainActivity.this, R.drawable.pinm)
                    );
                } catch (Exception e) {
                    Log.w("MainActivity", "Иконка пользователя не найдена");
                }

                view.getArrow().setVisible(true);
            }

            @Override
            public void onObjectRemoved(UserLocationView view) {}

            @Override
            public void onObjectUpdated(UserLocationView view, ObjectEvent event) {}
        });
    }



    private void fallbackToMoscow() {
        // Камера на Москву
        Point moscow = new Point(55.751225, 37.62954);
        centerMapOnLocation(moscow);

        // ⚠ Не ставим userLocation, чтобы она не использовалась как стартовая точка
        userLocation = null;

        Toast.makeText(this, "GPS недоступен, центр карты → Москва", Toast.LENGTH_SHORT).show();
    }


    private void centerMapOnLocation(Point loc) {
        if (mapView != null && mapView.getMapWindow() != null) {
            mapView.getMapWindow().getMap().move(
                    new CameraPosition(loc, 10f, 0f, 0f),
                    new Animation(Animation.Type.SMOOTH, 1f), null
            );
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
                requestUserLocation();
                initUserLocationLayer();
            } else {
                fallbackToMoscow();
                Toast.makeText(this,
                        "Геолокация отключена, выберите точку на карте",
                        Toast.LENGTH_LONG).show();
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