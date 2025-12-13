package com.example.yousightseeingdesign;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class PermissionActivity extends AppCompatActivity {

    private boolean allowGeo = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission);

        // ИЛИ использовать весь View через setOnTouchListener
        View contentView = findViewById(android.R.id.content);
        contentView.setOnTouchListener((v, event) -> {
            showGeoPermissionDialog();
            return true;
        });
    }

    private void showGeoPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Геопозиция")
                .setMessage("Разрешить приложению использовать вашу геопозицию?")
                .setPositiveButton("Да", (dialog, which) -> {
                    allowGeo = true;
                    openMapActivity();
                })
                .setNegativeButton("Нет", (dialog, which) -> {
                    allowGeo = false;
                    openMapActivity();
                })
                .setCancelable(false)
                .show();
    }

    private void openMapActivity() {
        Intent intent = new Intent(PermissionActivity.this, MainActivity.class);
        intent.putExtra("ALLOW_GEO", allowGeo);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
