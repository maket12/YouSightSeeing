package com.example.yousightseeingdesign;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        Button btnGoogle = findViewById(R.id.btnGoogleRegister);
        btnGoogle.setOnClickListener(v -> {
            // Переход на выбор категорий
            startActivity(new Intent(RegisterActivity.this, CategoriesActivity.class));
        });
    }
}
