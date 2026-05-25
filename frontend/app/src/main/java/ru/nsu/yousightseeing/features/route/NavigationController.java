package ru.nsu.yousightseeing.features.route;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.Priority;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.mapview.MapView;

import java.util.List;

import ru.nsu.yousightseeing.utils.DistanceHelper;

public class NavigationController {

    public interface NavigationListener {
        void onNavigationStarted();
        void onNavigationStopped();
        void onProgressChanged(double remainingMeters, int nearestIndex, int totalPoints);
        void onRouteFinished();
        void onUserOffRoute(double distanceFromRouteMeters);
        void onUserLocationChanged(Point userPoint);
    }

    private final Activity activity;
    private final MapView mapView;
    private final FusedLocationProviderClient fusedClient;
    private final NavigationListener listener;

    private List<Point> routePoints;
    private boolean isNavigating = false;
    private int lastNearestIndex = 0;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (result == null || result.getLastLocation() == null || routePoints == null) {
                return;
            }

            Point userPoint = new Point(
                    result.getLastLocation().getLatitude(),
                    result.getLastLocation().getLongitude()
            );

            handleUserLocation(userPoint);
        }
    };

    public NavigationController(
            Activity activity,
            MapView mapView,
            FusedLocationProviderClient fusedClient,
            NavigationListener listener
    ) {
        this.activity = activity;
        this.mapView = mapView;
        this.fusedClient = fusedClient;
        this.listener = listener;
    }

    public void startNavigation(List<Point> routePoints) {
        if (routePoints == null || routePoints.size() < 2) {
            Toast.makeText(activity, "Нет маршрута для навигации", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "Нет разрешения на геолокацию", Toast.LENGTH_SHORT).show();
            return;
        }

        this.routePoints = routePoints;
        this.isNavigating = true;
        this.lastNearestIndex = 0;

        LocationRequest request = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                3000
        )
                .setMinUpdateIntervalMillis(1500)
                .build();

        fusedClient.requestLocationUpdates(request, locationCallback, activity.getMainLooper());

        if (listener != null) {
            listener.onNavigationStarted();
        }
    }

    public void stopNavigation() {
        if (!isNavigating) return;

        isNavigating = false;
        fusedClient.removeLocationUpdates(locationCallback);

        if (listener != null) {
            listener.onNavigationStopped();
        }
    }

    public boolean isNavigating() {
        return isNavigating;
    }

    private void handleUserLocation(Point userPoint) {
        if (!isNavigating || routePoints == null || routePoints.isEmpty()) return;

        if (listener != null) {
            listener.onUserLocationChanged(userPoint);
        }

        int nearestIndex = findNearestRoutePointIndex(userPoint);
        lastNearestIndex = Math.max(lastNearestIndex, nearestIndex);

        double distanceFromRoute = DistanceHelper.distanceInMeters(
                userPoint,
                routePoints.get(nearestIndex)
        );

        if (distanceFromRoute > 80 && listener != null) {
            listener.onUserOffRoute(distanceFromRoute);
        }

        double remaining = calculateRemainingDistance(lastNearestIndex);
        moveCameraToUser(userPoint);

        if (listener != null) {
            listener.onProgressChanged(remaining, lastNearestIndex, routePoints.size());
        }

        if (remaining < 30) {
            stopNavigation();
            if (listener != null) {
                listener.onRouteFinished();
            }
        }
    }

    private int findNearestRoutePointIndex(Point userPoint) {
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;

        for (int i = lastNearestIndex; i < routePoints.size(); i++) {
            double distance = DistanceHelper.distanceInMeters(userPoint, routePoints.get(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }

        return bestIndex;
    }

    private double calculateRemainingDistance(int fromIndex) {
        double sum = 0.0;

        for (int i = fromIndex; i < routePoints.size() - 1; i++) {
            sum += DistanceHelper.distanceInMeters(routePoints.get(i), routePoints.get(i + 1));
        }

        return sum;
    }

    private void moveCameraToUser(Point userPoint) {
        if (mapView == null || mapView.getMapWindow() == null) return;

        mapView.getMapWindow().getMap().move(
                new CameraPosition(userPoint, 17f, 0f, 0f),
                new Animation(Animation.Type.SMOOTH, 0.5f),
                null
        );
    }
}