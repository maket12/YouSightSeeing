package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.AuthApi;
import ru.nsu.yousightseeing.api.UserApi;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName;
    private TextView tvEmail;
    private CheckBox cbCatActive;
    private CheckBox cbCatShopping;
    private CheckBox cbCatHistory;
    private CheckBox cbCatFun;
    private CheckBox cbCatNature;
    private CheckBox cbCatResorts;
    private CheckBox cbCatHidden;

    private static final String PREFS = "user_prefs";
    private static final String PREF_CATEGORIES = "categories";

    private static final String CAT_ACTIVE = "Активные приключения";
    private static final String CAT_SHOPPING = "Место для шоппинга";
    private static final String CAT_HISTORY = "История, культура";
    private static final String CAT_FUN = "Досуг и развлечения";
    private static final String CAT_NATURE = "Природа и свежий воздух";
    private static final String CAT_RESORTS = "Курорты и здоровый отдых";
    private static final String CAT_HIDDEN = "Необычные и скрытые уголки города";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName  = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        cbCatActive = findViewById(R.id.cbCatActive);
        cbCatShopping = findViewById(R.id.cbCatShopping);
        cbCatHistory = findViewById(R.id.cbCatHistory);
        cbCatFun = findViewById(R.id.cbCatFun);
        cbCatNature = findViewById(R.id.cbCatNature);
        cbCatResorts = findViewById(R.id.cbCatResorts);
        cbCatHidden = findViewById(R.id.cbCatHidden);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        loadProfile();
        bindCategories();

        // Выйти из аккаунта
        btnLogout.setOnClickListener(v -> {
            AuthApi.logout(ProfileActivity.this, new AuthApi.LogoutCallback() {
                @Override
                public void onSuccess() {
                    // локальные токены уже очищены внутри logout()
                    Intent intent = new Intent(ProfileActivity.this, AuthActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                }

                @Override
                public void onError(String message) {
                   Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                }
            });
        });


        // Вернуться в главное меню
        btnGoHome.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void bindCategories() {
        // загрузка сохранённых
        var prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        var saved = prefs.getStringSet(PREF_CATEGORIES, java.util.Collections.emptySet());
        if (saved == null) saved = java.util.Collections.emptySet();

        cbCatActive.setChecked(saved.contains(CAT_ACTIVE));
        cbCatShopping.setChecked(saved.contains(CAT_SHOPPING));
        cbCatHistory.setChecked(saved.contains(CAT_HISTORY));
        cbCatFun.setChecked(saved.contains(CAT_FUN));
        cbCatNature.setChecked(saved.contains(CAT_NATURE));
        cbCatResorts.setChecked(saved.contains(CAT_RESORTS));
        cbCatHidden.setChecked(saved.contains(CAT_HIDDEN));

        android.widget.CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> saveCategories();
        cbCatActive.setOnCheckedChangeListener(listener);
        cbCatShopping.setOnCheckedChangeListener(listener);
        cbCatHistory.setOnCheckedChangeListener(listener);
        cbCatFun.setOnCheckedChangeListener(listener);
        cbCatNature.setOnCheckedChangeListener(listener);
        cbCatResorts.setOnCheckedChangeListener(listener);
        cbCatHidden.setOnCheckedChangeListener(listener);
    }

    private void saveCategories() {
        java.util.Set<String> selection = new java.util.HashSet<>();
        if (cbCatActive.isChecked()) selection.add(CAT_ACTIVE);
        if (cbCatShopping.isChecked()) selection.add(CAT_SHOPPING);
        if (cbCatHistory.isChecked()) selection.add(CAT_HISTORY);
        if (cbCatFun.isChecked()) selection.add(CAT_FUN);
        if (cbCatNature.isChecked()) selection.add(CAT_NATURE);
        if (cbCatResorts.isChecked()) selection.add(CAT_RESORTS);
        if (cbCatHidden.isChecked()) selection.add(CAT_HIDDEN);

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putStringSet(PREF_CATEGORIES, selection)
                .apply();
    }

    private void loadProfile() {
        UserApi.getMe(this, new UserApi.UserCallback() {
            @Override
            public void onSuccess(JSONObject rootJson) {
                runOnUiThread(() -> {
                    Log.d("PROFILE", "userJson = " + rootJson.toString());

                    JSONObject user = rootJson.optJSONObject("user");
                    if (user == null) {
                        tvName.setText("Имя не задано");
                        tvEmail.setText("");
                        return;
                    }

                    String fullName = user.optString("full_name", "");
                    if (fullName == null || fullName.isEmpty() || "null".equalsIgnoreCase(fullName)) {
                        String first = user.optString("first_name", "");
                        String last  = user.optString("last_name", "");
                        fullName = (first + " " + last).trim();
                    }
                    if (fullName.isEmpty()) {
                        fullName = "Имя не задано";
                    }

                    tvName.setText(fullName);
                    tvEmail.setText(user.optString("email", ""));
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() ->
                        Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show()
                );
            }
        });
    }
}
