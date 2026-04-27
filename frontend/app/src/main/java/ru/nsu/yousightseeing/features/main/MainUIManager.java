package ru.nsu.yousightseeing.features.main;

import android.app.Activity;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.yandex.mapkit.mapview.MapView;

import ru.nsu.yousightseeing.R;

public class MainUIManager {

    // UI компоненты
    public final MapView mapView;
    public final EditText editCity;
    public final ImageButton btnSearch;
    public final Button btnBuildRoute;
    public final Button btnZoomIn;
    public final Button btnZoomOut;

    public final Button btnProfile;
    public final Button btnAddPlace;
    public final Button btnOpenProfile;

    public final LinearLayout bottomSheet;
    public final BottomSheetBehavior<LinearLayout> bottomSheetBehavior;
    public final LinearLayout placesContainer;
    public final Slider sliderDurationHours;
    public final SwitchMaterial switchSnack;
    public final TextView tvStartTitle;
    public final TextView tvStartSubtitle;
    public final TextView tvDurationValue;
    public final Button btnChangeStart;

    public final MaterialButtonToggleGroup toggleRouteMode;
    public final MaterialButton btnModeManual;
    public final MaterialButton btnModeAuto;

    public final LinearLayout manualSection;
    public final LinearLayout autoSection;

    public final EditText etAutoRadius;
    public final Slider sliderMaxPlaces;
    public final TextView tvMaxPlacesValue;

    public final Button btnEditCategories;

    public MainUIManager(Activity activity) {
        mapView = activity.findViewById(R.id.mapView);
        if (mapView == null) {
            Log.e("MainUIManager", "MapView is null! Check activity_main.xml for R.id.mapView");
        }
        editCity = activity.findViewById(R.id.editCity);
        btnSearch = activity.findViewById(R.id.btnSearch);
        btnZoomIn = activity.findViewById(R.id.btnZoomIn);
        btnZoomOut = activity.findViewById(R.id.btnZoomOut);
        btnProfile = activity.findViewById(R.id.btnProfile);
        btnBuildRoute = activity.findViewById(R.id.btnBuildRoute);
        btnEditCategories = activity.findViewById(R.id.btnEditCategories);
        btnAddPlace = activity.findViewById(R.id.btnAddPlace);
        btnOpenProfile = activity.findViewById(R.id.btnOpenProfile);
        btnChangeStart = activity.findViewById(R.id.btnChangeStart);
        toggleRouteMode = activity.findViewById(R.id.toggleRouteMode);
        btnModeManual = activity.findViewById(R.id.btnModeManual);
        btnModeAuto = activity.findViewById(R.id.btnModeAuto);
        manualSection = activity.findViewById(R.id.manualSection);
        autoSection = activity.findViewById(R.id.autoSection);
        bottomSheet = activity.findViewById(R.id.bottomSheet);
        placesContainer = activity.findViewById(R.id.placesContainer);
        tvStartTitle = activity.findViewById(R.id.tvStartTitle);
        tvStartSubtitle = activity.findViewById(R.id.tvStartSubtitle);
        tvDurationValue = activity.findViewById(R.id.tvDurationValue);
        etAutoRadius = activity.findViewById(R.id.etAutoRadius);
        sliderMaxPlaces = activity.findViewById(R.id.sliderMaxPlaces);
        tvMaxPlacesValue = activity.findViewById(R.id.tvMaxPlacesValue);
        sliderDurationHours = activity.findViewById(R.id.sliderDurationHours);
        switchSnack = activity.findViewById(R.id.switchSnack);

        if (bottomSheet != null) {
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        } else {
            bottomSheetBehavior = null;
        }
    }
}
