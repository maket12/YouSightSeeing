package ru.nsu.yousightseeing.features.main;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.search.Session;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.PlacesApi;
import ru.nsu.yousightseeing.api.RouteApi;
import ru.nsu.yousightseeing.features.MainActivity;
import ru.nsu.yousightseeing.features.ProfileActivity;
import ru.nsu.yousightseeing.features.map.MapInteractionController;
import ru.nsu.yousightseeing.features.poi.PoiController;
import ru.nsu.yousightseeing.features.route.AutoRouteController;
import ru.nsu.yousightseeing.features.route.RouteBuildMode;
import ru.nsu.yousightseeing.features.route.RouteConfirmationActivity;
import ru.nsu.yousightseeing.features.route.RouteController;
import ru.nsu.yousightseeing.features.startpoint.StartPointController;
import ru.nsu.yousightseeing.features.ui.UiStateController;
import ru.nsu.yousightseeing.model.Route;
import ru.nsu.yousightseeing.utils.DistanceHelper;
import ru.nsu.yousightseeing.utils.LocationHelper;
import ru.nsu.yousightseeing.utils.MapInputHelper;
import ru.nsu.yousightseeing.utils.MapPoiHelper;
import ru.nsu.yousightseeing.utils.MapPointHelper;
import ru.nsu.yousightseeing.utils.MapResetHelper;
import ru.nsu.yousightseeing.utils.MapRouteHelper;
import ru.nsu.yousightseeing.utils.MapUIHelper;
import ru.nsu.yousightseeing.utils.SearchHelper;

import static android.content.Context.MODE_PRIVATE;

public class MainPresenter implements MainContract.Presenter, MapInteractionController.MapInteractionCallback, RouteController.RouteControllerCallback, AutoRouteController.AutoRouteCallback, StartPointController.StartPointCallback, PoiController.PoiControllerCallback, UiStateController.UiStateCallback {

    private final MainContract.View view;
    private final MainUIManager uiManager;
    private final MainActivity mainActivity;

    // Yandex Search
    private SearchHelper searchHelper;
    private Session searchSession;

    // Controllers
    private MapInteractionController mapInteractionController;
    private RouteController routeController;
    private AutoRouteController autoRouteController;
    private StartPointController startPointController;
    private PoiController poiController;
    private UiStateController uiStateController;

    // State
    private Route currentRoute; // Will be refactored later
    private boolean isRouteBuilt = false;

    private RouteBuildMode currentBuildMode = RouteBuildMode.NONE;

    private static final int DEFAULT_RADIUS_METERS = 5000;
    private static final int DEFAULT_MAX_PLACES = 5;

    private MapInputHelper mapInputHelper;
    private Point userLocation;
    private boolean allowGeo = false;

    private static final String[] ALL_CATEGORIES = {
            "Природа и свежий воздух",
            "Активные приключения",
            "Курорты и здоровый отдых",
            "Досуг и развлечения",
            "История, культура",
            "Места для шопинга",
            "Необычные и скрытые уголки города"
    };

    private LocationHelper locationHelper;
    private MapUIHelper mapUIHelper;
    private MapRouteHelper mapRouteHelper;
    private MapPoiHelper mapPoiHelper;
    private MapPointHelper mapPointHelper;

    public MainPresenter(MainActivity mainActivity, MainUIManager uiManager) {
        this.view = mainActivity;
        this.uiManager = uiManager;
        this.mainActivity = mainActivity;
    }

    @Override
    public void initialize(boolean allowGeo) {
        this.allowGeo = allowGeo;
        Log.d("MainActivity", "ALLOW_GEO = " + allowGeo);

        if (uiManager.mapView == null) {
            throw new RuntimeException("MapView is null. Check layout file and MainUIManager.");
        }

        mapPointHelper = new MapPointHelper(mainActivity, uiManager.mapView);
        mapRouteHelper = new MapRouteHelper(mainActivity, uiManager.mapView);
        mapPoiHelper = new MapPoiHelper(mainActivity, uiManager.mapView);

        initializeSearch();
        initializeControllers();
        initializeUI();
    }

    private void initializeControllers() {
        mapInteractionController = new MapInteractionController(mainActivity, searchHelper, this);
        routeController = new RouteController(mainActivity, uiManager, mapRouteHelper, mapPointHelper, this);
        autoRouteController = new AutoRouteController(mainActivity, this);
        startPointController = new StartPointController(mainActivity, uiManager, mapPointHelper, this);
        poiController = new PoiController(mainActivity, mapPoiHelper, this);
        uiStateController = new UiStateController(uiManager, this);
    }

