package com.example.yousightseeingdesign;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class CategoriesActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        Button btnContinue = findViewById(R.id.btnContinue);
        if (btnContinue != null) {
            Log.d("Categories", "Кнопка btnContinue найдена!");
            btnContinue.setOnClickListener(v -> {
                Log.d("Categories", "Нажата кнопка - переходим на PermissionActivity");
                startActivity(new Intent(CategoriesActivity.this, PermissionActivity.class));
            });
        } else {
            Log.e("Categories", "Кнопка btnContinue НЕ НАЙДЕНА в layout!");
        }
    }
}
