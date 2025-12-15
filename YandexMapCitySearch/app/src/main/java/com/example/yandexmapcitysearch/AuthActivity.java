package com.example.yandexmapcitysearch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthActivity extends AppCompatActivity {
    private static final String TAG = "AuthActivity";
    private static final String PREFS_NAME = "auth_tokens";
    private GoogleSignInClient mGoogleSignInClient;
    private Button btnGoogleSignIn;
    private static Context appContext;
    private final OkHttpClient httpClient = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void initAppContext(Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestServerAuthCode(getString(R.string.default_web_client_id))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnGoogleSignIn.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        btnGoogleSignIn.setEnabled(false);
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private final ActivityResultLauncher<Intent> signInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Task<GoogleSignInAccount> task =
                                    GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                            try {
                                GoogleSignInAccount account = task.getResult(ApiException.class);
                                String googleIdToken = account.getIdToken();
                                if (googleIdToken == null) {
                                    Toast.makeText(this, "Google токен не получен", Toast.LENGTH_SHORT).show();
                                    btnGoogleSignIn.setEnabled(true);
                                    return;
                                }
                                sendGoogleTokenToBackend(googleIdToken);
                            } catch (ApiException e) {
                                Log.w(TAG, "Google sign in failed", e);
                                Toast.makeText(this, "Ошибка входа: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                btnGoogleSignIn.setEnabled(true);
                            }
                        } else {
                            btnGoogleSignIn.setEnabled(true);
                        }
                    });

    /**
     * POST /auth/google
     * body: { "google_token": "<google_token>" }
     * ожидаем в ответе JSON с access_token и refresh_token
     */
    private void sendGoogleTokenToBackend(String googleIdToken) {
        String url = "http://10.0.2.2:8080/auth/google";

        JSONObject json = new JSONObject();
        try {
            json.put("google_token", googleIdToken);
        } catch (JSONException e) {
            Toast.makeText(this, "Ошибка подготовки запроса", Toast.LENGTH_SHORT).show();
            btnGoogleSignIn.setEnabled(true);
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "auth/google failure", e);
                    Toast.makeText(AuthActivity.this,
                            "Сервер недоступен: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    btnGoogleSignIn.setEnabled(true);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String respBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        Toast.makeText(AuthActivity.this,
                                "Ошибка авторизации: " + response.code() + "\n" + respBody,
                                Toast.LENGTH_LONG).show();
                        btnGoogleSignIn.setEnabled(true);
                    });
                    return;
                }

                try {
                    JSONObject respJson = new JSONObject(respBody);

                    // плоский ответ: access_token, refresh_token, user
                    String access  = respJson.getString("access_token");
                    String refresh = respJson.optString("refresh_token", null);

                    saveTokensFromBackend(access, refresh);

                    runOnUiThread(() -> {
                        Toast.makeText(AuthActivity.this, "Успешный вход", Toast.LENGTH_SHORT).show();
                        navigateToMain();
                    });
                } catch (JSONException e) {
                    Log.e(TAG, "parse auth/google response error", e);
                    runOnUiThread(() -> {
                        Toast.makeText(AuthActivity.this,
                                "Некорректный ответ сервера",
                                Toast.LENGTH_LONG).show();
                        btnGoogleSignIn.setEnabled(true);
                    });
                }
            }
        });
    }
    private void saveTokensFromBackend(String accessToken, String refreshToken) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .apply();
    }

    public static String getAccessToken() {
        if (appContext == null) return null;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("access_token", null);
    }

    public static String getRefreshToken() {
        if (appContext == null) return null;
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("refresh_token", null);
    }

    public static boolean isAuthenticated() {
        if (appContext == null) return false;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains("access_token") && prefs.contains("refresh_token");
    }

    public static String getUserEmail() {
        if (appContext == null) return null;
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString("email", null);
    }

    private void navigateToMain() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        boolean hasPreferences = prefs.contains("categories");

        Intent intent;
        if (hasPreferences) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, PreferencesActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
