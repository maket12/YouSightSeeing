package ru.nsu.yousightseeing.features.route;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.geometry.Polyline;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.PolylineMapObject;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.runtime.image.ImageProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.text.DateFormat;
import java.util.Date;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.PlacesApi;
import ru.nsu.yousightseeing.api.RouteApi;

public class RouteConfirmationActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_POINTS_JSON = "route_points_json";
    public static final String EXTRA_PLACES_JSON = "places_json";
    public static final String EXTRA_DISTANCE = "distance";
    public static final String EXTRA_DURATION = "duration";

    private MapView mapView;
    private TextView tvDistance;
    private TextView tvDuration;
    private TextView tvPlacesCount;
    private LinearLayout placesContainer;
    private Button btnEditRoute;
    private Button btnConfirmRoute;
    private Button btnZoomInConfirm;
    private Button btnZoomOutConfirm;

    private PolylineMapObject routeLine;
    private List<Point> routePoints = new ArrayList<>();
    private List<RoutePlaceItem> places = new ArrayList<>();
    private double distance;
    private double duration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_confirmation);

        mapView = findViewById(R.id.mapViewConfirm);
        tvDistance = findViewById(R.id.tvDistance);
        tvDuration = findViewById(R.id.tvDuration);
        tvPlacesCount = findViewById(R.id.tvPlacesCount);
        placesContainer = findViewById(R.id.placesContainerConfirm);
        btnEditRoute = findViewById(R.id.btnEditRoute);
        btnConfirmRoute = findViewById(R.id.btnConfirmRoute);
        btnZoomInConfirm = findViewById(R.id.btnZoomInConfirm);
        btnZoomOutConfirm = findViewById(R.id.btnZoomOutConfirm);

        routePoints = parseRoutePoints(
                getIntent().getStringExtra(EXTRA_ROUTE_POINTS_JSON)
        );
        places = parsePlaces(
                getIntent().getStringExtra(EXTRA_PLACES_JSON)
        );

        distance = getIntent().getDoubleExtra(EXTRA_DISTANCE, 0.0);
        duration = getIntent().getDoubleExtra(EXTRA_DURATION, 0.0);

        renderSummary(distance, duration, places.size());
        renderPlaces(places);
        renderRoute(routePoints, places);

        btnEditRoute.setOnClickListener(v -> finish());

        btnConfirmRoute.setOnClickListener(v -> saveRouteAndOpenFinal());

        btnZoomInConfirm.setOnClickListener(v -> zoomIn());
        btnZoomOutConfirm.setOnClickListener(v -> zoomOut());
    }

    private void saveRouteAndOpenFinal() {
        if (routePoints == null || routePoints.isEmpty()) {
            Toast.makeText(this, "Нет маршрута для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        btnConfirmRoute.setEnabled(false);
        btnConfirmRoute.setText("Сохраняем...");

        Point start = routePoints.get(0);

        List<PlacesApi.Place> placesForRequest = new ArrayList<>();

        for (RoutePlaceItem item : places) {
            PlacesApi.Place place = new PlacesApi.Place();
            place.name = item.name;
            place.address = "";
            place.placeId = null;
            place.lat = item.lat;
            place.lon = item.lon;

            placesForRequest.add(place);
        }

        String title = "Маршрут " + DateFormat
                .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date());

        RouteApi.createRoute(
                this,
                title,
                start.getLatitude(),
                start.getLongitude(),
                distance,
                duration,
                new ArrayList<>(),
                places.size(),
                false,
                false,
                placesForRequest,
                new RouteApi.CreateRouteCallback() {
                    @Override
                    public void onSuccess(String routeId) {
                        runOnUiThread(() -> {
                            Toast.makeText(
                                    RouteConfirmationActivity.this,
                                    "Маршрут сохранён",
                                    Toast.LENGTH_SHORT
                            ).show();

                            openFinalRouteScreen(routeId);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            btnConfirmRoute.setEnabled(true);
                            btnConfirmRoute.setText("Сохранить");

                            Toast.makeText(
                                    RouteConfirmationActivity.this,
                                    message,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void openFinalRouteScreen(String routeId) {
        Intent intent = new Intent(this, RouteFinalActivity.class);

        intent.putExtra(EXTRA_ROUTE_POINTS_JSON,
                getIntent().getStringExtra(EXTRA_ROUTE_POINTS_JSON));
        intent.putExtra(EXTRA_PLACES_JSON,
                getIntent().getStringExtra(EXTRA_PLACES_JSON));
        intent.putExtra(EXTRA_DISTANCE,
                getIntent().getDoubleExtra(EXTRA_DISTANCE, 0.0));
        intent.putExtra(EXTRA_DURATION,
                getIntent().getDoubleExtra(EXTRA_DURATION, 0.0));
        intent.putExtra("saved_route_id", routeId);

        startActivity(intent);
    }
    private void zoomIn() {
        if (mapView == null || mapView.getMapWindow() == null) return;

        com.yandex.mapkit.map.Map map = mapView.getMapWindow().getMap();
        CameraPosition currentPosition = map.getCameraPosition();
        float newZoom = currentPosition.getZoom() + 1.0f;

        map.move(
                new CameraPosition(
                        currentPosition.getTarget(),
                        newZoom,
                        currentPosition.getAzimuth(),
                        currentPosition.getTilt()
                ),
                new Animation(Animation.Type.SMOOTH, 0.5f),
                null
        );
    }

    private void zoomOut() {
        if (mapView == null || mapView.getMapWindow() == null) return;

        com.yandex.mapkit.map.Map map = mapView.getMapWindow().getMap();
        CameraPosition currentPosition = map.getCameraPosition();
        float newZoom = Math.max(currentPosition.getZoom() - 1.0f, 0.0f);

        map.move(
                new CameraPosition(
                        currentPosition.getTarget(),
                        newZoom,
                        currentPosition.getAzimuth(),
                        currentPosition.getTilt()
                ),
                new Animation(Animation.Type.SMOOTH, 0.5f),
                null
        );
    }

    private void renderSummary(double distance, double duration, int placesCount) {
        if (distance > 0) {
            tvDistance.setText(String.format("%.1f км", distance / 1000.0));
        } else {
            tvDistance.setText("—");
        }

        if (duration > 0) {
            tvDuration.setText(String.format("%.0f мин", duration / 60.0));
        } else {
            tvDuration.setText("—");
        }

        tvPlacesCount.setText(String.valueOf(placesCount));
    }

    private void renderPlaces(List<RoutePlaceItem> places) {
        placesContainer.removeAllViews();

        if (places.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Места маршрута не найдены");
            empty.setTextSize(14f);
            placesContainer.addView(empty);
            return;
        }

        for (int i = 0; i < places.size(); i++) {
            RoutePlaceItem place = places.get(i);

            TextView item = new TextView(this);
            item.setText((i + 1) + ". " + place.name);
            item.setTextSize(15f);
            item.setPadding(0, 12, 0, 12);

            placesContainer.addView(item);
        }
    }

    private void renderRoute(List<Point> routePoints, List<RoutePlaceItem> places) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        MapObjectCollection mapObjects = mapView.getMapWindow().getMap().getMapObjects();

        for (RoutePlaceItem place : places) {
            Point point = new Point(place.lat, place.lon);
            PlacemarkMapObject marker = mapObjects.addPlacemark(point);
            marker.setIcon(ImageProvider.fromResource(this, R.drawable.pinm));
        }

        if (!routePoints.isEmpty()) {
            Polyline polyline = new Polyline(routePoints);
            routeLine = mapObjects.addPolyline(polyline);
            adjustCameraToRoute(routePoints);
        } else if (!places.isEmpty()) {
            Point first = new Point(places.get(0).lat, places.get(0).lon);
            mapView.getMapWindow().getMap().move(
                    new CameraPosition(first, 14f, 0f, 0f),
                    new Animation(Animation.Type.SMOOTH, 1f),
                    null
            );
        }
    }

    private void adjustCameraToRoute(List<Point> routeCoordinates) {
        if (routeCoordinates.isEmpty() || mapView == null) return;

        double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;

        for (Point p : routeCoordinates) {
            if (p.getLatitude() < minLat) minLat = p.getLatitude();
            if (p.getLatitude() > maxLat) maxLat = p.getLatitude();
            if (p.getLongitude() < minLon) minLon = p.getLongitude();
            if (p.getLongitude() > maxLon) maxLon = p.getLongitude();
        }

        double centerLat = (minLat + maxLat) / 2;
        double centerLon = (minLon + maxLon) / 2;

        float zoom;
        double latDiff = maxLat - minLat;
        double lonDiff = maxLon - minLon;
        double maxDiff = Math.max(latDiff, lonDiff);

        if (maxDiff < 0.005) zoom = 17f;
        else if (maxDiff < 0.02) zoom = 15f;
        else if (maxDiff < 0.05) zoom = 14f;
        else if (maxDiff < 0.1) zoom = 13f;
        else zoom = 12f;

        mapView.getMapWindow().getMap().move(
                new CameraPosition(new Point(centerLat, centerLon), zoom, 0f, 0f),
                new Animation(Animation.Type.SMOOTH, 1f),
                null
        );
    }

    private List<Point> parseRoutePoints(String json) {
        List<Point> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                double lon = pair.getDouble(0);
                double lat = pair.getDouble(1);
                result.add(new Point(lat, lon));
            }
        } catch (Exception e) {
            Log.e("RouteConfirmActivity", "parseRoutePoints error", e);
        }

        return result;
    }

    private List<RoutePlaceItem> parsePlaces(String json) {
        List<RoutePlaceItem> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return result;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);

                RoutePlaceItem item = new RoutePlaceItem();
                item.name = obj.optString("name", "Точка");
                item.lat = obj.optDouble("lat", 0.0);
                item.lon = obj.optDouble("lon", 0.0);

                result.add(item);
            }
        } catch (Exception e) {
            Log.e("RouteConfirmActivity", "parsePlaces error", e);
        }

        return result;
    }

    @Override
    protected void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        if (mapView != null) mapView.onStart();
    }

    @Override
    protected void onStop() {
        if (mapView != null) mapView.onStop();
        MapKitFactory.getInstance().onStop();
        super.onStop();
    }

    private static class RoutePlaceItem {
        String name;
        double lat;
        double lon;
    }
}
