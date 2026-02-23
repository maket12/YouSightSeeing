package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import ru.nsu.yousightseeing.R;

public class PreferencesActivity extends AppCompatActivity {

    private ImageButton btnNature, btnActive, btnResort, btnFun, btnHistory, btnShopping, btnUnusual;
    private ImageButton btnContinue;

    private final Map<ImageButton, String> buttonCategoryMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        // Находим кнопки
        btnNature = findViewById(R.id.btnNature);
        btnActive = findViewById(R.id.btnActive);
        btnResort = findViewById(R.id.btnResort);
        btnFun = findViewById(R.id.btnFun);
        btnHistory = findViewById(R.id.btnHistory);
        btnShopping = findViewById(R.id.btnShopping);
        btnUnusual = findViewById(R.id.btnUnusual);
        btnContinue = findViewById(R.id.btnContinue);

        // Заполняем карту категорий
        buttonCategoryMap.put(btnNature, "Природа и свежий воздух");
        buttonCategoryMap.put(btnActive, "Активные приключения");
        buttonCategoryMap.put(btnResort, "Курорты и здоровый отдых");
        buttonCategoryMap.put(btnFun, "Досуг и развлечения");
        buttonCategoryMap.put(btnHistory, "История, культура");
        buttonCategoryMap.put(btnShopping, "Места для шопинга");
        buttonCategoryMap.put(btnUnusual, "Необычные и скрытые уголки города");

        // Делаем кнопки кликабельными с маской
        for (ImageButton btn : buttonCategoryMap.keySet()) {
            btn.setOnClickListener(v -> toggleButtonDarkMask(btn));
        }

        // Кнопка продолжить
        btnContinue.setOnClickListener(v -> {
            List<String> selectedCategories = getSelectedCategories();
            if (selectedCategories.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одну категорию", Toast.LENGTH_SHORT).show();
                return;
            }
            savePreferences(selectedCategories);
            navigateToMain();
        });
    }

    // Получаем выбранные категории
    private List<String> getSelectedCategories() {
        List<String> categories = new ArrayList<>();
        for (Map.Entry<ImageButton, String> entry : buttonCategoryMap.entrySet()) {
            if (entry.getKey().isSelected()) {
                categories.add(entry.getValue());
            }
        }
        return categories;
    }

    // Сохраняем маску выбора (темная) на кнопке
    private void toggleButtonDarkMask(ImageButton btn) {
        btn.setSelected(!btn.isSelected());
        if (btn.isSelected()) {
            btn.setForeground(getDrawable(R.drawable.dark_mask));
        } else {
            btn.setForeground(null);
        }
    }


    // Сохраняем в SharedPreferences и Firestore
    private void savePreferences(List<String> categories) {
        getSharedPreferences("user_prefs", MODE_PRIVATE)
                .edit()
                .putStringSet("categories", new HashSet<>(categories))
                .apply();

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
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }
}
