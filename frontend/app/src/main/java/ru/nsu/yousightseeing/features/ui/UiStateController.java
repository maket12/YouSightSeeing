package ru.nsu.yousightseeing.features.ui;

import android.view.View;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import ru.nsu.yousightseeing.features.main.MainUIManager;
import ru.nsu.yousightseeing.features.route.RouteBuildMode;
import ru.nsu.yousightseeing.model.Route;

public class UiStateController {

    private final MainUIManager uiManager;
    private final UiStateCallback callback;

    public interface UiStateCallback {
        Route getCurrentRoute();
        boolean isRouteBuilt();
        RouteBuildMode getCurrentBuildMode();
        boolean isGeneratingAutoRoute();
        boolean isAwaitingAutoStartPoint();
        int getManualSelectedPlacesCount();
        boolean isRoutePointsEmpty();
    }

    public UiStateController(MainUIManager uiManager, UiStateCallback callback) {
        this.uiManager = uiManager;
        this.callback = callback;
    }

    public void applyBuildModeUI(RouteBuildMode mode) {
        if (uiManager.manualSection != null) {
            uiManager.manualSection.setVisibility(mode == RouteBuildMode.MANUAL ? View.VISIBLE : View.GONE);
        }
        if (uiManager.autoSection != null) {
            uiManager.autoSection.setVisibility(mode == RouteBuildMode.AUTO ? View.VISIBLE : View.GONE);
        }
        updateAll();
    }

    public void updateAll() {
        updateBuildRouteButton();
        updateEditCategoriesButton();
        updateBottomSheetState();
    }

    public void updateBottomSheetState() {
        if (uiManager.bottomSheetBehavior == null) return;
        if (callback.isRouteBuilt()) {
            uiManager.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    public void updateEditCategoriesButton() {
        if (uiManager.btnEditCategories != null) {
            uiManager.btnEditCategories.setEnabled(callback.isRoutePointsEmpty());
        }
    }

    public void updateBuildRouteButton() {
        if (uiManager.btnBuildRoute == null) return;

        if (callback.isRouteBuilt()) {
            uiManager.btnBuildRoute.setText("Сбросить маршрут");
            uiManager.btnBuildRoute.setEnabled(true);
            return;
        }

        if (callback.getCurrentBuildMode() == RouteBuildMode.AUTO) {
            if (callback.isGeneratingAutoRoute()) {
                uiManager.btnBuildRoute.setText("Генерируем...");
                uiManager.btnBuildRoute.setEnabled(false);
            } else if (callback.isAwaitingAutoStartPoint()) {
                uiManager.btnBuildRoute.setText("Выберите стартовую точку");
                uiManager.btnBuildRoute.setEnabled(true);
            } else {
                uiManager.btnBuildRoute.setText("Построить маршрут");
                uiManager.btnBuildRoute.setEnabled(true);
            }
            return;
        }

        if (callback.isAwaitingAutoStartPoint()) {
            uiManager.btnBuildRoute.setText("Выберите стартовую точку");
            uiManager.btnBuildRoute.setEnabled(true);
            return;
        }

        int count = callback.getManualSelectedPlacesCount();
        if (count < 2) {
            uiManager.btnBuildRoute.setText("Добавьте минимум 2 места");
            uiManager.btnBuildRoute.setEnabled(false);
        } else {
            uiManager.btnBuildRoute.setText("Построить маршрут (" + count + ")");
            uiManager.btnBuildRoute.setEnabled(true);
        }
    }

    public void collapseBottomSheet() {
        if (uiManager.bottomSheetBehavior != null) {
            uiManager.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    public void expandBottomSheet() {
        if (uiManager.bottomSheetBehavior != null) {
            uiManager.bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }
}
