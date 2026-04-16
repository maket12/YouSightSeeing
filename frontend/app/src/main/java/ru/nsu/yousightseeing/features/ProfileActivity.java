package ru.nsu.yousightseeing.features;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import org.json.JSONObject;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.AuthApi;
import ru.nsu.yousightseeing.api.UserApi;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName;
    private TextView tvEmail;
    private ImageView ivAvatar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        tvName  = findViewById(R.id.tvName);
        tvEmail = findViewById(R.id.tvEmail);
        ivAvatar = findViewById(R.id.ivAvatar);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        loadProfile();

        // Выйти
        btnLogout.setOnClickListener(v -> {
            AuthApi.logout(ProfileActivity.this, new AuthApi.LogoutCallback() {
                @Override
                public void onSuccess() {
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

        // Назад
        btnGoHome.setOnClickListener(v -> finish());
    }

    private void loadProfile() {
        UserApi.getMe(this, new UserApi.UserCallback() {
            @Override
            public void onSuccess(JSONObject user) {
                runOnUiThread(() -> {
                    Log.d("PROFILE", "userJson = " + user.toString());

                    String fullName = user.optString("full_name", "");
                    if (fullName == null || fullName.isEmpty() || "null".equalsIgnoreCase(fullName)) {
                        String first = user.optString("first_name", "");
                        String last = user.optString("last_name", "");
                        fullName = (first + " " + last).trim();
                    }

                    if (fullName.isEmpty()) {
                        fullName = AuthActivity.getUserName();
                    }

                    if (fullName == null || fullName.isEmpty()) {
                        fullName = "Имя не задано";
                    }

                    String email = user.optString("email", "");
                    if (email == null || email.isEmpty()) {
                        email = AuthActivity.getUserEmail();
                    }

                    if (email == null) {
                        email = "";
                    }

                    String photoUrl = user.optString("picture", "");
                    if (photoUrl == null || photoUrl.isEmpty()) {
                        photoUrl = AuthActivity.getUserPhoto();
                    }

                    Log.d("PROFILE", "photoUrl = " + photoUrl);

                    tvName.setText(fullName);
                    tvEmail.setText(email);

                    if (photoUrl != null && !photoUrl.isEmpty()) {
                        Glide.with(ProfileActivity.this)
                                .load(photoUrl)
                                .circleCrop()
                                .placeholder(R.mipmap.ic_launcher_round)
                                .error(R.mipmap.ic_launcher_round)
                                .into(ivAvatar);
                    } else {
                        ivAvatar.setImageResource(R.mipmap.ic_launcher_round);
                    }
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