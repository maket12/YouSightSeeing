package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.yandex.mapkit.geometry.Point;

import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.AuthApi;
import ru.nsu.yousightseeing.api.RouteApi;
import ru.nsu.yousightseeing.api.UserApi;
import ru.nsu.yousightseeing.features.route.RouteFinalActivity;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName;
    private TextView tvEmail;
    private ImageView ivAvatar;
    private LinearLayout routesContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName  = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        ivAvatar = findViewById(R.id.ivAvatar);
        routesContainer = findViewById(R.id.routesContainer);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        loadProfile();
        loadSavedRoutes();

        // Выйти
        btnLogout.setOnClickListener(v -> {
            AuthApi.logout(ProfileActivity.this, new AuthApi.LogoutCallback() {
                @Override
                public void onSuccess() {
                    Intent intent = new Intent(ProfileActivity.this, AuthActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }

                @Override
                public void onError(String message) {
                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });

        // Назад
        btnGoHome.setOnClickListener(v -> finish());
    }

    private void loadProfile() {
        UserApi.getMe(this, new UserApi.UserCallback() {
            @Override
            public void onSuccess(JSONObject user) {
                runOnUiThread(() -> {
                    Log.d("PROFILE", "userJson = " + user.toString());

                    String fullName = user.optString("full_name", "");
                    if (fullName == null || fullName.isEmpty() || "null".equalsIgnoreCase(fullName)) {
                        String first = user.optString("first_name", "");
                        String last = user.optString("last_name", "");
                        fullName = (first + " " + last).trim();
                    }

                    if (fullName.isEmpty()) {
                        fullName = AuthActivity.getUserName();
                    }

                    if (fullName == null || fullName.isEmpty()) {
                        fullName = "Имя не задано";
                    }

                    String email = user.optString("email", "");
                    if (email == null || email.isEmpty()) {
                        email = AuthActivity.getUserEmail();
                    }

                    if (email == null) {
                        email = "";
                    }

                    String photoUrl = user.optString("picture", "");
                    if (photoUrl == null || photoUrl.isEmpty()) {
                        photoUrl = AuthActivity.getUserPhoto();
                    }

                    Log.d("PROFILE", "photoUrl = " + photoUrl);

                    tvName.setText(fullName);
                    tvEmail.setText(email);

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(ProfileActivity.this)
                                .load(photoUrl)
                                .circleCrop()
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .into(ivAvatar);
                    } else {
                        ivAvatar.setImageResource(R.mipmap.ic_launcher_round);
                    }
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void loadSavedRoutes() {
        RouteApi.getSavedRoutes(this, 20, 0, new RouteApi.RouteListCallback() {
            @Override
            public void onSuccess(List<RouteApi.SavedRoute> routes) {
                runOnUiThread(() -> renderSavedRoutes(routes));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void renderSavedRoutes(List<RouteApi.SavedRoute> routes) {
        routesContainer.removeAllViews();

        if (routes == null || routes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("У вас пока нет сохранённых маршрутов.");
            empty.setTextColor(getResources().getColor(R.color.text_primary));
            empty.setTextSize(14f);
            routesContainer.addView(empty);
            return;
        }

        for (RouteApi.SavedRoute route : routes) {
            TextView item = new TextView(this);

            String distanceText = route.distance > 0
                    ? String.format("%.1f км", route.distance / 1000.0)
                    : "—";

            String durationText = route.duration > 0
                    ? String.format("%d мин", route.duration / 60)
                    : "—";

            item.setText(route.title + "\n" + distanceText + " • " + durationText + " • точек: " + route.points.size());
            item.setTextColor(getResources().getColor(R.color.text_primary));
            item.setTextSize(15f);
            item.setPadding(0, 12, 0, 12);

            item.setOnClickListener(v -> openSavedRoute(route.id));

            routesContainer.addView(item);
        }
    }

    private void openSavedRoute(String routeId) {
        RouteApi.getSavedRouteById(this, routeId, new RouteApi.GetSavedRouteCallback() {
            @Override
            public void onSuccess(RouteApi.SavedRoute route) {
                runOnUiThread(() -> rebuildAndOpenRoute(route));
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show()
                );
            }
        });
    }

    private void rebuildAndOpenRoute(RouteApi.SavedRoute route) {
        List<RouteApi.SavedRoutePoint> sortedPoints = new ArrayList<>(route.points);

        sortedPoints.sort((a, b) -> Integer.compare(a.position, b.position));

        List<Point> pointsForCalculate = new ArrayList<>();
        pointsForCalculate.add(new Point(route.startLatitude, route.startLongitude));

        for (RouteApi.SavedRoutePoint p : sortedPoints) {
            pointsForCalculate.add(new Point(p.latitude, p.longitude));
        }

        if (pointsForCalculate.size() < 2) {
            Toast.makeText(this, "Недостаточно точек для открытия маршрута", Toast.LENGTH_SHORT).show();
            return;
        }

        RouteApi.calculateRoute(
                this,
                pointsForCalculate,
                false,
                new RouteApi.RouteCallback() {
                    @Override
                    public void onSuccess(List<Point> routeGeometry, double distance, double duration) {
                        runOnUiThread(() -> openRouteFinalScreen(route, routeGeometry, distance, duration));
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() ->
                                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show()
                        );
                    }
                }
        );
    }

    private void openRouteFinalScreen(
            RouteApi.SavedRoute savedRoute,
            List<Point> routeGeometry,
            double distance,
            double duration
    ) {
        try {
            JSONArray routePointsJson = new JSONArray();

            for (Point p : routeGeometry) {
                JSONArray pair = new JSONArray();
                pair.put(p.getLongitude());
                pair.put(p.getLatitude());
                routePointsJson.put(pair);
            }

            JSONArray placesJson = new JSONArray();

            List<RouteApi.SavedRoutePoint> sortedPoints = new ArrayList<>(savedRoute.points);
            sortedPoints.sort((a, b) -> Integer.compare(a.position, b.position));

            for (RouteApi.SavedRoutePoint p : sortedPoints) {
                JSONObject obj = new JSONObject();
                obj.put("name", p.name);
                obj.put("lat", p.latitude);
                obj.put("lon", p.longitude);
                placesJson.put(obj);
            }

            Intent intent = new Intent(this, RouteFinalActivity.class);
            intent.putExtra(RouteFinalActivity.EXTRA_ROUTE_POINTS_JSON, routePointsJson.toString());
            intent.putExtra(RouteFinalActivity.EXTRA_PLACES_JSON, placesJson.toString());
            intent.putExtra(RouteFinalActivity.EXTRA_DISTANCE, distance);
            intent.putExtra(RouteFinalActivity.EXTRA_DURATION, duration);
            intent.putExtra("saved_route_id", savedRoute.id);

            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(this, "Ошибка открытия маршрута", Toast.LENGTH_LONG).show();
        }
    }
}