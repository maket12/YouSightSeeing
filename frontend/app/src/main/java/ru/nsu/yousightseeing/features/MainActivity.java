package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import ru.nsu.yousightseeing.BuildConfig;
import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.model.Route;
import ru.nsu.yousightseeing.utils.RouteOptimizer;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.BoundingBox;
import com.yandex.mapkit.geometry.Geometry;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.InputListener;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.PolylineMapObject;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.mapkit.layers.ObjectEvent;
import android.graphics.PointF;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class MainActivity extends AppCompatActivity implements Session.SearchListener {

    // UI компоненты
    private MapView mapView;
    private EditText editCity;
    private ImageButton btnSearch;
    private Button btnBuildRoute;
    private Button btnZoomIn;
    private Button btnZoomOut;

    private Button btnProfile;
    private Button btnAddPlace;
    private Button btnOpenProfile;

    private PlacemarkMapObject startMarker;
    private Point manualStartPoint;

    private Button btnSelectStartPoint;
    private boolean manualStartPointMode = false; // режим выбора стартовой точки

    private final Set<PlacemarkMapObject> selectedMarkers = new HashSet<>();

    private LinearLayout bottomSheet;
    private BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    private LinearLayout placesContainer;
    private Slider sliderDurationHours;
    private SwitchMaterial switchSnack;
    private TextView tvStartTitle;
    private TextView tvStartSubtitle;
    private Button btnChangeStart;

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
    private final List<PlacemarkMapObject> customMarkers = new ArrayList<>();
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
        initializeMapKit();
        setContentView(R.layout.activity_main);

        // ← СВЯЗЬ С PermissionActivity
        allowGeo = getIntent().getBooleanExtra("ALLOW_GEO", false);
        Log.d("MainActivity", "ALLOW_GEO = " + allowGeo);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

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

            MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY);
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
        btnAddPlace = findViewById(R.id.btnAddPlace);
        btnOpenProfile = findViewById(R.id.btnOpenProfile);

        bottomSheet = findViewById(R.id.bottomSheet);
        placesContainer = findViewById(R.id.placesContainer);
        sliderDurationHours = findViewById(R.id.sliderDurationHours);
        switchSnack = findViewById(R.id.switchSnack);
        tvStartTitle = findViewById(R.id.tvStartTitle);
        tvStartSubtitle = findViewById(R.id.tvStartSubtitle);
        btnChangeStart = findViewById(R.id.btnChangeStart);

        tvStartTitle.setOnClickListener(v -> enableStartPointSelection());
        tvStartSubtitle.setOnClickListener(v -> enableStartPointSelection());

        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setSkipCollapsed(false);
            bottomSheetBehavior.setHideable(false);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }


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
        updateSelectedPlacesList();
        updateBottomSheetState();
        updateStartHeader();
    }

    private void enableStartPointSelection() {
        // Нельзя менять, если старт ещё не выбран и геолокации нет
        if (startPoint == null && userLocation == null) {
            Toast.makeText(this,
                    "Сначала задайте отправную точку кнопкой \"Построить маршрут\"",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Если старт ещё не выбран, но есть геолокация — сначала покажем выбор
        if (startPoint == null && userLocation != null) {
            showRouteStartDialog();
            return;
        }

        manualStartPointMode = true;
        routeMode = true;

        Toast.makeText(this,
                "Тапните по карте, чтобы изменить стартовую точку",
                Toast.LENGTH_LONG).show();
    }

    private void updateBottomSheetState() {
        if (bottomSheetBehavior == null) return;

        if (currentRoute != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            return;
        }

        if (routeMode) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void updateSelectedPlacesList() {
        if (placesContainer == null) return;
        placesContainer.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(this);

        // Список для плана маршрута: только точки, отличные от старта
        List<Point> routePoints = new ArrayList<>();
        for (Point p : selectedPoints) {
            if (startPoint != null && distanceInMeters(startPoint, p) < 5.0) {
                continue;
            }
            routePoints.add(p);
        }

        if (routePoints.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Выберите места на карте — они появятся здесь.");
            empty.setTextColor(getResources().getColor(R.color.text_primary));
            empty.setAlpha(0.6f);
            empty.setTextSize(14f);
            empty.setPadding(0, 8, 0, 8);
            placesContainer.addView(empty);
            return;
        }

        for (Point point : routePoints) {

            // Найдём маркер/POI (если есть) для этой точки
            PlacemarkMapObject marker = null;
            GeoapifyClient.Place place = null;
            for (PlacemarkMapObject candidate : selectedMarkers) {
                GeoapifyClient.Place candidatePlace = (GeoapifyClient.Place) candidate.getUserData();
                if (candidatePlace != null && candidatePlace.location != null &&
                        distanceInMeters(point, candidatePlace.location) < 5.0) {
                    marker = candidate;
                    place = candidatePlace;
                    break;
                }
            }
            View item = inflater.inflate(R.layout.item_place_pill, placesContainer, false);

            TextView tvTitle = item.findViewById(R.id.tvPlaceTitle);
            TextView tvSubtitle = item.findViewById(R.id.tvPlaceSubtitle);
            ImageButton btnRemove = item.findViewById(R.id.btnRemovePlace);
            View pill = item.findViewById(R.id.placePill);

            String title;
            if (place != null && place.name != null && !place.name.isEmpty()) {
                title = place.name;
            } else {
                title = String.format("Точка (%.5f, %.5f)",
                        point.getLatitude(), point.getLongitude());
            }

            // помечаем стартовую точку
            if (startPoint != null && distanceInMeters(startPoint, point) < 5.0) {
                title = "Старт: " + title;
            }
            tvTitle.setText(title);

            String subtitle = "Добавлено в маршрут";
            tvSubtitle.setText(subtitle);

            final Point pointRef = point;
            final PlacemarkMapObject markerRef = marker;

            View.OnClickListener removeListener = v -> {
                if (markerRef != null) {
                    togglePlaceInRoute(markerRef);
                } else {
                    selectedPoints.remove(pointRef);
                    // если убираем стартовую точку
                    if (startPoint != null && distanceInMeters(startPoint, pointRef) < 5.0) {
                        startPoint = null;
                        showStartPoint(null);
                        updateStartHeader();
                    }
                    updateBuildRouteButton();
                }
                updateSelectedPlacesList();
            };
            btnRemove.setOnClickListener(removeListener);
            pill.setOnClickListener(removeListener);

            placesContainer.addView(item);
        }
    }

    /**
     * Обновляет заголовок блока "Начнем из" в bottom sheet.
     * Показывает, откуда стартует маршрут: геопозиция пользователя или выбранная точка.
     */
    private void updateStartHeader() {
        if (tvStartTitle == null || tvStartSubtitle == null) return;

        if (startPoint == null) {
            tvStartTitle.setText("Укажите отправную точку...");
            tvStartSubtitle.setText("");
            tvStartSubtitle.setAlpha(0.0f);
            return;
        }

        // Старт от текущей геопозиции пользователя
        if (userLocation != null && distanceInMeters(startPoint, userLocation) < 5.0) {
            tvStartTitle.setText("Ваше местоположение");
            tvStartSubtitle.setText("По данным геопозиции");
            tvStartSubtitle.setAlpha(0.6f);
            return;
        }

        // Попробуем найти место среди выбранных POI
        String titleFromPoi = null;
        for (PlacemarkMapObject marker : selectedMarkers) {
            GeoapifyClient.Place place = (GeoapifyClient.Place) marker.getUserData();
            if (place != null && place.location != null &&
                    distanceInMeters(startPoint, place.location) < 20.0) {
                titleFromPoi = place.name;
                break;
            }
        }

        if (titleFromPoi != null && !titleFromPoi.isEmpty()) {
            tvStartTitle.setText(titleFromPoi);
            tvStartSubtitle.setText("Выбранная точка на карте");
        } else {
            tvStartTitle.setText(String.format("%.5f, %.5f", startPoint.getLatitude(), startPoint.getLongitude()));
            tvStartSubtitle.setText("Тапните по карте, чтобы изменить");
        }
        tvStartSubtitle.setAlpha(0.6f);
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

        if (btnOpenProfile != null) {
            btnOpenProfile.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        }

        if (btnEditCategories != null) {
            btnEditCategories.setOnClickListener(v -> showEditCategoriesDialog());
        }

        if (btnAddPlace != null) {
            btnAddPlace.setOnClickListener(v -> {
                SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
                if (categories.isEmpty()) {
                    Toast.makeText(this, "Сначала выберите категории в профиле", Toast.LENGTH_SHORT).show();
                    return;
                }

                routeMode = true;
                poiMode = false;
                updateBuildRouteButton();
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }

                if (poiMarkers.isEmpty()) {
                    Toast.makeText(this, "👆 Тапните на карте — покажем точки интереса вокруг", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Тапните по точкам интереса на карте, чтобы добавить/убрать", Toast.LENGTH_LONG).show();
                }
            });
        }

        if (btnSelectStartPoint != null) {
            btnSelectStartPoint.setOnClickListener(v -> enableStartPointSelection());
        }

        if (btnChangeStart != null) {
            btnChangeStart.setOnClickListener(v -> {
                enableStartPointSelection();
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
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
                    updateBottomSheetState();
                    return;
                }

                if (!routeMode) {
                    // Включаем режим выбора маршрута
                    routeMode = true;
                    updateBottomSheetState();

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
                updateSelectedPlacesList();
                updateBottomSheetState();
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
        searchManager = SearchFactory.getInstance()
                .createSearchManager(com.yandex.mapkit.search.SearchManagerType.COMBINED);
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
            if (poiMarkers.isEmpty() && customMarkers.isEmpty()) {
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

    private void showStartPoint(Point point) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        // удалить старую звезду
        if (startMarker != null) {
            mapObjects.remove(startMarker);
            startMarker = null;
        }

        if (point == null) return;

        // создать новую
        startMarker = mapObjects.addPlacemark(point);
        startMarker.setIcon(
                ImageProvider.fromResource(this, android.R.drawable.btn_star_big_on)
        );
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
        updateSelectedPlacesList();
        updateBottomSheetState();
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

                            // ✅ Стартовая точка = геопозиция, но в план не добавляем
                            startPoint = userLocation;
                            lastPoiCenter = userLocation;

                            for (GeoapifyClient.Place place : places) {
                                if (place.location != null && distanceInMeters(userLocation, place.location) <= 5000) {
                                    PlacemarkMapObject marker = mapView.getMapWindow()
                                            .getMap().getMapObjects().addPlacemark(place.location);
                                    marker.setIcon(ImageProvider.fromResource(MainActivity.this, R.drawable.pinm));
                                    poiMarkers.add(marker);
                                }
                            }

                            updateBuildRouteButton();
                            updateStartHeader();
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

            if (customMarkers.contains(marker)) {
                // For custom points, remove them completely
                mapView.getMapWindow().getMap().getMapObjects().remove(marker);
                customMarkers.remove(marker);
            } else {
                // For POI, just reset the icon
                marker.setIcon(ImageProvider.fromResource(this, R.drawable.pinm));
            }

            String placeName = (place.name != null && !place.name.isEmpty()) ? place.name : String.format("%.5f, %.5f", place.location.getLatitude(), place.location.getLongitude());
            Toast.makeText(this, placeName + " убрано из маршрута", Toast.LENGTH_SHORT).show();
        } else {
            // ✅ ДОБАВИТЬ в маршрут
            selectedMarkers.add(marker);
            selectedPoints.add(place.location);

            marker.setIcon(ImageProvider.fromResource(this, android.R.drawable.btn_star_big_on));

            String placeName = (place.name != null && !place.name.isEmpty()) ? place.name : String.format("%.5f, %.5f", place.location.getLatitude(), place.location.getLongitude());
            Toast.makeText(this, placeName + " добавлено (" + selectedPoints.size() + ")", Toast.LENGTH_SHORT).show();
        }

        updateBuildRouteButton();
        updateEditCategoriesButton();
        updateSelectedPlacesList();
        updateBottomSheetState();

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

        // если пользователь меняет стартовую точку
        if (manualStartPointMode) {

            startPoint = point;
            manualStartPoint = point;

            showStartPoint(point);   // ⭐ обновляем звезду
            updateStartHeader();

            manualStartPointMode = false;

            // при смене старта подгружаем дополнительные POI вокруг новой точки
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
            if (categories == null) categories = new HashSet<>();
            searchNearbyPlaces(point.getLatitude(), point.getLongitude(), categories);

            Toast.makeText(this, "Стартовая точка изменена", Toast.LENGTH_SHORT).show();
            return;
        }

        // первый тап — выбираем стартовую точку и ищем POI
        if (poiMarkers.isEmpty() && customMarkers.isEmpty()) {

            startPoint = point;
            lastPoiCenter = point;

            showStartPoint(point);   // ⭐ показать стартовую точку

            Toast.makeText(this, "🔍 Ищем POI вокруг точки...", Toast.LENGTH_SHORT).show();

            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> categories = prefs.getStringSet("categories", new HashSet<>());

            searchNearbyPlaces(point.getLatitude(), point.getLongitude(), categories);

            updateStartHeader();
            return;
        }

        // ищем тап по существующему маркеру
        List<PlacemarkMapObject> allMarkers = new ArrayList<>(poiMarkers);
        allMarkers.addAll(customMarkers);

        PlacemarkMapObject tappedMarker = null;

        for (PlacemarkMapObject marker : allMarkers) {
            Point markerLocation = marker.getGeometry();
            if (markerLocation != null) {
                double distance = distanceBetween(point, markerLocation);
                if (distance < 0.0005) { // ~50 м
                    tappedMarker = marker;
                    break;
                }
            }
        }

        if (tappedMarker != null) {
            showPoiInfoDialog(tappedMarker);
        } else {
            addCustomPointFromTap(point);
        }
    }


    /** Расстояние между двумя точками в градусах (~111м на градус) */
    private double distanceBetween(Point p1, Point p2) {
        double latDiff = Math.abs(p1.getLatitude() - p2.getLatitude());
        double lonDiff = Math.abs(p1.getLongitude() - p2.getLongitude());
        return Math.sqrt(latDiff * latDiff + lonDiff * lonDiff);
    }

    private void addCustomPointFromTap(final Point point) {

        Toast.makeText(this, "Определение точки...", Toast.LENGTH_SHORT).show();

        SearchOptions options = new SearchOptions();
        options.setResultPageSize(1);

        searchSession = searchManager.submit(
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

                        runOnUiThread(() ->
                                showConfirmationForCustomPoint(point, title)
                        );
                    }

                    @Override
                    public void onSearchError(Error error) {

                        Log.e("MainActivity",
                                "Reverse geocoding error: " + error.toString());

                        runOnUiThread(() ->
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

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage("Добавить в маршрут?")
                .setPositiveButton("Добавить", (dialog, which) -> {
                    MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
                    PlacemarkMapObject newMarker = mapObjects.addPlacemark(point);

                    GeoapifyClient.Place newPlace = new GeoapifyClient.Place(title, point);
                    newMarker.setUserData(newPlace);

                    customMarkers.add(newMarker);
                    togglePlaceInRoute(newMarker);
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

        String title;
        if (place.name != null && !place.name.isEmpty()) {
            title = place.name;
        } else {
            title = String.format("%.5f, %.5f", place.location.getLatitude(), place.location.getLongitude());
        }

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
        updateBottomSheetState();
    }

    /**
     * Сброс маршрута
     */
    private void resetRoute() {
        if (mapView != null && mapView.getMapWindow() != null) {
            MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
            for (PlacemarkMapObject marker : poiMarkers) mapObjects.remove(marker);
            for (PlacemarkMapObject marker : customMarkers) mapObjects.remove(marker);
            if (routeLine != null) {
                mapObjects.remove(routeLine);
                routeLine = null;
            }
        }

        startPoint = null;
        selectedMarkers.clear();
        selectedPoints.clear();
        poiMarkers.clear();
        customMarkers.clear();
        poiMode = false;
        routeMode = false;
        currentRoute = null;
        currentPointIndex = 0;

        updateBuildRouteButton();
        updateEditCategoriesButton(); // ← добавлено
        updateSelectedPlacesList();
        updateBottomSheetState();
        updateStartHeader();
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

        if (mapView == null || mapView.getMapWindow() == null) {
            Log.e("MainActivity", "MapView or MapWindow is null");
            Toast.makeText(this, "Ошибка карты", Toast.LENGTH_SHORT).show();
            return;
        }

        com.yandex.mapkit.map.Map map = mapView.getMapWindow().getMap();

        if (map == null) {
            Log.e("MainActivity", "Map is null");
            return;
        }

        VisibleRegion visibleRegion = map.getVisibleRegion();

        if (visibleRegion == null) {
            Log.e("MainActivity", "Visible region is null");
            Toast.makeText(this, "Ошибка: область карты недоступна", Toast.LENGTH_SHORT).show();
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

        Log.d("MainActivity", "Search query submitted: " + query);
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
