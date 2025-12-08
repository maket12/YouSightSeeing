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
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

public class AuthActivity extends AppCompatActivity {
    private static final String TAG = "AuthActivity";
    private static final String PREFS_NAME = "auth_tokens";
    private GoogleSignInClient mGoogleSignInClient;
    private Button btnGoogleSignIn;
    private static Context appContext;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Инициализируем статический контекст
        appContext = getApplicationContext();

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

    private final ActivityResultLauncher<Intent> signInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        saveGoogleTokens(account);
                        Log.d(TAG, "Токены сохранены, переход к главному экрану");
                        navigateToMain();
                    } catch (ApiException e) {
                        Log.w(TAG, "Google sign in failed", e);
                        Toast.makeText(this, "Ошибка входа", Toast.LENGTH_SHORT).show();
                        btnGoogleSignIn.setEnabled(true);
                    }
                } else {
                    btnGoogleSignIn.setEnabled(true);
                }
            });

    private void saveGoogleTokens(GoogleSignInAccount account) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String accessToken = account.getIdToken();
        String refreshToken = account.getServerAuthCode();

        editor.putString("access_token", accessToken);
        editor.putString("refresh_token", refreshToken);
        editor.putString("user_id", account.getId());
        editor.putString("email", account.getEmail());
        editor.putString("name", account.getDisplayName());
        editor.putLong("access_token_expiry", System.currentTimeMillis() + 3600000);

        editor.apply();
    }

    /**
     * СТАТИЧЕСКИЕ МЕТОДЫ - работают из любой Activity
     */
    public static String getAccessToken() {
        if (appContext == null) return null;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        long expiry = prefs.getLong("access_token_expiry", 0);
        if (System.currentTimeMillis() > expiry) {
            Log.w(TAG, "Access token истёк");
            return null;
        }
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
        long expiry = prefs.getLong("access_token_expiry", 0);
        return System.currentTimeMillis() < expiry && prefs.contains("access_token");
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
