package ru.nsu.yousightseeing.features.route;

import android.content.SharedPreferences;

import com.yandex.mapkit.geometry.Point;

import java.util.HashSet;
import java.util.Set;

import ru.nsu.yousightseeing.api.RouteApi;
import ru.nsu.yousightseeing.features.MainActivity;
import ru.nsu.yousightseeing.utils.CategoryMapper;

import static android.content.Context.MODE_PRIVATE;

public class AutoRouteController {

    private final MainActivity mainActivity;
    private final AutoRouteCallback callback;
    private boolean isGenerating = false;

    public static class AutoRouteParameters {
        final int radius;
        final int maxPlaces;
        final int durationMinutes;
        final boolean includeFood;

        public AutoRouteParameters(int radius, int maxPlaces, int durationMinutes, boolean includeFood) {
            this.radius = radius;
            this.maxPlaces = maxPlaces;
            this.durationMinutes = durationMinutes;
            this.includeFood = includeFood;
        }
    }

    public interface AutoRouteCallback {
        void onRouteGenerationStart();
        void onRouteGenerated(RouteApi.GeneratedRouteResult result);
        void onRouteGenerationFailed(String message);
        Point getStartPoint();
        AutoRouteParameters getAutoRouteParameters();
    }

    public AutoRouteController(MainActivity mainActivity, AutoRouteCallback callback) {
        this.mainActivity = mainActivity;
        this.callback = callback;
    }

    public void startRouteGeneration() {
        if (isGenerating) {
            callback.onRouteGenerationFailed("Маршрут уже генерируется...");
            return;
        }

        Point startPoint = callback.getStartPoint();
        if (startPoint == null) {
            callback.onRouteGenerationFailed("Не выбрана стартовая точка");
            return;
        }

        SharedPreferences prefs = mainActivity.getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> userCategories = prefs.getStringSet("categories", new HashSet<>());
        if (userCategories.isEmpty()) {
            callback.onRouteGenerationFailed("Выберите категории в профиле");
            return;
        }

        Set<String> backendCategories = CategoryMapper.mapUserCategoriesToBackend(userCategories);
        AutoRouteParameters params = callback.getAutoRouteParameters();

        isGenerating = true;
        callback.onRouteGenerationStart();

        RouteApi.generateRoute(
                mainActivity,
                startPoint.getLatitude(),
                startPoint.getLongitude(),
                backendCategories,
                params.radius,
                params.maxPlaces,
                params.durationMinutes,
                params.includeFood,
                new RouteApi.GenerateRouteCallback() {
                    @Override
                    public void onSuccess(RouteApi.GeneratedRouteResult result) {
                        mainActivity.runOnUiThread(() -> {
                            isGenerating = false;
                            callback.onRouteGenerated(result);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        mainActivity.runOnUiThread(() -> {
                            isGenerating = false;
                            callback.onRouteGenerationFailed(message);
                        });
                    }
                }
        );
    }

    public boolean isGenerating() {
        return isGenerating;
    }

    public void cancel() {
        isGenerating = false;
    }
}
