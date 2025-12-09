package com.example.yousightseeingdesign;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import com.yandex.mapkit.map.InputListener;
import com.yandex.mapkit.map.Map;
import com.yandex.mapkit.geometry.Point;

import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.yandex.mapkit.Animation;
import com.yandex.mapkit.MapKitFactory;
import com.yandex.mapkit.geometry.Geometry;
import com.yandex.mapkit.layers.ObjectEvent;
import com.yandex.mapkit.map.CameraPosition;
import com.yandex.mapkit.map.MapObject;
import com.yandex.mapkit.map.MapObjectTapListener;
import com.yandex.mapkit.map.MapObjectCollection;
import com.yandex.mapkit.map.MapWindow;
import com.yandex.mapkit.map.PlacemarkMapObject;
import com.yandex.mapkit.map.VisibleRegionUtils;
import com.yandex.mapkit.mapview.MapView;
import com.yandex.mapkit.search.Response;
import com.yandex.mapkit.search.SearchFactory;
import com.yandex.mapkit.search.SearchManager;
import com.yandex.mapkit.search.SearchManagerType;
import com.yandex.mapkit.search.SearchOptions;
import com.yandex.mapkit.search.Session;
import com.yandex.mapkit.user_location.UserLocationLayer;
import com.yandex.mapkit.user_location.UserLocationObjectListener;
import com.yandex.mapkit.user_location.UserLocationView;
import com.yandex.runtime.Error;
import com.yandex.runtime.image.ImageProvider;
import com.bumptech.glide.Glide;

public class MainActivity extends AppCompatActivity implements Session.SearchListener {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private MapView mapView;
    private EditText editCity;
    private ListView suggestListView;
    private ArrayAdapter<String> suggestAdapter;
    private SearchManager searchManager;
    private Session searchSession;
    private UserLocationLayer userLocationLayer;
    private MapObjectCollection mapObjects;
    private boolean allowGeo = false;
    private boolean isSuggesting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        editCity = findViewById(R.id.editCity);
        suggestListView = findViewById(R.id.suggestListView);

        searchManager = SearchFactory.getInstance().createSearchManager(SearchManagerType.ONLINE);

        allowGeo = getIntent().getBooleanExtra("ALLOW_GEO", false);
        Log.d(TAG, "allowGeo = " + allowGeo);

        setupAutocomplete();
        checkLocationPermissions();

