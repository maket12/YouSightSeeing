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
import java.util.concurrent.TimeUnit;

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
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    public interface RouteCallback {
        void onSuccess(List<Point> points, double distance, double duration);
        void onError(String message);
    }

    public interface GenerateRouteCallback {
        void onSuccess(GeneratedRouteResult result);
        void onError(String message);
    }

    public interface CreateRouteCallback {
        void onSuccess(String routeId);
        void onError(String message);
    }

    public interface RouteListCallback {
        void onSuccess(List<SavedRoute> routes);
        void onError(String message);
    }

    public interface GetSavedRouteCallback {
        void onSuccess(SavedRoute route);
        void onError(String message);
    }

    public static class SavedRoute {
        public String id;
        public String title;
        public double startLatitude;
        public double startLongitude;
        public long distance;
        public int duration;
        public List<String> categories = new ArrayList<>();
        public int maxPlaces;
        public boolean includeFood;
        public boolean isPublic;
        public String shareCode;
        public String createdAt;
        public List<SavedRoutePoint> points = new ArrayList<>();
    }

    public static class SavedRoutePoint {
        public int position;
        public String placeId;
        public String name;
        public String address;
        public List<String> categories = new ArrayList<>();
        public double latitude;
        public double longitude;
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

            Log.d("ROUTE_API_BODY", bodyJson.toString());
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
                if (!alreadyRetried) {
                    Log.w(TAG, "Request failed, retrying...", e);
                    performCalculateRoute(ctx, points, optimizeOrder, cb, true);
                } else {
                    Log.e(TAG, "FULL ERROR after retry", e);
                    cb.onError("Сервер оборвал соединение (backend упал)");
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";

                Log.d("ROUTE_API_RESPONSE", respBody);

                if (respBody == null || respBody.isEmpty()) {
                    cb.onError("Пустой ответ от сервера");
                    return;
                }

                if (response.code() == 401
                        && (respBody.contains("expired") || respBody.contains("token is expired"))
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
                                     int durationMinutes,
                                     boolean includeFood,
                                     GenerateRouteCallback cb) {

        performGenerateRoute(
                ctx,
                startLat,
                startLon,
                categories,
                radius,
                maxPlaces,
                durationMinutes,
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
                                             int durationMinutes,
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
            // if (durationMinutes > 0) {
            //     bodyJson.put("duration_minutes", durationMinutes);
            // }

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
                if (!alreadyRetried) {
                    Log.w(TAG, "Request failed, retrying...", e);
                    performGenerateRoute(
                            ctx,
                            startLat,
                            startLon,
                            categories,
                            radius,
                            maxPlaces,
                            durationMinutes,
                            includeFood,
                            cb,
                            true // This is the retry attempt
                    );
                } else {
                    Log.e(TAG, "FULL ERROR after retry", e);
                    cb.onError("Сервер оборвал соединение (backend упал)");
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                Log.d("ROUTE_DEBUG", "Response = " + respBody);
                if (respBody == null || respBody.isEmpty()) {
                    cb.onError("Пустой ответ от сервера");
                    return;
                }

                if (response.code() == 401
                        && (respBody.contains("expired") || respBody.contains("token is expired"))
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
                                    durationMinutes,
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

                    JSONObject routeJson = json.optJSONObject("route");
                    if (routeJson != null) {
                        JSONArray pointsJson = routeJson.optJSONArray("points");
                        if (pointsJson != null) {
                            result.routePoints = parseRoutePoints(pointsJson);
                        }
                        result.distance = routeJson.optDouble("distance", 0.0);
                        result.duration = routeJson.optDouble("duration", 0.0);
                    }

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

    public static void createRoute(Context ctx,
                                   String title,
                                   double startLat,
                                   double startLon,
                                   double distance,
                                   double duration,
                                   List<String> categories,
                                   int maxPlaces,
                                   boolean includeFood,
                                   boolean isPublic,
                                   List<PlacesApi.Place> places,
                                   CreateRouteCallback cb) {

        performCreateRoute(
                ctx,
                title,
                startLat,
                startLon,
                distance,
                duration,
                categories,
                maxPlaces,
                includeFood,
                isPublic,
                places,
                cb,
                false
        );
    }

    private static void performCreateRoute(Context ctx,
                                           String title,
                                           double startLat,
                                           double startLon,
                                           double distance,
                                           double duration,
                                           List<String> categories,
                                           int maxPlaces,
                                           boolean includeFood,
                                           boolean isPublic,
                                           List<PlacesApi.Place> places,
                                           CreateRouteCallback cb,
                                           boolean alreadyRetried) {

        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        JSONObject bodyJson = new JSONObject();

        try {
            bodyJson.put("title", title);
            bodyJson.put("start_latitude", startLat);
            bodyJson.put("start_longitude", startLon);
            bodyJson.put("distance", Math.round(distance));
            bodyJson.put("duration", (int) Math.round(duration));

            JSONArray categoriesJson = new JSONArray();
            if (categories != null) {
                for (String category : categories) {
                    categoriesJson.put(category);
                }
            }
            bodyJson.put("categories", categoriesJson);

            bodyJson.put("max_places", maxPlaces);
            bodyJson.put("include_food", includeFood);
            bodyJson.put("is_public", isPublic);
            bodyJson.put("share_code", JSONObject.NULL);

            JSONArray pointsJson = new JSONArray();

            if (places != null) {
                for (int i = 0; i < places.size(); i++) {
                    PlacesApi.Place place = places.get(i);

                    JSONObject pointJson = new JSONObject();
                    pointJson.put("position", i + 1);

                    if (place.placeId != null && !place.placeId.isEmpty()) {
                        pointJson.put("place_id", place.placeId);
                    } else {
                        pointJson.put("place_id", JSONObject.NULL);
                    }

                    pointJson.put("name",
                            place.name != null && !place.name.isEmpty()
                                    ? place.name
                                    : "Точка");

                    pointJson.put("address",
                            place.address != null
                                    ? place.address
                                    : "");

                    JSONArray placeCategoriesJson = new JSONArray();
                    if (place.categories != null) {
                        for (String category : place.categories) {
                            placeCategoriesJson.put(category);
                        }
                    }
                    pointJson.put("categories", placeCategoriesJson);

                    pointJson.put("latitude", place.lat);
                    pointJson.put("longitude", place.lon);

                    pointsJson.put(pointJson);
                }
            }

            bodyJson.put("points", pointsJson);

            Log.d("CREATE_ROUTE_BODY", bodyJson.toString());

        } catch (JSONException e) {
            cb.onError("Ошибка формирования запроса");
            return;
        }

        RequestBody body = RequestBody.create(bodyJson.toString(), JSON);

        Request request = new Request.Builder()
                .url(ApiConfig.ROUTES)
                .post(body)
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (!alreadyRetried) {
                    Log.w(TAG, "createRoute failed, retrying...", e);
                    performCreateRoute(
                            ctx,
                            title,
                            startLat,
                            startLon,
                            distance,
                            duration,
                            categories,
                            maxPlaces,
                            includeFood,
                            isPublic,
                            places,
                            cb,
                            true
                    );
                } else {
                    Log.e(TAG, "createRoute failure after retry", e);
                    cb.onError("Ошибка сети: " + e.getMessage());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";

                Log.d("CREATE_ROUTE_RESPONSE", respBody);

                if (response.code() == 401
                        && (respBody.contains("expired") || respBody.contains("token is expired"))
                        && !alreadyRetried) {

                    AuthApi.refreshTokens(ctx, new AuthApi.RefreshCallback() {
                        @Override
                        public void onSuccess(String newAccess, String newRefresh) {
                            performCreateRoute(
                                    ctx,
                                    title,
                                    startLat,
                                    startLon,
                                    distance,
                                    duration,
                                    categories,
                                    maxPlaces,
                                    includeFood,
                                    isPublic,
                                    places,
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
                    cb.onError("Ошибка сохранения маршрута: " + response.code() + "\n" + respBody);
                    return;
                }

                try {
                    JSONObject json = new JSONObject(respBody);
                    String routeId = json.optString("route_id", "");
                    cb.onSuccess(routeId);
                } catch (JSONException e) {
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    public static void getSavedRoutes(Context ctx, int limit, int offset, RouteListCallback cb) {
        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        String url = ApiConfig.ROUTES + "?limit=" + limit + "&offset=" + offset;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "getSavedRoutes failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    cb.onError("Ошибка загрузки маршрутов: " + response.code() + "\n" + body);
                    return;
                }

                try {
                    JSONObject root = new JSONObject(body);
                    JSONArray routesJson = root.optJSONArray("routes");

                    List<SavedRoute> routes = new ArrayList<>();

                    if (routesJson != null) {
                        for (int i = 0; i < routesJson.length(); i++) {
                            routes.add(parseSavedRoute(routesJson.getJSONObject(i)));
                        }
                    }

                    cb.onSuccess(routes);
                } catch (JSONException e) {
                    Log.e(TAG, "parse saved routes error", e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    public static void getSavedRouteById(Context ctx, String routeId, GetSavedRouteCallback cb) {
        String access = AuthActivity.getAccessToken();
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        Request request = new Request.Builder()
                .url(ApiConfig.routeById(routeId))
                .get()
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "getSavedRouteById failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    cb.onError("Ошибка загрузки маршрута: " + response.code() + "\n" + body);
                    return;
                }

                try {
                    JSONObject root = new JSONObject(body);
                    JSONObject routeJson = root.getJSONObject("route");

                    cb.onSuccess(parseSavedRoute(routeJson));
                } catch (JSONException e) {
                    Log.e(TAG, "parse saved route error", e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }

    private static SavedRoute parseSavedRoute(JSONObject json) throws JSONException {
        SavedRoute route = new SavedRoute();

        route.id = json.optString("id", "");
        route.title = json.optString("title", "Маршрут");
        route.startLatitude = json.optDouble("start_latitude", 0.0);
        route.startLongitude = json.optDouble("start_longitude", 0.0);
        route.distance = json.optLong("distance", 0);
        route.duration = json.optInt("duration", 0);
        route.maxPlaces = json.optInt("max_places", 0);
        route.includeFood = json.optBoolean("include_food", false);
        route.isPublic = json.optBoolean("is_public", false);
        route.shareCode = json.optString("share_code", null);
        route.createdAt = json.optString("created_at", "");

        JSONArray categoriesJson = json.optJSONArray("categories");
        if (categoriesJson != null) {
            for (int i = 0; i < categoriesJson.length(); i++) {
                route.categories.add(categoriesJson.optString(i));
            }
        }

        JSONArray pointsJson = json.optJSONArray("points");
        if (pointsJson != null) {
            for (int i = 0; i < pointsJson.length(); i++) {
                route.points.add(parseSavedRoutePoint(pointsJson.getJSONObject(i)));
            }
        }

        return route;
    }

    private static SavedRoutePoint parseSavedRoutePoint(JSONObject json) {
        SavedRoutePoint point = new SavedRoutePoint();

        point.position = json.optInt("position", 0);
        point.placeId = json.optString("place_id", null);
        point.name = json.optString("name", "Точка");
        point.address = json.optString("address", "");
        point.latitude = json.optDouble("latitude", 0.0);
        point.longitude = json.optDouble("longitude", 0.0);

        JSONArray categoriesJson = json.optJSONArray("categories");
        if (categoriesJson != null) {
            for (int i = 0; i < categoriesJson.length(); i++) {
                point.categories.add(categoriesJson.optString(i));
            }
        }

        return point;
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