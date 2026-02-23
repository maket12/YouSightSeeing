package ru.nsu.yousightseeing.features;

import android.content.Context;
import com.yandex.mapkit.geometry.Point;
import ru.nsu.yousightseeing.api.PlacesApi;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GeoapifyClient {
    private final Context context;

    private static final Map<String, String> CATEGORY_MAP = new HashMap<>();
    static {
        CATEGORY_MAP.put("Природа и свежий воздух", "leisure.park");
        CATEGORY_MAP.put("Активные приключения", "sport.sports_centre");
        CATEGORY_MAP.put("Курорты и здоровый отдых", "leisure.spa");
        CATEGORY_MAP.put("Досуг и развлечения", "tourism.attraction");
        CATEGORY_MAP.put("История, культура", "tourism.sights");
        CATEGORY_MAP.put("Места для шопинга", "commercial.shopping_mall");
        CATEGORY_MAP.put("Необычные и скрытые уголки города", "tourism.sights");
    }


    public GeoapifyClient(Context context) {
        this.context = context;
    }

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

    /**
     * Преобразует пользовательские категории в Geoapify формат
     */
    private Set<String> mapUserCategories(Set<String> userCategories) {
        Set<String> geoapifyCategories = new HashSet<>();
        for (String userCat : userCategories) {
            String geoCat = CATEGORY_MAP.get(userCat);
            if (geoCat != null) {
                geoapifyCategories.add(geoCat);
            }
        }
        // Дефолтные категории, если пользовательские пустые
        if (geoapifyCategories.isEmpty()) {
            geoapifyCategories.add("tourism.attraction");
            geoapifyCategories.add("leisure.park");
        }
        return geoapifyCategories;
    }

    public void getNearbyPlaces(double lat, double lon, Set<String> categories, GeoapifyCallback callback) {
        // Маппинг категорий!
        Set<String> geoapifyCategories = mapUserCategories(categories);

        PlacesApi.searchAround(context, lat, lon, 5000, geoapifyCategories, 20,
                new PlacesApi.PlacesCallback() {
                    @Override
                    public void onSuccess(List<PlacesApi.Place> placesApi) {
                        List<GeoapifyClient.Place> geoPlaces = new ArrayList<>();
                        for (PlacesApi.Place p : placesApi) {
                            Point point = new Point(p.lat, p.lon);
                            geoPlaces.add(new GeoapifyClient.Place(p.name, point));
                        }
                        callback.onSuccess(geoPlaces);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onError(message);
                    }
                }
        );
    }
}
