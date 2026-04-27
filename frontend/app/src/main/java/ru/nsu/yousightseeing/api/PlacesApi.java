package ru.nsu.yousightseeing.api;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import ru.nsu.yousightseeing.features.AuthActivity;

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

/**
 * Клиент для /ru.nsu.yousightseeing.api/places/search (Geoapify через backend).
 * Отправляет SearchPoiRequest и парсит SearchPlacesResponse.
 */
public final class PlacesApi {

    private static final String TAG = "PlacesApi";
    private static final OkHttpClient client = new OkHttpClient();
    private static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");

    /**
     * Модель точки интереса на клиенте
     */
    public static class Place {
        public String name;
        public String address;
        public List<String> categories = new ArrayList<>();
        public double lat;
        public double lon;
        public String placeId;

        public Place() {}

        public Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    /**
     * Колбэк для результатов поиска
     */
    public interface PlacesCallback {
        void onSuccess(List<Place> places);
        void onError(String message);
    }

    /**
     * Публичный метод: поиск POI вокруг lat/lon.
     * Внутри может один раз сделать refresh токена и повторить запрос.
     */
    public static void searchAround(Context ctx, double lat, double lon, int radius,
                                    Set<String> categories, int limit, PlacesCallback cb) {
        performSearch(ctx, lat, lon, radius, categories, limit, cb, false);
    }

    /**
     * Внутренний метод с флагом alreadyRetried, чтобы refresh делался только один раз.
     */
    private static void performSearch(Context ctx, double lat, double lon, int radius,
                                      Set<String> categories, int limit, PlacesCallback cb,
                                      boolean alreadyRetried) {
        String access = AuthActivity.getAccessToken();
        Log.d("AUTH_TEST", "Access token = " + access);
        if (access == null) {
            cb.onError("Требуется авторизация");
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("lat", lat);
            body.put("lon", lon);
            body.put("radius", radius);
            JSONArray cats = new JSONArray();
            if (categories != null) for (String c : categories) cats.put(c);
            body.put("categories", cats);
            if (limit > 0) body.put("limit", limit);
        } catch (JSONException e) {
            cb.onError("Ошибка формирования запроса");
            return;
        }

        RequestBody reqBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(ApiConfig.PLACES_SEARCH)
                .post(reqBody)
                .addHeader("Authorization", "Bearer " + access)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network failure", e);
                cb.onError("Ошибка сети: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "POI Response: " + response.code() + " | " + respBody.substring(0, Math.min(200, respBody.length())));

                // АВТО-REFRESH ТОКЕНА (1 раз)
                if (response.code() == 401 && respBody.contains("expired") && !alreadyRetried) {
                    Log.w(TAG, "🔄 POI token expired → auto refresh");
                    AuthApi.refreshTokens(ctx, new AuthApi.RefreshCallback() {
                        @Override
                        public void onSuccess(String newAccess, String newRefresh) {
                            performSearch(ctx, lat, lon, radius, categories, limit, cb, true);
                        }

                        @Override
                        public void onError(String message) {
                            cb.onError("Сессия истекла, войдите заново: " + message);
                        }
                    });
                    return;
                }

                if (!response.isSuccessful()) {
                    Log.e(TAG, "POI search failed: " + response.code() + " | " + respBody);
                    cb.onError("Ошибка поиска: " + response.code());
                    return;
                }

                try {
                    JSONObject json = new JSONObject(respBody);
                    JSONArray placesJson = json.getJSONArray("places");

                    List<Place> result = new ArrayList<>();
                    Log.d(TAG, "📍 Backend вернул " + placesJson.length() + " POI");

                    // ПОЛНЫЙ ПАРСИНГ JSON!
                    for (int i = 0; i < placesJson.length(); i++) {
                        JSONObject p = placesJson.getJSONObject(i);

                        Place place = new Place();
                        place.name = p.optString("name", "");
                        place.address = p.optString("address", "");
                        place.placeId = p.optString("place_id", "");

                        // Координаты: backend [lon, lat] → Android [lat, lon]
                        JSONArray coords = p.optJSONArray("coordinates");
                        if (coords != null && coords.length() == 2) {
                            place.lon = coords.getDouble(0);  // lon
                            place.lat = coords.getDouble(1);  // lat
                        }

                        // Категории
                        JSONArray catsArr = p.optJSONArray("categories");
                        if (catsArr != null) {
                            place.categories.clear();
                            for (int j = 0; j < catsArr.length(); j++) {
                                place.categories.add(catsArr.optString(j));
                            }
                        }

                        // Только валидные POI с координатами
                        if (!place.name.isEmpty() && place.lat != 0 && place.lon != 0) {
                            result.add(place);
                            Log.d(TAG, "➕ POI: " + place.name + " (" + place.lat + "," + place.lon + ")");
                        }
                    }

                    Log.d(TAG, "Готово к отправке: " + result.size() + " POI");
                    cb.onSuccess(result);

                } catch (JSONException e) {
                    Log.e(TAG, "JSON parse error: " + e.getMessage(), e);
                    cb.onError("Некорректный ответ сервера");
                }
            }
        });
    }
}
