package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName  = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        loadProfile();

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
