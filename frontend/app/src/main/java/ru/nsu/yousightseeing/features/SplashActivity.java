package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Не нужен layout, просто решаем, куда перейти
        navigateNext();
    }

    private void navigateNext() {
        boolean loggedIn = AuthActivity.isAuthenticated(); // проверка авторизации

        // Получаем выбранные пользователем категории
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> categories = prefs.getStringSet("categories", new HashSet<>());

        Intent nextIntent;

        if (!loggedIn) {
            // Пользователь не вошёл — идём на экран авторизации
            nextIntent = new Intent(this, AuthActivity.class);
        } else if (categories == null || categories.isEmpty()) {
            // Пользователь вошёл, но не выбрал категории — идём на экран выбора
            nextIntent = new Intent(this, PreferencesActivity.class);
        } else {
            // Пользователь вошёл и категории выбраны — идём на главный экран или PermissionActivity
            nextIntent = new Intent(this, PermissionActivity.class);
        }

        startActivity(nextIntent);
        finish(); // закрываем SplashActivity
    }
}
