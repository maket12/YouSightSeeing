package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
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
import ru.nsu.yousightseeing.api.PlacesApi;
import ru.nsu.yousightseeing.api.RouteApi;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

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

    private UserLocationLayer userLocationLayer;

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
    private enum RouteBuildMode {
        NONE,
        MANUAL,
        AUTO
    }

    private RouteBuildMode currentBuildMode = RouteBuildMode.NONE;

    private boolean awaitingAutoStartPoint = false;
    private boolean isGeneratingAutoRoute = false;

    private static final int DEFAULT_RADIUS_METERS = 5000;
    private static final int DEFAULT_MAX_PLACES = 5;
    private MaterialButtonToggleGroup toggleRouteMode;
    private MaterialButton btnModeManual;
    private MaterialButton btnModeAuto;

    private LinearLayout manualSection;
    private LinearLayout autoSection;

    private EditText etAutoRadius;
    private Slider sliderMaxPlaces;
    private TextView tvMaxPlacesValue;
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

        if (getIntent().hasExtra("ALLOW_GEO")) {
            allowGeo = getIntent().getBooleanExtra("ALLOW_GEO", false);
        }

        Log.d("MainActivity", "ALLOW_GEO = " + allowGeo);

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        initializeUI();
        initializeSearch();
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
        btnChangeStart = findViewById(R.id.btnChangeStart);

        toggleRouteMode = findViewById(R.id.toggleRouteMode);
        btnModeManual = findViewById(R.id.btnModeManual);
        btnModeAuto = findViewById(R.id.btnModeAuto);

        manualSection = findViewById(R.id.manualSection);
        autoSection = findViewById(R.id.autoSection);

        bottomSheet = findViewById(R.id.bottomSheet);
        placesContainer = findViewById(R.id.placesContainer);

        tvStartTitle = findViewById(R.id.tvStartTitle);
        tvStartSubtitle = findViewById(R.id.tvStartSubtitle);

        etAutoRadius = findViewById(R.id.etAutoRadius);
        sliderMaxPlaces = findViewById(R.id.sliderMaxPlaces);
        tvMaxPlacesValue = findViewById(R.id.tvMaxPlacesValue);

        // Оставлен в XML скрытым для совместимости
        sliderDurationHours = findViewById(R.id.sliderDurationHours);

        switchSnack = findViewById(R.id.switchSnack);

        // Убираем возможность кликать по тексту
        if (tvStartTitle != null) {
            tvStartTitle.setOnClickListener(null);
        }

        if (tvStartSubtitle != null) {
            tvStartSubtitle.setOnClickListener(null);
        }

        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
            bottomSheetBehavior.setSkipCollapsed(false);
            bottomSheetBehavior.setHideable(false);
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        if (etAutoRadius != null && etAutoRadius.getText().toString().trim().isEmpty()) {
            etAutoRadius.setText(String.valueOf(DEFAULT_RADIUS_METERS));
        }

        if (sliderMaxPlaces != null) {
            sliderMaxPlaces.setValue(DEFAULT_MAX_PLACES);
        }

        if (tvMaxPlacesValue != null) {
            tvMaxPlacesValue.setText(String.valueOf(DEFAULT_MAX_PLACES));
        }

        orsClient = new OpenRouteServiceClient();

        if (mapView != null) {
            initializeMap();
            checkAndRequestLocation();
        } else {
            Toast.makeText(this, "Ошибка инициализации MapView", Toast.LENGTH_LONG).show();
            Log.e("MainActivity", "MapView is null");
        }

        // Режим по умолчанию — ручной
        currentBuildMode = RouteBuildMode.MANUAL;

        if (toggleRouteMode != null) {
            toggleRouteMode.check(R.id.btnModeManual);
        }

        applyBuildModeUI(RouteBuildMode.MANUAL);

        setupButtonListeners();
        updateSelectedPlacesList();
        updateBottomSheetState();
        updateStartHeader();
        updateBuildRouteButton();
        updateEditCategoriesButton();
        collapseBottomSheet();
    }

    private void applyBuildModeUI(RouteBuildMode mode) {
        currentBuildMode = mode;

        if (manualSection != null) {
            manualSection.setVisibility(mode == RouteBuildMode.MANUAL ? View.VISIBLE : View.GONE);
        }

        if (autoSection != null) {
            autoSection.setVisibility(mode == RouteBuildMode.AUTO ? View.VISIBLE : View.GONE);
        }

        if (mode == RouteBuildMode.MANUAL) {
            awaitingAutoStartPoint = false;
            isGeneratingAutoRoute = false;
            // УБИРАЕМ АВТОМАТИЧЕСКОЕ routeMode = true, чтобы нельзя было просто так тыкать карту
            routeMode = false;
        } else if (mode == RouteBuildMode.AUTO) {
            manualStartPointMode = false;
            routeMode = false;
            poiMode = false;
        }

        updateBuildRouteButton();
        updateBottomSheetState();
    }

    private void enableStartPointSelection() {
        collapseBottomSheet();

        if (currentBuildMode == RouteBuildMode.AUTO) {
            if (userLocation != null) {
                showAutomaticRouteStartDialog();
            } else {
                awaitingAutoStartPoint = true;
                manualStartPointMode = false;
                routeMode = false;

                Toast.makeText(this,
                        "Тапните по карте, чтобы выбрать стартовую точку",
                        Toast.LENGTH_LONG).show();
            }

            updateBuildRouteButton();
            return;
        }

        if (userLocation != null) {
            showManualRouteStartDialog();
        } else {
            manualStartPointMode = true;
            awaitingAutoStartPoint = false;
            routeMode = true;

            Toast.makeText(this,
                    "Тапните по карте, чтобы выбрать стартовую точку",
                    Toast.LENGTH_LONG).show();
        }

        updateBuildRouteButton();
    }

    private void updateBottomSheetState() {
        if (bottomSheetBehavior == null) return;

        if (currentRoute != null) {
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
            if (btnChangeStart != null) {
                btnChangeStart.setVisibility(View.GONE);
            }
            return;
        }

        if (btnChangeStart != null) {
            btnChangeStart.setVisibility(View.VISIBLE);
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
                    fullResetRoute();
                    Toast.makeText(MainActivity.this, "Маршрут полностью очищен", Toast.LENGTH_SHORT).show();
                }
            };

            mapWindow.getMap().addInputListener(mapInputListener);

            if (!allowGeo) {
                mapWindow.getMap().move(
                        new CameraPosition(new Point(55.751225, 37.62954), 10f, 0f, 0f),
                        new Animation(Animation.Type.SMOOTH, 1),
                        null
                );
            }
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

        if (btnChangeStart != null) {
            btnChangeStart.setOnClickListener(v -> {
                enableStartPointSelection();
                if (bottomSheetBehavior != null) {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
            });
        }

        if (toggleRouteMode != null) {
            toggleRouteMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;

                if (currentRoute != null && checkedId == R.id.btnModeAuto) {
                    Toast.makeText(this, "Сбросьте маршрут, чтобы построить автоматически", Toast.LENGTH_SHORT).show();
                    group.check(R.id.btnModeManual);
                    return;
                }

                if (checkedId == R.id.btnModeAuto && !selectedPoints.isEmpty() && currentRoute == null) {
                    new AlertDialog.Builder(this)
                            .setTitle("Очистить маршрут?")
                            .setMessage("При переходе в автоматический режим ваши добавленные точки будут удалены. Вы хотите продолжить?")
                            .setPositiveButton("Да", (dialog, which) -> {
                                fullResetRoute();
                                applyBuildModeUI(RouteBuildMode.AUTO);
                                expandBottomSheet();
                            })
                            .setNegativeButton("Нет", (dialog, which) -> {
                                group.check(R.id.btnModeManual);
                            })
                            .show();
                    return;
                }

                if (checkedId == R.id.btnModeManual) {
                    applyBuildModeUI(RouteBuildMode.MANUAL);
                } else if (checkedId == R.id.btnModeAuto) {
                    applyBuildModeUI(RouteBuildMode.AUTO);
                }

                expandBottomSheet();
            });
        }

        if (sliderMaxPlaces != null && tvMaxPlacesValue != null) {
            tvMaxPlacesValue.setText(String.valueOf((int) sliderMaxPlaces.getValue()));
            sliderMaxPlaces.addOnChangeListener((slider, value, fromUser) ->
                    tvMaxPlacesValue.setText(String.valueOf((int) value))
            );
        }

        if (btnAddPlace != null) {
            btnAddPlace.setOnClickListener(v -> {
                SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
                Set<String> categories = prefs.getStringSet("categories", new HashSet<>());

                if (categories.isEmpty()) {
                    Toast.makeText(this, "Сначала выберите категории в профиле", Toast.LENGTH_SHORT).show();
                    return;
                }

                applyBuildModeUI(RouteBuildMode.MANUAL);
                routeMode = true;
                poiMode = false;

                if (startPoint == null) {
                    Toast.makeText(this, "Сначала выберите отправную точку", Toast.LENGTH_SHORT).show();
                    enableStartPointSelection();
                    return;
                }

                if (poiMarkers.isEmpty() || lastPoiCenter == null || distanceInMeters(startPoint, lastPoiCenter) > 100) {
                    searchNearbyPlaces(
                            startPoint.getLatitude(),
                            startPoint.getLongitude(),
                            categories
                    );
                    Toast.makeText(this, "Загружаем места рядом со стартом...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                            "Тапните по точкам на карте, чтобы добавить или убрать их",
                            Toast.LENGTH_LONG).show();
                }

                updateBuildRouteButton();
                updateBottomSheetState();
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
                    fullResetRoute();
                    Toast.makeText(this, "Маршрут полностью сброшен", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (currentBuildMode == RouteBuildMode.MANUAL) {
                    if (startPoint == null) {
                        enableStartPointSelection();
                        return;
                    }

                    if (getManualSelectedPlacesCount() < 2) {
                        Toast.makeText(this, "Добавьте минимум 2 места", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    collapseBottomSheet();
                    buildOptimalRoute();
                    return;
                }

                if (currentBuildMode == RouteBuildMode.AUTO) {
                    if (isGeneratingAutoRoute) {
                        Toast.makeText(this, "Маршрут уже генерируется...", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (startPoint == null) {
                        enableStartPointSelection();
                        return;
                    }

                    int radius = getAutoRadius();
                    int maxPlaces = getAutoMaxPlaces();
                    boolean includeFood = switchSnack != null && switchSnack.isChecked();
                    collapseBottomSheet();
                    generateAutomaticRoute(startPoint, radius, maxPlaces, includeFood);
                }
            });
        }
    }
    private int getAutoRadius() {
        if (etAutoRadius == null) return DEFAULT_RADIUS_METERS;
        return parsePositiveInt(etAutoRadius.getText().toString(), DEFAULT_RADIUS_METERS);
    }

    private int getAutoMaxPlaces() {
        if (sliderMaxPlaces == null) return DEFAULT_MAX_PLACES;
        return Math.round(sliderMaxPlaces.getValue());
    }

    private int getManualSelectedPlacesCount() {
        int count = 0;

        for (Point p : selectedPoints) {
            if (startPoint == null || distanceInMeters(startPoint, p) >= 5.0) {
                count++;
            }
        }

        return count;
    }

    private void showRouteModeDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Как построить маршрут?")
                .setItems(
                        new CharSequence[]{"Автоматически", "Выбрать точки вручную"},
                        (dialog, which) -> {
                            if (which == 0) {
                                startAutomaticRouteFlow();
                            } else {
                                startManualRouteFlow();
                            }
                        }
                )
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void startManualRouteFlow() {
        currentBuildMode = RouteBuildMode.MANUAL;
        routeMode = true;
        poiMode = false;
        awaitingAutoStartPoint = false;
        isGeneratingAutoRoute = false;

        updateBuildRouteButton();
        updateBottomSheetState();
        updateSelectedPlacesList();

        if (userLocation != null) {
            showManualRouteStartDialog();
        } else {
            Toast.makeText(this, "👆 Выберите точку на карте для начала маршрута", Toast.LENGTH_LONG).show();
        }
    }

    private void showManualRouteStartDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Начало маршрута")
                .setMessage("Строить маршрут от вашей текущей геопозиции или выбрать точку на карте?")
                .setPositiveButton("От моей позиции", (dialog, which) -> {
                    buildRouteAroundUser();
                    expandBottomSheet();
                    dialog.dismiss();
                })
                .setNegativeButton("Выбрать точку на карте", (dialog, which) -> {
                    manualStartPointMode = true;
                    Toast.makeText(this, "👆 Тапните на карте для выбора стартовой точки", Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                })
                .show();
    }

    private void startAutomaticRouteFlow() {
        currentBuildMode = RouteBuildMode.AUTO;
        routeMode = false;
        poiMode = false;
        awaitingAutoStartPoint = false;
        isGeneratingAutoRoute = false;

        clearNearbyPlaces();
        selectedMarkers.clear();
        selectedPoints.clear();

        updateBuildRouteButton();
        updateBottomSheetState();
        updateSelectedPlacesList();

        if (userLocation != null) {
            showAutomaticRouteStartDialog();
        } else {
            awaitingAutoStartPoint = true;
            updateBuildRouteButton();
            Toast.makeText(this, "👆 Тапните на карте для выбора стартовой точки", Toast.LENGTH_LONG).show();
        }
    }

    private void showAutomaticRouteStartDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Начало маршрута")
                .setMessage("Построить маршрут от вашей текущей геопозиции или выбрать старт на карте?")
                .setPositiveButton("От моей позиции", (dialog, which) -> {
                    if (userLocation == null) {
                        Toast.makeText(this, "Геопозиция недоступна", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    startPoint = userLocation;
                    lastPoiCenter = userLocation;
                    awaitingAutoStartPoint = false;

                    updateStartHeader();
                    updateBuildRouteButton();
                    expandBottomSheet();

                    Toast.makeText(this,
                            "Стартовая точка выбрана: текущее местоположение",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Выбрать точку на карте", (dialog, which) -> {
                    awaitingAutoStartPoint = true;
                    manualStartPointMode = false;
                    routeMode = false;

                    updateBuildRouteButton();
                    updateBottomSheetState();

                    Toast.makeText(this,
                            "Тапните по карте для выбора стартовой точки",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void showAutoRouteParamsDialog(Point start) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        EditText etRadius = new EditText(this);
        etRadius.setHint("Радиус, м");
        etRadius.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etRadius.setText(String.valueOf(DEFAULT_RADIUS_METERS));
        layout.addView(etRadius);

        EditText etMaxPlaces = new EditText(this);
        etMaxPlaces.setHint("Максимум точек");
        etMaxPlaces.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etMaxPlaces.setText(String.valueOf(DEFAULT_MAX_PLACES));
        layout.addView(etMaxPlaces);

        CheckBox cbFood = new CheckBox(this);
        cbFood.setText("Добавить кафе");
        cbFood.setChecked(switchSnack != null && switchSnack.isChecked());
        layout.addView(cbFood);

        new AlertDialog.Builder(this)
                .setTitle("Параметры маршрута")
                .setView(layout)
                .setPositiveButton("Построить", (dialog, which) -> {
                    int radius = parsePositiveInt(etRadius.getText().toString(), DEFAULT_RADIUS_METERS);
                    int maxPlaces = parsePositiveInt(etMaxPlaces.getText().toString(), DEFAULT_MAX_PLACES);
                    boolean includeFood = cbFood.isChecked();

                    generateAutomaticRoute(start, radius, maxPlaces, includeFood);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private Set<String> mapUserCategoriesToBackend(Set<String> userCategories) {
        Set<String> mapped = new HashSet<>();

        for (String category : userCategories) {
            switch (category) {
                case "Природа и свежий воздух":
                    mapped.add("leisure.park");
                    break;
                case "Активные приключения":
                    mapped.add("sport.sports_centre");
                    break;
                case "Курорты и здоровый отдых":
                    mapped.add("leisure.spa");
                    break;
                case "Досуг и развлечения":
                    mapped.add("tourism.attraction");
                    break;
                case "История, культура":
                    mapped.add("tourism.sights");
                    break;
                case "Места для шопинга":
                    mapped.add("commercial.shopping_mall");
                    break;
                case "Необычные и скрытые уголки города":
                    mapped.add("tourism.sights");
                    break;
            }
        }

        if (mapped.isEmpty()) {
            mapped.add("tourism.attraction");
            mapped.add("leisure.park");
        }

        return mapped;
    }

    private void collapseBottomSheet() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void expandBottomSheet() {
        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
    private void generateAutomaticRoute(Point start, int radius, int maxPlaces, boolean includeFood) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> userCategories = prefs.getStringSet("categories", new HashSet<>());
        Set<String> backendCategories = mapUserCategoriesToBackend(userCategories);

        isGeneratingAutoRoute = true;
        awaitingAutoStartPoint = false;
        updateBuildRouteButton();

        Toast.makeText(this, "Генерируем маршрут...", Toast.LENGTH_SHORT).show();

        RouteApi.generateRoute(
                this,
                start.getLatitude(),
                start.getLongitude(),
                backendCategories,
                radius,
                maxPlaces,
                includeFood,
                new RouteApi.GenerateRouteCallback() {
                    @Override
                    public void onSuccess(RouteApi.GeneratedRouteResult result) {
                        runOnUiThread(() -> {
                            isGeneratingAutoRoute = false;
                            displayGeneratedRoute(result);
                            updateBuildRouteButton();
                            updateBottomSheetState();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            isGeneratingAutoRoute = false;
                            awaitingAutoStartPoint = false;

                            // ОСТАЁМСЯ в AUTO, чтобы кнопка и экран не переключались в ручную логику
                            currentBuildMode = RouteBuildMode.AUTO;

                            updateBuildRouteButton();
                            expandBottomSheet();

                            Toast.makeText(
                                    MainActivity.this,
                                    "Ошибка генерации маршрута: " + message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void displayGeneratedPlaces(List<PlacesApi.Place> places) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
        clearNearbyPlaces();

        for (PlacesApi.Place place : places) {
            Point location = new Point(place.lat, place.lon);

            PlacemarkMapObject marker = mapObjects.addPlacemark(location);
            marker.setIcon(ImageProvider.fromResource(this, android.R.drawable.btn_star_big_on));

            GeoapifyClient.Place p = new GeoapifyClient.Place(place.name, location);

            marker.setUserData(p);

            poiMarkers.add(marker);
            selectedMarkers.add(marker);
            selectedPoints.add(location);
        }
    }

    private void displayGeneratedRoute(RouteApi.GeneratedRouteResult result) {
        selectedMarkers.clear();
        selectedPoints.clear();

        if (result.places != null) {
            displayGeneratedPlaces(result.places);
        }

        if (result.routePoints != null && !result.routePoints.isEmpty()) {
            displayRoute(result.routePoints, result.distance);
        }

        currentBuildMode = RouteBuildMode.MANUAL;
        toggleRouteMode.check(R.id.btnModeManual);

        updateSelectedPlacesList();

        collapseBottomSheet();

        String message = "Маршрут построен";
        if (result.distance > 0 && result.duration > 0) {
            message += String.format(" • %.1f км • %.0f мин",
                    result.distance / 1000.0,
                    result.duration / 60.0);
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void clearCurrentRouteOnly() {
        if (mapView != null && mapView.getMapWindow() != null && routeLine != null) {
            mapView.getMapWindow().getMap().getMapObjects().remove(routeLine);
            routeLine = null;
        }

        currentRoute = null;
        updateBuildRouteButton();
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

        if (currentBuildMode == RouteBuildMode.AUTO) {
            if (isGeneratingAutoRoute) {
                btnBuildRoute.setText("Генерируем...");
                btnBuildRoute.setEnabled(false);
                return;
            }

            if (awaitingAutoStartPoint || startPoint == null) {
                btnBuildRoute.setText("Выберите стартовую точку");
                btnBuildRoute.setEnabled(true);
                return;
            }

            btnBuildRoute.setText("Построить маршрут");
            btnBuildRoute.setEnabled(true);
            return;
        }

        // MANUAL
        if (startPoint == null) {
            btnBuildRoute.setText("Выберите стартовую точку");
            btnBuildRoute.setEnabled(true);
            return;
        }

        int count = getManualSelectedPlacesCount();

        if (count < 2) {
            btnBuildRoute.setText("Добавьте минимум 2 места");
            btnBuildRoute.setEnabled(false);
        } else {
            btnBuildRoute.setText("Построить маршрут (" + count + ")");
            btnBuildRoute.setEnabled(true);
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

        // 1. Сохраняем уже выбранные маркеры (из автогенерации или добавленные вручную)
        List<PlacemarkMapObject> markersToKeep = new ArrayList<>(selectedMarkers);

        // 2. Очищаем старые невыбранные POI
        for (PlacemarkMapObject marker : poiMarkers) {
            if (!selectedMarkers.contains(marker)) {
                mapObjects.remove(marker);
            }
        }
        poiMarkers.clear();

        // 3. Возвращаем сохраненные обратно в список
        poiMarkers.addAll(markersToKeep);

        // 4. Добавляем новые
        for (GeoapifyClient.Place place : places) {
            if (place.location == null) continue;

            // Проверяем, нет ли уже такого места среди выбранных (чтобы не создавать дубликат)
            boolean alreadyExists = false;
            for (Point existingPoint : selectedPoints) {
                if (distanceInMeters(existingPoint, place.location) < 5.0) {
                    alreadyExists = true;
                    break;
                }
            }

            if (!alreadyExists) {
                PlacemarkMapObject marker = mapObjects.addPlacemark(place.location);
                marker.setUserData(place);
                poiMarkers.add(marker);

                // 🔹 Здесь используем иконку pinm
                marker.setIcon(ImageProvider.fromResource(this, R.drawable.pinm));
            }
        }

        Toast.makeText(this, "⭐ Найдено новых POI рядом. Тапните для добавления", Toast.LENGTH_LONG).show();

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
                                    marker.setUserData(place);
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
        if (awaitingAutoStartPoint) {
            awaitingAutoStartPoint = false;
            startPoint = point;
            lastPoiCenter = point;

            updateStartHeader();
            updateBuildRouteButton();
            collapseBottomSheet();

            Toast.makeText(this, "Стартовая точка выбрана", Toast.LENGTH_SHORT).show();
            return;
        }

        if (manualStartPointMode) {
            manualStartPoint = point;
            startPoint = point;
            lastPoiCenter = point;
            manualStartPointMode = false;
            routeMode = true;

            Toast.makeText(this, "Стартовая точка выбрана", Toast.LENGTH_SHORT).show();

            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
            searchNearbyPlaces(point.getLatitude(), point.getLongitude(), categories);

            updateStartHeader();
            updateBuildRouteButton();
            collapseBottomSheet();
            return;
        }

        if (currentBuildMode == RouteBuildMode.AUTO) {
            return;
        }

        if (!routeMode) return;

        if (poiMarkers.isEmpty()) {
            startPoint = point;
            lastPoiCenter = point;

            Toast.makeText(this, "🔍 Ищем POI вокруг точки...", Toast.LENGTH_SHORT).show();

            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            Set<String> categories = prefs.getStringSet("categories", new HashSet<>());
            searchNearbyPlaces(point.getLatitude(), point.getLongitude(), categories);

            updateStartHeader();
            updateBuildRouteButton();
            updateBottomSheetState();
            return;
        }

        PlacemarkMapObject tappedMarker = null;

        for (PlacemarkMapObject marker : poiMarkers) {
            GeoapifyClient.Place markerPlace = (GeoapifyClient.Place) marker.getUserData();
            if (markerPlace != null && markerPlace.location != null) {
                double distance = distanceBetween(point, markerPlace.location);
                if (distance < 0.0005) {
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
        displayRoute(routeCoordinates, 0.0);
    }

    private void displayRoute(List<Point> routeCoordinates, double distance) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        if (routeLine != null) {
            mapObjects.remove(routeLine);
        }

        Polyline poly = new Polyline(routeCoordinates);
        routeLine = mapObjects.addPolyline(poly);

        adjustCameraToRoute(routeCoordinates);

        currentRoute = new Route(
                routeCoordinates,
                "Маршрут " + System.currentTimeMillis(),
                distance
        );

        collapseBottomSheet();
        Toast.makeText(MainActivity.this, "Маршрут построен!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Сброс маршрута
     */
    private void resetRoute() {
        if (mapView != null && mapView.getMapWindow() != null) {
            MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
            
            // Удаляем только те маркеры, которые не находятся в списке выбранных
            List<PlacemarkMapObject> markersToRemove = new ArrayList<>();
            for (PlacemarkMapObject marker : poiMarkers) {
                if (!selectedMarkers.contains(marker)) {
                    markersToRemove.add(marker);
                }
            }
            
            for (PlacemarkMapObject marker : markersToRemove) {
                mapObjects.remove(marker);
                poiMarkers.remove(marker);
            }

            if (routeLine != null) {
                mapObjects.remove(routeLine);
                routeLine = null;
            }
        }

        // Мы больше не очищаем selectedMarkers, selectedPoints и стартовую точку
        // startPoint = null;
        // manualStartPoint = null;
        // manualStartPointMode = false;
        // selectedMarkers.clear();
        // selectedPoints.clear();
        
        poiMode = false;
        routeMode = false;
        awaitingAutoStartPoint = false;
        isGeneratingAutoRoute = false;
        currentBuildMode = RouteBuildMode.NONE;

        currentRoute = null;
        currentPointIndex = 0;

        updateBuildRouteButton();
        updateEditCategoriesButton();
        updateSelectedPlacesList();
        updateBottomSheetState();
        updateStartHeader();
    }

    /**
     * Полный сброс (вызывается при переключении из ручного в авто режим и тд)
     */
    private void fullResetRoute() {
        if (mapView != null && mapView.getMapWindow() != null) {
            MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();
            for (PlacemarkMapObject marker : poiMarkers) mapObjects.remove(marker);
            if (routeLine != null) {
                mapObjects.remove(routeLine);
                routeLine = null;
            }
            
            if (startMarker != null) {
                mapObjects.remove(startMarker);
                startMarker = null;
            }
        }

        startPoint = null;
        manualStartPoint = null;
        manualStartPointMode = false;

        selectedMarkers.clear();
        selectedPoints.clear();
        poiMarkers.clear();

        poiMode = false;
        routeMode = false;
        awaitingAutoStartPoint = false;
        isGeneratingAutoRoute = false;
        currentBuildMode = RouteBuildMode.NONE;

        currentRoute = null;
        currentPointIndex = 0;

        updateBuildRouteButton();
        updateEditCategoriesButton();
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

        // не создавать второй слой
        if (userLocationLayer != null) return;

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

        if (userLocationLayer != null) {
            userLocationLayer.setVisible(true);
        }

        if (allowGeo) {
            requestUserLocation();
        }
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
