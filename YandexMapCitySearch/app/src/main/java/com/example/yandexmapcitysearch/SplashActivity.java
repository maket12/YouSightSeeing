package com.example.yandexmapcitysearch;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 1500; // 1.5 секунды

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Небольшая задержка для показа splash screen
        new Handler().postDelayed(() -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

            Intent intent;
            if (currentUser != null) {
                boolean hasPreferences = getSharedPreferences("user_prefs", MODE_PRIVATE)
                        .contains("categories");

                if (hasPreferences) {
                    intent = new Intent(SplashActivity.this, MainActivity.class);
                } else {
                    intent = new Intent(SplashActivity.this, PreferencesActivity.class);
                }
            } else {
                intent = new Intent(SplashActivity.this, AuthActivity.class);
            }

            startActivity(intent);
            finish();
        }, SPLASH_DURATION);
    }
}
