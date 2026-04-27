package ru.nsu.yousightseeing.utils;

import androidx.annotation.NonNull;

import com.yandex.mapkit.geometry.Point;
import com.yandex.mapkit.map.InputListener;
import com.yandex.mapkit.map.Map;

public class MapInputHelper implements InputListener {

    public interface MapTapListener {
        void onMapTap(Point point);
        void onMapLongTap(Point point);
    }

    private final MapTapListener listener;

    public MapInputHelper(MapTapListener listener) {
        this.listener = listener;
    }

    @Override
    public void onMapTap(@NonNull Map map, @NonNull Point point) {
        if (listener != null) {
            listener.onMapTap(point);
        }
    }

    @Override
    public void onMapLongTap(@NonNull Map map, @NonNull Point point) {
        if (listener != null) {
            listener.onMapLongTap(point);
        }
    }
}