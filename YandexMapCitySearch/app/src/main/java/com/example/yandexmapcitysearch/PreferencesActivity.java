package com.example.yandexmapcitysearch;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class PreferencesActivity extends AppCompatActivity {

    private ChipGroup chipGroupCategories;
    private Button btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        chipGroupCategories = findViewById(R.id.chipGroupCategories);
        btnContinue = findViewById(R.id.btnContinue);

        btnContinue.setOnClickListener(v -> {
            List<String> selectedCategories = getSelectedCategories();

            if (selectedCategories.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одну категорию", Toast.LENGTH_SHORT).show();
                return;
            }

            // Сохранение выбранных категорий
            savePreferences(selectedCategories);

            // Переход на главный экран
            navigateToMain();
        });
    }

    private List<String> getSelectedCategories() {
        List<String> categories = new ArrayList<>();
        for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
            Chip chip = (Chip) chipGroupCategories.getChildAt(i);
            if (chip.isChecked()) {
                categories.add(chip.getText().toString());
            }
        }
        return categories;
    }

    private void savePreferences(List<String> categories) {
        // 1. Сохранение локально (быстро, офлайн)
        getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit()
                .putStringSet("categories", new HashSet<>(categories))
                .apply();

        // 2. Синхронизация с Firestore (опционально)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            Map<String, Object> userPrefs = new HashMap<>();
            userPrefs.put("categories", categories);
            userPrefs.put("updatedAt", FieldValue.serverTimestamp());

            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.getUid())
                    .set(userPrefs, SetOptions.merge());
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
