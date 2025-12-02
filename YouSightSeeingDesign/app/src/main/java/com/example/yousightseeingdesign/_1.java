package com.example.yousightseeingdesign;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;

public class _1 extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_1);

        ImageView logo = findViewById(R.id.logo);

        // Создаем анимацию: логотип поднимается вверх на 150px
        TranslateAnimation logoMoveUp = new TranslateAnimation(
                0, 0,   // X: не меняем
                0, -150 // Y: вверх
        );
        logoMoveUp.setDuration(800); // длительность анимации
        logoMoveUp.setFillAfter(true); // сохраняем конечное положение

        // Слушатель окончания анимации
        logoMoveUp.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                // можно ничего не делать
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                // Переход на RegisterActivity после завершения анимации
                Intent intent = new Intent(_1.this, RegisterActivity.class);
                startActivity(intent);
                finish(); // закрываем заставку
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
                // не используется
            }
        });

        // Задержка перед запуском анимации (например, 1 секунда)
        logo.postDelayed(() -> logo.startAnimation(logoMoveUp), 1000);
    }
}
