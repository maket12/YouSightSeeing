package com.example.yandexmapcitysearch;

import android.text.TextUtils;
import android.util.Log;
import com.yandex.mapkit.geometry.Point;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeoapifyClient {

    private static final String GEOAPIFY_API_KEY = BuildConfig.GEOAPIFY_API_KEY;

    public static class Place {
        public String name;
        public Point location;

        public Place(String name, Point location) {
            this.name = name;
            this.location = location;
        }
    }

    public interface GeoapifyCallback {
        void onSuccess(List<Place> places);
        void onError(String errorMessage);
    }

    // Маппинг пользовательских категорий на Geoapify ключи
    private static final Map<String, String> categoryMap = new HashMap<>();
    static {
        categoryMap.put("Парки", "tourism.park");
        categoryMap.put("Архитектура", "tourism.architecture");
        categoryMap.put("Музеи", "tourism.museum");
        categoryMap.put("Кафе", "catering.cafe");
        categoryMap.put("Рестораны", "catering.restaurant");
        categoryMap.put("Необычные и скрытые уголки города", "tourism.attraction");
        // Добавьте другие категории по вашему списку
    }

    public static Set<String> mapUserCategoriesToGeoapify(Set<String> userCategories) {
        Set<String> geoapifyCategories = new HashSet<>();
        for (String userCat : userCategories) {
            String geoCat = categoryMap.get(userCat);
            if (geoCat != null) {
                geoapifyCategories.add(geoCat);
            }
        }
        if (geoapifyCategories.isEmpty()) {
            geoapifyCategories.add("tourism.attraction"); // дефолтная
        }
        return geoapifyCategories;
    }

    // Метод с передачей пользовательских категорий
    public void getNearbyPlaces(double lat, double lon, Set<String> userCategories, GeoapifyCallback callback) {
        new Thread(() -> {
            try {
                // Преобразуем пользовательские категории в Geoapify
                Set<String> geoCategoriesSet = mapUserCategoriesToGeoapify(userCategories);
                String categoriesParam = TextUtils.join(",", geoCategoriesSet);

                String urlStr = String.format(
                        "https://api.geoapify.com/v2/places?categories=%s&filter=circle:%.6f,%.6f,1000&limit=5&apiKey=%s",
                        categoriesParam, lon, lat, GEOAPIFY_API_KEY
                );

                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray features = jsonResponse.getJSONArray("features");

                List<Place> places = new ArrayList<>();
                for (int i = 0; i < features.length(); i++) {
                    JSONObject feature = features.getJSONObject(i);
                    String name = feature.getJSONObject("properties").optString("name", "Без названия");
                    JSONArray coords = feature.getJSONObject("geometry").getJSONArray("coordinates");
                    double lon_ = coords.getDouble(0);
                    double lat_ = coords.getDouble(1);
                    places.add(new Place(name, new Point(lat_, lon_)));
                }

                callback.onSuccess(places);

            } catch (Exception e) {
                Log.e("Geoapify", "Ошибка: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
