package ru.nsu.yousightseeing.features;

import android.app.Application;
import com.yandex.mapkit.MapKitFactory;

import ru.nsu.yousightseeing.BuildConfig;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY);
        AuthActivity.initAppContext(getApplicationContext());
    }
}