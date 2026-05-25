package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashSet;
import java.util.Set;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.AuthApi;

public class SplashScreenActivity extends AppCompatActivity {

    private boolean animationFinished = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        ImageView logo = findViewById(R.id.logo);

        TranslateAnimation logoMoveUp = new TranslateAnimation(
                0, 0,
                0, -150
        );

        logoMoveUp.setDuration(800);
        logoMoveUp.setFillAfter(true);

        logoMoveUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                animationFinished = true;
                checkAuthAndNavigate();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        logo.postDelayed(() -> logo.startAnimation(logoMoveUp), 1000);
    }

    private void checkAuthAndNavigate() {
        if (!animationFinished) return;

        if (!AuthActivity.isAuthenticated()) {
            openAuth();
            return;
        }

        AuthApi.refreshTokens(this, new AuthApi.RefreshCallback() {
            @Override
            public void onSuccess(String accessToken, String refreshToken) {
                runOnUiThread(() -> {
                    AuthActivity.saveTokens(accessToken, refreshToken);
                    navigateAfterAuth();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    AuthActivity.clearTokens();

                    Toast.makeText(
                            SplashScreenActivity.this,
                            "Сессия истекла, войдите снова",
                            Toast.LENGTH_SHORT
                    ).show();

                    openAuth();
                });
            }
        });
    }

    private void navigateAfterAuth() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Set<String> categories = prefs.getStringSet("categories", new HashSet<>());

        Intent nextIntent;

        if (categories == null || categories.isEmpty()) {
            nextIntent = new Intent(this, PreferencesActivity.class);
        } else {
            nextIntent = new Intent(this, PermissionActivity.class);
        }

        startActivity(nextIntent);
        finish();
    }

    private void openAuth() {
        Intent intent = new Intent(this, AuthActivity.class);
        startActivity(intent);
        finish();
    }
}