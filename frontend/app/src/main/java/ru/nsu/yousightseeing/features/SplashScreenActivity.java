package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

import ru.nsu.yousightseeing.R;

public class SplashScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen); // твой layout с логотипом

        ImageView logo = findViewById(R.id.logo);

        // Анимация: логотип поднимается вверх на 150px
        TranslateAnimation logoMoveUp = new TranslateAnimation(
                0, 0,   // X: не меняем
                0, -150 // Y: вверх
        );
        logoMoveUp.setDuration(800); // длительность анимации
        logoMoveUp.setFillAfter(true); // сохраняем конечное положение

        // Слушатель окончания анимации
        logoMoveUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) { }

            @Override
            public void onAnimationEnd(Animation animation) {
                // Переход на SplashActivity с логикой
                Intent intent = new Intent(SplashScreenActivity.this, SplashActivity.class);
                startActivity(intent);
                finish(); // закрываем заставку
            }

            @Override
            public void onAnimationRepeat(Animation animation) { }
        });

        // Задержка перед запуском анимации (1 сек)
        logo.postDelayed(() -> logo.startAnimation(logoMoveUp), 1000);
    }
}
