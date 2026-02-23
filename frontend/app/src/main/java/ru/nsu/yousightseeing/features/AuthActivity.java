package ru.nsu.yousightseeing.features;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import ru.nsu.yousightseeing.R;
import ru.nsu.yousightseeing.api.AuthApi;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";
    private static final String PREFS_NAME = "auth_tokens";

    private GoogleSignInClient mGoogleSignInClient;
    private ImageButton btnGoogleSignIn; // теперь ImageButton

    private static Context appContext;

    public static void initAppContext(Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appContext = getApplicationContext();
        setContentView(R.layout.activity_auth);

        // Настройка Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestServerAuthCode(getString(R.string.default_web_client_id))
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Привязка к ImageButton из нового XML
        btnGoogleSignIn = findViewById(R.id.btnGoogleRegister);
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

                                AuthApi.googleAuth(googleIdToken, new AuthApi.GoogleAuthCallback() {
                                    @Override
                                    public void onSuccess(String access, String refresh) {
                                        runOnUiThread(() -> {
                                            saveTokensFromBackend(access, refresh);
                                            Toast.makeText(AuthActivity.this, "Успешный вход", Toast.LENGTH_SHORT).show();
                                            navigateToMain();
                                        });
                                    }

                                    @Override
                                    public void onError(String message) {
                                        runOnUiThread(() -> {
                                            Toast.makeText(AuthActivity.this, message, Toast.LENGTH_LONG).show();
                                            btnGoogleSignIn.setEnabled(true);
                                        });
                                    }
                                });

                            } catch (ApiException e) {
                                Log.w(TAG, "Google sign in failed", e);
                                Toast.makeText(this, "Ошибка входа: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                btnGoogleSignIn.setEnabled(true);
                            }
                        } else {
                            btnGoogleSignIn.setEnabled(true);
                        }
                    });

    private void saveTokensFromBackend(String accessToken, String refreshToken) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor ed = prefs.edit()
                .putString("access_token", accessToken);

        if (refreshToken != null && !refreshToken.isEmpty()) {
            ed.putString("refresh_token", refreshToken);
        }
        ed.apply();
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
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }
}
