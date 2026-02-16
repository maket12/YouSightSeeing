package ru.nsu.yousightseeing.features;

import android.content.Context;

import com.yandex.mapkit.geometry.Point;

import java.util.List;

import ru.nsu.yousightseeing.api.RouteApi;

public class OpenRouteServiceClient {

    public interface ORSCallback {
        void onSuccess(List<Point> routePoints);
        void onError(String errorMessage);
    }

    /**
     * Многоточечный маршрут через backend /ru.nsu.yousightseeing.api/routes/calculate
     */
    public void getMultiPointRoute(Context ctx, List<Point> points, ORSCallback callback) {
        if (points == null || points.size() < 2) {
            callback.onError("Необходимо минимум 2 точки для построения маршрута");
            return;
        }

        RouteApi.calculateRoute(ctx, points, false, new RouteApi.RouteCallback() {
            @Override
            public void onSuccess(List<Point> routePoints, double distance, double duration) {
                // distance/duration можно потом использовать при сохранении Route
                callback.onSuccess(routePoints);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }
}