    private void initializeUI() {
        if (uiManager.tvStartTitle != null) uiManager.tvStartTitle.setOnClickListener(null);
        if (uiManager.tvStartSubtitle != null) uiManager.tvStartSubtitle.setOnClickListener(null);
        if (uiManager.sliderDurationHours != null) uiManager.sliderDurationHours.setValue(3f);
        if (uiManager.tvDurationValue != null) uiManager.tvDurationValue.setText("3 ч");
        if (uiManager.etAutoRadius != null && uiManager.etAutoRadius.getText().toString().trim().isEmpty()) {
            uiManager.etAutoRadius.setText(String.valueOf(DEFAULT_RADIUS_METERS));
        }
        if (uiManager.sliderMaxPlaces != null) uiManager.sliderMaxPlaces.setValue(DEFAULT_MAX_PLACES);
        if (uiManager.tvMaxPlacesValue != null) uiManager.tvMaxPlacesValue.setText(String.valueOf(DEFAULT_MAX_PLACES));

        if (uiManager.mapView != null) {
            mapUIHelper = new MapUIHelper(mainActivity, uiManager.mapView);
            locationHelper = new LocationHelper(mainActivity, uiManager.mapView, allowGeo, new LocationHelper.LocationCallback() {
                @Override
                public void onLocationFound(Point location) {
                    userLocation = location;
                }
                @Override
                public void onLocationFailed() {
                    userLocation = null;
                }
            });
            initializeMap();
            locationHelper.checkAndRequestLocation();
        } else {
            view.showToast("Ошибка инициализации MapView");
            Log.e("MainActivity", "MapView is null");
        }

        currentBuildMode = RouteBuildMode.MANUAL;
        if (uiManager.toggleRouteMode != null) uiManager.toggleRouteMode.check(R.id.btnModeManual);
        applyBuildModeUI(RouteBuildMode.MANUAL);
        setupButtonListeners();
        updateAllUI();
        uiStateController.collapseBottomSheet();
    }

    private void applyBuildModeUI(RouteBuildMode mode) {
        currentBuildMode = mode;
        if (mode == RouteBuildMode.MANUAL) {
            if (autoRouteController != null) autoRouteController.cancel();
        }
        uiStateController.applyBuildModeUI(mode);
    }

    private void updateSelectedPlacesList() {
        if (uiManager.placesContainer == null) return;
        uiManager.placesContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(mainActivity);

        List<Point> routePoints = new ArrayList<>();
        Point startPoint = startPointController.getStartPoint();
        for (Point p : routeController.getSelectedPoints()) {
            if (startPoint != null && DistanceHelper.distanceInMeters(startPoint, p) < 5.0) continue;
            routePoints.add(p);
        }

        if (routePoints.isEmpty()) {
            TextView empty = new TextView(mainActivity);
            empty.setText("Выберите места на карте — они появятся здесь.");
            empty.setTextColor(mainActivity.getResources().getColor(R.color.text_primary));
            empty.setAlpha(0.6f);
            empty.setTextSize(14f);
            empty.setPadding(0, 8, 0, 8);
            uiManager.placesContainer.addView(empty);
            return;
        }

        for (Point point : routePoints) {
            PlacemarkMapObject marker = null;
            PlacesApi.Place place = null;
            for (PlacemarkMapObject candidate : routeController.getSelectedMarkers()) {
                if (candidate.getUserData() instanceof PlacesApi.Place) {
                    PlacesApi.Place candidatePlace = (PlacesApi.Place) candidate.getUserData();
                    if (candidatePlace.lat != 0 && DistanceHelper.distanceInMeters(point, new Point(candidatePlace.lat, candidatePlace.lon)) < 5.0) {
                        marker = candidate;
                        place = candidatePlace;
                        break;
                    }
                }
            }
            View item = inflater.inflate(R.layout.item_place_pill, uiManager.placesContainer, false);
            TextView tvTitle = item.findViewById(R.id.tvPlaceTitle);
            TextView tvSubtitle = item.findViewById(R.id.tvPlaceSubtitle);
            android.widget.ImageButton btnRemove = item.findViewById(R.id.btnRemovePlace);
            View pill = item.findViewById(R.id.placePill);

            String title;
            if (place != null && place.name != null && !place.name.isEmpty()) {
                title = place.name;
            } else {
                title = String.format("Точка (%.5f, %.5f)", point.getLatitude(), point.getLongitude());
            }
            if (startPoint != null && DistanceHelper.distanceInMeters(startPoint, point) < 5.0) {
                title = "Старт: " + title;
            }
            tvTitle.setText(title);
            tvSubtitle.setText("Добавлено в маршрут");

            final PlacemarkMapObject markerRef = marker;
            View.OnClickListener removeListener = v -> {
                if (markerRef != null) routeController.togglePlaceInRoute(markerRef);
                updateSelectedPlacesList();
            };
            btnRemove.setOnClickListener(removeListener);
            pill.setOnClickListener(removeListener);
            uiManager.placesContainer.addView(item);
        }
    }

