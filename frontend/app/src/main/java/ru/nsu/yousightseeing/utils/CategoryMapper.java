package ru.nsu.yousightseeing.utils;

import java.util.HashSet;
import java.util.Set;

public class CategoryMapper {
    public static Set<String> mapUserCategoriesToBackend(Set<String> userCategories) {
        Set<String> mapped = new HashSet<>();

        for (String category : userCategories) {
            switch (category) {
                case "Природа и свежий воздух":
                    mapped.add("leisure.park");
                    break;
                case "Активные приключения":
                    mapped.add("sport.sports_centre");
                    break;
                case "Курорты и здоровый отдых":
                    mapped.add("leisure.spa");
                    break;
                case "Досуг и развлечения":
                    mapped.add("tourism.attraction");
                    break;
                case "История, культура":
                    mapped.add("tourism.sights");
                    break;
                case "Места для шопинга":
                    mapped.add("commercial.shopping_mall");
                    break;
                case "Необычные и скрытые уголки города":
                    mapped.add("tourism.sights");
                    break;
            }
        }

        if (mapped.isEmpty()) {
            mapped.add("tourism.attraction");
            mapped.add("leisure.park");
        }

        return mapped;
    }
}