        findViewById(R.id.btnSearch).setOnClickListener(v -> {
            String query = editCity.getText().toString().trim();
            if (!query.isEmpty()) {
                submitQuery(query);
                suggestListView.setVisibility(View.GONE);
            } else {
                Toast.makeText(this, "Введите запрос для поиска", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupAutocomplete() {
        suggestAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        suggestListView.setAdapter(suggestAdapter);
        suggestListView.setVisibility(View.GONE);

        editCity.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() > 2 && !isSuggesting) {
                    requestSuggestions(query);
                } else if (query.length() <= 2) {
                    suggestListView.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        suggestListView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            editCity.setText(selected);
            suggestListView.setVisibility(View.GONE);
            submitQuery(selected);
        });
    }

    private void requestSuggestions(String query) {
        isSuggesting = true;
        if (searchSession != null) {
            searchSession.cancel();
        }
        Geometry visibleRegion = VisibleRegionUtils.toPolygon(mapView.getMapWindow().getMap().getVisibleRegion());
        if (visibleRegion == null) return;

        SearchOptions options = new SearchOptions();
        searchSession = searchManager.submit(query, visibleRegion, options, new Session.SearchListener() {
            @Override
            public void onSearchResponse(Response response) {
                isSuggesting = false;
                suggestAdapter.clear();
                if (response.getCollection() != null && response.getCollection().getChildren() != null) {
                    for (int i = 0; i < Math.min(5, response.getCollection().getChildren().size()); i++) {
                        String name = response.getCollection().getChildren().get(i).getObj().getName();
                        if (name != null) {
                            suggestAdapter.add(name);
                        }
                    }
                }
                suggestAdapter.notifyDataSetChanged();
                if (!suggestAdapter.isEmpty()) {
                    suggestListView.setVisibility(View.VISIBLE);
                } else {
                    suggestListView.setVisibility(View.GONE);
                }
            }

            @Override
            public void onSearchError(Error error) {
                isSuggesting = false;
                suggestListView.setVisibility(View.GONE);
                Log.e(TAG, "Suggest error: " + error);
            }
        });
    }

    private void checkLocationPermissions() {
        if (!allowGeo) {
            Log.d(TAG, "Геолокация отключена");
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "Разрешения уже есть");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Разрешения получены");
                initUserLocationLayer();
            } else {
                Toast.makeText(this, "Нужны разрешения для геолокации", Toast.LENGTH_LONG).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void submitQuery(String query) {
        if (mapView == null || mapView.getMapWindow() == null || mapView.getMapWindow().getMap() == null) {
            Toast.makeText(this, "Ошибка карты", Toast.LENGTH_SHORT).show();
            return;
        }
        if (searchSession != null) {
            searchSession.cancel();
            searchSession = null;
        }
        Geometry visibleRegion = VisibleRegionUtils.toPolygon(mapView.getMapWindow().getMap().getVisibleRegion());
        if (visibleRegion == null) return;

        SearchOptions options = new SearchOptions();
        searchSession = searchManager.submit(query, visibleRegion, options, this);
    }

    @Override
    public void onSearchError(Error error) {
        Toast.makeText(this, "Ошибка поиска", Toast.LENGTH_SHORT).show();
        Log.e(TAG, "Search error: " + error.toString());
    }

    private final MapObjectTapListener mapObjectTapListener = new MapObjectTapListener() {
        @Override
        public boolean onMapObjectTap(@NonNull MapObject mapObject, @NonNull Point point) {
            Log.d(TAG, "✅ КЛИК ПО ТОЧКЕ! " + point.getLatitude() + "," + point.getLongitude());

            reverseGeocodePoint(point);
            return true;
        }
    };

    private final InputListener mapInputListener = new InputListener() {
        @Override
        public void onMapTap(Map map, Point point) {
            Log.d(TAG, "✅ КЛИК ПО КАРТЕ! " + point.getLatitude() + "," + point.getLongitude());
            showPointInfo(point);
        }

        @Override
        public void onMapLongTap(Map map, Point point) {
            PlacemarkMapObject mark = mapObjects.addPlacemark(point);
            mark.addTapListener(mapObjectTapListener);
            Toast.makeText(MainActivity.this, "📍 Метка добавлена!", Toast.LENGTH_SHORT).show();
        }
    };

    private void showPointInfo(Point location) {
        View popupView = getLayoutInflater().inflate(R.layout.popup_place_info, null);

        TextView tvName = popupView.findViewById(R.id.tvPlaceName);
        TextView tvAddress = popupView.findViewById(R.id.tvPlaceAddress);
        TextView tvDescription = popupView.findViewById(R.id.tvPlaceDescription);
        ImageView ivPhoto = popupView.findViewById(R.id.ivPlacePhoto);

        tvName.setText("📍 3D вид места");
        tvAddress.setText(String.format("lat: %.6f\nlon: %.6f",
                location.getLatitude(), location.getLongitude()));
        tvDescription.setText("3D карта + панорама");

        String yandex3D = String.format(
                "https://static-maps.yandex.ru/1.x/?ll=%f,%f&z=18&l=map&size=400,250&l=map3d&pt=%f,%f,pm2rdm1",
                location.getLongitude(), location.getLatitude(),
                location.getLongitude(), location.getLatitude()
        );

        String panoramaUrl = String.format(
                "https://static-maps.yandex.ru/1.x/?ll=%f,%f&z=18&l=panorama&size=400,250",
                location.getLongitude(), location.getLatitude()
        );

        Glide.with(this)
                .load(yandex3D)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(panoramaUrl)
                .into(ivPhoto);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("3D + Панорама")
                .setView(popupView)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void updateDialogText(String placeName) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            TextView tvName = loadingDialog.findViewById(R.id.tvPlaceName);
            if (tvName != null) {
                tvName.setText("📍 " + placeName);
            }
        }
    }

    private void reverseGeocodePoint(Point location) {
        showLoadingDialog(location);

        new Thread(() -> {
            try {
                okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();

                String nominatimUrl = String.format(
                        "https://nominatim.openstreetmap.org/reverse?format=json&lat=%f&lon=%f&zoom=18",
                        location.getLatitude(), location.getLongitude()
                );

                okhttp3.Request nominatimRequest = new okhttp3.Request.Builder()
                        .url(nominatimUrl)
                        .header("User-Agent", "YouSightseeing/1.0")
                        .build();

                okhttp3.Response nominatimResponse = client.newCall(nominatimRequest).execute();
                String nominatimJson = nominatimResponse.body().string();
                nominatimResponse.close();

                org.json.JSONObject nominatimObj = new org.json.JSONObject(nominatimJson);
                String displayName = nominatimObj.optString("display_name", "Место");

                runOnUiThread(() -> updateDialogText(displayName));

                String yandexPhoto = String.format(
                        "https://static-maps.yandex.ru/1.x/?ll=%f,%f&z=18&l=map&size=400,250&pt=%f,%f,pm2rdm1",
                        location.getLongitude(), location.getLatitude(),
                        location.getLongitude(), location.getLatitude()
                );
                runOnUiThread(() -> updateDialogPhoto(yandexPhoto));

            } catch (Exception e) {
                Log.e(TAG, "Geocode error", e);
            }
        }).start();
    }

    private AlertDialog loadingDialog;
    private void showLoadingDialog(Point location) {
        View popupView = getLayoutInflater().inflate(R.layout.popup_place_info, null);

        TextView tvName = popupView.findViewById(R.id.tvPlaceName);
        TextView tvAddress = popupView.findViewById(R.id.tvPlaceAddress);
        TextView tvDescription = popupView.findViewById(R.id.tvPlaceDescription);
        ImageView ivPhoto = popupView.findViewById(R.id.ivPlacePhoto);

        tvName.setText("📍 Получаем информацию...");
        tvAddress.setText(String.format("lat: %.6f\nlon: %.6f",
                location.getLatitude(), location.getLongitude()));
        tvDescription.setText("Загружаем данные из OpenStreetMap API...");

        Glide.with(this)
                .load("https://static-maps.yandex.ru/1.x/?z=15&l=map&size=400,250")
                .placeholder(android.R.drawable.ic_menu_gallery)
                .into(ivPhoto);

        loadingDialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("📍 Место")
                .setView(popupView)
                .setPositiveButton("Закрыть", null)
                .create();
        loadingDialog.show();
    }

    private void updateDialogPhoto(String photoUrl) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            ImageView ivPhoto = loadingDialog.findViewById(R.id.ivPlacePhoto);
            if (ivPhoto != null) {
                Glide.with(this)
                        .load(photoUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(ivPhoto);
            }
        }
    }

    @Override
    public void onSearchResponse(Response response) {
        Log.d(TAG, "✅ Поиск завершен");
        MapWindow mapWindow = mapView.getMapWindow();
        mapObjects = mapWindow.getMap().getMapObjects();
        mapObjects.clear();

        if (response.getCollection() == null || response.getCollection().getChildren() == null
                || response.getCollection().getChildren().isEmpty()) {
            Toast.makeText(this, "Ничего не найдено", Toast.LENGTH_SHORT).show();
            return;
        }

        for (int i = 0; i < Math.min(20, response.getCollection().getChildren().size()); i++) {
            try {
                Point loc = response.getCollection().getChildren().get(i).getObj().getGeometry().get(0).getPoint();
                PlacemarkMapObject placemark = mapObjects.addPlacemark(loc);
                placemark.setText(response.getCollection().getChildren().get(i).getObj().getName());
                placemark.addTapListener(mapObjectTapListener);
            } catch (Exception e) {
                Log.e(TAG, "Ошибка метки " + i, e);
            }
        }

        Point firstLoc = response.getCollection().getChildren().get(0).getObj().getGeometry().get(0).getPoint();
        mapWindow.getMap().move(
                new CameraPosition(firstLoc, 16.0f, 0.0f, 0.0f),
                new Animation(Animation.Type.SMOOTH, 1.0f), null
        );

        Toast.makeText(this, "📍 " + Math.min(20, response.getCollection().getChildren().size()) + " мест! 👆 Кликните по метке", Toast.LENGTH_LONG).show();
    }

    private void initUserLocationLayer() {
        userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.getMapWindow());
        userLocationLayer.setVisible(true);
        userLocationLayer.setAutoZoomEnabled(true);

        mapView.post(() -> {
            if (userLocationLayer != null) {
                userLocationLayer.setAnchor(
                        new PointF(mapView.getWidth() / 2f, mapView.getHeight() / 2f),
                        new PointF(mapView.getWidth() / 2f, mapView.getHeight() * 0.75f)
                );
            }
        });

        userLocationLayer.setObjectListener(new UserLocationObjectListener() {
            @Override
            public void onObjectAdded(UserLocationView view) {
                Log.d(TAG, "✅ UserLocation добавлен!");
                try {
                    view.getPin().setIcon(ImageProvider.fromResource(MainActivity.this, R.drawable.pinm));
                } catch (Exception e) {
                    Log.w(TAG, "Иконка не найдена");
                }
                view.getArrow().setVisible(true);
            }

            @Override
            public void onObjectRemoved(UserLocationView view) {}

            @Override
            public void onObjectUpdated(UserLocationView view, ObjectEvent event) {}
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        MapKitFactory.getInstance().onStart();
        if (mapView != null) {
            mapView.onStart();
            mapView.getMapWindow().getMap().addInputListener(mapInputListener);
            mapView.getMapWindow().getMap().move(
                    new CameraPosition(new Point(55.751225, 37.62954), 10.0f, 0.0f, 0.0f),
                    new Animation(Animation.Type.SMOOTH, 1.0f),
                    null
            );

            if (allowGeo && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                initUserLocationLayer();
            }
        }
    }

    @Override
    protected void onStop() {
        if (mapView != null) mapView.onStop();
        MapKitFactory.getInstance().onStop();
        if (searchSession != null) {
            searchSession.cancel();
        }
        super.onStop();
    }
}
