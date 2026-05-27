package ru.nsu.yousightseeing.features.startpoint;

import android.app.AlertDialog;
import android.view.View;

import com.yandex.mapkit.geometry.Point;

import ru.nsu.yousightseeing.features.MainActivity;
import ru.nsu.yousightseeing.features.main.MainUIManager;
import ru.nsu.yousightseeing.features.route.RouteBuildMode;
import ru.nsu.yousightseeing.utils.DistanceHelper;
import ru.nsu.yousightseeing.utils.MapPointHelper;

public class StartPointController {

    private final MainActivity mainActivity;
    private final MainUIManager uiManager;
    private final StartPointCallback callback;
    private final MapPointHelper mapPointHelper;

    private Point startPoint;
    private boolean manualStartPointMode = false;
    private boolean awaitingAutoStartPoint = false;

    public interface StartPointCallback {
        void showToast(String message);
        void collapseBottomSheet();
        void expandBottomSheet();
        void onStartPointSelected(Point point);
        Point getUserLocation();
        RouteBuildMode getCurrentBuildMode();
    }

    public StartPointController(MainActivity mainActivity, MainUIManager uiManager, MapPointHelper mapPointHelper, StartPointCallback callback) {
        this.mainActivity = mainActivity;
        this.uiManager = uiManager;
        this.mapPointHelper = mapPointHelper;
        this.callback = callback;
    }

    public void enableStartPointSelection() {
        callback.collapseBottomSheet();

        if (callback.getCurrentBuildMode() == RouteBuildMode.AUTO) {
            if (callback.getUserLocation() != null) {
                showAutomaticRouteStartDialog();
            } else {
                awaitingAutoStartPoint = true;
                manualStartPointMode = false;
                callback.showToast("Тапните по карте, чтобы выбрать стартовую точку");
            }
            return;
        }

        if (callback.getUserLocation() != null) {
            showManualRouteStartDialog();
        } else {
            manualStartPointMode = true;
            awaitingAutoStartPoint = false;
            callback.showToast("Тапните по карте, чтобы выбрать стартовую точку");
        }
    }

    public boolean handleMapTap(Point point) {
        if (awaitingAutoStartPoint) {
            setStartPoint(point);
            awaitingAutoStartPoint = false;
            callback.showToast("Стартовая точка выбрана");
            callback.collapseBottomSheet();
            return true;
        }

        if (manualStartPointMode) {
            setStartPoint(point);
            manualStartPointMode = false;
            callback.showToast("Стартовая точка выбрана");
            callback.collapseBottomSheet();
            return true;
        }
        return false;
    }

    private void showManualRouteStartDialog() {
        new AlertDialog.Builder(mainActivity)
                .setTitle("Начало маршрута")
                .setMessage("Строить маршрут от вашей текущей геопозиции или выбрать точку на карте?")
                .setPositiveButton("От моей позиции", (dialog, which) -> {
                    setStartPoint(callback.getUserLocation());
                    callback.expandBottomSheet();
                    dialog.dismiss();
                })
                .setNegativeButton("Выбрать точку на карте", (dialog, which) -> {
                    manualStartPointMode = true;
                    callback.showToast("👆 Тапните на карте для выбора стартовой точки");
                    dialog.dismiss();
                })
                .show();
    }

    private void showAutomaticRouteStartDialog() {
        new AlertDialog.Builder(mainActivity)
                .setTitle("Начало маршрута")
                .setMessage("Построить маршрут от вашей текущей геопозиции или выбрать старт на карте?")
                .setPositiveButton("От моей позиции", (dialog, which) -> {
                    if (callback.getUserLocation() == null) {
                        callback.showToast("Геопозиция недоступна");
                        return;
                    }
                    setStartPoint(callback.getUserLocation());
                    callback.expandBottomSheet();
                    callback.showToast("Стартовая точка выбрана: текущее местоположение");
                })
                .setNegativeButton("Выбрать точку на карте", (dialog, which) -> {
                    awaitingAutoStartPoint = true;
                    manualStartPointMode = false;
                    callback.showToast("Тапните по карте для выбора стартовой точки");
                })
                .show();
    }

    public void updateStartHeader() {
        if (uiManager.tvStartTitle == null || uiManager.tvStartSubtitle == null) return;

        if (startPoint == null) {
            uiManager.tvStartTitle.setText("Укажите отправную точку...");
            uiManager.tvStartSubtitle.setText("");
            uiManager.tvStartSubtitle.setAlpha(0.0f);
            if (uiManager.btnChangeStart != null) {
                uiManager.btnChangeStart.setVisibility(View.GONE);
            }
            return;
        }

        if (uiManager.btnChangeStart != null) {
            uiManager.btnChangeStart.setVisibility(View.VISIBLE);
        }

        Point userLocation = callback.getUserLocation();
        if (userLocation != null && DistanceHelper.distanceInMeters(startPoint, userLocation) < 5.0) {
            uiManager.tvStartTitle.setText("Ваше местоположение");
            uiManager.tvStartSubtitle.setText("По данным геопозиции");
            uiManager.tvStartSubtitle.setAlpha(0.6f);
            return;
        }

        // This part depends on RouteController, so we'll leave it in MainPresenter for now
        // and call it from there. A future refactoring could improve this.
        uiManager.tvStartTitle.setText(String.format("%.5f, %.5f", startPoint.getLatitude(), startPoint.getLongitude()));
        uiManager.tvStartSubtitle.setText("Тапните по карте, чтобы изменить");
        uiManager.tvStartSubtitle.setAlpha(0.6f);
    }

    public void setStartPoint(Point point) {
        this.startPoint = point;
        mapPointHelper.showStartPoint(point);
        callback.onStartPointSelected(point);
    }

    public Point getStartPoint() {
        return startPoint;
    }

    public boolean isAwaitingAutoStartPoint() {
        return awaitingAutoStartPoint;
    }

    public void reset() {
        startPoint = null;
        manualStartPointMode = false;
        awaitingAutoStartPoint = false;
        mapPointHelper.showStartPoint(null);
    }
}
