package com.example.yandexmapcitysearch;

import android.app.Application;
import com.yandex.mapkit.MapKitFactory;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY);
        AuthActivity.initAppContext(getApplicationContext());
    }
}