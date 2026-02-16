package ru.nsu.yousightseeing.api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import ru.nsu.yousightseeing.features.AuthActivity;
import com.yandex.mapkit.geometry.Point;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class RouteApi {

    private static final String TAG = "RouteApi";
    private static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    public interface RouteCallback {
        void onSuccess(List<Point> points, double distance, double duration);
        void onError(String message);
    }

    /**
     * POST /ru.nsu.yousightseeing.api/routes/calculate
     * body: RouteRequest
     * {
     *   "coordinates": [[lon,lat], ...],
     *   "profile": "foot-walking",
     *   "preference": "fastest",
     *   "optimize_order": true/false
     * }
     */
    public static void calculateRoute(Context ctx,
                                      List<Point> points,
                                      boolean optimizeOrder,
                                      RouteCallback cb) {

        if (points == null || points.size() < 2) {
            cb.onError("Нужно минимум 2 точки для маршрута");
            return;
        }

        // оборачиваем логику в вспомогательный метод, чтобы можно было повторить запрос
        performCalculateRoute(ctx, points, optimizeOrder, cb, false);
    }

    private static void performCalculateRoute(Context ctx,
                                              List<Point> points,
                                              boolean optimizeOrder,
                                              RouteCallback cb,
                                              boolean alreadyRetried) {

        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        JSONObject bodyJson = new JSONObject();
        JSONArray coords = new JSONArray();
        try {
            for (Point p : points) {
                JSONArray pair = new JSONArray()
                        .put(p.getLongitude())
                        .put(p.getLatitude());
                coords.put(pair);
            }
            bodyJson.put("coordinates", coords);
            bodyJson.put("profile", "foot-walking");
            bodyJson.put("preference", "fastest");
            bodyJson.put("optimize_order", optimizeOrder);
        } catch (JSONException e) {
            cb.onError("Ошибка формирования запроса");
            return;
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(ApiConfig.ROUTES_CALCULATE)
                .post(body)
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "calculateRoute failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";

                // если токен просрочен и ещё не пытались рефрешить
                if (response.code() == 401
                        && respBody.contains("token is expired")
                        && !alreadyRetried) {

                    Log.w(TAG, "Access token expired, trying to refresh");

                    AuthApi.refreshTokens(ctx, new AuthApi.RefreshCallback() {
                        @Override
                        public void onSuccess(String newAccess, String newRefresh) {
                            // после успешного refresh повторяем запрос маршрута
                            performCalculateRoute(ctx, points, optimizeOrder, cb, true);
                        }

                        @Override
                        public void onError(String message) {
                            cb.onError("Сессия истекла, войдите заново: " + message);
                        }
                    });
                    return;
                }

                if (!response.isSuccessful()) {
                    Log.e(TAG, "calculateRoute error " + response.code() + " " + respBody);
                    cb.onError("Ошибка маршрута: " + response.code() + "\n" + respBody);
                    return;
                }

                try {
                    JSONObject json = new JSONObject(respBody);
                    JSONArray pts = json.getJSONArray("points");
                    double distance = json.getDouble("distance");
                    double duration = json.getDouble("duration");

                    List<Point> routePoints = new ArrayList<>();
                    for (int i = 0; i < pts.length(); i++) {
                        JSONArray pair = pts.getJSONArray(i);
                        double lon = pair.getDouble(0);
                        double lat = pair.getDouble(1);
                        routePoints.add(new Point(lat, lon));
                    }

                    cb.onSuccess(routePoints, distance, duration);
                } catch (JSONException e) {
                    Log.e(TAG, "parse RouteResponse error", e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }
}