    private void updateStartHeader() {
        startPointController.updateStartHeader();
        Point startPoint = startPointController.getStartPoint();
        if (startPoint == null) return;

        String titleFromPoi = null;
        for (PlacemarkMapObject marker : routeController.getSelectedMarkers()) {
            if (marker.getUserData() instanceof PlacesApi.Place) {
                PlacesApi.Place place = (PlacesApi.Place) marker.getUserData();
                if (place.lat != 0 && DistanceHelper.distanceInMeters(startPoint, new Point(place.lat, place.lon)) < 20.0) {
                    titleFromPoi = place.name;
                    break;
                }
            }
        }
        if (titleFromPoi != null && !titleFromPoi.isEmpty()) {
            uiManager.tvStartTitle.setText(titleFromPoi);
            uiManager.tvStartSubtitle.setText("Выбранная точка на карте");
        }
    }

    private void initializeMap() {
        MapWindow mapWindow = uiManager.mapView.getMapWindow();
        if (mapWindow != null) {
            mapInputHelper = new MapInputHelper(new MapInputHelper.MapTapListener() {
                @Override
                public void onMapTap(Point point) {
                    handleMapTap(point);
                }
                @Override
                public void onMapLongTap(Point point) {
                    fullResetRoute();
                    view.showToast("Маршрут полностью очищен");
                }
            });
            mapWindow.getMap().addInputListener(mapInputHelper);
            if (!allowGeo) {
                mapWindow.getMap().move(
                        new CameraPosition(new Point(55.751225, 37.62954), 10f, 0f, 0f),
                        new Animation(Animation.Type.SMOOTH, 1),
                        null
                );
            }
        }
    }

