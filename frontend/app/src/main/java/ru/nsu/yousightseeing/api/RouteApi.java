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
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class RouteApi {

    private static final String TAG = "RouteApi";
    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient client = new OkHttpClient();

    public interface RouteCallback {
        void onSuccess(List<Point> points, double distance, double duration);
        void onError(String message);
    }

    public interface GenerateRouteCallback {
        void onSuccess(GeneratedRouteResult result);
        void onError(String message);
    }

    public static class GeneratedRouteResult {
        public List<PlacesApi.Place> places = new ArrayList<>();
        public List<Point> routePoints = new ArrayList<>();
        public double distance;
        public double duration;
    }

    /**
     * POST /api/routes/calculate
     * body:
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

                if (response.code() == 401
                        && respBody.contains("token is expired")
                        && !alreadyRetried) {

                    Log.w(TAG, "Access token expired, trying to refresh");

                    AuthApi.refreshTokens(ctx, new AuthApi.RefreshCallback() {
                        @Override
                        public void onSuccess(String newAccess, String newRefresh) {
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

                    List<Point> routePoints = parseRoutePoints(pts);
                    cb.onSuccess(routePoints, distance, duration);

                } catch (JSONException e) {
                    Log.e(TAG, "parse RouteResponse error", e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    /**
     * POST /api/routes/generate
     * body:
     * {
     *   "start_lat": number,
     *   "start_lon": number,
     *   "categories": [...],
     *   "radius": number,
     *   "max_places": number,
     *   "include_food": true/false
     * }
     *
     * response:
     * {
     *   "places": [...],
     *   "route": {
     *     "points": [[lon,lat], ...],
     *     "distance": number,
     *     "duration": number
     *   }
     * }
     */
    public static void generateRoute(Context ctx,
                                     double startLat,
                                     double startLon,
                                     Set<String> categories,
                                     int radius,
                                     int maxPlaces,
                                     boolean includeFood,
                                     GenerateRouteCallback cb) {

        performGenerateRoute(
                ctx,
                startLat,
                startLon,
                categories,
                radius,
                maxPlaces,
                includeFood,
                cb,
                false
        );
    }

    private static void performGenerateRoute(Context ctx,
                                             double startLat,
                                             double startLon,
                                             Set<String> categories,
                                             int radius,
                                             int maxPlaces,
                                             boolean includeFood,
                                             GenerateRouteCallback cb,
                                             boolean alreadyRetried) {

        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        JSONObject bodyJson = new JSONObject();
        try {
            bodyJson.put("start_lat", startLat);
            bodyJson.put("start_lon", startLon);

            JSONArray cats = new JSONArray();
            if (categories != null) {
                for (String c : categories) {
                    cats.put(c);
                }
            }
            bodyJson.put("categories", cats);

            if (radius > 0) {
                bodyJson.put("radius", radius);
            }
            if (maxPlaces > 0) {
                bodyJson.put("max_places", maxPlaces);
            }

            bodyJson.put("include_food", includeFood);

        } catch (JSONException e) {
            cb.onError("Ошибка формирования запроса");
            return;
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);
        Request request = new Request.Builder()
                .url(ApiConfig.ROUTES_GENERATE)
                .post(body)
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "generateRoute failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";

                if (response.code() == 401
                        && respBody.contains("expired")
                        && !alreadyRetried) {

                    Log.w(TAG, "Generate route token expired, trying to refresh");

                    AuthApi.refreshTokens(ctx, new AuthApi.RefreshCallback() {
                        @Override
                        public void onSuccess(String newAccess, String newRefresh) {
                            performGenerateRoute(
                                    ctx,
                                    startLat,
                                    startLon,
                                    categories,
                                    radius,
                                    maxPlaces,
                                    includeFood,
                                    cb,
                                    true
                            );
                        }

                        @Override
                        public void onError(String message) {
                            cb.onError("Сессия истекла, войдите заново: " + message);
                        }
                    });
                    return;
                }

                if (!response.isSuccessful()) {
                    Log.e(TAG, "generateRoute error " + response.code() + " " + respBody);
                    cb.onError("Ошибка генерации маршрута: " + response.code() + "\n" + respBody);
                    return;
                }

                try {
                    JSONObject json = new JSONObject(respBody);

                    GeneratedRouteResult result = new GeneratedRouteResult();

                    JSONArray placesJson = json.optJSONArray("places");
                    if (placesJson != null) {
                        for (int i = 0; i < placesJson.length(); i++) {
                            JSONObject p = placesJson.getJSONObject(i);
                            result.places.add(parsePlace(p));
                        }
                    }

                    JSONObject routeJson = json.getJSONObject("route");
                    JSONArray pointsJson = routeJson.getJSONArray("points");

                    result.routePoints = parseRoutePoints(pointsJson);
                    result.distance = routeJson.optDouble("distance", 0.0);
                    result.duration = routeJson.optDouble("duration", 0.0);

                    cb.onSuccess(result);

                } catch (JSONException e) {
                    Log.e(TAG, "parse generateRoute response error", e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    private static List<Point> parseRoutePoints(JSONArray pts) throws JSONException {
        List<Point> routePoints = new ArrayList<>();
        for (int i = 0; i < pts.length(); i++) {
            JSONArray pair = pts.getJSONArray(i);
            double lon = pair.getDouble(0);
            double lat = pair.getDouble(1);
            routePoints.add(new Point(lat, lon));
        }
        return routePoints;
    }

    private static PlacesApi.Place parsePlace(JSONObject p) throws JSONException {
        PlacesApi.Place place = new PlacesApi.Place();

        place.name = p.optString("name", "");
        place.address = p.optString("address", "");
        place.placeId = p.optString("place_id", "");

        JSONArray coords = p.optJSONArray("coordinates");
        if (coords != null && coords.length() == 2) {
            place.lon = coords.getDouble(0);
            place.lat = coords.getDouble(1);
        } else {
            place.lat = p.optDouble("lat", 0.0);
            place.lon = p.optDouble("lon", 0.0);
        }

        JSONArray catsArr = p.optJSONArray("categories");
        if (catsArr != null) {
            place.categories.clear();
            for (int j = 0; j < catsArr.length(); j++) {
                place.categories.add(catsArr.optString(j));
            }
        }

        return place;
    }
}