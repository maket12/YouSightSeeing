package ru.nsu.yousightseeing.api;

import android.content.Context;

import com.yandex.mapkit.geometry.Point;

import java.util.List;

public class OpenRouteServiceClient {
    public void getMultiPointRoute(Context mainActivity, List<Point> optimizedPoints, ORSCallback orsCallback) {
    }

    public interface ORSCallback {
        void onSuccess(List<Point> routeCoordinates, double distance, double duration);
        void onError(String errorMessage);
    }
}
