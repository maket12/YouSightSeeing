package ru.nsu.yousightseeing.features.main;

import android.content.Intent;

public interface MainContract {
    interface View {
        void showToast(String message);
        void startActivity(Class<?> activityClass);
    }

    interface Presenter {
        void initialize(boolean allowGeo);
        void onRequestPermissionsResult(int requestCode, int[] grantResults);
        void onStart();
        void onStop();
    }
}
