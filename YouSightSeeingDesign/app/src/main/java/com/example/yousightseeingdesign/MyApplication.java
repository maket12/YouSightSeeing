package com.example.yousightseeingdesign;

import android.app.Application;
import com.yandex.mapkit.MapKitFactory;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // ✅ КРИТИЧНО: API ключ ДО initialize!
        MapKitFactory.setApiKey("64f967c6-2825-410c-8ec1-2219f2852080"); // ТВОЙ ключ
        MapKitFactory.initialize(this);
    }
}
