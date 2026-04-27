package ru.nsu.yousightseeing.utils;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.layers.ObjectEvent;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.runtime.image.ImageProvider;

import ru.nsu.yousightseeing.R;

public class LocationHelper {
    public static final int REQ_LOCATION = 1001;

    private final Activity activity;
    private final MapView mapView;
    private final FusedLocationProviderClient fusedClient;
    private final LocationCallback callback;
    private final boolean allowGeo;
    private UserLocationLayer userLocationLayer;

    public interface LocationCallback {
        void onLocationFound(Point location);
        void onLocationFailed();
    }

    public LocationHelper(Activity activity, MapView mapView, boolean allowGeo, LocationCallback callback) {
        this.activity = activity;
        this.mapView = mapView;
        this.allowGeo = allowGeo;
        this.callback = callback;
        this.fusedClient = LocationServices.getFusedLocationProviderClient(activity);
    }

    public void checkAndRequestLocation() {
        if (!allowGeo) {
            Log.d("LocationHelper", "Геолокация отключена пользователем");
            return;
        }

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);

        } else {
            requestUserLocation();
            initUserLocationLayer();
        }
    }

    public void requestUserLocation() {
        try {
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Point userLocation = new Point(location.getLatitude(), location.getLongitude());
                            centerMapOnLocation(userLocation);
                            if (callback != null) {
                                callback.onLocationFound(userLocation);
                            }
                        } else {
                            fallbackToMoscow();
                        }
                    })
                    .addOnFailureListener(e -> fallbackToMoscow());
        } catch (SecurityException e) {
            fallbackToMoscow();
        }
    }

    public void initUserLocationLayer() {
        if (mapView == null) return;
        if (userLocationLayer != null) return;

        userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.getMapWindow());
        userLocationLayer.setVisible(true);
        userLocationLayer.setAutoZoomEnabled(false);

        mapView.post(() -> {
            userLocationLayer.setAnchor(
                    new PointF(mapView.getWidth() / 2f, mapView.getHeight() / 2f),
                    new PointF(mapView.getWidth() / 2f, mapView.getHeight() * 0.75f)
            );
        });

        userLocationLayer.setObjectListener(new UserLocationObjectListener() {
            @Override
            public void onObjectAdded(UserLocationView view) {
                try {
                    view.getPin().setIcon(
                            ImageProvider.fromResource(activity, R.drawable.pinm)
                    );
                } catch (Exception e) {
                    Log.w("LocationHelper", "Иконка пользователя не найдена");
                }
                view.getArrow().setVisible(true);
            }

            @Override
            public void onObjectRemoved(UserLocationView view) {}

            @Override
            public void onObjectUpdated(UserLocationView view, ObjectEvent event) {}
        });
    }

    public void fallbackToMoscow() {
        Point moscow = new Point(55.751225, 37.62954);
        centerMapOnLocation(moscow);

        if (callback != null) {
            callback.onLocationFailed();
        }

        Toast.makeText(activity, "GPS недоступен, центр карты → Москва", Toast.LENGTH_SHORT).show();
    }

    public void centerMapOnLocation(Point loc) {
        if (mapView != null && mapView.getMapWindow() != null) {
            mapView.getMapWindow().getMap().move(
                    new CameraPosition(loc, 10f, 0f, 0f),
                    new Animation(Animation.Type.SMOOTH, 1f), null
            );
        }
    }

    public void onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestUserLocation();
                initUserLocationLayer();
            } else {
                fallbackToMoscow();
                Toast.makeText(activity,
                        "Геолокация отключена, выберите точку на карте",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    public void setVisible(boolean visible) {
        if (userLocationLayer != null) {
            userLocationLayer.setVisible(visible);
        }
    }
}