    private void setupButtonListeners() {
        if (uiManager.btnZoomIn != null) uiManager.btnZoomIn.setOnClickListener(v -> mapUIHelper.zoomIn());
        if (uiManager.btnZoomOut != null) uiManager.btnZoomOut.setOnClickListener(v -> mapUIHelper.zoomOut());
        if (uiManager.btnSearch != null) {
            uiManager.btnSearch.setOnClickListener(v -> {
                String city = uiManager.editCity != null ? uiManager.editCity.getText().toString().trim() : "";
                if (!city.isEmpty()) searchHelper.submitQuery(city);
                else view.showToast("Введите название города");
            });
        }
        if (uiManager.btnProfile != null) uiManager.btnProfile.setOnClickListener(v -> view.startActivity(ProfileActivity.class));
        if (uiManager.btnOpenProfile != null) uiManager.btnOpenProfile.setOnClickListener(v -> view.startActivity(ProfileActivity.class));
        if (uiManager.btnEditCategories != null) uiManager.btnEditCategories.setOnClickListener(v -> showEditCategoriesDialog());
        if (uiManager.btnChangeStart != null) {
            uiManager.btnChangeStart.setOnClickListener(v -> {
                startPointController.enableStartPointSelection();
                uiStateController.collapseBottomSheet();
            });
        }
        if (uiManager.toggleRouteMode != null) {
            uiManager.toggleRouteMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                boolean hasManualData = currentRoute != null || !routeController.getSelectedPoints().isEmpty() || startPointController.getStartPoint() != null;
                if (checkedId == R.id.btnModeAuto && hasManualData) {
                    new AlertDialog.Builder(mainActivity)
                            .setTitle("Очистить маршрут?")
                            .setMessage("При переходе в автоматический режим текущий маршрут и выбранные точки будут удалены. Вы хотите продолжить?")
                            .setPositiveButton("Да", (dialog, which) -> {
                                fullResetRoute();
                                applyBuildModeUI(RouteBuildMode.AUTO);
                                uiStateController.expandBottomSheet();
                            })
                            .setNegativeButton("Нет", (dialog, which) -> group.check(R.id.btnModeManual))
                            .show();
                    return;
                }
                if (checkedId == R.id.btnModeManual) applyBuildModeUI(RouteBuildMode.MANUAL);
                else if (checkedId == R.id.btnModeAuto) applyBuildModeUI(RouteBuildMode.AUTO);
                uiStateController.expandBottomSheet();
            });
        }
        if (uiManager.sliderMaxPlaces != null && uiManager.tvMaxPlacesValue != null) {
            uiManager.tvMaxPlacesValue.setText(String.valueOf((int) uiManager.sliderMaxPlaces.getValue()));
            uiManager.sliderMaxPlaces.addOnChangeListener((slider, value, fromUser) -> uiManager.tvMaxPlacesValue.setText(String.valueOf((int) value)));
        }
        if (uiManager.sliderDurationHours != null && uiManager.tvDurationValue != null) {
            uiManager.tvDurationValue.setText(String.format("%d ч", (int) uiManager.sliderDurationHours.getValue()));
            uiManager.sliderDurationHours.addOnChangeListener((slider, value, fromUser) -> {
                int hours = (int) value;
                uiManager.tvDurationValue.setText(String.format("%d ч", hours));
            });
        }
        if (uiManager.btnAddPlace != null) {
            uiManager.btnAddPlace.setOnClickListener(v -> {
                applyBuildModeUI(RouteBuildMode.MANUAL);
                if (startPointController.getStartPoint() == null) {
                    view.showToast("Сначала выберите отправную точку");
                    startPointController.enableStartPointSelection();
                    return;
                }
                Point startPoint = startPointController.getStartPoint();
                if (poiController.getPoiMarkers().isEmpty() || poiController.getLastPoiCenter() == null || DistanceHelper.distanceInMeters(startPoint, poiController.getLastPoiCenter()) > 100) {
                    poiController.searchNearbyPlaces(startPoint.getLatitude(), startPoint.getLongitude());
                } else {
                    view.showToast("Тапните по точкам на карте, чтобы добавить или убрать их");
                }
                updateAllUI();
            });
        }
        if (uiManager.btnBuildRoute != null) {
            uiManager.btnBuildRoute.setOnClickListener(v -> {
                if (isRouteBuilt) {
                    fullResetRoute();
                    view.showToast("Маршрут полностью сброшен");
                    return;
                }
                if (currentBuildMode == RouteBuildMode.MANUAL) {
                    if (startPointController.getStartPoint() == null) {
                        startPointController.enableStartPointSelection();
                        return;
                    }
                    if (routeController.getManualSelectedPlacesCount() < 2) {
                        view.showToast("Добавьте минимум 2 места");
                        return;
                    }
                    uiStateController.collapseBottomSheet();
                    routeController.buildOptimalRoute();
                    isRouteBuilt = true;
                } else if (currentBuildMode == RouteBuildMode.AUTO) {
                    if (startPointController.getStartPoint() == null) {
                        startPointController.enableStartPointSelection();
                        return;
                    }
                    autoRouteController.startRouteGeneration();
                }
            });
        }
    }

    private int getAutoRadius() {
        if (uiManager.etAutoRadius == null) return DEFAULT_RADIUS_METERS;
        return parsePositiveInt(uiManager.etAutoRadius.getText().toString(), DEFAULT_RADIUS_METERS);
    }

    private int getAutoMaxPlaces() {
        if (uiManager.sliderMaxPlaces == null) return DEFAULT_MAX_PLACES;
        return Math.round(uiManager.sliderMaxPlaces.getValue());
    }

    private int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void showEditCategoriesDialog() {
        SharedPreferences prefs = mainActivity.getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> saved = prefs.getStringSet("categories", new HashSet<>());
        boolean[] checked = new boolean[ALL_CATEGORIES.length];
        for (int i = 0; i < ALL_CATEGORIES.length; i++) {
            checked[i] = saved.contains(ALL_CATEGORIES[i]);
        }
        new AlertDialog.Builder(mainActivity)
                .setTitle("Выбранные категории")
                .setMultiChoiceItems(ALL_CATEGORIES, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    Set<String> newSelection = new HashSet<>();
                    for (int i = 0; i < ALL_CATEGORIES.length; i++) {
                        if (checked[i]) newSelection.add(ALL_CATEGORIES[i]);
                    }
                    if (newSelection.isEmpty()) {
                        view.showToast("Нужно выбрать хотя бы одну категорию");
                        return;
                    }
                    prefs.edit().putStringSet("categories", newSelection).apply();
                    view.showToast("Категории обновлены");
                    poiController.reloadPoisWithNewCategories();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void initializeSearch() {
        if (uiManager.mapView != null) {
            searchHelper = new SearchHelper(mainActivity, uiManager.mapView);
        }
    }

    private void handleMapTap(Point point) {
        Log.d("MAP_TAP", "tap detected");
        if (startPointController.handleMapTap(point)) {
            return;
        }
        if (currentBuildMode == RouteBuildMode.MANUAL) {
            mapInteractionController.handleMapTap(point);
        }
    }

    @Override
    public void onPoiTapped(PlacemarkMapObject marker) {
        routeController.togglePlaceInRoute(marker);
    }

    @Override
    public void onCustomPointTapped(Point point, @Nullable String name) {
        routeController.addCustomPoint(point, name);
    }

    @Override
    public List<PlacemarkMapObject> getPoiMarkers() {
        return poiController.getPoiMarkers();
    }

    @Override
    public List<PlacemarkMapObject> getCustomMarkers() {
        return routeController.getCustomMarkers();
    }

    @Override
    public Set<PlacemarkMapObject> getSelectedMarkers() {
        return routeController.getSelectedMarkers();
    }

    @Override
    public void onRouteStateChanged() {
        updateAllUI();
    }

    @Override
    public void showToast(String message) {
        view.showToast(message);
    }

    @Override
    public Point getStartPoint() {
        return startPointController.getStartPoint();
    }

    @Override
    public Route getCurrentRoute() {
        return currentRoute;
    }

    @Override
    public void setCurrentRoute(Route route) {
        this.currentRoute = route;
    }

    private void resetRoute() {
        MapResetHelper.fullReset(uiManager.mapView, poiController.getPoiMarkers(), routeController.getCustomMarkers(), routeController.getSelectedMarkers(), mapRouteHelper, mapPointHelper);
        if (autoRouteController != null) autoRouteController.cancel();
        currentBuildMode = RouteBuildMode.NONE;
        currentRoute = null;
        isRouteBuilt = false;
        routeController.reset();
        startPointController.reset();
        poiController.clear();
        updateAllUI();
    }

    private void fullResetRoute() {
        MapResetHelper.fullReset(uiManager.mapView, poiController.getPoiMarkers(), routeController.getCustomMarkers(), routeController.getSelectedMarkers(), mapRouteHelper, mapPointHelper);
        routeController.reset();
        startPointController.reset();
        poiController.clear();
        if (autoRouteController != null) autoRouteController.cancel();
        currentBuildMode = RouteBuildMode.NONE;
        currentRoute = null;
        isRouteBuilt = false;
        updateAllUI();
    }
    
    public void updateAllUI() {
        uiStateController.updateAll();
        updateSelectedPlacesList();
        updateStartHeader();
    }

    private int getAutoDurationMinutes() {
        if (uiManager.sliderDurationHours == null) return 180;
        int hours = Math.round(uiManager.sliderDurationHours.getValue());
        return hours * 60;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (locationHelper != null) {
            locationHelper.onRequestPermissionsResult(requestCode, grantResults);
        }
    }

    @Override
    public void onStart() {
        if (locationHelper != null) {
            locationHelper.setVisible(true);
            if (allowGeo) {
                locationHelper.requestUserLocation();
            }
        }
    }

    @Override
    public void onStop() {
        if (searchSession != null) searchSession.cancel();
        if (searchHelper != null) searchHelper.cancelSearch();
        if (mapInteractionController != null) mapInteractionController.cancel();
        if (autoRouteController != null) autoRouteController.cancel();
    }

    @Override
    public void onRouteGenerationStart() {
        uiStateController.updateBuildRouteButton();
        view.showToast("Генерируем маршрут...");
    }

    @Override
    public void onRouteGenerated(RouteApi.GeneratedRouteResult result) {
        if (result == null || result.routePoints == null || result.routePoints.size() < 2) {
            view.showToast("Не удалось построить маршрут. Попробуйте другие параметры.");
            uiStateController.updateBuildRouteButton();
            return;
        }
        poiController.clear();
        routeController.reset();
        mapRouteHelper.clearCurrentRouteOnly();
        applyBuildModeUI(RouteBuildMode.MANUAL);
        routeController.addCustomPoint(startPointController.getStartPoint(), "Старт");
        mapPointHelper.showStartPoint(startPointController.getStartPoint());
        if (result.places != null) {
            for (PlacesApi.Place place : result.places) {
                Point placeLocation = new Point(place.lat, place.lon);
                routeController.addCustomPoint(placeLocation, place.name);
            }
        }
        mapRouteHelper.drawRoute(result.routePoints);
        Intent intent = new Intent(mainActivity, RouteConfirmationActivity.class);

        JSONArray placesJson = new JSONArray();
        if (result.places != null) {
            for (PlacesApi.Place place : result.places) {
                try {
                    JSONObject obj = new JSONObject();
                    obj.put("name", place.name);
                    obj.put("lat", place.lat);
                    obj.put("lon", place.lon);
                    placesJson.put(obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        JSONArray routePointsJson = new JSONArray();
        for (Point p : result.routePoints) {
            try {
                JSONArray pair = new JSONArray();
                pair.put(p.getLongitude());
                pair.put(p.getLatitude());
                routePointsJson.put(pair);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        intent.putExtra(RouteConfirmationActivity.EXTRA_PLACES_JSON, placesJson.toString());
        intent.putExtra(RouteConfirmationActivity.EXTRA_ROUTE_POINTS_JSON, routePointsJson.toString());
        intent.putExtra(RouteConfirmationActivity.EXTRA_DISTANCE, result.distance);
        intent.putExtra(RouteConfirmationActivity.EXTRA_DURATION, result.duration);

        mainActivity.startActivity(intent);
        updateAllUI();
        uiStateController.collapseBottomSheet();
        view.showToast("Маршрут построен! Теперь его можно редактировать.");
    }

    @Override
    public void onRouteGenerationFailed(String message) {
        currentBuildMode = RouteBuildMode.AUTO;
        uiStateController.updateBuildRouteButton();
        uiStateController.expandBottomSheet();
        if (message.contains("failed to search places")) {
            view.showToast("Не удалось найти места. Попробуйте ещё раз");
        } else {
            view.showToast("Ошибка генерации маршрута: " + message);
        }
    }

    @Override
    public AutoRouteController.AutoRouteParameters getAutoRouteParameters() {
        int radius = getAutoRadius();
        int maxPlaces = getAutoMaxPlaces();
        int durationMinutes = getAutoDurationMinutes();
        boolean includeFood = uiManager.switchSnack != null && uiManager.switchSnack.isChecked();
        return new AutoRouteController.AutoRouteParameters(radius, maxPlaces, durationMinutes, includeFood);
    }

    @Override
    public void onStartPointSelected(Point point) {
        updateAllUI();
        if (currentBuildMode == RouteBuildMode.MANUAL) {
            poiController.searchNearbyPlaces(point.getLatitude(), point.getLongitude());
        }
    }

    @Override
    public Point getUserLocation() {
        return userLocation;
    }

    @Override
    public RouteBuildMode getCurrentBuildMode() {
        return currentBuildMode;
    }

    @Override
    public List<Point> getSelectedPoints() {
        return routeController.getSelectedPoints();
    }

    @Override
    public boolean isGeneratingAutoRoute() {
        return autoRouteController.isGenerating();
    }

    @Override
    public boolean isAwaitingAutoStartPoint() {
        return startPointController.isAwaitingAutoStartPoint();
    }

    @Override
    public int getManualSelectedPlacesCount() {
        return routeController.getManualSelectedPlacesCount();
    }

    @Override
    public boolean isRoutePointsEmpty() {
        return routeController.getSelectedPoints().isEmpty();
    }

    @Override
    public void collapseBottomSheet() {
        uiStateController.collapseBottomSheet();
    }

    @Override
    public void expandBottomSheet() {
        uiStateController.expandBottomSheet();
    }

    @Override
    public boolean isRouteBuilt() {
        return isRouteBuilt;
    }
}
