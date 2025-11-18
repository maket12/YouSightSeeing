package com.example.yandexmapcitysearch;

import android.util.Log;

import com.yandex.mapkit.geometry.Point;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class OpenRouteServiceClient {

    private static final String BASE_URL = "https://api.openrouteservice.org/v2/directions/foot-walking/geojson";
    private static final String API_KEY = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6ImUwYThlMGNiZTU3NjQ0NmM4MDM2OTZkZDAyZjU4ODhkIiwiaCI6Im11cm11cjY0In0=";

    public interface ORSCallback {
        void onSuccess(List<Point> routePoints);
        void onError(String errorMessage);
    }

    /**
     * Получает маршрут между двумя точками (оригинальный метод)
     */
    public void getRoute(Point start, Point end, ORSCallback callback) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONArray coords = new JSONArray();
                coords.put(new JSONArray().put(start.getLongitude()).put(start.getLatitude()));
                coords.put(new JSONArray().put(end.getLongitude()).put(end.getLatitude()));
                body.put("coordinates", coords);

                URL url = new URL(BASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes());
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray coordinates = jsonResponse
                        .getJSONArray("features")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<Point> routePoints = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray coord = coordinates.getJSONArray(i);
                    routePoints.add(new Point(coord.getDouble(1), coord.getDouble(0)));
                }

                callback.onSuccess(routePoints);

            } catch (Exception e) {
                Log.e("ORS", "Ошибка при запросе: " + e.getMessage());
                callback.onError(e.getMessage());
            }
        }).start();
    }

    /**
     * Получает маршрут через несколько точек (новый метод)
     * OpenRouteService Directions API поддерживает до 50 waypoints для пешего маршрута
     */
    public void getMultiPointRoute(List<Point> points, ORSCallback callback) {
        if (points == null || points.size() < 2) {
            callback.onError("Необходимо минимум 2 точки для построения маршрута");
            return;
        }

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                JSONArray coords = new JSONArray();

                // Добавляем все точки в массив координат
                for (Point point : points) {
                    coords.put(new JSONArray().put(point.getLongitude()).put(point.getLatitude()));
                }

                body.put("coordinates", coords);

                Log.d("ORS", "Построение маршрута через " + points.size() + " точек");

                URL url = new URL(BASE_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", API_KEY);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000); // Увеличенный таймаут для множественных точек
                conn.setReadTimeout(15000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes());
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                    StringBuilder errorResponse = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResponse.append(errorLine);
                    }
                    Log.e("ORS", "HTTP Error " + responseCode + ": " + errorResponse.toString());
                    callback.onError("Ошибка сервера: " + responseCode);
                    return;
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONArray coordinates = jsonResponse
                        .getJSONArray("features")
                        .getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONArray("coordinates");

                List<Point> routePoints = new ArrayList<>();
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray coord = coordinates.getJSONArray(i);
                    routePoints.add(new Point(coord.getDouble(1), coord.getDouble(0)));
                }

                Log.d("ORS", "Маршрут успешно построен, точек в маршруте: " + routePoints.size());
                callback.onSuccess(routePoints);

            } catch (Exception e) {
                Log.e("ORS", "Ошибка при запросе многоточечного маршрута: " + e.getMessage());
                e.printStackTrace();
                callback.onError(e.getMessage());
            }
        }).start();
    }
}
